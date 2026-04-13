package com.port80.app.ui.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.view.WindowManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.port80.app.data.model.CameraInfo
import com.port80.app.data.model.StopReason
import com.port80.app.data.model.StreamState
import com.port80.app.ui.components.CameraPreview
import com.port80.app.ui.components.MinimalStreamingOverlay
import com.port80.app.ui.components.PermissionHandler
import com.port80.app.ui.components.StreamHud
import kotlinx.coroutines.flow.collectLatest

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
    val lastFailureDetail by viewModel.lastFailureDetail.collectAsState()

    // Keep screen on while streaming (when enabled in settings)
    val keepScreenOn by viewModel.keepScreenOnSetting.collectAsState()
    val activity = LocalContext.current as? Activity

    val isActiveStream = streamState is StreamState.Connecting ||
        streamState is StreamState.Live ||
        streamState is StreamState.Reconnecting

    val isMinimalMode by viewModel.isMinimalMode.collectAsState()

    // Use Activity window flag so it survives in-app navigation
    DisposableEffect(isActiveStream, keepScreenOn) {
        if (isActiveStream && keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error snackbar when stream stops with an error
    LaunchedEffect(streamState) {
        val state = streamState
        if (state is StreamState.Stopped && state.reason != StopReason.USER_REQUEST) {
            val message = stoppedMessage(state.reason, lastFailureDetail)
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.uiEvents.collectLatest { event ->
            when (event) {
                StreamViewModel.UiEvent.ServiceDied -> {
                    snackbarHostState.showSnackbar("Streaming service stopped unexpectedly")
                }

                StreamViewModel.UiEvent.NoProfilesConfigured -> {
                    snackbarHostState.showSnackbar("No streaming endpoint configured")
                }
            }
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
            // Camera preview is ALWAYS in the composition tree to keep the
            // SurfaceHolder alive. Removing it would trigger surfaceDestroyed()
            // which stops the RootEncoder camera capture and kills the stream.
            val showMinimal = isMinimalMode && isActiveStream
            CameraPreview(
                modifier = if (showMinimal) Modifier.size(1.dp) else Modifier.fillMaxSize(),
                onSurfaceReady = { openGlView -> viewModel.onSurfaceReady(openGlView) },
                onSurfaceDestroyed = { viewModel.onSurfaceDestroyed() }
            )

            if (showMinimal) {
                MinimalStreamingOverlay(
                    stats = streamStats,
                    isMuted = (streamState as? StreamState.Live)?.isMuted == true,
                    onStopStream = { viewModel.stopStream() },
                    onToggleMute = { viewModel.toggleMute() },
                    onExitMinimalMode = { viewModel.exitMinimalMode() }
                )
            } else {
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
                    onMinimalModeClick = { viewModel.toggleMinimalMode() },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp)
                )
            }
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
    onMinimalModeClick: () -> Unit,
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
                        viewModel.startStreamWithDefaultProfile()
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
                    imageVector = if (isStreaming) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isStreaming) "Stop stream" else "Start stream",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Mute/Unmute button (only shown when streaming)
        if (isStreaming) {
            ControlButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                onClick = { viewModel.toggleMute() }
            )
        }

        // Switch camera button (only shown when streaming)
        // Short tap cycles through cameras. Long press (on multi-camera devices)
        // opens a picker popup to select a specific lens.
        if (isStreaming) {
            CameraSwitchButton(
                hasMultipleRearCameras = viewModel.hasMultipleRearCameras,
                availableCameras = viewModel.availableCameras,
                onCycle = { viewModel.switchCamera() },
                onSelectCamera = { cameraId -> viewModel.switchCamera(cameraId) }
            )
        }

        // Minimal mode button (only shown when streaming)
        if (isStreaming) {
            ControlButton(
                icon = Icons.Filled.DarkMode,
                contentDescription = "Minimal mode",
                onClick = onMinimalModeClick
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
 * Camera switch button with multi-camera support.
 *
 * - **Short tap:** cycles to the next camera (existing front/back behavior,
 *   extended to cycle through multiple rear cameras).
 * - **Long press:** opens a dropdown picker showing all available cameras
 *   (only on devices with >1 rear camera).
 * - **Single rear camera devices:** behaves identically to the original button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CameraSwitchButton(
    hasMultipleRearCameras: Boolean,
    availableCameras: List<CameraInfo>,
    onCycle: () -> Unit,
    onSelectCamera: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Box {
        SmallFloatingActionButton(
            onClick = { /* handled by combinedClickable below */ },
            containerColor = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier.combinedClickable(
                onClick = onCycle,
                onLongClick = if (hasMultipleRearCameras) {
                    { showPicker = true }
                } else {
                    null
                }
            )
        ) {
            Icon(
                imageVector = Icons.Filled.Cameraswitch,
                contentDescription = "Switch camera"
            )
        }

        // Camera picker dropdown — shown on long press for multi-camera devices
        if (showPicker) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { showPicker = false }
            ) {
                availableCameras.forEach { cam ->
                    DropdownMenuItem(
                        text = { Text(cam.label) },
                        onClick = {
                            onSelectCamera(cam.id)
                            showPicker = false
                        }
                    )
                }
            }
        }
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
    StopReason.ERROR_PROFILE -> "Profile Missing" to Color.Red
    StopReason.ERROR_AUTH -> "Auth Failed" to Color.Red
    StopReason.ERROR_ENCODER -> "Encoder Error" to Color.Red
    StopReason.ERROR_CAMERA -> "Camera Error" to Color.Red
    StopReason.ERROR_AUDIO -> "Audio Error" to Color.Red
    StopReason.THERMAL_CRITICAL -> "Overheated" to Color.Red
    StopReason.BATTERY_CRITICAL -> "Low Battery" to Color.Red
}

private fun stoppedMessage(reason: StopReason, detail: String?): String {
    val base = when (reason) {
        StopReason.ERROR_PROFILE ->
            "No endpoint profile found. Configure an endpoint in Settings > Endpoints."

        StopReason.ERROR_AUTH ->
            "Server rejected authentication. Verify stream key/username/password."

        StopReason.ERROR_CAMERA ->
            "Camera error while preparing stream. Check camera permission and close other camera apps."

        StopReason.ERROR_AUDIO ->
            "Microphone/audio error while preparing stream. Check mic permission and close apps using audio input."

        StopReason.ERROR_ENCODER ->
            "Could not connect to the streaming endpoint. Verify URL, network, and ingest server status."

        StopReason.THERMAL_CRITICAL ->
            "Streaming stopped: device overheated. Let the device cool down before retrying."

        StopReason.BATTERY_CRITICAL ->
            "Streaming stopped: battery critically low. Charge device and try again."

        StopReason.USER_REQUEST -> "Stream stopped"
    }

    return if (detail.isNullOrBlank()) base else "$base\n$detail"
}

