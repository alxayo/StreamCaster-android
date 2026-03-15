package com.port80.app.thermal

import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import com.port80.app.data.model.ThermalLevel
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device temperature on API 29+ using PowerManager's thermal API.
 *
 * Android reports thermal status as an integer:
 * - THERMAL_STATUS_NONE (0): Normal temperature
 * - THERMAL_STATUS_MODERATE (2): Getting warm
 * - THERMAL_STATUS_SEVERE (3): Hot — should reduce workload
 * - THERMAL_STATUS_CRITICAL (4): Very hot — must stop heavy work
 *
 * We map these to our simpler ThermalLevel enum.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class ThermalStatusMonitor(
    private val powerManager: PowerManager
) : ThermalMonitor {

    companion object {
        private const val TAG = "ThermalStatusMonitor"
    }

    private val _thermalLevel = MutableStateFlow(ThermalLevel.NORMAL)
    override val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    override fun startMonitoring() {
        listener = PowerManager.OnThermalStatusChangedListener { status ->
            val level = mapThermalStatus(status)
            _thermalLevel.value = level
            RedactingLogger.d(TAG, "Thermal status changed: $level (raw: $status)")
        }
        powerManager.addThermalStatusListener(listener!!)
        RedactingLogger.d(TAG, "Thermal monitoring started (API 29+ path)")
    }

    override fun stopMonitoring() {
        listener?.let { powerManager.removeThermalStatusListener(it) }
        listener = null
        RedactingLogger.d(TAG, "Thermal monitoring stopped")
    }

    private fun mapThermalStatus(status: Int): ThermalLevel {
        return when {
            status >= PowerManager.THERMAL_STATUS_CRITICAL -> ThermalLevel.CRITICAL
            status >= PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
            status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            else -> ThermalLevel.NORMAL
        }
    }
}
