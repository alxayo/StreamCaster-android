package com.port80.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.port80.app.util.RedactingLogger

/**
 * Detects when the RECORD_AUDIO permission is revoked mid-stream.
 *
 * If the user revokes microphone permission while streaming,
 * we stop the stream with ERROR_AUDIO because we can't continue
 * without audio capture capability.
 *
 * This is checked periodically by the streaming service.
 */
class MicrophoneRevocationHandler(
    private val context: Context,
    private val onRevoked: () -> Unit
) {
    companion object {
        private const val TAG = "MicRevocationHandler"
    }

    /**
     * Check if RECORD_AUDIO permission is still granted.
     * Call this periodically during streaming.
     * @return true if permission is still granted
     */
    fun checkPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            RedactingLogger.w(TAG, "RECORD_AUDIO permission revoked!")
            onRevoked()
        }

        return granted
    }
}
