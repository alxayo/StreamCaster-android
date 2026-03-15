package com.port80.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.view.OpenGlView
import com.port80.app.crash.CredentialSanitizer
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
class StreamingService : Service(), StreamingServiceControl, ConnectChecker {

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

    private val _lastFailureDetail = MutableStateFlow<String?>(null)
    override val lastFailureDetail: StateFlow<String?> = _lastFailureDetail.asStateFlow()

    // -- Encoder: real RtmpCamera2 bridge, constructed once the service context is ready --
    private val encoderBridge: EncoderBridge by lazy { RtmpCamera2Bridge(this) }

    // -- Coroutine scope tied to service lifecycle --
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -- Surface management --
    private var currentSurface: OpenGlView? = null

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
        _lastFailureDetail.value = null
        RedactingLogger.i(TAG, "Starting stream with profile: $profileId")

        serviceScope.launch {
            try {
                // Read the endpoint profile (credentials fetched here, never from Intent)
                val profile = profileRepository.getById(profileId)
                if (profile == null) {
                    RedactingLogger.e(TAG, "Profile not found: $profileId")
                    _streamState.value = StreamState.Stopped(StopReason.ERROR_PROFILE)
                    return@launch
                }

                // Start preview first — this creates the RtmpCamera2 instance
                // bound to the screen surface before any encoder operations.
                if (currentSurface == null) {
                    RedactingLogger.w(TAG, "startStream(): no preview surface attached; stream will attempt headless camera start")
                } else {
                    RedactingLogger.d(TAG, "startStream(): preview surface present, starting preview")
                    encoderBridge.startPreview(requireNotNull(currentSurface))
                }

                // Now connect — RtmpCamera2 instance is guaranteed to exist.
                RedactingLogger.d(TAG, "startStream(): invoking encoderBridge.connect()")
                encoderBridge.connect(profile.rtmpUrl, profile.streamKey)

            } catch (e: Exception) {
                RedactingLogger.e(TAG, "Failed to start stream", e)
                _lastFailureDetail.value = "Could not start streaming: ${e.javaClass.simpleName}. Check camera/audio permissions and try again."
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

    override fun attachPreviewSurface(openGlView: OpenGlView) {
        currentSurface = openGlView
        // Only start preview if we're in a state that has the camera active
        val state = _streamState.value
        if (state is StreamState.Live || state == StreamState.Connecting) {
            encoderBridge.startPreview(openGlView)
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

    // ==========================================================
    // ConnectChecker — driven by RtmpCamera2Bridge callbacks
    // ==========================================================

    override fun onConnectionStarted(url: String) {
        RedactingLogger.d(TAG, "RTMP connection started: $url")
    }

    override fun onConnectionSuccess() {
        RedactingLogger.i(TAG, "RTMP connection succeeded — stream is live")
        _lastFailureDetail.value = null
        _streamState.value = StreamState.Live()
    }

    override fun onConnectionFailed(reason: String) {
        RedactingLogger.e(TAG, "RTMP connection failed: $reason")
        _lastFailureDetail.value = buildFailureDetail(reason)
        val stopReason = mapFailureReason(reason)
        serviceScope.launch {
            _streamState.value = StreamState.Stopped(stopReason)
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        // Update stats when RootEncoder reports actual measured bitrate.
        _streamStats.value = _streamStats.value.copy(videoBitrateKbps = (bitrate / 1000).toInt())
    }

    override fun onDisconnect() {
        val previousState = _streamState.value
        RedactingLogger.w(
            TAG,
            "RTMP disconnected (previousState=$previousState, encoderStreaming=${encoderBridge.isStreaming()})"
        )
        if (previousState is StreamState.Live || previousState is StreamState.Reconnecting) {
            _lastFailureDetail.value =
                "Connection to server was lost. Check network stability and server availability, then retry."
        }
        // ConnectionManager will drive reconnect; for now surface a stopped state.
        serviceScope.launch {
            if (_streamState.value is StreamState.Live || _streamState.value is StreamState.Reconnecting) {
                _streamState.value = StreamState.Stopped(StopReason.ERROR_ENCODER)
            }
        }
    }

    override fun onAuthError() {
        RedactingLogger.e(TAG, "RTMP auth error — wrong stream key or credentials")
        _lastFailureDetail.value =
            "Authentication rejected by the server. Verify stream key/username/password in endpoint settings."
        serviceScope.launch {
            _streamState.value = StreamState.Stopped(StopReason.ERROR_AUTH)
        }
    }

    override fun onAuthSuccess() {
        RedactingLogger.d(TAG, "RTMP auth succeeded")
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

    /**
     * Convert low-level encoder/connect failure details into user-facing stop reasons.
     */
    private fun mapFailureReason(reason: String): StopReason {
        val normalized = reason.uppercase()
        return when {
            "AUTH" in normalized -> StopReason.ERROR_AUTH
            "AUDIO" in normalized -> StopReason.ERROR_AUDIO
            "CAMERA" in normalized || "PREVIEW" in normalized -> StopReason.ERROR_CAMERA
            else -> StopReason.ERROR_ENCODER
        }
    }

    private fun buildFailureDetail(reason: String): String {
        val sanitizedReason = CredentialSanitizer.sanitize(reason)
        val normalized = sanitizedReason.uppercase()

        return when {
            "AUTH" in normalized ->
                "Authentication failed. Double-check stream key and account credentials."

            "TIMED OUT" in normalized || "TIMEOUT" in normalized ->
                "Connection timed out. Verify endpoint URL, internet access, and firewall/network restrictions."

            "REFUSED" in normalized || "UNREACHABLE" in normalized || "NO ROUTE" in normalized ->
                "Server is unreachable. Confirm the RTMP/RTMPS host, port, and that the ingest server is online."

            "AUDIO" in normalized ->
                "Audio initialization failed. Check microphone permission and whether another app is using the mic."

            "CAMERA" in normalized || "PREVIEW" in normalized ->
                "Camera initialization failed. Check camera permission and close other apps using the camera."

            "ENCODER_PREP_FAILED" in normalized ->
                "Device encoder setup failed. Try lowering resolution/FPS/bitrate in Video Settings."

            else ->
                "Could not connect to streaming endpoint. Verify endpoint URL and network, then retry. Detail: $sanitizedReason"
        }
    }
}
