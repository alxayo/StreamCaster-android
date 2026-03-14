package com.port80.app.crash

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for CredentialSanitizer — the most important security utility in the app.
 * These tests verify that stream keys, passwords, and auth tokens are ALWAYS
 * removed from strings before they reach logs or crash reports.
 */
class CredentialSanitizerTest {

    @Test
    fun `RTMP URL with stream key is sanitized`() {
        val input = "Connecting to rtmp://ingest.example.com/live/my_secret_stream_key_12345"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse("Stream key should be removed", result.contains("my_secret_stream_key_12345"))
        assertTrue("Host should remain", result.contains("rtmp://ingest.example.com/live/"))
        assertTrue("Should contain mask", result.contains("****"))
    }

    @Test
    fun `RTMPS URL with stream key is sanitized`() {
        val input = "rtmps://secure.server.com/app/secret_key_abc"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse(result.contains("secret_key_abc"))
        assertTrue(result.contains("rtmps://secure.server.com/app/****"))
    }

    @Test
    fun `URL with query parameters is sanitized`() {
        val input = "rtmp://host/app/key?auth=my_token_123&other=visible"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse("Auth token should be removed", result.contains("my_token_123"))
    }

    @Test
    fun `streamKey query parameter is sanitized`() {
        val input = "Request with streamKey=abc123def456 in params"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse(result.contains("abc123def456"))
        assertTrue(result.contains("streamKey=****"))
    }

    @Test
    fun `password query parameter is sanitized`() {
        val input = "Auth failed: password=hunter2"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse(result.contains("hunter2"))
        assertTrue(result.contains("password=****"))
    }

    @Test
    fun `embedded credentials in URL are sanitized`() {
        val input = "rtmp://admin:secretpass@host.com/live/key123"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse("Username should be removed", result.contains("admin"))
        assertFalse("Password should be removed", result.contains("secretpass"))
        assertFalse("Stream key should be removed", result.contains("key123"))
    }

    @Test
    fun `safe string is not modified`() {
        val input = "Stream started successfully at 1280x720 30fps"
        val result = CredentialSanitizer.sanitize(input)

        assertEquals(input, result)
    }

    @Test
    fun `empty string returns empty`() {
        assertEquals("", CredentialSanitizer.sanitize(""))
    }

    @Test
    fun `multiple URLs in one string are all sanitized`() {
        val input = "Primary: rtmp://host1/app/key1 Backup: rtmps://host2/app/key2"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse(result.contains("key1"))
        assertFalse(result.contains("key2"))
    }

    @Test
    fun `case insensitive parameter matching`() {
        val input = "StreamKey=MyKey123 and AUTH=MyToken456"
        val result = CredentialSanitizer.sanitize(input)

        assertFalse(result.contains("MyKey123"))
        assertFalse(result.contains("MyToken456"))
    }
}
