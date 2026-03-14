package com.port80.app.thermal

import com.port80.app.data.model.ThermalLevel
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for monitoring device temperature.
 * On API 29+, uses PowerManager.OnThermalStatusChangedListener.
 * On API 23-28, falls back to BatteryManager temperature readings.
 * Implementations are provided in T-021.
 */
interface ThermalMonitor {
    /** Current thermal level. Updates automatically when temperature changes. */
    val thermalLevel: StateFlow<ThermalLevel>

    /** Start monitoring device temperature. */
    fun startMonitoring()

    /** Stop monitoring (call in onDestroy to prevent leaks). */
    fun stopMonitoring()
}
