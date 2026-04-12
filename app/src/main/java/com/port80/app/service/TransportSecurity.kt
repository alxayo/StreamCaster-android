package com.port80.app.service

import com.port80.app.util.RedactingLogger

/**
 * Enforces transport security rules for streaming connections.
 *
 * SECURITY RULES from spec §9.2:
 * 1. RTMPS (rtmps://) is the PREFERRED protocol — uses TLS/SSL
 * 2. Plain RTMP (rtmp://) is allowed ONLY with explicit user warning + opt-in
 * 3. We use the SYSTEM DEFAULT TrustManager — no custom X509TrustManager
 * 4. No certificate pinning — users with self-signed certs install via system settings
 * 5. SRT (srt://) with passphrase is considered encrypted; without passphrase, warn user
 *
 * This class validates URLs before connecting.
 */
object TransportSecurity {

    private const val TAG = "TransportSecurity"

    /**
     * Check if the connection uses secure transport.
     * - RTMPS: always secure (TLS)
     * - SRT with passphrase: encrypted (AES)
     * - Plain RTMP / SRT without passphrase: NOT secure
     */
    fun isSecureTransport(url: String, srtPassphrase: String? = null): Boolean {
        val trimmed = url.trim().lowercase()
        return when {
            trimmed.startsWith("rtmps://") -> true
            trimmed.startsWith("srt://") -> !srtPassphrase.isNullOrBlank()
            else -> false
        }
    }

    /**
     * Check if the URL uses plain (unencrypted) RTMP.
     * @param url The RTMP URL to check
     * @return true if the URL uses plain RTMP (credentials sent in cleartext!)
     */
    fun isPlainRtmp(url: String): Boolean {
        return url.trim().lowercase().startsWith("rtmp://") && !isSecureTransport(url)
    }

    /**
     * Check if the URL uses SRT without encryption.
     * @return true if SRT URL with no passphrase
     */
    fun isUnencryptedSrt(url: String, srtPassphrase: String? = null): Boolean {
        return url.trim().lowercase().startsWith("srt://") && srtPassphrase.isNullOrBlank()
    }

    /**
     * Validate that the URL is a valid streaming URL (RTMP, RTMPS, or SRT).
     * @return null if valid, or an error message string if invalid
     */
    fun validateUrl(url: String): String? {
        val trimmed = url.trim()

        if (trimmed.isBlank()) {
            return "URL cannot be empty"
        }

        val lower = trimmed.lowercase()

        // SRT validation
        if (lower.startsWith("srt://")) {
            return validateSrtUrl(trimmed)
        }

        // RTMP/RTMPS validation
        if (!lower.startsWith("rtmp://") && !lower.startsWith("rtmps://")) {
            return "URL must start with rtmp://, rtmps://, or srt://"
        }

        // Check for basic URL structure: protocol://host/app
        val withoutProtocol = trimmed.substringAfter("://")
        if (!withoutProtocol.contains("/")) {
            return "URL must include an application path (e.g., rtmp://host/live)"
        }

        val host = withoutProtocol.substringBefore("/")
        if (host.isBlank()) {
            return "URL must include a hostname"
        }

        return null // Valid
    }

    /**
     * Validate an SRT URL. Format: srt://host:port
     * Port is required (no well-known default for SRT).
     */
    private fun validateSrtUrl(url: String): String? {
        val withoutProtocol = url.substringAfter("://")
        val hostPort = withoutProtocol.substringBefore("?").substringBefore("/")

        if (!hostPort.contains(":")) {
            return "SRT URL must include a port (e.g., srt://host:9000)"
        }

        val host = hostPort.substringBefore(":")
        if (host.isBlank()) {
            return "SRT URL must include a hostname"
        }

        val portStr = hostPort.substringAfter(":")
        val port = portStr.toIntOrNull()
        if (port == null || port !in 1..65535) {
            return "SRT port must be a number between 1 and 65535"
        }

        return null // Valid
    }

    /**
     * Get a user-friendly warning message for plain RTMP connections.
     * The UI should show this and require explicit user consent before connecting.
     */
    fun getPlainRtmpWarning(): String {
        return "This URL uses unencrypted RTMP. Your stream key and " +
               "credentials will be sent in cleartext, which means anyone " +
               "on the same network could intercept them.\n\n" +
               "For security, use RTMPS (rtmps://) if your streaming service supports it.\n\n" +
               "Do you want to continue with the unencrypted connection?"
    }

    /**
     * Get a user-friendly warning for SRT without passphrase encryption.
     */
    fun getUnencryptedSrtWarning(): String {
        return "This SRT connection has no passphrase set. While SRT provides " +
               "reliable transport, the stream data will not be encrypted.\n\n" +
               "Consider adding a passphrase in the endpoint settings for encryption.\n\n" +
               "Do you want to continue without encryption?"
    }
}
