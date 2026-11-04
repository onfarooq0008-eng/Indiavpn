package com.indiavpn.fast.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Server reachability check. WireGuard is UDP-only and silently ignores everything that is not a valid
 * handshake, so we probe TCP ports instead. A host counts as ONLINE if any probe port either accepts the
 * connection or actively refuses it (a refusal means the machine answered, so it is up). Only a timeout /
 * unreachable result on every port means OFFLINE. The result is used purely as online/offline -- the
 * measured milliseconds are never shown to the user.
 */
object PingUtil {
    const val OFFLINE = -2
    private val PROBE_PORTS = intArrayOf(22, 443, 80)

    // Blocking socket calls cannot be interrupted, so probes run in their own scope and the caller only
    // waits with a timeout.
    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** @return round-trip ms (>= 0) when the host answered, [OFFLINE] otherwise. [port] is tried first. */
    suspend fun pingHost(host: String, port: Int, timeoutMs: Int = 1500): Int {
        if (host.isBlank() || host == "0.0.0.0") return OFFLINE
        val result = CompletableDeferred<Int>()
        probeScope.launch {
            val address = try {
                InetAddress.getByName(host) // resolve once, shared by every port probe
            } catch (_: Exception) {
                result.complete(OFFLINE)
                return@launch
            }
            val ports = (listOf(port) + PROBE_PORTS.toList()).distinct()
            val pending = java.util.concurrent.atomic.AtomicInteger(ports.size)
            for (p in ports) {
                launch {
                    val ms = probe(address, p, timeoutMs)
                    if (ms >= 0) result.complete(ms)
                    else if (pending.decrementAndGet() == 0) result.complete(OFFLINE)
                }
            }
        }
        return withTimeoutOrNull(timeoutMs + 700L) { result.await() } ?: OFFLINE
    }

    private fun probe(address: InetAddress, port: Int, timeoutMs: Int): Int {
        val start = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(address, port), timeoutMs) }
            ((System.nanoTime() - start) / 1_000_000).toInt()
        } catch (e: ConnectException) {
            // "Connection refused" = the host replied with a reset, so it is alive; a timeout is not.
            if (e.message?.contains("refused", ignoreCase = true) == true)
                ((System.nanoTime() - start) / 1_000_000).toInt()
            else OFFLINE
        } catch (_: Exception) {
            OFFLINE
        }
    }

    /** Ping many hosts concurrently, returns map of id -> ms ([OFFLINE] if unreachable). */
    suspend fun pingAll(targets: List<Triple<String, String, Int>>): Map<String, Int> = coroutineScope {
        targets.map { (id, host, port) -> async { id to pingHost(host, port) } }.awaitAll().toMap()
    }
}
