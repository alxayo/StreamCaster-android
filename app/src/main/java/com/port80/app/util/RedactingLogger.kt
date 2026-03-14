package com.port80.app.util

import android.util.Log
import com.port80.app.crash.CredentialSanitizer

/**
 * A logging wrapper that automatically removes sensitive data before writing logs.
 *
 * ALWAYS use this instead of android.util.Log directly when the message
 * might contain RTMP URLs, stream keys, or credentials.
 *
 * Usage:
 *   RedactingLogger.d("StreamService", "Connecting to $rtmpUrl")
 *   // Logs: "Connecting to rtmp://host/app/[REDACTED]" (key is hidden)
 *
 * For messages that definitely don't contain secrets (e.g., "Button clicked"),
 * using android.util.Log directly is fine.
 */
object RedactingLogger {

    /**
     * Log a debug message with automatic secret redaction.
     * @param tag Log tag (usually the class name)
     * @param message The message to log (secrets will be automatically removed)
     */
    fun d(tag: String, message: String) {
        Log.d(tag, CredentialSanitizer.sanitize(message))
    }

    /**
     * Log an info message with automatic secret redaction.
     */
    fun i(tag: String, message: String) {
        Log.i(tag, CredentialSanitizer.sanitize(message))
    }

    /**
     * Log a warning message with automatic secret redaction.
     */
    fun w(tag: String, message: String) {
        Log.w(tag, CredentialSanitizer.sanitize(message))
    }

    /**
     * Log a warning message with a throwable and automatic secret redaction.
     */
    fun w(tag: String, message: String, throwable: Throwable) {
        Log.w(tag, CredentialSanitizer.sanitize(message), throwable)
    }

    /**
     * Log an error message with automatic secret redaction.
     */
    fun e(tag: String, message: String) {
        Log.e(tag, CredentialSanitizer.sanitize(message))
    }

    /**
     * Log an error message with a throwable and automatic secret redaction.
     */
    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, CredentialSanitizer.sanitize(message), throwable)
    }
}
