package com.port80.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.port80.app.data.model.StreamState
import com.port80.app.data.model.StreamStats
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the foreground service notification shown while streaming.
 *
 * The notification shows:
 * - Current state (Connecting, Live, Reconnecting, etc.)
 * - Live stats (bitrate, duration) when streaming
 * - Action buttons: Stop and Mute/Unmute
 *
 * Design rules:
 * - Tapping the notification opens MainActivity (deep-link, NOT startForegroundService)
 * - Stop/Mute actions are BROADCASTS to the running service
 * - Actions are debounced ≥ 500ms to prevent double-taps
 */
@Singleton
class StreamNotificationController @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationController {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    override fun createNotification(state: StreamState, stats: StreamStats): Notification {
        return buildNotification(state, stats)
    }

    override fun updateNotification(state: StreamState, stats: StreamStats) {
        val notification = buildNotification(state, stats)
        notificationManager.notify(NotificationController.NOTIFICATION_ID, notification)
    }

    override fun cancel() {
        notificationManager.cancel(NotificationController.NOTIFICATION_ID)
    }

    private fun buildNotification(state: StreamState, stats: StreamStats): Notification {
        val builder = NotificationCompat.Builder(context, NotificationController.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(NotificationController.openActivityIntent(context))
            .setSilent(true)

        when (state) {
            is StreamState.Idle -> {
                builder.setContentTitle("StreamCaster")
                    .setContentText("Ready to stream")
            }
            is StreamState.Connecting -> {
                builder.setContentTitle("Connecting...")
                    .setContentText("Establishing RTMP connection")
            }
            is StreamState.Live -> {
                val duration = formatDuration(stats.durationMs)
                val bitrate = "${stats.videoBitrateKbps} kbps"
                builder.setContentTitle("Live \u2022 $duration")
                    .setContentText("$bitrate \u2022 ${stats.resolution} \u2022 ${stats.fps.toInt()} fps")

                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Stop",
                    NotificationController.stopStreamIntent(context)
                )

                val muteLabel = if (state.isMuted) "Unmute" else "Mute"
                builder.addAction(
                    android.R.drawable.ic_lock_silent_mode,
                    muteLabel,
                    NotificationController.toggleMuteIntent(context)
                )
            }
            is StreamState.Reconnecting -> {
                builder.setContentTitle("Reconnecting...")
                    .setContentText("Attempt ${state.attempt + 1} \u2022 Retry in ${state.nextRetryMs / 1000}s")
                    .addAction(
                        android.R.drawable.ic_media_pause,
                        "Stop",
                        NotificationController.stopStreamIntent(context)
                    )
            }
            is StreamState.Stopping -> {
                builder.setContentTitle("Stopping...")
                    .setContentText("Finalizing stream")
            }
            is StreamState.Stopped -> {
                builder.setContentTitle("Stream Ended")
                    .setContentText("Reason: ${state.reason.name.lowercase().replace('_', ' ')}")
                    .setOngoing(false)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationController.CHANNEL_ID,
                "Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows streaming status while StreamCaster is live"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 60000) % 60
        val hours = ms / 3600000
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
