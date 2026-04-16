package com.port80.app.data.model

import com.pedro.srt.srt.packets.control.handshake.EncryptionType

/**
 * AES key length for SRT encryption.
 *
 * Controls the strength of AES encryption negotiated during the SRT handshake.
 * The passphrase is used to derive a Key Encryption Key (KEK) via PBKDF2,
 * and the key length determines the AES cipher mode (128, 192, or 256-bit).
 *
 * @param bytes Key length in bytes (16, 24, or 32).
 */
enum class SrtKeyLength(val bytes: Int) {
    AES_128(16),
    AES_192(24),
    AES_256(32);

    /** Human-readable display label for UI. */
    fun displayName(): String = when (this) {
        AES_128 -> "AES-128"
        AES_192 -> "AES-192"
        AES_256 -> "AES-256"
    }

    /** Map to RootEncoder's EncryptionType for the setPassphrase() API. */
    fun toRootEncoder(): EncryptionType = when (this) {
        AES_128 -> EncryptionType.AES128
        AES_192 -> EncryptionType.AES192
        AES_256 -> EncryptionType.AES256
    }

    companion object {
        /** Parse from stored string, defaulting to AES_128 (SRT standard default). */
        fun fromString(value: String?): SrtKeyLength =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AES_128
    }
}
