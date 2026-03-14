package com.port80.app.data

import android.content.pm.ActivityInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.port80.app.data.model.Resolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

    companion object {
        // Video
        val KEY_RESOLUTION_WIDTH = intPreferencesKey("resolution_width")
        val KEY_RESOLUTION_HEIGHT = intPreferencesKey("resolution_height")
        val KEY_FPS = intPreferencesKey("fps")
        val KEY_VIDEO_BITRATE = intPreferencesKey("video_bitrate_kbps")
        val KEY_KEYFRAME_INTERVAL = intPreferencesKey("keyframe_interval_sec")

        // Audio
        val KEY_AUDIO_BITRATE = intPreferencesKey("audio_bitrate_kbps")
        val KEY_AUDIO_SAMPLE_RATE = intPreferencesKey("audio_sample_rate")
        val KEY_STEREO = booleanPreferencesKey("stereo")

        // General
        val KEY_ABR_ENABLED = booleanPreferencesKey("abr_enabled")
        val KEY_DEFAULT_CAMERA_ID = stringPreferencesKey("default_camera_id")
        val KEY_ORIENTATION_LOCKED = booleanPreferencesKey("orientation_locked")
        val KEY_PREFERRED_ORIENTATION = intPreferencesKey("preferred_orientation")

        // Battery
        val KEY_LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        val KEY_CRITICAL_BATTERY_THRESHOLD = intPreferencesKey("critical_battery_threshold")

        // Recording
        val KEY_LOCAL_RECORDING_ENABLED = booleanPreferencesKey("local_recording_enabled")

        // Defaults
        const val DEFAULT_RESOLUTION_WIDTH = 1280
        const val DEFAULT_RESOLUTION_HEIGHT = 720
        const val DEFAULT_FPS = 30
        const val DEFAULT_VIDEO_BITRATE = 2500
        const val DEFAULT_KEYFRAME_INTERVAL = 2
        const val DEFAULT_AUDIO_BITRATE = 128
        const val DEFAULT_AUDIO_SAMPLE_RATE = 44100
        const val DEFAULT_STEREO = true
        const val DEFAULT_ABR_ENABLED = true
        const val DEFAULT_CAMERA_ID = "0"
        const val DEFAULT_ORIENTATION_LOCKED = false
        val DEFAULT_PREFERRED_ORIENTATION = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        const val DEFAULT_LOW_BATTERY_THRESHOLD = 5
        const val DEFAULT_CRITICAL_BATTERY_THRESHOLD = 2
        const val DEFAULT_LOCAL_RECORDING_ENABLED = false
    }

    // ── Video settings ──

    override fun getResolution(): Flow<Resolution> = dataStore.data.map { prefs ->
        Resolution(
            width = prefs[KEY_RESOLUTION_WIDTH] ?: DEFAULT_RESOLUTION_WIDTH,
            height = prefs[KEY_RESOLUTION_HEIGHT] ?: DEFAULT_RESOLUTION_HEIGHT
        )
    }

    override suspend fun setResolution(resolution: Resolution) {
        dataStore.edit { prefs ->
            prefs[KEY_RESOLUTION_WIDTH] = resolution.width
            prefs[KEY_RESOLUTION_HEIGHT] = resolution.height
        }
    }

    override fun getFps(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_FPS] ?: DEFAULT_FPS
    }

    override suspend fun setFps(fps: Int) {
        dataStore.edit { prefs -> prefs[KEY_FPS] = fps }
    }

    override fun getVideoBitrateKbps(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_VIDEO_BITRATE] ?: DEFAULT_VIDEO_BITRATE
    }

    override suspend fun setVideoBitrateKbps(bitrate: Int) {
        dataStore.edit { prefs -> prefs[KEY_VIDEO_BITRATE] = bitrate }
    }

    override fun getKeyframeIntervalSec(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_KEYFRAME_INTERVAL] ?: DEFAULT_KEYFRAME_INTERVAL
    }

    override suspend fun setKeyframeIntervalSec(interval: Int) {
        dataStore.edit { prefs -> prefs[KEY_KEYFRAME_INTERVAL] = interval }
    }

    // ── Audio settings ──

    override fun getAudioBitrateKbps(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_BITRATE] ?: DEFAULT_AUDIO_BITRATE
    }

    override suspend fun setAudioBitrateKbps(bitrate: Int) {
        dataStore.edit { prefs -> prefs[KEY_AUDIO_BITRATE] = bitrate }
    }

    override fun getAudioSampleRate(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_AUDIO_SAMPLE_RATE] ?: DEFAULT_AUDIO_SAMPLE_RATE
    }

    override suspend fun setAudioSampleRate(sampleRate: Int) {
        dataStore.edit { prefs -> prefs[KEY_AUDIO_SAMPLE_RATE] = sampleRate }
    }

    override fun getStereo(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_STEREO] ?: DEFAULT_STEREO
    }

    override suspend fun setStereo(stereo: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_STEREO] = stereo }
    }

    // ── General settings ──

    override fun getAbrEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ABR_ENABLED] ?: DEFAULT_ABR_ENABLED
    }

    override suspend fun setAbrEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ABR_ENABLED] = enabled }
    }

    override fun getDefaultCameraId(): Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_CAMERA_ID] ?: DEFAULT_CAMERA_ID
    }

    override suspend fun setDefaultCameraId(cameraId: String) {
        dataStore.edit { prefs -> prefs[KEY_DEFAULT_CAMERA_ID] = cameraId }
    }

    override fun getOrientationLocked(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ORIENTATION_LOCKED] ?: DEFAULT_ORIENTATION_LOCKED
    }

    override suspend fun setOrientationLocked(locked: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_ORIENTATION_LOCKED] = locked }
    }

    override fun getPreferredOrientation(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_PREFERRED_ORIENTATION] ?: DEFAULT_PREFERRED_ORIENTATION
    }

    override suspend fun setPreferredOrientation(orientation: Int) {
        dataStore.edit { prefs -> prefs[KEY_PREFERRED_ORIENTATION] = orientation }
    }

    // ── Battery settings ──

    override fun getLowBatteryThreshold(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_LOW_BATTERY_THRESHOLD] ?: DEFAULT_LOW_BATTERY_THRESHOLD
    }

    override suspend fun setLowBatteryThreshold(percent: Int) {
        dataStore.edit { prefs -> prefs[KEY_LOW_BATTERY_THRESHOLD] = percent }
    }

    override fun getCriticalBatteryThreshold(): Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CRITICAL_BATTERY_THRESHOLD] ?: DEFAULT_CRITICAL_BATTERY_THRESHOLD
    }

    override suspend fun setCriticalBatteryThreshold(percent: Int) {
        dataStore.edit { prefs -> prefs[KEY_CRITICAL_BATTERY_THRESHOLD] = percent }
    }

    // ── Recording settings ──

    override fun getLocalRecordingEnabled(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LOCAL_RECORDING_ENABLED] ?: DEFAULT_LOCAL_RECORDING_ENABLED
    }

    override suspend fun setLocalRecordingEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_LOCAL_RECORDING_ENABLED] = enabled }
    }
}
