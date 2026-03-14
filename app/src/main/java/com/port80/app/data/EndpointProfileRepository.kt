package com.port80.app.data

import com.port80.app.data.model.EndpointProfile
import kotlinx.coroutines.flow.Flow

/**
 * Interface for managing saved RTMP endpoint profiles.
 * All credential fields (streamKey, username, password) are stored encrypted
 * via EncryptedSharedPreferences backed by Android Keystore.
 *
 * The implementation (in T-005) ensures credentials NEVER appear in logs,
 * Intent extras, or any unencrypted storage.
 */
interface EndpointProfileRepository {
    /** Get all saved profiles as a Flow that updates when profiles change. */
    fun getAll(): Flow<List<EndpointProfile>>

    /** Get a single profile by its ID. Returns null if not found. */
    suspend fun getById(id: String): EndpointProfile?

    /** Get the profile marked as default. Returns null if no default is set. */
    suspend fun getDefault(): EndpointProfile?

    /** Save a new profile or update an existing one (matched by ID). */
    suspend fun save(profile: EndpointProfile)

    /** Delete a profile by its ID. Does nothing if the ID doesn't exist. */
    suspend fun delete(id: String)

    /** Mark a profile as the default (clears default flag on all others). */
    suspend fun setDefault(id: String)

    /**
     * Check if the Android Keystore key is available.
     * Returns false after a device backup/restore (keys don't transfer).
     * When false, the user must re-enter their credentials.
     */
    suspend fun isKeystoreAvailable(): Boolean
}
