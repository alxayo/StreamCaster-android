package com.port80.app.data

import com.port80.app.data.model.EndpointProfile
import com.port80.app.data.model.SrtMode
import com.port80.app.data.model.VideoCodec
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
        url = "rtmp://ingest.youtube.com/live",
        streamKey = "abc-secret-key",
        username = "user@example.com",
        password = "s3cret!",
        isDefault = true
    )

    private val minimalProfile = EndpointProfile(
        id = "uuid-456",
        name = "Twitch",
        url = "rtmps://live.twitch.tv/app",
        streamKey = "live_key_123"
    )

    @Test
    fun `round-trip with all fields populated`() {
        val map = ProfileSerializer.toMap(fullProfile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals(fullProfile.id, restored.id)
        assertEquals(fullProfile.name, restored.name)
        assertEquals(fullProfile.url, restored.url)
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
        assertEquals(minimalProfile.url, restored.url)
        assertEquals(minimalProfile.streamKey, restored.streamKey)
        assertNull(restored.username)
        assertNull(restored.password)
    }

    @Test
    fun `map contains expected keys and values`() {
        val map = ProfileSerializer.toMap(fullProfile)

        assertEquals("uuid-123", map["id"])
        assertEquals("My YouTube", map["name"])
        assertEquals("rtmp://ingest.youtube.com/live", map["url"])
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
            "url" to "rtmp://custom.server/live",
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
            "url" to "rtmp://test.server/live",
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

    // ── SRT fields ──────────────────────────────────────────────────

    private val srtProfile = EndpointProfile(
        id = "uuid-srt",
        name = "SRT Server",
        url = "srt://192.168.0.12:10080",
        streamKey = "",
        videoCodec = VideoCodec.H265,
        srtPassphrase = "mysecretpass10",
        srtLatencyMs = 200,
        srtMode = SrtMode.CALLER,
        srtStreamId = "#!::m=publish,r=live/test"
    )

    @Test
    fun `round-trip with all SRT fields populated`() {
        val map = ProfileSerializer.toMap(srtProfile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals(srtProfile.id, restored.id)
        assertEquals(srtProfile.name, restored.name)
        assertEquals(srtProfile.url, restored.url)
        assertEquals(VideoCodec.H265, restored.videoCodec)
        assertEquals("mysecretpass10", restored.srtPassphrase)
        assertEquals(200, restored.srtLatencyMs)
        assertEquals(SrtMode.CALLER, restored.srtMode)
        assertEquals("#!::m=publish,r=live/test", restored.srtStreamId)
    }

    @Test
    fun `round-trip preserves videoCodec for RTMP profiles`() {
        val profile = fullProfile.copy(videoCodec = VideoCodec.AV1)
        val map = ProfileSerializer.toMap(profile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals(VideoCodec.AV1, restored.videoCodec)
    }

    @Test
    fun `deserialization of legacy profile without SRT fields uses defaults`() {
        val legacyMap = mapOf<String, Any?>(
            "id" to "uuid-old",
            "name" to "Old RTMP",
            "url" to "rtmp://old.server/live",
            "streamKey" to "key-old",
            "username" to null,
            "password" to null
        )

        val profile = ProfileSerializer.fromMap(legacyMap)

        assertEquals(VideoCodec.H264, profile.videoCodec)
        assertNull(profile.srtPassphrase)
        assertEquals(120, profile.srtLatencyMs)
        assertEquals(SrtMode.CALLER, profile.srtMode)
        assertNull(profile.srtStreamId)
    }

    @Test
    fun `deserialization handles unknown videoCodec gracefully`() {
        val map = mapOf<String, Any?>(
            "id" to "uuid-bad-codec",
            "name" to "Bad Codec",
            "url" to "rtmp://server/live",
            "streamKey" to "key",
            "videoCodec" to "VP9_NONEXISTENT"
        )

        val profile = ProfileSerializer.fromMap(map)
        assertEquals(VideoCodec.H264, profile.videoCodec)
    }

    @Test
    fun `SRT stream ID with special characters survives round-trip`() {
        val profile = srtProfile.copy(srtStreamId = "#!::m=publish,r=live/stream key&extra=val")
        val map = ProfileSerializer.toMap(profile)
        val restored = ProfileSerializer.fromMap(map)

        assertEquals(profile.srtStreamId, restored.srtStreamId)
    }

    @Test
    fun `map contains SRT keys with correct values`() {
        val map = ProfileSerializer.toMap(srtProfile)

        assertEquals("H265", map["videoCodec"])
        assertEquals("mysecretpass10", map["srtPassphrase"])
        assertEquals(200, map["srtLatencyMs"])
        assertEquals("CALLER", map["srtMode"])
        assertEquals("#!::m=publish,r=live/test", map["srtStreamId"])
    }

    @Test
    fun `null SRT optional fields survive round-trip`() {
        val profile = srtProfile.copy(
            srtPassphrase = null,
            srtStreamId = null
        )
        val map = ProfileSerializer.toMap(profile)
        val restored = ProfileSerializer.fromMap(map)

        assertNull(restored.srtPassphrase)
        assertNull(restored.srtStreamId)
    }
}
