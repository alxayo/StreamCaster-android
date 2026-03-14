package com.port80.app

import android.app.Application
import com.port80.app.crash.AcraConfigurator
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for StreamCaster.
 * @HiltAndroidApp triggers Hilt code generation for dependency injection.
 */
@HiltAndroidApp
class App : Application() {

    /**
     * Called before onCreate(). This is where ACRA must be initialized
     * because it needs to register its exception handler early.
     */
    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)

        // Only enable crash reporting in release builds.
        // In debug builds, crashes show in logcat and the debugger.
        if (!BuildConfig.DEBUG) {
            AcraConfigurator.init(this)
        }
    }
}
