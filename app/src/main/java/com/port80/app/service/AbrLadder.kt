package com.port80.app.service

import com.port80.app.data.model.Resolution

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
    val label: String // Human-readable label like "720p30"
)

object AbrLadder {
    /**
     * Default quality ladder for streaming.
     * Ordered from highest quality (index 0) to lowest (last index).
     */
    val DEFAULT_LADDER = listOf(
        AbrRung(Resolution(1920, 1080), 30, 4500, "1080p30"),
        AbrRung(Resolution(1280, 720), 30, 2500, "720p30"),
        AbrRung(Resolution(1280, 720), 15, 1500, "720p15"),
        AbrRung(Resolution(854, 480), 30, 1200, "480p30"),
        AbrRung(Resolution(854, 480), 15, 800, "480p15"),
        AbrRung(Resolution(640, 360), 15, 500, "360p15")
    )

    /**
     * Find the rung that matches or is closest to the given configuration.
     * Used to determine the starting position on the ladder.
     */
    fun findClosestRung(resolution: Resolution, fps: Int): Int {
        // Find exact match or closest by resolution then fps
        return DEFAULT_LADDER.indexOfFirst {
            it.resolution == resolution && it.fps == fps
        }.let { if (it >= 0) it else 1 } // Default to 720p30 (index 1)
    }
}
