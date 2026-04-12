package com.port80.app.data.model

/**
 * Video codec for streaming.
 *
 * H.265 and AV1 are "Enhanced RTMP" codecs — they work over RTMP/RTMPS
 * but require server-side support. AV1 is NOT supported over SRT
 * (RootEncoder restriction).
 */
enum class VideoCodec {
    /** H.264/AVC — universally supported baseline codec. */
    H264,
    /** H.265/HEVC — ~30% better compression than H.264. Enhanced RTMP. */
    H265,
    /** AV1 — ~45% better compression than H.264. Enhanced RTMP only. */
    AV1;

    /** True if this codec requires Enhanced RTMP signaling. */
    fun isEnhancedRtmp(): Boolean = this != H264

    /** True if this codec can be used over SRT (AV1 cannot). */
    fun supportsSrt(): Boolean = this != AV1

    /** Human-readable display label. */
    fun displayName(): String = when (this) {
        H264 -> "H.264"
        H265 -> "H.265 (HEVC)"
        AV1 -> "AV1"
    }
}
