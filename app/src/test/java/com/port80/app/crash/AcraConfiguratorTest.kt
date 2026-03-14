package com.port80.app.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests that ACRA report sanitization works correctly.
 * The most critical test: no secrets survive the sanitization process.
 */
class AcraConfiguratorTest {

    @Test
    fun `sanitizeReport removes stream keys from all fields`() {
        val report = mutableMapOf(
            "STACK_TRACE" to "Exception at rtmp://ingest.example.com/live/my_secret_stream_key_12345",
            "CUSTOM_DATA" to "url=rtmp://host/app/secret123",
            "USER_COMMENT" to "I was streaming when it crashed"
        )

        AcraConfigurator.sanitizeReport(report)

        for ((field, value) in report) {
            assertFalse(
                "Field $field should not contain secret key",
                value.contains("my_secret_stream_key_12345")
            )
            assertFalse(
                "Field $field should not contain secret",
                value.contains("secret123")
            )
        }

        assertEquals("I was streaming when it crashed", report["USER_COMMENT"])
    }

    @Test
    fun `sanitizeReport handles empty map`() {
        val report = mutableMapOf<String, String>()
        AcraConfigurator.sanitizeReport(report)
        assertTrue(report.isEmpty())
    }

    @Test
    fun `sanitizeReport handles multiple sensitive fields`() {
        val report = mutableMapOf(
            "field1" to "streamKey=abc123",
            "field2" to "password=hunter2",
            "field3" to "rtmps://host/app/key456"
        )

        AcraConfigurator.sanitizeReport(report)

        assertFalse(report["field1"]!!.contains("abc123"))
        assertFalse(report["field2"]!!.contains("hunter2"))
        assertFalse(report["field3"]!!.contains("key456"))
    }
}
