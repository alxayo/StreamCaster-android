package com.port80.app.data

import com.port80.app.data.model.Resolution
import kotlinx.coroutines.flow.Flow

/**
 * Interface for reading and writing user preferences.
 * Uses Jetpack DataStore for non-sensitive settings (no credentials here).
 * The implementation is provided in T-004.
 */
interface SettingsRepository {
    // ── Video settings ──
    fun getResolution(): Flow<Resolution>
    suspend fun setResolution(resolution: Resolution)

    fun getFps(): Flow<Int>
    suspend fun setFps(fps: Int)

    fun getVideoBitrateKbps(): Flow<Int>
    suspend fun setVideoBitrateKbps(bitrate: Int)

    fun getKeyframeIntervalSec(): Flow<Int>
    suspend fun setKeyframeIntervalSec(interval: Int)

    // ── Audio settings ──
    fun getAudioBitrateKbps(): Flow<Int>
    suspend fun setAudioBitrateKbps(bitrate: Int)

    fun getAudioSampleRate(): Flow<Int>
    suspend fun setAudioSampleRate(sampleRate: Int)

    fun getStereo(): Flow<Boolean>
    suspend fun setStereo(stereo: Boolean)

    // ── General settings ──
    fun getAbrEnabled(): Flow<Boolean>
    suspend fun setAbrEnabled(enabled: Boolean)

    fun getDefaultCameraId(): Flow<String>
    suspend fun setDefaultCameraId(cameraId: String)

    fun getOrientationLocked(): Flow<Boolean>
    suspend fun setOrientationLocked(locked: Boolean)

    fun getPreferredOrientation(): Flow<Int>
    suspend fun setPreferredOrientation(orientation: Int)

    // ── Battery settings ──
    fun getLowBatteryThreshold(): Flow<Int>
    suspend fun setLowBatteryThreshold(percent: Int)

    fun getCriticalBatteryThreshold(): Flow<Int>
    suspend fun setCriticalBatteryThreshold(percent: Int)

    // ── Recording settings ──
    fun getLocalRecordingEnabled(): Flow<Boolean>
    suspend fun setLocalRecordingEnabled(enabled: Boolean)
}
