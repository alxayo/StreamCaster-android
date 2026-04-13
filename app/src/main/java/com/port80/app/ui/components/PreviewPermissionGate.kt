package com.port80.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Auto-requests CAMERA permission on composition for live preview.
 *
 * Unlike [PermissionHandler] (which waits for a user tap), this triggers
 * the permission dialog immediately when the composable enters composition.
 *
 * @param onGranted Called once camera permission is granted (user can preview)
 * @param onDenied Called if camera permission is denied (show placeholder)
 */
@Composable
fun PreviewPermissionGate(
    onGranted: @Composable () -> Unit,
    onDenied: @Composable () -> Unit
) {
    val context = LocalContext.current

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    // Request permission on first composition if not already granted
    LaunchedEffect(Unit) {
        if (!cameraGranted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (cameraGranted) {
        onGranted()
    } else {
        onDenied()
    }
}
