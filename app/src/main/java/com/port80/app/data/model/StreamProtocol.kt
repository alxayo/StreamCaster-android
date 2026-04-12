package com.port80.app.data.model

/**
 * Streaming protocol determined by the URL scheme.
 * Used to select the correct EncoderBridge implementation
 * and show/hide protocol-specific UI fields.
 */
enum class StreamProtocol {
    /** Plain RTMP (rtmp://) — unencrypted. */
    RTMP,
    /** RTMP over TLS (rtmps://) — encrypted via system TrustManager. */
    RTMPS,
    /** Secure Reliable Transport (srt://) — UDP-based, optional passphrase encryption. */
    SRT;

    companion object {
        /**
         * Derive the protocol from a server URL's scheme.
         * Defaults to RTMP for unrecognized schemes.
         */
        fun fromUrl(url: String): StreamProtocol {
            val trimmed = url.trim().lowercase()
            return when {
                trimmed.startsWith("rtmps://") -> RTMPS
                trimmed.startsWith("srt://") -> SRT
                else -> RTMP
            }
        }
    }
}
