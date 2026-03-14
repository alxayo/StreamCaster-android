package com.port80.app

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import com.port80.app.data.SettingsRepository
import com.port80.app.util.OrientationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Single Activity for the entire app.
 * Uses Jetpack Compose for all UI rendering.
 * @AndroidEntryPoint enables Hilt dependency injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply orientation lock BEFORE setContent to prevent
        // layout flicker on recreation. Uses runBlocking because
        // DataStore reads are fast local I/O and this must complete
        // before the UI is inflated.
        applyOrientationLock()

        setContent {
            Text("StreamCaster") // Placeholder — will be replaced with real UI
        }
    }

    /**
     * Reads orientation preferences synchronously and applies them.
     *
     * When a stream is active, orientation is always locked (the streaming
     * service will set the preferred orientation before the Activity recreates).
     * When idle, respects the user's lock/unlock preference.
     */
    private fun applyOrientationLock() {
        val isLocked = runBlocking { settingsRepository.getOrientationLocked().first() }
        val preferredOrientation = runBlocking {
            settingsRepository.getPreferredOrientation().first()
        }

        if (isLocked) {
            val orientation = if (preferredOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                // User wants lock but hasn't picked an orientation yet — lock to current
                OrientationHelper.lockToCurrentOrientation(this)
                return
            } else {
                preferredOrientation
            }
            OrientationHelper.lock(this, orientation)
        } else {
            OrientationHelper.unlock(this)
        }
    }
}
