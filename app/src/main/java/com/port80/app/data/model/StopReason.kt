package com.port80.app.data.model

/**
 * Why a stream was stopped. Used inside [StreamState.Stopped].
 * Each reason helps the UI show an appropriate message to the user.
 */
enum class StopReason {
    /** User tapped the stop button. */
    USER_REQUEST,
    /** No valid endpoint profile could be resolved when starting a stream. */
    ERROR_PROFILE,
    /** The video/audio encoder crashed or could not be restarted. */
    ERROR_ENCODER,
    /** RTMP server rejected our credentials (wrong stream key or password). */
    ERROR_AUTH,
    /** Camera hardware error or camera was permanently taken by another app. */
    ERROR_CAMERA,
    /** Microphone was revoked or became unavailable mid-stream. */
    ERROR_AUDIO,
    /** Device reached critical temperature — stream stopped to prevent overheating. */
    THERMAL_CRITICAL,
    /** Battery level dropped below the critical threshold (default: 2%). */
    BATTERY_CRITICAL
}
