package com.port80.app.data

import android.content.pm.ActivityInfo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.port80.app.data.model.Resolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher + Job())

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: DataStoreSettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test_settings.preferences_pb") }
        )
        repo = DataStoreSettingsRepository(dataStore)
    }

    // ── Default value tests ──

    @Test
    fun `default resolution is 1280x720`() = runTest(testDispatcher) {
        val resolution = repo.getResolution().first()
        assertEquals(Resolution(1280, 720), resolution)
    }

    @Test
    fun `default fps is 30`() = runTest(testDispatcher) {
        assertEquals(30, repo.getFps().first())
    }

    @Test
    fun `default video bitrate is 2500`() = runTest(testDispatcher) {
        assertEquals(2500, repo.getVideoBitrateKbps().first())
    }

    @Test
    fun `default keyframe interval is 2`() = runTest(testDispatcher) {
        assertEquals(2, repo.getKeyframeIntervalSec().first())
    }

    @Test
    fun `default audio bitrate is 128`() = runTest(testDispatcher) {
        assertEquals(128, repo.getAudioBitrateKbps().first())
    }

    @Test
    fun `default audio sample rate is 44100`() = runTest(testDispatcher) {
        assertEquals(44100, repo.getAudioSampleRate().first())
    }

    @Test
    fun `default stereo is true`() = runTest(testDispatcher) {
        assertEquals(true, repo.getStereo().first())
    }

    @Test
    fun `default ABR enabled is true`() = runTest(testDispatcher) {
        assertEquals(true, repo.getAbrEnabled().first())
    }

    @Test
    fun `default camera ID is 0`() = runTest(testDispatcher) {
        assertEquals("0", repo.getDefaultCameraId().first())
    }

    @Test
    fun `default orientation locked is false`() = runTest(testDispatcher) {
        assertEquals(false, repo.getOrientationLocked().first())
    }

    @Test
    fun `default preferred orientation is UNSPECIFIED`() = runTest(testDispatcher) {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            repo.getPreferredOrientation().first()
        )
    }

    @Test
    fun `default low battery threshold is 5`() = runTest(testDispatcher) {
        assertEquals(5, repo.getLowBatteryThreshold().first())
    }

    @Test
    fun `default critical battery threshold is 2`() = runTest(testDispatcher) {
        assertEquals(2, repo.getCriticalBatteryThreshold().first())
    }

    @Test
    fun `default local recording enabled is false`() = runTest(testDispatcher) {
        assertEquals(false, repo.getLocalRecordingEnabled().first())
    }

    // ── Round-trip (set then get) tests ──

    @Test
    fun `set and get resolution`() = runTest(testDispatcher) {
        val newRes = Resolution(1920, 1080)
        repo.setResolution(newRes)
        assertEquals(newRes, repo.getResolution().first())
    }

    @Test
    fun `set and get fps`() = runTest(testDispatcher) {
        repo.setFps(60)
        assertEquals(60, repo.getFps().first())
    }

    @Test
    fun `set and get video bitrate`() = runTest(testDispatcher) {
        repo.setVideoBitrateKbps(5000)
        assertEquals(5000, repo.getVideoBitrateKbps().first())
    }

    @Test
    fun `set and get keyframe interval`() = runTest(testDispatcher) {
        repo.setKeyframeIntervalSec(4)
        assertEquals(4, repo.getKeyframeIntervalSec().first())
    }

    @Test
    fun `set and get audio bitrate`() = runTest(testDispatcher) {
        repo.setAudioBitrateKbps(256)
        assertEquals(256, repo.getAudioBitrateKbps().first())
    }

    @Test
    fun `set and get audio sample rate`() = runTest(testDispatcher) {
        repo.setAudioSampleRate(48000)
        assertEquals(48000, repo.getAudioSampleRate().first())
    }

    @Test
    fun `set and get stereo`() = runTest(testDispatcher) {
        repo.setStereo(false)
        assertEquals(false, repo.getStereo().first())
    }

    @Test
    fun `set and get ABR enabled`() = runTest(testDispatcher) {
        repo.setAbrEnabled(false)
        assertEquals(false, repo.getAbrEnabled().first())
    }

    @Test
    fun `set and get default camera ID`() = runTest(testDispatcher) {
        repo.setDefaultCameraId("1")
        assertEquals("1", repo.getDefaultCameraId().first())
    }

    @Test
    fun `set and get orientation locked`() = runTest(testDispatcher) {
        repo.setOrientationLocked(true)
        assertEquals(true, repo.getOrientationLocked().first())
    }

    @Test
    fun `set and get preferred orientation`() = runTest(testDispatcher) {
        repo.setPreferredOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            repo.getPreferredOrientation().first()
        )
    }

    @Test
    fun `set and get low battery threshold`() = runTest(testDispatcher) {
        repo.setLowBatteryThreshold(10)
        assertEquals(10, repo.getLowBatteryThreshold().first())
    }

    @Test
    fun `set and get critical battery threshold`() = runTest(testDispatcher) {
        repo.setCriticalBatteryThreshold(3)
        assertEquals(3, repo.getCriticalBatteryThreshold().first())
    }

    @Test
    fun `set and get local recording enabled`() = runTest(testDispatcher) {
        repo.setLocalRecordingEnabled(true)
        assertEquals(true, repo.getLocalRecordingEnabled().first())
    }
}
