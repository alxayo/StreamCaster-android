package com.port80.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.port80.app.util.RedactingLogger

/**
 * Handles camera access being revoked while streaming in the background.
 *
 * When the OS revokes camera access (e.g., another app takes priority):
 * 1. Stop the video track (no more camera frames)
 * 2. Keep the audio-only RTMP session alive
 * 3. Show "Camera paused" in the notification
 * 4. On return to foreground: re-acquire camera and resume video
 *
 * This is different from T-039 (microphone revocation) which stops the stream
 * entirely because audio is essential.
 */
class CameraRevocationHandler(
    private val context: Context,
    private val onCameraRevoked: () -> Unit,
    private val onCameraAvailable: () -> Unit
) {
    companion object {
        private const val TAG = "CameraRevocation"
    }

    private var wasCameraAvailable = true

    /**
     * Check camera permission status. Call periodically or on lifecycle events.
     */
    fun checkCameraAccess() {
        val isAvailable = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (wasCameraAvailable && !isAvailable) {
            RedactingLogger.w(TAG, "Camera access revoked — switching to audio-only")
            onCameraRevoked()
        } else if (!wasCameraAvailable && isAvailable) {
            RedactingLogger.i(TAG, "Camera access restored — resuming video")
            onCameraAvailable()
        }

        wasCameraAvailable = isAvailable
    }
}
