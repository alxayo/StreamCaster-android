package com.port80.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.port80.app.camera.DeviceCapabilityQuery
import com.port80.app.data.SettingsRepository
import com.port80.app.data.model.Resolution
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for all settings screens.
 *
 * Each setting is exposed as a [StateFlow] so Compose can observe it
 * and recompose automatically when the value changes. Device capabilities
 * are queried once at creation to populate pickers with valid options.
 *
 * All updates are fire-and-forget coroutines that write through
 * [SettingsRepository] to DataStore on disk.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val deviceCapabilityQuery: DeviceCapabilityQuery
) : ViewModel() {

    // WhileSubscribed(5000) keeps the upstream Flow alive for 5 seconds
    // after the last subscriber disappears (e.g., during config change).
    // This avoids re-reading DataStore on every recomposition.
    private val subscriptionPolicy = SharingStarted.WhileSubscribed(5000)

    // ── Video settings ──────────────────────────────────────────────

    val resolution: StateFlow<Resolution> = settingsRepository.getResolution()
        .stateIn(viewModelScope, subscriptionPolicy, Resolution(1280, 720))

    val fps: StateFlow<Int> = settingsRepository.getFps()
        .stateIn(viewModelScope, subscriptionPolicy, 30)

    val videoBitrateKbps: StateFlow<Int> = settingsRepository.getVideoBitrateKbps()
        .stateIn(viewModelScope, subscriptionPolicy, 2500)

    val keyframeIntervalSec: StateFlow<Int> = settingsRepository.getKeyframeIntervalSec()
        .stateIn(viewModelScope, subscriptionPolicy, 2)

    // ── Audio settings ──────────────────────────────────────────────

    val audioBitrateKbps: StateFlow<Int> = settingsRepository.getAudioBitrateKbps()
        .stateIn(viewModelScope, subscriptionPolicy, 128)

    val audioSampleRate: StateFlow<Int> = settingsRepository.getAudioSampleRate()
        .stateIn(viewModelScope, subscriptionPolicy, 44100)

    val stereo: StateFlow<Boolean> = settingsRepository.getStereo()
        .stateIn(viewModelScope, subscriptionPolicy, true)

    // ── General settings ────────────────────────────────────────────

    val abrEnabled: StateFlow<Boolean> = settingsRepository.getAbrEnabled()
        .stateIn(viewModelScope, subscriptionPolicy, true)

    val defaultCameraId: StateFlow<String> = settingsRepository.getDefaultCameraId()
        .stateIn(viewModelScope, subscriptionPolicy, "0")

    val orientationLocked: StateFlow<Boolean> = settingsRepository.getOrientationLocked()
        .stateIn(viewModelScope, subscriptionPolicy, false)

    val preferredOrientation: StateFlow<Int> = settingsRepository.getPreferredOrientation()
        .stateIn(viewModelScope, subscriptionPolicy, -1)

    val lowBatteryThreshold: StateFlow<Int> = settingsRepository.getLowBatteryThreshold()
        .stateIn(viewModelScope, subscriptionPolicy, 5)

    val criticalBatteryThreshold: StateFlow<Int> = settingsRepository.getCriticalBatteryThreshold()
        .stateIn(viewModelScope, subscriptionPolicy, 2)

    val localRecordingEnabled: StateFlow<Boolean> = settingsRepository.getLocalRecordingEnabled()
        .stateIn(viewModelScope, subscriptionPolicy, false)

    // ── Device capabilities (read-only, queried once) ───────────────

    /** Resolutions the back camera + H.264 encoder both support. */
    val supportedResolutions: List<Resolution> by lazy {
        val ids = deviceCapabilityQuery.getCameraIds()
        if (ids.isNotEmpty()) {
            deviceCapabilityQuery.getSupportedResolutions(ids.first())
        } else {
            listOf(Resolution(1920, 1080), Resolution(1280, 720), Resolution(854, 480))
        }
    }

    /** Frame rates the device supports for the currently selected resolution. */
    fun supportedFpsForResolution(resolution: Resolution): List<Int> {
        val ids = deviceCapabilityQuery.getCameraIds()
        return if (ids.isNotEmpty()) {
            deviceCapabilityQuery.getSupportedFps(ids.first(), resolution)
        } else {
            listOf(24, 25, 30)
        }
    }

    /** Maximum video bitrate the hardware encoder can sustain. */
    val maxVideoBitrateKbps: Int by lazy { deviceCapabilityQuery.getMaxVideoBitrateKbps() }

    val hasFrontCamera: Boolean by lazy { deviceCapabilityQuery.hasFrontCamera() }
    val hasBackCamera: Boolean by lazy { deviceCapabilityQuery.hasBackCamera() }

    // ── Update methods ──────────────────────────────────────────────
    // Each launches a coroutine scoped to the ViewModel so it survives
    // a quick configuration change but is cancelled when the screen
    // is fully destroyed.

    fun setResolution(resolution: Resolution) {
        viewModelScope.launch { settingsRepository.setResolution(resolution) }
    }

    fun setFps(fps: Int) {
        viewModelScope.launch { settingsRepository.setFps(fps) }
    }

    fun setVideoBitrateKbps(bitrate: Int) {
        viewModelScope.launch { settingsRepository.setVideoBitrateKbps(bitrate) }
    }

    fun setKeyframeIntervalSec(interval: Int) {
        viewModelScope.launch { settingsRepository.setKeyframeIntervalSec(interval) }
    }

    fun setAudioBitrateKbps(bitrate: Int) {
        viewModelScope.launch { settingsRepository.setAudioBitrateKbps(bitrate) }
    }

    fun setAudioSampleRate(sampleRate: Int) {
        viewModelScope.launch { settingsRepository.setAudioSampleRate(sampleRate) }
    }

    fun setStereo(stereo: Boolean) {
        viewModelScope.launch { settingsRepository.setStereo(stereo) }
    }

    fun setAbrEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAbrEnabled(enabled) }
    }

    fun setDefaultCameraId(cameraId: String) {
        viewModelScope.launch { settingsRepository.setDefaultCameraId(cameraId) }
    }

    fun setOrientationLocked(locked: Boolean) {
        viewModelScope.launch { settingsRepository.setOrientationLocked(locked) }
    }

    fun setPreferredOrientation(orientation: Int) {
        viewModelScope.launch { settingsRepository.setPreferredOrientation(orientation) }
    }

    fun setLowBatteryThreshold(percent: Int) {
        viewModelScope.launch { settingsRepository.setLowBatteryThreshold(percent) }
    }

    fun setCriticalBatteryThreshold(percent: Int) {
        viewModelScope.launch { settingsRepository.setCriticalBatteryThreshold(percent) }
    }

    fun setLocalRecordingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setLocalRecordingEnabled(enabled) }
    }
}
