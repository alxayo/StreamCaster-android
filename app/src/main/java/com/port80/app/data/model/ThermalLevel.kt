package com.port80.app.data.model

/**
 * Simplified thermal levels used throughout the app.
 * Maps to Android's PowerManager thermal status on API 29+,
 * or to BatteryManager temperature thresholds on API 23-28.
 */
enum class ThermalLevel {
    /** Device temperature is normal — no action needed. */
    NORMAL,
    /** Device is getting warm — show a warning badge on the HUD. */
    MODERATE,
    /** Device is hot — reduce video quality (lower resolution/fps). */
    SEVERE,
    /** Device is critically hot — stop streaming immediately. */
    CRITICAL
}
