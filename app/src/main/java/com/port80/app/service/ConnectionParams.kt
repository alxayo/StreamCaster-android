package com.port80.app.service

import com.port80.app.data.model.SrtKeyLength
import com.port80.app.data.model.SrtMode
import com.port80.app.data.model.VideoCodec

/**
 * Typed connection parameters for each supported protocol.
 *
 * Using sealed types (not raw URL strings) ensures:
 * - Each variant carries exactly the fields it needs
 * - Protocol logic is encapsulated in the bridge, not spread across the service
 * - Secrets (passphrase, streamKey) travel through typed params, not URL concatenation
 */
sealed class ConnectionParams {
    abstract val videoCodec: VideoCodec

    /** RTMP or RTMPS connection parameters. */
    data class Rtmp(
        /** Base URL: rtmp://host/app or rtmps://host/app (without stream key). */
        val baseUrl: String,
        /** Stream key appended to the URL path. */
        val streamKey: String,
        /** Optional RTMP authentication username. */
        val username: String?,
        /** Optional RTMP authentication password. */
        val password: String?,
        override val videoCodec: VideoCodec,
    ) : ConnectionParams()

    /** SRT connection parameters. */
    data class Srt(
        /** SRT server hostname or IP. */
        val host: String,
        /** SRT server port. */
        val port: Int,
        /** Encryption passphrase (10–79 chars), or null for unencrypted. */
        val passphrase: String?,
        /** AES key length for encryption. Only used when passphrase is set. */
        val srtKeyLength: SrtKeyLength = SrtKeyLength.AES_128,
        /** Link latency in milliseconds. */
        val latencyMs: Int,
        /** Connection mode: caller, listener, or rendezvous. */
        val mode: SrtMode,
        /** Stream ID for server-side routing/multiplexing. */
        val streamId: String?,
        override val videoCodec: VideoCodec,
    ) : ConnectionParams() {
        companion object {
            const val DEFAULT_LATENCY_MS = 120
        }
    }
}
