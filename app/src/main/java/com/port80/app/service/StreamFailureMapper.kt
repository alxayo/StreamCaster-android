package com.port80.app.service

import com.port80.app.crash.CredentialSanitizer
import com.port80.app.data.model.StopReason

/**
 * Utility to map raw error strings to user-facing messages and StopReasons.
 */
object StreamFailureMapper {

    fun mapFailureReason(reason: String): StopReason {
        val normalized = reason.uppercase()
        return when {
            "AUTH" in normalized -> StopReason.ERROR_AUTH
            "AUDIO" in normalized -> StopReason.ERROR_AUDIO
            "CAMERA" in normalized || "PREVIEW" in normalized -> StopReason.ERROR_CAMERA
            else -> StopReason.ERROR_ENCODER
        }
    }

    fun buildFailureDetail(reason: String): String {
        val sanitizedReason = CredentialSanitizer.sanitize(reason)
        val normalized = sanitizedReason.uppercase()

        return when {
            "AUTH" in normalized ->
                "Authentication failed. Double-check stream key and account credentials."

            "TIMED OUT" in normalized || "TIMEOUT" in normalized ->
                "Connection timed out. Verify endpoint URL, internet access, and firewall/network restrictions."

            "REFUSED" in normalized || "UNREACHABLE" in normalized || "NO ROUTE" in normalized ->
                "Server is unreachable. Confirm the RTMP/RTMPS host, port, and that the ingest server is online."

            "AUDIO" in normalized ->
                "Audio initialization failed. Check microphone permission and whether another app is using the mic."

            "CAMERA" in normalized || "PREVIEW" in normalized ->
                "Camera initialization failed. Check camera permission and close other apps using the camera."

            "ENCODER_PREP_FAILED" in normalized ->
                "Device encoder setup failed. Try lowering resolution/FPS/bitrate in Video Settings."

            else ->
                "Could not connect to streaming endpoint. Verify endpoint URL and network, then retry. Detail: $sanitizedReason"
        }
    }
}
