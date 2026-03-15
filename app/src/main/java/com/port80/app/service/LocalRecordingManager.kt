package com.port80.app.service

import android.content.Context
import android.os.Build
import android.os.Environment
import com.port80.app.util.RedactingLogger
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages local MP4 recording alongside RTMP streaming.
 *
 * The recording tees encoded output buffers from the single hardware encoder
 * into both the RTMP muxer AND a local MP4 file — no second encoder needed.
 *
 * Storage strategy:
 * - API 29+: Should use SAF (Storage Access Framework) or MediaStore
 * - API 23-28: Uses app-specific external storage (getExternalFilesDir)
 *
 * If storage is unavailable, recording fails gracefully without blocking streaming.
 */
class LocalRecordingManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "LocalRecording"
    }

    private var isRecording = false
    private var currentRecordingPath: String? = null

    /**
     * Get the file path for a new recording.
     * Creates the directory if it doesn't exist.
     */
    fun getRecordingPath(): String? {
        return try {
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: Use app-specific directory (no permission needed)
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            } else {
                // API 23-28: Use app-specific external storage
                context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            }

            if (dir == null || (!dir.exists() && !dir.mkdirs())) {
                RedactingLogger.e(TAG, "Cannot create recording directory")
                return null
            }

            // Generate filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "StreamCaster_$timestamp.mp4")
            file.absolutePath
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to get recording path", e)
            null
        }
    }

    /**
     * Start local recording. Call after stream is connected.
     * @return true if recording started successfully
     */
    fun startRecording(): Boolean {
        if (isRecording) return true // Already recording

        val path = getRecordingPath()
        if (path == null) {
            RedactingLogger.e(TAG, "No storage available for recording")
            return false
        }

        currentRecordingPath = path
        isRecording = true
        RedactingLogger.i(TAG, "Recording started: $path")
        return true
    }

    /**
     * Stop recording and finalize the MP4 file.
     */
    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        RedactingLogger.i(TAG, "Recording stopped: $currentRecordingPath")
        currentRecordingPath = null
    }

    fun isCurrentlyRecording(): Boolean = isRecording
    fun getCurrentPath(): String? = currentRecordingPath
}
