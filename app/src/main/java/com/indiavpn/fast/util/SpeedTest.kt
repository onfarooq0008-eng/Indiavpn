package com.indiavpn.fast.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Speed test against Cloudflare's public endpoint (speed.cloudflare.com). With the VPN connected the
 * numbers reflect the tunnel. Download and upload each run [STREAMS] parallel HTTP/1.1 connections for
 * up to [STEP_NANOS], because a single stream rarely fills a fast link. [onProgress] is always
 * delivered on the main thread with live Mbps.
 */
object SpeedTest {
    private const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes="
    private const val UP_URL = "https://speed.cloudflare.com/__up"
    private const val STEP_NANOS = 7_000_000_000L
    private const val REPORT_NANOS = 250_000_000L
    private const val STREAMS = 4
    private const val DOWN_CHUNK = 25_000_000L   // per request; a worker simply asks again when it ends
    private const val UP_CHUNK = 2_000_000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // HTTP/1.1 = one TCP connection per stream; HTTP/2 would multiplex all streams onto one.
            .protocols(listOf(Protocol.HTTP_1_1))
            .dispatcher(Dispatcher().apply { maxRequests = 16; maxRequestsPerHost = 16 })
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private val main = Handler(Looper.getMainLooper())

    private fun request(url: String) = Request.Builder()
        .url(url)
        .header("Cache-Control", "no-cache")
        .header("Accept-Encoding", "identity")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android) IndiaVPN")

    /** Median round-trip time in ms of a few tiny requests (the first pays for DNS + TLS and is skipped). */
    suspend fun ping(): Int = withContext(Dispatchers.IO) {
        val times = ArrayList<Long>()
        repeat(5) { i ->
            ensureActive()
            val start = System.nanoTime()
            client.newCall(request(DOWN_URL + "0").build()).execute().use { it.body?.bytes() }
            if (i > 0) times.add((System.nanoTime() - start) / 1_000_000)
        }
        times.sorted()[times.size / 2].toInt()
    }

    /** Downloads for up to 7 s on parallel streams and returns the average Mbps. */
    suspend fun download(onProgress: (Double) -> Unit): Double = runStreams(onProgress) { total, deadline ->
        val buffer = ByteArray(64 * 1024)
        while (System.nanoTime() < deadline) {
            client.newCall(request(DOWN_URL + DOWN_CHUNK).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val stream = (response.body ?: throw IOException("Empty response")).byteStream()
                while (System.nanoTime() < deadline) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total.addAndGet(n.toLong())
                }
            }
        }
    }

    /** Uploads for up to 7 s on parallel streams and returns the average Mbps. */
    suspend fun upload(onProgress: (Double) -> Unit): Double = runStreams(onProgress) { total, deadline ->
        val chunk = ByteArray(32 * 1024)
        while (System.nanoTime() < deadline) {
            val body = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaType()
                override fun contentLength() = UP_CHUNK
                override fun writeTo(sink: BufferedSink) {
                    var sent = 0L
                    while (sent < UP_CHUNK) {
                        sink.write(chunk)
                        sink.flush()
                        sent += chunk.size
                        total.addAndGet(chunk.size.toLong())
                    }
                }
            }
            client.newCall(request(UP_URL).post(body).build()).execute().use {
                if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            }
        }
    }

    /**
     * Runs [STREAMS] workers that add to a shared byte counter until the deadline, reports live speed
     * from that counter, and returns the overall average. A worker that fails after the counter has
     * moved is ignored (the others keep going); if nothing was transferred at all, the error is thrown.
     */
    private suspend fun runStreams(
        onProgress: (Double) -> Unit,
        worker: (total: AtomicLong, deadlineNanos: Long) -> Unit
    ): Double = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job]
        val handle = job?.invokeOnCompletion { client.dispatcher.cancelAll() }
        val total = AtomicLong(0)
        val start = System.nanoTime()
        val deadline = start + STEP_NANOS
        var firstError: Exception? = null
        try {
            coroutineScope {
                val workers = (1..STREAMS).map {
                    async {
                        try {
                            worker(total, deadline)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (firstError == null) firstError = e
                        }
                    }
                }
                val reporter = async {
                    var lastBytes = 0L
                    var lastTime = start
                    while (System.nanoTime() < deadline && workers.any { it.isActive }) {
                        kotlinx.coroutines.delay(REPORT_NANOS / 1_000_000)
                        ensureActive()
                        val now = System.nanoTime()
                        val bytes = total.get()
                        emit(onProgress, mbps(bytes - lastBytes, now - lastTime))
                        lastBytes = bytes
                        lastTime = now
                    }
                }
                workers.awaitAll()
                reporter.await()
            }
        } catch (e: IOException) {
            // cancelAll() from a cancelled job surfaces as an IOException; treat that as cancellation
            ensureActive()
            throw e
        } finally {
            handle?.dispose()
        }
        ensureActive()
        val bytes = total.get()
        if (bytes == 0L) throw (firstError ?: IOException("No data transferred"))
        mbps(bytes, System.nanoTime() - start)
    }

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0L) 0.0 else bytes * 8.0 / (nanos / 1e9) / 1e6

    private fun emit(onProgress: (Double) -> Unit, value: Double) {
        main.post { onProgress(value) }
    }
}
