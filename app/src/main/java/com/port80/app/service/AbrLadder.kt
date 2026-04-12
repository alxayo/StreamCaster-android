package com.port80.app.service

import com.port80.app.data.model.Resolution
import com.port80.app.data.model.VideoCodec

/**
 * Defines the ABR (Adaptive Bitrate) quality ladder.
 *
 * When network conditions worsen, the ABR system steps DOWN the ladder
 * (lower resolution, fewer fps, lower bitrate). When conditions improve,
 * it steps back UP to the user's preferred quality.
 *
 * The ladder is ordered from highest to lowest quality.
 * Each rung represents a complete encoder configuration.
 */
data class AbrRung(
    val resolution: Resolution,
    val fps: Int,
    val bitrateKbps: Int,
    val label: String
)

object AbrLadder {
    /** H.264 ladder — baseline bitrates. */
    val H264_LADDER = listOf(
        AbrRung(Resolution(1920, 1080), 30, 4500, "1080p30"),
        AbrRung(Resolution(1280, 720), 30, 2500, "720p30"),
        AbrRung(Resolution(1280, 720), 15, 1500, "720p15"),
        AbrRung(Resolution(854, 480), 30, 1200, "480p30"),
        AbrRung(Resolution(854, 480), 15, 800, "480p15"),
        AbrRung(Resolution(640, 360), 15, 500, "360p15")
    )

    /** H.265 ladder — ~30% lower bitrates due to better compression. */
    val H265_LADDER = listOf(
        AbrRung(Resolution(1920, 1080), 30, 3000, "1080p30"),
        AbrRung(Resolution(1280, 720), 30, 1700, "720p30"),
        AbrRung(Resolution(1280, 720), 15, 1000, "720p15"),
        AbrRung(Resolution(854, 480), 30, 800, "480p30"),
        AbrRung(Resolution(854, 480), 15, 550, "480p15"),
        AbrRung(Resolution(640, 360), 15, 350, "360p15")
    )

    /** AV1 ladder — ~45% lower bitrates due to superior compression. */
    val AV1_LADDER = listOf(
        AbrRung(Resolution(1920, 1080), 30, 2500, "1080p30"),
        AbrRung(Resolution(1280, 720), 30, 1400, "720p30"),
        AbrRung(Resolution(1280, 720), 15, 850, "720p15"),
        AbrRung(Resolution(854, 480), 30, 650, "480p30"),
        AbrRung(Resolution(854, 480), 15, 450, "480p15"),
        AbrRung(Resolution(640, 360), 15, 275, "360p15")
    )

    /** Backward-compatible alias for the H.264 ladder. */
    val DEFAULT_LADDER = H264_LADDER

    /** Get the appropriate ABR ladder for a given codec. */
    fun forCodec(codec: VideoCodec): List<AbrRung> = when (codec) {
        VideoCodec.H264 -> H264_LADDER
        VideoCodec.H265 -> H265_LADDER
        VideoCodec.AV1 -> AV1_LADDER
    }

    /**
     * Find the rung that matches or is closest to the given configuration.
     * Used to determine the starting position on the ladder.
     */
    fun findClosestRung(
        resolution: Resolution,
        fps: Int,
        codec: VideoCodec = VideoCodec.H264
    ): Int {
        val ladder = forCodec(codec)
        return ladder.indexOfFirst {
            it.resolution == resolution && it.fps == fps
        }.let { if (it >= 0) it else 1 } // Default to 720p30 (index 1)
    }
}
