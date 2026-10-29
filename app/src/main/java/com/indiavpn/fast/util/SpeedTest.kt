package com.indiavpn.fast.util

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Internet speed test used by the Speed Test screen. It measures against Cloudflare's public speed-test
 * endpoint (speed.cloudflare.com), so with the VPN connected the numbers reflect speed through the tunnel.
 * Each step is a suspend function; [onProgress] is always called on the main thread with live Mbps.
 */
object SpeedTest {
    private const val DOWN_URL = "https://speed.cloudflare.com/__down?bytes="
    private const val UP_URL = "https://speed.cloudflare.com/__up"
    private const val STEP_NANOS = 7_000_000_000L // each of download / upload runs for at most 7 s
    private const val REPORT_NANOS = 200_000_000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    private val main = Handler(Looper.getMainLooper())

    /** Thrown from inside the upload body to stop sending once the time budget is used up. */
    private class StopSending : IOException("done")

    /** Median round-trip time in ms of a few tiny requests (the first one pays for DNS + TLS, so it is skipped). */
    suspend fun ping(): Int = withContext(Dispatchers.IO) {
        val times = ArrayList<Long>()
        repeat(5) { i ->
            ensureActive()
            val request = Request.Builder().url(DOWN_URL + "0").header("Cache-Control", "no-cache").build()
            val start = System.nanoTime()
            client.newCall(request).execute().use { it.body?.bytes() }
            if (i > 0) times.add((System.nanoTime() - start) / 1_000_000)
        }
        times.sorted()[times.size / 2].toInt()
    }

    /** Downloads for up to 7 s and returns the average Mbps. */
    suspend fun download(onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(DOWN_URL + "100000000").build())
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val stream = (response.body ?: throw IOException("Empty response")).byteStream()
                val buffer = ByteArray(64 * 1024)
                val start = System.nanoTime()
                var windowStart = start
                var windowBytes = 0L
                var total = 0L
                while (true) {
                    ensureActive()
                    val n = stream.read(buffer)
                    if (n < 0) break
                    total += n
                    windowBytes += n
                    val now = System.nanoTime()
                    if (now - windowStart >= REPORT_NANOS) {
                        emit(onProgress, mbps(windowBytes, now - windowStart))
                        windowStart = now
                        windowBytes = 0
                    }
                    if (now - start >= STEP_NANOS) break
                }
                mbps(total, System.nanoTime() - start)
            }
        } finally {
            handle?.dispose()
        }
    }

    /** Uploads zeros for up to 7 s and returns the average Mbps. */
    suspend fun upload(onProgress: (Double) -> Unit): Double = withContext(Dispatchers.IO) {
        val job = currentCoroutineContext()[Job]
        val chunk = ByteArray(32 * 1024)
        val total = 100_000_000L
        val start = System.nanoTime()
        var sent = 0L
        var windowStart = start
        var windowBytes = 0L
        var elapsed = 1L

        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = total
            override fun isOneShot() = true // never replay a half-sent body
            override fun writeTo(sink: BufferedSink) {
                while (sent < total) {
                    val now = System.nanoTime()
                    if (now - start >= STEP_NANOS || job?.isActive == false) {
                        elapsed = now - start
                        throw StopSending()
                    }
                    sink.write(chunk)
                    sink.flush()
                    sent += chunk.size
                    windowBytes += chunk.size
                    val t = System.nanoTime()
                    if (t - windowStart >= REPORT_NANOS) {
                        emit(onProgress, mbps(windowBytes, t - windowStart))
                        windowStart = t
                        windowBytes = 0
                    }
                }
                elapsed = System.nanoTime() - start
            }
        }
        val call = client.newCall(Request.Builder().url(UP_URL).post(body).build())
        val handle = job?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().close()
        } catch (_: StopSending) {
            // expected: the time budget ran out
        } catch (e: IOException) {
            // The server may close the connection once it has enough data; that is fine if we sent something.
            if (sent == 0L) throw e
        } finally {
            handle?.dispose()
        }
        ensureActive()
        if (sent == 0L) throw IOException("Upload failed")
        mbps(sent, elapsed)
    }

    private fun mbps(bytes: Long, nanos: Long): Double =
        if (nanos <= 0L) 0.0 else bytes * 8.0 / (nanos / 1e9) / 1e6

    private fun emit(onProgress: (Double) -> Unit, value: Double) {
        main.post { onProgress(value) }
    }
}
