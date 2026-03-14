package com.port80.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.SurfaceHolder
import androidx.core.app.NotificationCompat
import com.port80.app.data.EndpointProfileRepository
import com.port80.app.data.SettingsRepository
import com.port80.app.data.model.StreamState
import com.port80.app.data.model.StreamStats
import com.port80.app.data.model.StopReason
import com.port80.app.util.RedactingLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The foreground service that manages the entire streaming session.
 *
 * This is the SINGLE SOURCE OF TRUTH for stream state.
 * The UI (ViewModel) observes state via StateFlow but never modifies it.
 *
 * Lifecycle:
 * 1. Activity calls startForegroundService() with profileId
 * 2. Service starts, shows notification, transitions to Connecting
 * 3. Connects to RTMP server - transitions to Live
 * 4. On stop: disconnects, releases resources, stops self
 *
 * State machine:
 *   Idle -> Connecting -> Live -> Stopping -> Stopped
 *                      \-> Reconnecting -/
 */
@AndroidEntryPoint
class StreamingService : Service(), StreamingServiceControl {

    companion object {
        private const val TAG = "StreamingService"
        /** Intent extra key for the endpoint profile ID (a String, not credentials!) */
        const val EXTRA_PROFILE_ID = "profileId"
    }

    // -- Injected dependencies --
    @Inject lateinit var profileRepository: EndpointProfileRepository
    @Inject lateinit var settingsRepository: SettingsRepository

    // -- State (owned exclusively by this service) --
    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)
    override val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _streamStats = MutableStateFlow(StreamStats())
    override val streamStats: StateFlow<StreamStats> = _streamStats.asStateFlow()

    // -- Encoder (stub for now, replaced by real impl in T-007b) --
    private var encoderBridge: EncoderBridge = StubEncoderBridge()

    // -- Coroutine scope tied to service lifecycle --
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -- Surface management --
    private var currentSurface: SurfaceHolder? = null

    // -- Binder for Activity/ViewModel to communicate with this service --
    inner class LocalBinder : Binder() {
        /** Get the service instance as StreamingServiceControl. */
        fun getService(): StreamingServiceControl = this@StreamingService
    }
    private val binder = LocalBinder()

    // ==========================================================
    // Service Lifecycle
    // ==========================================================

    override fun onCreate() {
        super.onCreate()
        RedactingLogger.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RedactingLogger.d(TAG, "onStartCommand received")

        // Show a foreground notification immediately (required within 10s on API 31+)
        startForeground(
            NotificationController.NOTIFICATION_ID,
            createBasicNotification()
        )

        // Extract the profile ID from the intent (NEVER credentials!)
        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        if (profileId != null) {
            startStream(profileId)
        }

        // START_NOT_STICKY: don't restart service if killed by OS
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        RedactingLogger.d(TAG, "Client bound to service")
        return binder
    }

    override fun onDestroy() {
        RedactingLogger.d(TAG, "Service destroying - cleaning up")
        cleanupAndStop()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ==========================================================
    // StreamingServiceControl Implementation
    // ==========================================================

    override fun startStream(profileId: String) {
        // Idempotent: only start if we're idle or stopped
        val currentState = _streamState.value
        if (currentState != StreamState.Idle && currentState !is StreamState.Stopped) {
            RedactingLogger.d(TAG, "startStream ignored - already in state: $currentState")
            return
        }

        _streamState.value = StreamState.Connecting
        RedactingLogger.i(TAG, "Starting stream with profile: $profileId")

        serviceScope.launch {
            try {
                // Read the endpoint profile (credentials fetched here, never from Intent)
                val profile = profileRepository.getById(profileId)
                if (profile == null) {
                    RedactingLogger.e(TAG, "Profile not found: $profileId")
                    _streamState.value = StreamState.Stopped(StopReason.ERROR_AUTH)
                    return@launch
                }

                // Connect via encoder bridge
                encoderBridge.connect(profile.rtmpUrl, profile.streamKey)
                _streamState.value = StreamState.Live()
                RedactingLogger.i(TAG, "Stream is now live!")

                // Start preview if surface is available
                currentSurface?.let { encoderBridge.startPreview(it) }

            } catch (e: Exception) {
                RedactingLogger.e(TAG, "Failed to start stream", e)
                _streamState.value = StreamState.Stopped(StopReason.ERROR_ENCODER)
            }
        }
    }

    override fun stopStream() {
        // Idempotent: only stop if we're actually streaming or connecting
        val currentState = _streamState.value
        if (currentState == StreamState.Idle || currentState is StreamState.Stopped) {
            RedactingLogger.d(TAG, "stopStream ignored - already in state: $currentState")
            return
        }

        RedactingLogger.i(TAG, "Stopping stream (user request)")
        _streamState.value = StreamState.Stopping

        serviceScope.launch {
            cleanupAndStop()
            _streamState.value = StreamState.Stopped(StopReason.USER_REQUEST)
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        }
    }

    override fun toggleMute() {
        val currentState = _streamState.value
        if (currentState is StreamState.Live) {
            val newMuteState = !currentState.isMuted
            _streamState.value = currentState.copy(isMuted = newMuteState)
            RedactingLogger.d(TAG, "Mute toggled: $newMuteState")
        }
    }

    override fun switchCamera() {
        if (_streamState.value is StreamState.Live) {
            encoderBridge.switchCamera()
            RedactingLogger.d(TAG, "Camera switched")
        }
    }

    override fun attachPreviewSurface(holder: SurfaceHolder) {
        currentSurface = holder
        // Only start preview if we're in a state that has the camera active
        val state = _streamState.value
        if (state is StreamState.Live || state == StreamState.Connecting) {
            encoderBridge.startPreview(holder)
        }
        RedactingLogger.d(TAG, "Preview surface attached")
    }

    override fun detachPreviewSurface() {
        currentSurface = null
        encoderBridge.stopPreview()
        RedactingLogger.d(TAG, "Preview surface detached")
    }

    // ==========================================================
    // Private Helpers
    // ==========================================================

    /** Clean up all streaming resources. */
    private fun cleanupAndStop() {
        try {
            encoderBridge.disconnect()
            encoderBridge.release()
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Error during cleanup", e)
        }
    }

    /** Create the notification channel (required on Android 8.0+). */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NotificationController.CHANNEL_ID,
                "Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows streaming status while StreamCaster is live"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Create a basic notification for startForeground().
     * This will be replaced with a richer notification by NotificationController (T-008).
     */
    private fun createBasicNotification() =
        NotificationCompat.Builder(this, NotificationController.CHANNEL_ID)
            .setContentTitle("StreamCaster")
            .setContentText("Preparing to stream...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(NotificationController.openActivityIntent(this))
            .build()
}
