package com.port80.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.port80.app.util.RedactingLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitors battery level during streaming.
 *
 * When battery drops below the low threshold (default 5%), shows a warning.
 * When it drops below critical threshold (default 2%), auto-stops the stream
 * to prevent data loss (recording needs to be finalized).
 */
class BatteryMonitor(
    private val context: Context,
    private val lowThreshold: Int = 5,
    private val criticalThreshold: Int = 2,
    private val onLowBattery: () -> Unit,
    private val onCriticalBattery: () -> Unit
) {
    companion object {
        private const val TAG = "BatteryMonitor"
    }

    private val _batteryPercent = MutableStateFlow(100)
    val batteryPercent: StateFlow<Int> = _batteryPercent.asStateFlow()

    private var hasWarnedLow = false
    private var receiver: BroadcastReceiver? = null

    fun startMonitoring() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    val percent = (level * 100) / scale
                    _batteryPercent.value = percent

                    // Check thresholds
                    if (percent <= criticalThreshold) {
                        RedactingLogger.w(TAG, "CRITICAL battery: $percent%")
                        onCriticalBattery()
                    } else if (percent <= lowThreshold && !hasWarnedLow) {
                        RedactingLogger.w(TAG, "Low battery warning: $percent%")
                        hasWarnedLow = true
                        onLowBattery()
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        RedactingLogger.d(TAG, "Battery monitoring started (low=$lowThreshold%, critical=$criticalThreshold%)")
    }

    fun stopMonitoring() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (e: Exception) { /* already unregistered */ }
        }
        receiver = null
        hasWarnedLow = false
        RedactingLogger.d(TAG, "Battery monitoring stopped")
    }
}
