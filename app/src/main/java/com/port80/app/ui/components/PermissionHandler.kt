package com.port80.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Result of a permission check — tells the caller what permissions were granted.
 * Based on this, the app decides whether to stream video+audio, audio-only, etc.
 */
data class PermissionResult(
    /** Whether CAMERA permission is granted. */
    val cameraGranted: Boolean,
    /** Whether RECORD_AUDIO permission is granted. */
    val audioGranted: Boolean,
    /** Whether POST_NOTIFICATIONS permission is granted (always true below API 33). */
    val notificationsGranted: Boolean
) {
    /** True if we have everything needed for full video+audio streaming. */
    val canStreamVideoAndAudio: Boolean
        get() = cameraGranted && audioGranted

    /** True if we can at least stream audio (no camera needed). */
    val canStreamAudioOnly: Boolean
        get() = audioGranted
}

/**
 * Composable that manages runtime permission requests for streaming.
 *
 * This doesn't show any UI by itself — instead, it provides a function
 * [requestPermissions] that the caller invokes when the user taps "Start Stream".
 * When permissions are resolved, [onResult] is called with the result.
 *
 * Example usage:
 * ```
 * PermissionHandler(
 *     onResult = { result ->
 *         if (result.canStreamVideoAndAudio) {
 *             viewModel.startStream()
 *         } else if (result.canStreamAudioOnly) {
 *             // Offer audio-only mode
 *         }
 *     }
 * ) { requestPermissions ->
 *     Button(onClick = { requestPermissions() }) {
 *         Text("Start Stream")
 *     }
 * }
 * ```
 *
 * @param onResult Called when permission results are available
 * @param content The UI content — receives a lambda to trigger permission requests
 */
@Composable
fun PermissionHandler(
    onResult: (PermissionResult) -> Unit,
    content: @Composable (requestPermissions: () -> Unit) -> Unit
) {
    val context = LocalContext.current

    // Track whether we should show a rationale dialog
    var showRationale by remember { mutableStateOf(false) }
    var rationaleMessage by remember { mutableStateOf("") }
    // Store the callback to invoke after the rationale dialog is dismissed
    var pendingPermissionRequest by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Build the list of permissions we need to request
    val permissionsToRequest = remember {
        buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            // POST_NOTIFICATIONS is only needed on API 33+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    // Launcher that handles the system permission dialog results
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Build a PermissionResult from what the user granted/denied
        val result = PermissionResult(
            cameraGranted = results[Manifest.permission.CAMERA] == true,
            audioGranted = results[Manifest.permission.RECORD_AUDIO] == true,
            notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                results[Manifest.permission.POST_NOTIFICATIONS] == true
            } else {
                true // Below API 33, notifications don't need permission
            }
        )
        onResult(result)
    }

    // Function to check if permissions are already granted
    fun checkCurrentPermissions(): PermissionResult {
        return PermissionResult(
            cameraGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED,
            audioGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED,
            notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    // The main function that triggers permission requests
    val requestPermissions: () -> Unit = {
        val current = checkCurrentPermissions()
        if (current.canStreamVideoAndAudio && current.notificationsGranted) {
            // All permissions already granted — proceed immediately
            onResult(current)
        } else {
            // Need to request missing permissions
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    // Show rationale dialog if needed
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("Permissions Required") },
            text = { Text(rationaleMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    pendingPermissionRequest?.invoke()
                }) {
                    Text("Grant Permissions")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Render the caller's content with the permission request function
    content(requestPermissions)
}
