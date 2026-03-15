package com.port80.app.thermal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.port80.app.data.model.ThermalLevel
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors device temperature on API 23-28 using battery temperature.
 *
 * On older Android versions, there's no direct thermal API.
 * Instead, we read the battery temperature from ACTION_BATTERY_CHANGED broadcasts.
 *
 * Temperature thresholds (from spec SL-07):
 * - Below 38°C: Normal
 * - 38°C - 41°C: Moderate (show warning)
 * - 41°C - 43°C: Severe (reduce quality)
 * - Above 43°C: Critical (stop streaming)
 *
 * Note: Battery temperature is reported in tenths of a degree Celsius.
 * So 380 = 38.0°C.
 */
class BatteryTempMonitor(
    private val context: Context
) : ThermalMonitor {

    companion object {
        private const val TAG = "BatteryTempMonitor"
        private const val TEMP_MODERATE = 380   // 38.0°C in tenths
        private const val TEMP_SEVERE = 410     // 41.0°C
        private const val TEMP_CRITICAL = 430   // 43.0°C
    }

    private val _thermalLevel = MutableStateFlow(ThermalLevel.NORMAL)
    override val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    override fun startMonitoring() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                val level = when {
                    tempTenths >= TEMP_CRITICAL -> ThermalLevel.CRITICAL
                    tempTenths >= TEMP_SEVERE -> ThermalLevel.SEVERE
                    tempTenths >= TEMP_MODERATE -> ThermalLevel.MODERATE
                    else -> ThermalLevel.NORMAL
                }

                if (level != _thermalLevel.value) {
                    _thermalLevel.value = level
                    RedactingLogger.d(TAG, "Battery temp: ${tempTenths / 10.0}°C → $level")
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        RedactingLogger.d(TAG, "Battery temperature monitoring started (API 23-28 fallback)")
    }

    override fun stopMonitoring() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
                // Already unregistered
            }
        }
        receiver = null
        RedactingLogger.d(TAG, "Battery temperature monitoring stopped")
    }
}
