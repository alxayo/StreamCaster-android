package com.port80.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Settings hub — shows a list of settings categories the user can tap into.
 * This acts as a menu for all the different settings screens.
 *
 * Categories:
 * - Streaming Endpoints (manage RTMP profiles)
 * - Video Settings (resolution, fps, bitrate)
 * - Audio Settings (bitrate, sample rate, stereo)
 * - General Settings (ABR, camera, orientation, battery)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHubScreen(
    onNavigateToVideo: () -> Unit = {},
    onNavigateToAudio: () -> Unit = {},
    onNavigateToGeneral: () -> Unit = {},
    onNavigateToEndpoints: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCategory(
                icon = Icons.Filled.Call,
                title = "Streaming Endpoints",
                description = "Manage RTMP server profiles and stream keys",
                onClick = onNavigateToEndpoints
            )
            SettingsCategory(
                icon = Icons.Filled.Create,
                title = "Video",
                description = "Resolution, frame rate, bitrate, keyframe interval",
                onClick = onNavigateToVideo
            )
            SettingsCategory(
                icon = Icons.Filled.Refresh,
                title = "Audio",
                description = "Bitrate, sample rate, stereo/mono",
                onClick = onNavigateToAudio
            )
            SettingsCategory(
                icon = Icons.Filled.Settings,
                title = "General",
                description = "Adaptive bitrate, camera, orientation, battery thresholds",
                onClick = onNavigateToGeneral
            )
        }
    }
}

@Composable
private fun SettingsCategory(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
