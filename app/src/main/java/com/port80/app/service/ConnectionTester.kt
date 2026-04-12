package com.port80.app.service

import com.port80.app.data.model.EndpointProfile
import com.port80.app.data.model.StreamProtocol
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Tests connectivity to validate endpoint settings before going live.
 *
 * - RTMP/RTMPS: TCP connection test to the server.
 * - SRT: UDP socket connect to the server (reachability check, not a full SRT handshake).
 *
 * The same transport security rules apply here as for live streaming.
 */
class ConnectionTester {

    companion object {
        private const val TAG = "ConnectionTester"
        private const val DEFAULT_RTMP_PORT = 1935
        private const val DEFAULT_RTMPS_PORT = 443
        private const val DEFAULT_SRT_PORT = 8888
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }

    sealed class TestResult {
        data object Success : TestResult()
        data class Failure(val message: String) : TestResult()
    }

    /**
     * Test connectivity to a streaming endpoint.
     * @param profile The endpoint profile to test
     * @return TestResult indicating success or failure with a message
     */
    suspend fun testConnection(profile: EndpointProfile): TestResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = profile.url.trim()
                val urlValidation = TransportSecurity.validateUrl(url)
                if (urlValidation != null) {
                    return@withContext TestResult.Failure(urlValidation)
                }

                val protocol = StreamProtocol.fromUrl(url)
                when (protocol) {
                    StreamProtocol.RTMP, StreamProtocol.RTMPS -> testTcpConnection(url)
                    StreamProtocol.SRT -> testSrtConnection(url)
                }
            } catch (e: Exception) {
                RedactingLogger.e(TAG, "Connection test failed", e)
                TestResult.Failure("Test failed: ${e.message}")
            }
        }
    }

    /** TCP connection test for RTMP/RTMPS endpoints. */
    private suspend fun testTcpConnection(url: String): TestResult {
        val (host, port) = parseHostPort(url)
        RedactingLogger.d(TAG, "TCP test to host on port $port")

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
        return result ?: TestResult.Failure("Connection timed out after ${CONNECT_TIMEOUT_MS / 1000}s")
    }

    /**
     * UDP reachability test for SRT endpoints.
     * Note: verifies host:port is reachable via UDP, not a full SRT handshake.
     */
    private suspend fun testSrtConnection(url: String): TestResult {
        val (host, port) = parseSrtHostPort(url)
        RedactingLogger.d(TAG, "UDP reachability test to host on port $port")

        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = CONNECT_TIMEOUT_MS.toInt()
                    socket.connect(InetSocketAddress(host, port))
                    if (socket.isConnected) {
                        TestResult.Success
                    } else {
                        TestResult.Failure("UDP connect failed")
                    }
                }
            } catch (e: Exception) {
                TestResult.Failure("Could not reach SRT server: ${e.message}")
            }
        }
        return result ?: TestResult.Failure("Connection timed out after ${CONNECT_TIMEOUT_MS / 1000}s")
    }

    /** Parse host and port from an RTMP/RTMPS URL. Visible for testing. */
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

    /** Parse host and port from an SRT URL (srt://host:port). Visible for testing. */
    internal fun parseSrtHostPort(url: String): Pair<String, Int> {
        val withoutProtocol = url.substringAfter("://")
        val hostPort = withoutProtocol.substringBefore("?").substringBefore("/")

        return if (hostPort.contains(":")) {
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":").toIntOrNull() ?: DEFAULT_SRT_PORT
            host to port
        } else {
            hostPort to DEFAULT_SRT_PORT
        }
    }
}
