package com.port80.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity for the entire app.
 * Uses Jetpack Compose for all UI rendering.
 * @AndroidEntryPoint enables Hilt dependency injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("StreamCaster") // Placeholder — will be replaced with real UI
        }
    }
}
