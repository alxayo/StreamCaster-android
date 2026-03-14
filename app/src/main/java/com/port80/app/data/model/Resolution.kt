package com.port80.app.data.model

/**
 * Represents a video resolution (width × height in pixels).
 * Width is always the larger dimension (landscape orientation).
 * Common values: 1920×1080 (1080p), 1280×720 (720p), 854×480 (480p).
 */
data class Resolution(
    /** Width in pixels (landscape orientation). */
    val width: Int,
    /** Height in pixels (landscape orientation). */
    val height: Int
) {
    /** Human-readable string like "1280x720". */
    override fun toString(): String = "${width}x${height}"

    /** Common shorthand like "720p" based on the height. */
    val label: String
        get() = "${height}p"
}
