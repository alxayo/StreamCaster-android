package com.port80.app.data.model

/**
 * An streaming endpoint configuration saved by the user.
 * Users can have multiple profiles (e.g., "YouTube", "Twitch", "Custom Server").
 * Supports RTMP, RTMPS, and SRT protocols.
 *
 * SECURITY: Stream keys, passwords, and SRT passphrases are stored encrypted via
 * EncryptedSharedPreferences. They must NEVER appear in logs or Intent extras.
 */
data class EndpointProfile(
    /** Unique identifier for this profile (UUID string). */
    val id: String,
    /** User-friendly name like "My YouTube Channel". */
    val name: String,
    /** Server URL — rtmp://, rtmps://, or srt:// scheme. */
    val url: String,
    /** Optional stream key appended to the RTMP/RTMPS server URL when present. */
    val streamKey: String = "",
    /** Optional username for RTMP authentication. */
    val username: String? = null,
    /** Optional password for RTMP authentication. */
    val password: String? = null,
    /** Whether this is the default profile used when starting a stream. */
    val isDefault: Boolean = false,
    /** Video codec — H.264 (default), H.265 (Enhanced RTMP), or AV1 (Enhanced RTMP). */
    val videoCodec: VideoCodec = VideoCodec.H264,
    /** SRT encryption passphrase (10–79 characters). Encrypted at rest. */
    val srtPassphrase: String? = null,
    /** AES key length for SRT encryption. Only used when passphrase is set. */
    val srtKeyLength: SrtKeyLength = SrtKeyLength.AES_128,
    /** SRT latency in milliseconds. Controls buffering/delay tradeoff. */
    val srtLatencyMs: Int = 120,
    /** SRT connection mode. Caller is standard; listener/rendezvous are experimental. */
    val srtMode: SrtMode = SrtMode.CALLER,
    /** SRT stream ID for multiplexing or routing on the server. */
    val srtStreamId: String? = null,
) {
    /** Derive the streaming protocol from the URL scheme. */
    val protocol: StreamProtocol get() = StreamProtocol.fromUrl(url)

    /** True if this profile uses SRT protocol. */
    val isSrt: Boolean get() = protocol == StreamProtocol.SRT

    /** True if this profile uses a non-H.264 codec (Enhanced RTMP). */
    val isEnhancedRtmp: Boolean get() = videoCodec.isEnhancedRtmp() && !isSrt
}
