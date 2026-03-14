package com.port80.app.crash

/**
 * Utility for removing sensitive information from strings before logging or crash reporting.
 *
 * RTMP URLs contain stream keys that must never appear in logs:
 *   Original:  rtmp://ingest.example.com/live/my_secret_key_12345
 *   Sanitized: rtmp://ingest.example.com/live/[REDACTED]
 *
 * This sanitizer handles:
 * - RTMP/RTMPS URLs with stream keys in the path
 * - Query parameters like streamKey=xxx, key=xxx, password=xxx, auth=xxx
 * - URLs with embedded credentials (rtmp://user:pass@host/app/key)
 *
 * SECURITY: This class is the single source of truth for credential redaction.
 * Used by RedactingLogger (T-038) and ACRA ReportTransformer (T-026).
 */
object CredentialSanitizer {

    // Matches rtmp:// or rtmps:// followed by host/app/streamkey
    // Group 1 captures everything up to and including the app name
    // The rest (the stream key) gets replaced with ****
    private val RTMP_URL_PATTERN = Regex(
        """(rtmps?://[^/\s]+/[^/\s]+)/\S+"""
    )

    // Matches embedded credentials in URLs: rtmp://user:pass@host
    private val EMBEDDED_CREDENTIALS_PATTERN = Regex(
        """(rtmps?://)([^:@\s]+:[^@\s]+)@"""
    )

    // Matches sensitive query parameters and their values
    // Captures the parameter name and replaces only the value
    private val SENSITIVE_PARAM_PATTERN = Regex(
        """((?:streamKey|stream_key|key|password|passwd|auth|token|secret)=)[^\s&]+""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Remove all sensitive data from the given string.
     * Safe to call on any string — returns unchanged if no secrets are found.
     *
     * @param input The string that might contain RTMP URLs, keys, or passwords
     * @return The sanitized string with all secrets replaced by "****"
     */
    fun sanitize(input: String): String {
        var result = input

        // Step 1: Replace embedded user:pass in URLs (before URL path replacement)
        // rtmp://user:pass@host -> rtmp://----:----@host
        result = EMBEDDED_CREDENTIALS_PATTERN.replace(result) { match ->
            "${match.groupValues[1]}****:****@"
        }

        // Step 2: Replace stream keys in RTMP URL paths
        // rtmp://host/app/secret_key -> rtmp://host/app/----
        result = RTMP_URL_PATTERN.replace(result) { match ->
            "${match.groupValues[1]}/****"
        }

        // Step 3: Replace sensitive query parameter values
        // streamKey=abc123 -> streamKey=----
        result = SENSITIVE_PARAM_PATTERN.replace(result) { match ->
            "${match.groupValues[1]}****"
        }

        return result
    }
}
