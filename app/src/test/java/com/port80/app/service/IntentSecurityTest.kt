package com.port80.app.service

import org.junit.Assert.*
import org.junit.Test

/**
 * Security tests verifying that no credentials are ever passed in Intent extras.
 * The service must ONLY receive a profileId string.
 * Credentials are read from EndpointProfileRepository at runtime.
 */
class IntentSecurityTest {

    @Test
    fun `StreamingService EXTRA_PROFILE_ID is the only expected extra`() {
        // The service should only use profileId from the intent
        assertEquals("profileId", StreamingService.EXTRA_PROFILE_ID)
    }

    @Test
    fun `service does not define extras for credentials`() {
        // Verify no credential-related constants exist on StreamingService
        val fields = StreamingService::class.java.declaredFields.map { it.name }
        val dangerousNames = listOf("EXTRA_STREAM_KEY", "EXTRA_PASSWORD",
            "EXTRA_URL", "EXTRA_RTMP_URL", "EXTRA_AUTH", "EXTRA_TOKEN",
            "EXTRA_USERNAME", "EXTRA_CREDENTIALS")
        for (name in dangerousNames) {
            assertFalse("StreamingService should not have field '$name'", fields.contains(name))
        }
    }
}
