package com.port80.app.service

import com.port80.app.data.model.EndpointProfile
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tests RTMP connectivity to validate endpoint settings before going live.
 * This does a TCP connection test to the RTMP server — it doesn't actually
 * start streaming, just verifies the server is reachable.
 *
 * The same transport security rules apply here as for live streaming:
 * - RTMPS connections use the system default TrustManager
 * - Plain RTMP connections require user acknowledgment
 */
class ConnectionTester {

    companion object {
        private const val TAG = "ConnectionTester"
        private const val DEFAULT_RTMP_PORT = 1935
        private const val DEFAULT_RTMPS_PORT = 443
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }

    /**
     * Result of a connection test.
     */
    sealed class TestResult {
        data object Success : TestResult()
        data class Failure(val message: String) : TestResult()
    }

    /**
     * Test connectivity to an RTMP endpoint.
     * This runs on a background thread and returns the result.
     *
     * @param profile The endpoint profile to test
     * @return TestResult indicating success or failure with a message
     */
    suspend fun testConnection(profile: EndpointProfile): TestResult {
        return withContext(Dispatchers.IO) {
            try {
                // Parse the URL to get host and port
                val url = profile.rtmpUrl.trim()
                val urlValidation = TransportSecurity.validateUrl(url)
                if (urlValidation != null) {
                    return@withContext TestResult.Failure(urlValidation)
                }

                val (host, port) = parseHostPort(url)
                RedactingLogger.d(TAG, "Testing connection to host on port $port")

                // Try TCP connection with timeout
                val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS.toInt())
                            TestResult.Success
                        }
                    } catch (e: Exception) {
                        TestResult.Failure("Could not connect: ${e.message}")
                    }
                }

                result ?: TestResult.Failure("Connection timed out after ${CONNECT_TIMEOUT_MS / 1000}s")

            } catch (e: Exception) {
                RedactingLogger.e(TAG, "Connection test failed", e)
                TestResult.Failure("Test failed: ${e.message}")
            }
        }
    }

    /**
     * Parse host and port from an RTMP URL.
     * Visible for testing.
     */
    internal fun parseHostPort(url: String): Pair<String, Int> {
        val withoutProtocol = url.substringAfter("://")
        val hostPort = withoutProtocol.substringBefore("/")

        return if (hostPort.contains(":")) {
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":").toIntOrNull() ?: DEFAULT_RTMP_PORT
            host to port
        } else {
            val isSecure = url.lowercase().startsWith("rtmps://")
            hostPort to if (isSecure) DEFAULT_RTMPS_PORT else DEFAULT_RTMP_PORT
        }
    }
}
