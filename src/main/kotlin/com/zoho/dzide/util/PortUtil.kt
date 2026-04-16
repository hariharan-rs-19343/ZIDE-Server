package com.zoho.dzide.util

import java.net.InetSocketAddress
import java.net.Socket

object PortUtil {

    fun isPortInUse(port: Int, host: String = "localhost", timeoutMs: Int = 1000): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun waitForPort(port: Int, timeoutMs: Long = 30_000, intervalMs: Long = 1000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (isPortInUse(port)) return true
            Thread.sleep(intervalMs)
        }
        return false
    }

    fun waitForPortRelease(port: Int, timeoutMs: Long = 30_000, intervalMs: Long = 1000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!isPortInUse(port)) return true
            Thread.sleep(intervalMs)
        }
        return false
    }

    fun findAvailablePort(basePort: Int, range: Int = 200): Int {
        val start = if (basePort >= 1024) basePort else 5005
        for (port in start until start + range) {
            if (!isPortInUse(port)) return port
        }
        throw IllegalStateException("Unable to find a free port in range $start..${start + range}")
    }
}
