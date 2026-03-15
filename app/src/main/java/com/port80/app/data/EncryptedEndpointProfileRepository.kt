package com.port80.app.data

import android.content.Context
import android.content.SharedPreferences
import com.port80.app.util.RedactingLogger
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.port80.app.data.model.EndpointProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores RTMP endpoint profiles with encrypted credentials.
 * Uses EncryptedSharedPreferences backed by Android Keystore.
 *
 * SECURITY: Stream keys and passwords are encrypted at rest.
 * If the Keystore becomes unavailable (e.g., after device restore),
 * the user must re-enter credentials — we NEVER fall back to plaintext.
 */
@Singleton
class EncryptedEndpointProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : EndpointProfileRepository {

    private val prefs: SharedPreferences? = createEncryptedPrefs()

    private val profilesFlow = MutableStateFlow(loadAllProfiles())

    private fun createEncryptedPrefs(): SharedPreferences? {
        return try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                PREFS_FILE_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: GeneralSecurityException) {
            RedactingLogger.e(TAG, "Keystore unavailable — credentials cannot be accessed")
            null
        } catch (e: Exception) {
            RedactingLogger.e(TAG, "Failed to create encrypted preferences")
            null
        }
    }

    override fun getAll(): Flow<List<EndpointProfile>> = profilesFlow

    override suspend fun getById(id: String): EndpointProfile? {
        val prefs = prefs ?: return null
        val json = prefs.getString(profileKey(id), null) ?: return null
        return ProfileSerializer.fromJsonString(json)
    }

    override suspend fun getDefault(): EndpointProfile? {
        val prefs = prefs ?: return null
        val defaultId = prefs.getString(KEY_DEFAULT_PROFILE_ID, null) ?: return null
        return getById(defaultId)
    }

    override suspend fun save(profile: EndpointProfile) {
        val prefs = prefs
            ?: throw IllegalStateException("Keystore unavailable — cannot save credentials")

        val ids = loadProfileIds().toMutableSet()
        ids.add(profile.id)

        prefs.edit()
            .putString(KEY_PROFILES_INDEX, JSONArray(ids.toList()).toString())
            .putString(profileKey(profile.id), ProfileSerializer.toJsonString(profile))
            .apply()

        refreshFlow()
    }

    override suspend fun delete(id: String) {
        val prefs = prefs ?: return

        val ids = loadProfileIds().toMutableSet()
        if (!ids.remove(id)) return

        val editor = prefs.edit()
            .putString(KEY_PROFILES_INDEX, JSONArray(ids.toList()).toString())
            .remove(profileKey(id))

        // Clear default if the deleted profile was default.
        val defaultId = prefs.getString(KEY_DEFAULT_PROFILE_ID, null)
        if (defaultId == id) {
            editor.remove(KEY_DEFAULT_PROFILE_ID)
        }

        editor.apply()
        refreshFlow()
    }

    override suspend fun setDefault(id: String) {
        val prefs = prefs
            ?: throw IllegalStateException("Keystore unavailable — cannot set default")

        if (prefs.getString(profileKey(id), null) == null) return

        prefs.edit()
            .putString(KEY_DEFAULT_PROFILE_ID, id)
            .apply()

        refreshFlow()
    }

    override suspend fun isKeystoreAvailable(): Boolean = prefs != null

    // ── Internal helpers ──────────────────────────────────────────

    private fun loadProfileIds(): List<String> {
        val prefs = prefs ?: return emptyList()
        val raw = prefs.getString(KEY_PROFILES_INDEX, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadAllProfiles(): List<EndpointProfile> {
        val prefs = prefs ?: return emptyList()
        val defaultId = prefs.getString(KEY_DEFAULT_PROFILE_ID, null)
        return loadProfileIds().mapNotNull { id ->
            val json = prefs.getString(profileKey(id), null) ?: return@mapNotNull null
            try {
                ProfileSerializer.fromJsonString(json).let { profile ->
                    profile.copy(isDefault = profile.id == defaultId)
                }
            } catch (e: Exception) {
                RedactingLogger.w(TAG, "Skipping corrupt profile entry")
                null
            }
        }
    }

    private fun refreshFlow() {
        profilesFlow.value = loadAllProfiles()
    }

    companion object {
        private const val TAG = "EncryptedProfileRepo"
        private const val PREFS_FILE_NAME = "endpoint_profiles_encrypted"
        private const val KEY_PROFILES_INDEX = "profiles_index"
        private const val KEY_DEFAULT_PROFILE_ID = "default_profile_id"

        private fun profileKey(id: String): String = "profile_$id"
    }
}

/**
 * Pure-Kotlin serializer for [EndpointProfile].
 * Uses [Map] as the intermediate representation so the logic is testable
 * on JVM without Android's org.json stubs.
 *
 * The JSON string conversion (via [JSONObject]) is thin glue used only
 * at the storage boundary.
 */
internal object ProfileSerializer {

    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_RTMP_URL = "rtmpUrl"
    private const val KEY_STREAM_KEY = "streamKey"
    private const val KEY_USERNAME = "username"
    private const val KEY_PASSWORD = "password"

    /** Convert an [EndpointProfile] to a [Map]. isDefault is NOT included. */
    fun toMap(profile: EndpointProfile): Map<String, Any?> = buildMap {
        put(KEY_ID, profile.id)
        put(KEY_NAME, profile.name)
        put(KEY_RTMP_URL, profile.rtmpUrl)
        put(KEY_STREAM_KEY, profile.streamKey)
        put(KEY_USERNAME, profile.username)
        put(KEY_PASSWORD, profile.password)
    }

    /** Reconstruct an [EndpointProfile] from a [Map]. */
    fun fromMap(map: Map<String, Any?>): EndpointProfile = EndpointProfile(
        id = map[KEY_ID] as String,
        name = map[KEY_NAME] as String,
        rtmpUrl = map[KEY_RTMP_URL] as String,
        streamKey = map[KEY_STREAM_KEY] as String,
        username = map[KEY_USERNAME] as? String,
        password = map[KEY_PASSWORD] as? String,
        isDefault = false // Resolved externally from default_profile_id.
    )

    /** Serialize a profile to a JSON string via [JSONObject]. */
    fun toJsonString(profile: EndpointProfile): String {
        val json = JSONObject()
        for ((key, value) in toMap(profile)) {
            json.put(key, value ?: JSONObject.NULL)
        }
        return json.toString()
    }

    /** Deserialize a profile from a JSON string via [JSONObject]. */
    fun fromJsonString(jsonString: String): EndpointProfile {
        val json = JSONObject(jsonString)
        val map = mutableMapOf<String, Any?>()
        for (key in json.keys()) {
            map[key] = if (json.isNull(key)) null else json.get(key)
        }
        return fromMap(map)
    }
}
