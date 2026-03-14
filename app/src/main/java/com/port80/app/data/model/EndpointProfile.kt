package com.port80.app.data.model

/**
 * An RTMP streaming endpoint configuration saved by the user.
 * Users can have multiple profiles (e.g., "YouTube", "Twitch", "Custom Server").
 *
 * SECURITY: Stream keys and passwords are stored encrypted via
 * EncryptedSharedPreferences. They must NEVER appear in logs or Intent extras.
 */
data class EndpointProfile(
    /** Unique identifier for this profile (UUID string). */
    val id: String,
    /** User-friendly name like "My YouTube Channel". */
    val name: String,
    /** RTMP or RTMPS URL, e.g., "rtmp://ingest.example.com/live". */
    val rtmpUrl: String,
    /** Stream key that authenticates this specific stream. */
    val streamKey: String,
    /** Optional username for RTMP authentication. */
    val username: String? = null,
    /** Optional password for RTMP authentication. */
    val password: String? = null,
    /** Whether this is the default profile used when starting a stream. */
    val isDefault: Boolean = false
)
