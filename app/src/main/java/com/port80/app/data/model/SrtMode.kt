package com.port80.app.data.model

/**
 * SRT connection mode.
 *
 * - CALLER: App initiates the connection to the server (most common).
 * - LISTENER: App waits for an incoming connection (experimental in RootEncoder).
 * - RENDEZVOUS: Both sides initiate simultaneously for NAT traversal (experimental).
 */
enum class SrtMode {
    CALLER,
    LISTENER,
    RENDEZVOUS;

    /** URL query parameter value for RootEncoder's SRT URL format. */
    fun toUrlParam(): String = name.lowercase()

    /** Human-readable display label. */
    fun displayName(): String = when (this) {
        CALLER -> "Caller"
        LISTENER -> "Listener (experimental)"
        RENDEZVOUS -> "Rendezvous (experimental)"
    }

    companion object {
        /** Parse from stored string, defaulting to CALLER. */
        fun fromString(value: String?): SrtMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CALLER
    }
}
