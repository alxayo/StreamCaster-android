package com.port80.app.ui.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Shows a dialog guiding the user to disable battery optimization for StreamCaster.
 * Aggressive battery management by Samsung, Xiaomi, Huawei, etc. can kill our
 * foreground service and stop the stream.
 *
 * The dialog explains WHY this is needed and provides a button to open system settings.
 */
@Composable
fun BatteryOptimizationGuide(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isOptimized = remember { isAppBatteryOptimized(context) }

    if (isOptimized) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Battery Optimization") },
            text = { Text(getBatteryGuideText()) },
            confirmButton = {
                TextButton(onClick = {
                    openBatterySettings(context)
                    onDismiss()
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        )
    }
}

/** Check if the app is subject to battery optimization. */
fun isAppBatteryOptimized(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return !pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** Open the battery optimization settings for this app. */
fun openBatterySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback to general battery settings
        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** Get OEM-specific guidance text. */
fun getBatteryGuideText(): String {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("samsung") ->
            "Samsung devices may kill background apps. " +
                "Go to Settings → Apps → StreamCaster → Battery → Unrestricted " +
                "to prevent stream interruptions."
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
            "Xiaomi/Redmi devices aggressively kill background apps. " +
                "Go to Settings → Apps → StreamCaster → Autostart and " +
                "Battery Saver → No Restrictions."
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            "Huawei/Honor devices may stop background streaming. " +
                "Go to Settings → Apps → StreamCaster → Battery → Unmanaged."
        manufacturer.contains("oppo") || manufacturer.contains("realme") ||
            manufacturer.contains("oneplus") ->
            "Go to Settings → Battery → App Battery Management → StreamCaster → " +
                "Don't Optimize."
        else ->
            "To prevent your device from stopping the stream in the background, " +
                "please disable battery optimization for StreamCaster in your " +
                "device settings."
    }
}
