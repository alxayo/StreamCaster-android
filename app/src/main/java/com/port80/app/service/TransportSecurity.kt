package com.port80.app.service

import com.port80.app.util.RedactingLogger

/**
 * Enforces transport security rules for RTMP connections.
 *
 * SECURITY RULES from spec §9.2:
 * 1. RTMPS (rtmps://) is the PREFERRED protocol — uses TLS/SSL
 * 2. Plain RTMP (rtmp://) is allowed ONLY with explicit user warning + opt-in
 * 3. We use the SYSTEM DEFAULT TrustManager — no custom X509TrustManager
 * 4. No certificate pinning — users with self-signed certs install via system settings
 *
 * This class validates URLs before connecting.
 */
object TransportSecurity {

    private const val TAG = "TransportSecurity"

    /**
     * Check if the URL uses secure transport (RTMPS).
     * @param url The RTMP URL to check
     * @return true if the URL uses RTMPS (encrypted)
     */
    fun isSecureTransport(url: String): Boolean {
        return url.trim().lowercase().startsWith("rtmps://")
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
     * Validate that the URL is a valid RTMP/RTMPS URL.
     * @return null if valid, or an error message string if invalid
     */
    fun validateUrl(url: String): String? {
        val trimmed = url.trim()

        if (trimmed.isBlank()) {
            return "URL cannot be empty"
        }

        if (!trimmed.lowercase().startsWith("rtmp://") && !trimmed.lowercase().startsWith("rtmps://")) {
            return "URL must start with rtmp:// or rtmps://"
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
}
