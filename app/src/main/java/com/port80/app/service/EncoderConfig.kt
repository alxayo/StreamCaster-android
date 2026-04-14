package com.port80.app.service

import com.port80.app.data.model.VideoCodec

/**
 * Encoder configuration passed to [EncoderBridge.connect] for prepareVideo/prepareAudio.
 *
 * This replaces the no-arg prepareVideo()/prepareAudio() calls, giving the bridge
 * explicit control over resolution, bitrate, codec, and audio parameters.
 */
data class EncoderConfig(
    val videoCodec: VideoCodec = VideoCodec.H264,
    val width: Int = 1280,
    val height: Int = 720,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 2500,
    val audioBitrateKbps: Int = 128,
    val audioSampleRate: Int = 44100,
    val stereo: Boolean = true,
    val keyframeIntervalSec: Int = 2,
    /** Width oriented for the current display rotation (portrait: swap w↔h). */
    val orientedWidth: Int = width,
    /** Height oriented for the current display rotation (portrait: swap w↔h). */
    val orientedHeight: Int = height,
    /** Display rotation in degrees (0, 90, 180, 270) passed to prepareVideo. */
    val rotation: Int = 0,
)
