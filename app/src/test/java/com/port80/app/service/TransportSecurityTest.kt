package com.port80.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportSecurityTest {

    // --- isSecureTransport ---

    @Test
    fun `isSecureTransport returns true for rtmps URL`() {
        assertTrue(TransportSecurity.isSecureTransport("rtmps://live.example.com/app/key"))
    }

    @Test
    fun `isSecureTransport returns true for uppercase RTMPS URL`() {
        assertTrue(TransportSecurity.isSecureTransport("RTMPS://live.example.com/app/key"))
    }

    @Test
    fun `isSecureTransport returns true for mixed case RTMPS URL`() {
        assertTrue(TransportSecurity.isSecureTransport("Rtmps://live.example.com/app/key"))
    }

    @Test
    fun `isSecureTransport returns true with leading whitespace`() {
        assertTrue(TransportSecurity.isSecureTransport("  rtmps://live.example.com/app/key"))
    }

    @Test
    fun `isSecureTransport returns false for plain rtmp URL`() {
        assertFalse(TransportSecurity.isSecureTransport("rtmp://live.example.com/app/key"))
    }

    @Test
    fun `isSecureTransport returns false for http URL`() {
        assertFalse(TransportSecurity.isSecureTransport("http://example.com"))
    }

    // --- isPlainRtmp ---

    @Test
    fun `isPlainRtmp returns true for rtmp URL`() {
        assertTrue(TransportSecurity.isPlainRtmp("rtmp://live.example.com/app/key"))
    }

    @Test
    fun `isPlainRtmp returns true for uppercase RTMP URL`() {
        assertTrue(TransportSecurity.isPlainRtmp("RTMP://live.example.com/app/key"))
    }

    @Test
    fun `isPlainRtmp returns true with leading whitespace`() {
        assertTrue(TransportSecurity.isPlainRtmp("  rtmp://live.example.com/app/key"))
    }

    @Test
    fun `isPlainRtmp returns false for rtmps URL`() {
        assertFalse(TransportSecurity.isPlainRtmp("rtmps://live.example.com/app/key"))
    }

    @Test
    fun `isPlainRtmp returns false for http URL`() {
        assertFalse(TransportSecurity.isPlainRtmp("http://example.com"))
    }

    // --- validateUrl ---

    @Test
    fun `validateUrl returns error for empty string`() {
        val error = TransportSecurity.validateUrl("")
        assertNotNull(error)
        assertTrue(error!!.contains("empty", ignoreCase = true))
    }

    @Test
    fun `validateUrl returns error for blank string`() {
        val error = TransportSecurity.validateUrl("   ")
        assertNotNull(error)
        assertTrue(error!!.contains("empty", ignoreCase = true))
    }

    @Test
    fun `validateUrl returns error for non-rtmp protocol`() {
        val error = TransportSecurity.validateUrl("http://example.com/live")
        assertNotNull(error)
        assertTrue(error!!.contains("rtmp://", ignoreCase = true))
    }

    @Test
    fun `validateUrl returns error for missing application path`() {
        val error = TransportSecurity.validateUrl("rtmp://live.example.com")
        assertNotNull(error)
        assertTrue(error!!.contains("application path", ignoreCase = true))
    }

    @Test
    fun `validateUrl returns error for missing hostname`() {
        val error = TransportSecurity.validateUrl("rtmp:///live")
        assertNotNull(error)
        assertTrue(error!!.contains("hostname", ignoreCase = true))
    }

    @Test
    fun `validateUrl returns null for valid rtmp URL`() {
        assertNull(TransportSecurity.validateUrl("rtmp://live.example.com/app"))
    }

    @Test
    fun `validateUrl returns null for valid rtmps URL`() {
        assertNull(TransportSecurity.validateUrl("rtmps://live.example.com/app/key"))
    }

    @Test
    fun `validateUrl returns null for URL with port`() {
        assertNull(TransportSecurity.validateUrl("rtmp://live.example.com:1935/app"))
    }

    @Test
    fun `validateUrl returns null for URL with whitespace trimmed`() {
        assertNull(TransportSecurity.validateUrl("  rtmps://live.example.com/app  "))
    }

    // --- getPlainRtmpWarning ---

    @Test
    fun `getPlainRtmpWarning returns non-empty warning`() {
        val warning = TransportSecurity.getPlainRtmpWarning()
        assertTrue(warning.isNotBlank())
        assertTrue(warning.contains("unencrypted", ignoreCase = true))
        assertTrue(warning.contains("RTMPS", ignoreCase = false))
    }

    // --- SRT transport security ---

    @Test
    fun `isSecureTransport returns true for SRT with passphrase`() {
        assertTrue(TransportSecurity.isSecureTransport("srt://host:9000", "mysecret"))
    }

    @Test
    fun `isSecureTransport returns false for SRT without passphrase`() {
        assertFalse(TransportSecurity.isSecureTransport("srt://host:9000"))
        assertFalse(TransportSecurity.isSecureTransport("srt://host:9000", null))
        assertFalse(TransportSecurity.isSecureTransport("srt://host:9000", ""))
    }

    @Test
    fun `isUnencryptedSrt returns true for SRT without passphrase`() {
        assertTrue(TransportSecurity.isUnencryptedSrt("srt://host:9000"))
        assertTrue(TransportSecurity.isUnencryptedSrt("srt://host:9000", null))
        assertTrue(TransportSecurity.isUnencryptedSrt("srt://host:9000", ""))
    }

    @Test
    fun `isUnencryptedSrt returns false for SRT with passphrase`() {
        assertFalse(TransportSecurity.isUnencryptedSrt("srt://host:9000", "secret"))
    }

    @Test
    fun `isUnencryptedSrt returns false for RTMP URLs`() {
        assertFalse(TransportSecurity.isUnencryptedSrt("rtmp://host/app"))
    }

    @Test
    fun `validateUrl accepts valid SRT URL with port`() {
        assertNull(TransportSecurity.validateUrl("srt://host.com:9000"))
    }

    @Test
    fun `validateUrl accepts SRT URL with query params`() {
        assertNull(TransportSecurity.validateUrl("srt://host.com:9000?mode=caller&latency=120"))
    }

    @Test
    fun `validateUrl rejects SRT URL without port`() {
        val error = TransportSecurity.validateUrl("srt://host.com")
        assertNotNull(error)
        assertTrue(error!!.contains("port", ignoreCase = true))
    }

    @Test
    fun `validateUrl rejects SRT URL with invalid port`() {
        val error = TransportSecurity.validateUrl("srt://host.com:99999")
        assertNotNull(error)
        assertTrue(error!!.contains("port", ignoreCase = true))
    }

    @Test
    fun `validateUrl rejects SRT URL with empty host`() {
        val error = TransportSecurity.validateUrl("srt://:9000")
        assertNotNull(error)
        assertTrue(error!!.contains("hostname", ignoreCase = true))
    }

    @Test
    fun `getUnencryptedSrtWarning returns non-empty warning`() {
        val warning = TransportSecurity.getUnencryptedSrtWarning()
        assertTrue(warning.isNotBlank())
        assertTrue(warning.contains("passphrase", ignoreCase = true))
    }
}
