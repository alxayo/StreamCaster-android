package com.port80.app.data

import com.port80.app.data.model.EndpointProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [ProfileSerializer] map-based serialization.
 * Runs on JVM — no Android context or org.json needed.
 */
class EndpointProfileSerializationTest {

    private val fullProfile = EndpointProfile(
        id = "uuid-123",
        name = "My YouTube",
        rtmpUrl = "rtmp://ingest.youtube.com/live",
        streamKey = "abc-secret-key",
        username = "user@example.com",
        password = "s3cret!",
        isDefault = true
    )

    private val minimalProfile = EndpointProfile(
        id = "uuid-456",
        name = "Twitch",
        rtmpUrl = "rtmps://live.twitch.tv/app",
        streamKey = "live_key_123"
    )

    @Test
    fun `round-trip with all fields populated`() {
        val map = ProfileSerializer.toMap(fullProfile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals(fullProfile.id, restored.id)
        assertEquals(fullProfile.name, restored.name)
        assertEquals(fullProfile.rtmpUrl, restored.rtmpUrl)
        assertEquals(fullProfile.streamKey, restored.streamKey)
        assertEquals(fullProfile.username, restored.username)
        assertEquals(fullProfile.password, restored.password)
        // isDefault is NOT part of the map — it's tracked separately.
        assertFalse(restored.isDefault)
    }

    @Test
    fun `round-trip with optional fields null`() {
        val map = ProfileSerializer.toMap(minimalProfile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals(minimalProfile.id, restored.id)
        assertEquals(minimalProfile.name, restored.name)
        assertEquals(minimalProfile.rtmpUrl, restored.rtmpUrl)
        assertEquals(minimalProfile.streamKey, restored.streamKey)
        assertNull(restored.username)
        assertNull(restored.password)
    }

    @Test
    fun `map contains expected keys and values`() {
        val map = ProfileSerializer.toMap(fullProfile)

        assertEquals("uuid-123", map["id"])
        assertEquals("My YouTube", map["name"])
        assertEquals("rtmp://ingest.youtube.com/live", map["rtmpUrl"])
        assertEquals("abc-secret-key", map["streamKey"])
        assertEquals("user@example.com", map["username"])
        assertEquals("s3cret!", map["password"])
    }

    @Test
    fun `map does not contain isDefault`() {
        val map = ProfileSerializer.toMap(fullProfile)
        assertFalse(map.containsKey("isDefault"))
    }

    @Test
    fun `deserialization handles missing optional fields`() {
        val map = mapOf<String, Any?>(
            "id" to "uuid-789",
            "name" to "Custom",
            "rtmpUrl" to "rtmp://custom.server/live",
            "streamKey" to "key-789"
        )

        val profile = ProfileSerializer.fromMap(map)

        assertEquals("uuid-789", profile.id)
        assertNull(profile.username)
        assertNull(profile.password)
    }

    @Test
    fun `deserialization handles explicit null optional fields`() {
        val map = mapOf<String, Any?>(
            "id" to "uuid-abc",
            "name" to "Explicit Nulls",
            "rtmpUrl" to "rtmp://test.server/live",
            "streamKey" to "key-abc",
            "username" to null,
            "password" to null
        )

        val profile = ProfileSerializer.fromMap(map)

        assertNull(profile.username)
        assertNull(profile.password)
    }

    @Test
    fun `special characters in stream key survive round-trip`() {
        val profile = minimalProfile.copy(
            streamKey = "key/with+special=chars&more"
        )
        val map = ProfileSerializer.toMap(profile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals(profile.streamKey, restored.streamKey)
    }

    @Test
    fun `empty string fields survive round-trip`() {
        val profile = fullProfile.copy(username = "", password = "")
        val map = ProfileSerializer.toMap(profile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals("", restored.username)
        assertEquals("", restored.password)
    }
}
