package com.port80.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for StreamCaster.
 * @HiltAndroidApp triggers Hilt code generation for dependency injection.
 */
@HiltAndroidApp
class App : Application()
