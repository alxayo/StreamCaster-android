package com.port80.app.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.port80.app.data.model.StopReason
import com.port80.app.data.model.StreamState
import com.port80.app.ui.components.CameraPreview
import com.port80.app.ui.components.PermissionHandler
import com.port80.app.ui.components.StreamHud

/**
 * Main streaming screen — the primary UI the user interacts with.
 *
 * Layout (landscape-first design):
 * ┌──────────────────────────────────┐
 * │ [HUD - bitrate, fps, duration]  │
 * │                                  │
 * │       Camera Preview             │ [Start/Stop]
 * │                                  │ [Mute]
 * │                                  │ [Switch Camera]
 * │ [Connection state]              │ [Settings]
 * └──────────────────────────────────┘
 *
 * Controls are at the right edge for easy thumb reach in landscape.
 */
@Composable
fun StreamScreen(
    viewModel: StreamViewModel = hiltViewModel(),
    onNavigateToSettings: () -> Unit = {},
    onNavigateToEndpoints: () -> Unit = {}
) {
    val streamState by viewModel.streamState.collectAsState()
    val streamStats by viewModel.streamStats.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar when stream stops with an error
    LaunchedEffect(streamState) {
        val state = streamState
        if (state is StreamState.Stopped && state.reason != StopReason.USER_REQUEST) {
            val message = when (state.reason) {
                StopReason.ERROR_ENCODER -> "Stream stopped: encoder error"
                StopReason.ERROR_AUTH -> "Stream stopped: authentication failed"
                StopReason.ERROR_CAMERA -> "Stream stopped: camera error"
                StopReason.ERROR_AUDIO -> "Stream stopped: audio error"
                StopReason.THERMAL_CRITICAL -> "Stream stopped: device overheating"
                StopReason.BATTERY_CRITICAL -> "Stream stopped: battery critically low"
                else -> "Stream stopped unexpectedly"
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    // Track whether to show the plain-RTMP warning dialog
    var showRtmpWarning by remember { mutableStateOf(false) }
    var pendingProfileId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            // Layer 1: Camera preview (full-screen background)
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onSurfaceReady = { holder -> viewModel.onSurfaceReady(holder) },
                onSurfaceDestroyed = { viewModel.onSurfaceDestroyed() }
            )

            // Layer 2: HUD overlay at the top (only visible when live or reconnecting)
            if (streamState is StreamState.Live || streamState is StreamState.Reconnecting) {
                StreamHud(
                    stats = streamStats,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            // Layer 3: Connection state indicator at bottom-start
            ConnectionStateLabel(
                state = streamState,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )

            // Layer 4: Control buttons on the right edge
            ControlPanel(
                streamState = streamState,
                viewModel = viewModel,
                onSettingsClick = onNavigateToSettings,
                onStartRequested = { profileId ->
                    // TODO: Check if profile uses plain RTMP and show warning
                    viewModel.startStream(profileId)
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            )
        }
    }
}

/**
 * Vertical column of control buttons on the right edge of the screen.
 */
@Composable
private fun ControlPanel(
    streamState: StreamState,
    viewModel: StreamViewModel,
    onSettingsClick: () -> Unit,
    onStartRequested: (profileId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isStreaming = streamState is StreamState.Live ||
        streamState is StreamState.Connecting ||
        streamState is StreamState.Reconnecting

    val isMuted = (streamState as? StreamState.Live)?.isMuted == true

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Start/Stop button — large FAB
        PermissionHandler(
            onResult = { result ->
                if (result.canStreamVideoAndAudio) {
                    if (isStreaming) {
                        viewModel.stopStream()
                    } else {
                        // Use "default" profile; real profile selection comes from endpoints screen
                        onStartRequested("default")
                    }
                }
            }
        ) { requestPermissions ->
            FloatingActionButton(
                onClick = {
                    if (isStreaming) {
                        viewModel.stopStream()
                    } else {
                        requestPermissions()
                    }
                },
                containerColor = if (isStreaming) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(64.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isStreaming) Icons.Filled.Close else Icons.Filled.Call,
                    contentDescription = if (isStreaming) "Stop stream" else "Start stream",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Mute/Unmute button (only shown when streaming)
        if (isStreaming) {
            ControlButton(
                icon = if (isMuted) MuteIcon else UnmuteIcon,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                onClick = { viewModel.toggleMute() }
            )
        }

        // Switch camera button (only shown when streaming)
        if (isStreaming) {
            ControlButton(
                icon = Icons.Filled.Refresh,
                contentDescription = "Switch camera",
                onClick = { viewModel.switchCamera() }
            )
        }

        // Settings button (only shown when idle or stopped)
        if (!isStreaming) {
            ControlButton(
                icon = Icons.Filled.Settings,
                contentDescription = "Settings",
                onClick = onSettingsClick
            )
        }
    }
}

/**
 * Small FAB used for secondary controls (mute, switch camera, settings).
 */
@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        shape = CircleShape
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription
        )
    }
}

/**
 * Connection state label shown at the bottom-left of the screen.
 */
@Composable
private fun ConnectionStateLabel(
    state: StreamState,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (state) {
        is StreamState.Idle -> "" to Color.Transparent
        is StreamState.Connecting -> "Connecting…" to Color.Yellow
        is StreamState.Live -> "● LIVE" to Color.Red
        is StreamState.Reconnecting -> "Reconnecting (${state.attempt})…" to Color(0xFFFF8800)
        is StreamState.Stopping -> "Stopping…" to Color.Yellow
        is StreamState.Stopped -> stoppedLabel(state.reason)
    }

    if (text.isNotEmpty()) {
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            modifier = modifier
                .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.small)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun stoppedLabel(reason: StopReason): Pair<String, Color> = when (reason) {
    StopReason.USER_REQUEST -> "Stopped" to Color.White
    StopReason.ERROR_AUTH -> "Auth Failed" to Color.Red
    StopReason.ERROR_ENCODER -> "Encoder Error" to Color.Red
    StopReason.ERROR_CAMERA -> "Camera Error" to Color.Red
    StopReason.ERROR_AUDIO -> "Audio Error" to Color.Red
    StopReason.THERMAL_CRITICAL -> "Overheated" to Color.Red
    StopReason.BATTERY_CRITICAL -> "Low Battery" to Color.Red
}

// Icons.Filled doesn't include MicOff/Mic, so we reuse existing icons.
// Call = mic-like for unmuted, Close overlay signals muted state.
// If material-icons-extended is added later, swap to Icons.Filled.Mic / MicOff.
private val UnmuteIcon = Icons.Filled.Call
private val MuteIcon = Icons.Filled.Close
