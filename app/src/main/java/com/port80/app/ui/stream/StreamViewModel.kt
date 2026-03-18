package com.port80.app.ui.stream

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.view.SurfaceHolder
import com.pedro.library.view.OpenGlView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.port80.app.data.EndpointProfileRepository
import com.port80.app.data.model.StreamState
import com.port80.app.data.model.StreamStats
import com.port80.app.data.model.StopReason
import com.port80.app.service.StreamingService
import com.port80.app.service.StreamingServiceControl
import com.port80.app.util.RedactingLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * ViewModel for the streaming screen.
 *
 * This ViewModel acts as a bridge between the UI (Compose) and the
 * StreamingService (foreground service). It:
 *
 * 1. Binds to StreamingService via Android's ServiceConnection
 * 2. Observes StreamState and StreamStats as StateFlows
 * 3. Forwards user actions (start, stop, mute, switch camera) to the service
 * 4. Manages the camera preview surface lifecycle
 *
 * IMPORTANT: The ViewModel NEVER modifies stream state directly.
 * All state changes go through the service.
 */
@HiltViewModel
class StreamViewModel @Inject constructor(
    application: Application,
    private val profileRepository: EndpointProfileRepository
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "StreamViewModel"
    }

    // ── Service binding ──────────────────────────

    // Reference to the service's control interface — null when not bound.
    private var serviceControl: StreamingServiceControl? = null

    // Tracks whether we currently hold a binding to the service.
    private var isBound = false

    // ── State exposed to the UI ──────────────────

    // These MutableStateFlows mirror the service's StateFlows.
    // The UI observes the read-only versions below.
    private val _streamState = MutableStateFlow<StreamState>(StreamState.Idle)

    /** Current streaming state (Idle, Connecting, Live, etc.). */
    val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

    private val _streamStats = MutableStateFlow(StreamStats())

    /** Live streaming statistics (bitrate, FPS, dropped frames, etc.). */
    val streamStats: StateFlow<StreamStats> = _streamStats.asStateFlow()

    private val _lastFailureDetail = MutableStateFlow<String?>(null)

    /** Last user-facing diagnostic detail for stream startup/connection failures. */
    val lastFailureDetail: StateFlow<String?> = _lastFailureDetail.asStateFlow()

    private val _isEnergySavingEnabled = MutableStateFlow(false)

    /**
     * Whether energy-saving mode is active.
     *
     * When true, the camera preview is hidden behind a dark overlay. The RTMP
     * stream and camera encoder continue running undisturbed — only the on-screen
     * display is suppressed. This is implemented entirely in the UI layer so that
     * neither [stopPreview] nor any encoder operation is triggered, which would
     * otherwise break the live stream.
     */
    val isEnergySavingEnabled: StateFlow<Boolean> = _isEnergySavingEnabled.asStateFlow()

    // One-shot UI events (e.g. "service died") — SharedFlow so they're
    // not replayed on recomposition / re-collection.
    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 1)

    /** One-shot events the UI should show (snackbar, toast, etc.). */
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    // ── Surface management ───────────────────────

    // CompletableDeferred acts as a one-shot gate:
    // preview attach waits until the surface is actually created.
    private var surfaceDeferred = CompletableDeferred<SurfaceHolder>()

    // WeakReference prevents memory leaks — if the Activity/Fragment is
    // destroyed, the OpenGlView can be garbage-collected.
    private var surfaceRef: WeakReference<OpenGlView>? = null

    // ── Service connection callback ──────────────
    // Android calls these methods when the service binding succeeds or drops.
    private val serviceConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            // Cast the generic IBinder to our LocalBinder to get
            // the StreamingServiceControl interface.
            val localBinder = binder as? StreamingService.LocalBinder
            serviceControl = localBinder?.getService()
            isBound = true

            serviceControl?.let { service ->
                // Detect process-death recovery: the ViewModel was recreated
                // but the FGS was still alive and streaming.
                val serviceState = service.streamState.value
                if (serviceState is StreamState.Live || serviceState is StreamState.Reconnecting) {
                    RedactingLogger.i(TAG, "Recovering — service already in $serviceState")
                } else {
                    RedactingLogger.d(TAG, "Bound to StreamingService (state: $serviceState)")
                }

                // Start collecting the service's state flows into our local
                // MutableStateFlows. Each runs in its own coroutine so they
                // don't block each other.
                viewModelScope.launch {
                    service.streamState.collect { state ->
                        _streamState.value = state
                        // Energy-saving mode is only meaningful while streaming.
                        // Reset it automatically so the next session starts with
                        // preview enabled, regardless of how the previous stream ended.
                        if (state is StreamState.Idle || state is StreamState.Stopped) {
                            _isEnergySavingEnabled.value = false
                        }
                    }
                }
                viewModelScope.launch {
                    service.streamStats.collect { stats ->
                        _streamStats.value = stats
                    }
                }
                viewModelScope.launch {
                    service.lastFailureDetail.collect { detail ->
                        _lastFailureDetail.value = detail
                    }
                }

                // Re-attach preview surface. This covers two cases:
                // 1. Surface was created before the service connected (normal flow)
                // 2. Process-death recovery: ViewModel + surface are new,
                //    service is already Live and needs the new surface
                surfaceRef?.get()?.let { openGlView ->
                    service.attachPreviewSurface(openGlView)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Called when the service process crashes or is killed by the OS.
            // This does NOT fire on a normal unbind — only on unexpected death.
            val wasPreviouslyActive = serviceControl != null && (
                _streamState.value is StreamState.Live ||
                _streamState.value is StreamState.Connecting ||
                _streamState.value is StreamState.Reconnecting)
            serviceControl = null
            isBound = false
            _streamState.value = StreamState.Stopped(StopReason.USER_REQUEST)
            RedactingLogger.w(TAG, "Service disconnected unexpectedly")

            if (wasPreviouslyActive) {
                _uiEvents.tryEmit(UiEvent.ServiceDied)
            }
        }
    }

    // Bind to the service as soon as this ViewModel is created.
    // On first launch this creates the service (BIND_AUTO_CREATE).
    // After process-death recreation the FGS may still be running —
    // binding succeeds and onServiceConnected picks up the live state.
    init {
        bindToService()
    }

    // ══════════════════════════════════════════════
    //  User Actions — forwarded to the service
    // ══════════════════════════════════════════════

    /**
     * Start streaming with the given endpoint profile.
     *
     * This launches the foreground service (if not running) and triggers
     * the RTMP connection. The Intent carries only the profile ID — the
     * service reads the actual credentials from the repository at runtime.
     *
     * Uses [startForegroundService] (required on API 26+) to ensure the
     * service can call startForeground() within the 10-second window.
     */
    fun startStream(profileId: String) {
        val context = getApplication<Application>()

        // Build the intent with ONLY the profile ID.
        // Never put stream keys or URLs in Intent extras.
        val intent = Intent(context, StreamingService::class.java).apply {
            putExtra(StreamingService.EXTRA_PROFILE_ID, profileId)
        }
        context.startForegroundService(intent)

        // Bind so we can observe state and forward commands.
        if (!isBound) {
            bindToService()
        }
    }

    /**
     * Resolve the current default profile from the repository and start streaming.
     * Falls back to the first available profile when no explicit default is set.
     */
    fun startStreamWithDefaultProfile() {
        viewModelScope.launch {
            val resolvedProfileId = profileRepository.getDefault()?.id
                ?: profileRepository.getAll().first().firstOrNull()?.id

            if (resolvedProfileId == null) {
                _uiEvents.tryEmit(UiEvent.NoProfilesConfigured)
                return@launch
            }

            startStream(resolvedProfileId)
        }
    }

    /**
     * Stop the current stream gracefully.
     * Idempotent — safe to call when already stopped or idle.
     */
    fun stopStream() {
        serviceControl?.stopStream()
    }

    /**
     * Toggle audio mute on/off.
     * Idempotent — the service tracks the current mute state internally.
     */
    fun toggleMute() {
        serviceControl?.toggleMute()
    }

    /**
     * Switch between front and back cameras.
     * Idempotent — safe to call during any stream state.
     */
    fun switchCamera() {
        serviceControl?.switchCamera()
    }

    /**
     * Toggle the energy-saving (preview-hidden) mode on or off.
     *
     * This is a **UI-only** toggle — it never touches the encoder, camera, or
     * RTMP connection. A dark overlay is shown over the SurfaceView so the device
     * display consumes less power, but video encoding and streaming continue
     * without interruption.
     *
     * Safe to call any number of times while streaming.
     */
    fun toggleEnergySaving() {
        _isEnergySavingEnabled.value = !_isEnergySavingEnabled.value
        RedactingLogger.d(TAG, "Energy-saving mode toggled: ${_isEnergySavingEnabled.value}")
    }

    // ══════════════════════════════════════════════
    //  Surface Lifecycle
    // ══════════════════════════════════════════════

    /**
     * Called when the SurfaceView's surface is created and ready for drawing.
     *
     * This does two things:
     * 1. Completes the [surfaceDeferred] gate (signals "surface is available")
     * 2. Attaches the surface to the service so RtmpCamera2 can render the
     *    camera preview onto it
     *
     * The SurfaceHolder is stored as a [WeakReference] — if the Activity is
     * garbage-collected, we won't hold it in memory.
     */
    fun onSurfaceReady(openGlView: OpenGlView) {
        surfaceRef = WeakReference(openGlView)

        // Complete the gate only once; subsequent calls are no-ops.
        if (!surfaceDeferred.isCompleted) {
            surfaceDeferred.complete(openGlView.holder)
        }

        // If already bound to the service, attach immediately.
        serviceControl?.attachPreviewSurface(openGlView)
        RedactingLogger.d(TAG, "Surface ready — preview attached")
    }

    /**
     * Called when the SurfaceView's surface is destroyed (e.g., the user
     * navigates away or the Activity goes to the background).
     *
     * This detaches the preview but does NOT stop the stream — the service
     * continues streaming without a preview surface.
     */
    fun onSurfaceDestroyed() {
        surfaceRef = null
        serviceControl?.detachPreviewSurface()

        // Reset the deferred so the next surfaceCreated() can complete it
        // again. CompletableDeferred is single-use — once completed, a new
        // instance is needed for the next surface lifecycle.
        surfaceDeferred = CompletableDeferred()
        RedactingLogger.d(TAG, "Surface destroyed — preview detached")
    }

    // ══════════════════════════════════════════════
    //  Private Helpers
    // ══════════════════════════════════════════════

    /**
     * Bind to the StreamingService using the application context.
     *
     * BIND_AUTO_CREATE will create the service if it isn't running yet.
     * If the service hasn't been started via startForegroundService(),
     * the bind may fail — we catch and log silently.
     */
    private fun bindToService() {
        val context = getApplication<Application>()
        val intent = Intent(context, StreamingService::class.java)
        try {
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            RedactingLogger.w(TAG, "Could not bind to service (not running yet)")
        }
    }

    /**
     * Clean up when the ViewModel is being destroyed (e.g., the user
     * leaves the streaming screen for good).
     *
     * Unbinds from the service to prevent leaked ServiceConnection.
     * This does NOT stop the stream — the foreground service continues
     * independently until explicitly stopped.
     */
    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                // Already unbound — harmless.
            }
            isBound = false
        }
    }

    // ══════════════════════════════════════════════
    //  UI Events
    // ══════════════════════════════════════════════

    /** One-shot events that the UI layer should display once. */
    sealed class UiEvent {
        /** The streaming service died unexpectedly (OS killed the process). */
        data object ServiceDied : UiEvent()

        /** No endpoint profile is configured, so stream start cannot proceed. */
        data object NoProfilesConfigured : UiEvent()
    }
}
