package com.port80.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.port80.app.MainActivity
import com.port80.app.data.model.StreamState
import com.port80.app.data.model.StreamStats

/**
 * Interface for managing the foreground service notification.
 *
 * Design rules from the spec:
 * - Tapping the notification opens the Activity (deep-link, NOT startForegroundService)
 * - "Stop" action sends a broadcast to the running service
 * - "Mute/Unmute" action sends a broadcast to the running service
 * - All notification actions are debounced (≥ 500ms) to prevent double-taps
 */
interface NotificationController {
    /** Create the initial notification for startForeground(). */
    fun createNotification(state: StreamState, stats: StreamStats): Notification

    /** Update the notification to show new state/stats (called at ~1 Hz). */
    fun updateNotification(state: StreamState, stats: StreamStats)

    /** Remove the notification (called from onDestroy). */
    fun cancel()

    companion object {
        /** Notification ID used for the foreground service. */
        const val NOTIFICATION_ID = 1001
        /** Notification channel ID for the streaming service. */
        const val CHANNEL_ID = "stream_service_channel"

        // Broadcast action strings for notification buttons
        const val ACTION_STOP = "com.port80.app.ACTION_STOP_STREAM"
        const val ACTION_TOGGLE_MUTE = "com.port80.app.ACTION_TOGGLE_MUTE"

        /**
         * Creates a PendingIntent that opens the main Activity when tapped.
         * This does NOT start the foreground service — it just opens the UI.
         */
        fun openActivityIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Creates a PendingIntent that sends a "stop stream" broadcast.
         * The StreamingService's BroadcastReceiver handles this.
         */
        fun stopStreamIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_STOP).apply {
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /**
         * Creates a PendingIntent that sends a "toggle mute" broadcast.
         */
        fun toggleMuteIntent(context: Context): PendingIntent {
            val intent = Intent(ACTION_TOGGLE_MUTE).apply {
                setPackage(context.packageName)
            }
            return PendingIntent.getBroadcast(
                context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
