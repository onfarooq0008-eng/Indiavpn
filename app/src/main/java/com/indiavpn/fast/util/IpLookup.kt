package com.indiavpn.fast.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Finds the device's current public IP. Asks https://api.ipify.org/ first, then other public services and the app's backend, so
 * one dead endpoint never leaves the Home screen without an address. Returns null if all fail.
 */
object IpLookup {
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val IPV6 = Regex("""^[0-9a-fA-F:]{3,45}$""")

    private val PUBLIC_SOURCES = listOf(
        "https://api.ipify.org/",          // primary: plain-text public IP
        "https://1.1.1.1/cdn-cgi/trace",   // fallbacks
        "https://icanhazip.com"
    )

    suspend fun find(backendIp: suspend () -> String, attempts: Int = 3): String? {
        repeat(attempts) { attempt ->
            for (url in PUBLIC_SOURCES) {
                val ip = fetch(url)
                if (ip != null) return ip
            }
            val fromBackend = try {
                backendIp()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (isIp(fromBackend)) return fromBackend!!.trim()
            if (attempt < attempts - 1) delay(800) // the tunnel may need a moment to start routing
        }
        return null
    }

    private fun isIp(s: String?): Boolean {
        val v = s?.trim().orEmpty()
        return IPV4.matches(v) || (v.contains(':') && IPV6.matches(v))
    }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val body = r.body?.string()?.trim().orEmpty()
                val ip = when {
                    body.startsWith("{") -> JSONObject(body).optString("ip")
                    body.contains("ip=") -> body.lineSequence().firstOrNull { it.startsWith("ip=") }?.removePrefix("ip=")
                    else -> body
                }
                ip?.trim()?.takeIf { isIp(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
