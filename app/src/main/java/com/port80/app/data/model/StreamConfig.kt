package com.port80.app.data.model

/**
 * Complete configuration for a streaming session.
 * Built from user settings before starting a stream.
 * This is a snapshot — changes to settings during a stream don't take effect
 * until the next stream starts (except ABR adjustments).
 */
data class StreamConfig(
    /** Which endpoint profile to connect to. */
    val profileId: String,
    /** Whether to stream video (false = audio-only mode). */
    val videoEnabled: Boolean = true,
    /** Whether to stream audio (false = video-only mode). */
    val audioEnabled: Boolean = true,
    /** Video resolution (width × height). Default: 720p. */
    val resolution: Resolution = Resolution(1280, 720),
    /** Frames per second. Common values: 24, 25, 30, 60. */
    val fps: Int = 30,
    /** Video bitrate in kilobits per second. Range: 500–8000. */
    val videoBitrateKbps: Int = 2500,
    /** Audio bitrate in kilobits per second. Common: 64, 96, 128, 192. */
    val audioBitrateKbps: Int = 128,
    /** Audio sample rate in Hz. Usually 44100 or 48000. */
    val audioSampleRate: Int = 44100,
    /** true = stereo audio, false = mono. */
    val stereo: Boolean = true,
    /** Seconds between keyframes (I-frames). Range: 1–5. */
    val keyframeIntervalSec: Int = 2,
    /** Whether adaptive bitrate is enabled (auto-adjusts quality based on network). */
    val abrEnabled: Boolean = true,
    /** Whether to also save a local MP4 copy while streaming. */
    val localRecordingEnabled: Boolean = false
)
