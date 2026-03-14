package com.port80.app.data.model

/**
 * Real-time statistics about the active stream.
 * Updated at approximately 1 Hz by the StreamingService.
 * The UI displays these values on the streaming HUD overlay.
 */
data class StreamStats(
    /** Current video bitrate being sent, in kilobits per second. */
    val videoBitrateKbps: Int = 0,
    /** Current audio bitrate being sent, in kilobits per second. */
    val audioBitrateKbps: Int = 0,
    /** Current frames per second being encoded. */
    val fps: Float = 0f,
    /** Total number of video frames dropped since stream start. */
    val droppedFrames: Long = 0,
    /** Current video resolution as a string like "1280x720". */
    val resolution: String = "",
    /** How long the stream has been running, in milliseconds. */
    val durationMs: Long = 0,
    /** Whether local MP4 recording is active alongside streaming. */
    val isRecording: Boolean = false,
    /** Current device thermal level — affects quality decisions. */
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL
)
