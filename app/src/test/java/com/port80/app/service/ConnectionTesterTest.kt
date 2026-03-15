package com.port80.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [ConnectionTester] URL parsing logic.
 *
 * These are pure-JVM tests — no Android framework needed.
 * Network-level tests (actual TCP) would be instrumented tests.
 */
class ConnectionTesterTest {

    private val tester = ConnectionTester()

    // ── Host extraction ───────────────────────────

    @Test
    fun `parseHostPort extracts host from rtmp URL`() {
        val (host, _) = tester.parseHostPort("rtmp://live.example.com/app/stream")
        assertEquals("live.example.com", host)
    }

    @Test
    fun `parseHostPort extracts host from rtmps URL`() {
        val (host, _) = tester.parseHostPort("rtmps://secure.example.com/app/stream")
        assertEquals("secure.example.com", host)
    }

    @Test
    fun `parseHostPort extracts host when custom port is specified`() {
        val (host, _) = tester.parseHostPort("rtmp://ingest.example.com:1936/live/key")
        assertEquals("ingest.example.com", host)
    }

    // ── Default port selection ────────────────────

    @Test
    fun `parseHostPort defaults to 1935 for rtmp URLs`() {
        val (_, port) = tester.parseHostPort("rtmp://live.example.com/app/stream")
        assertEquals(1935, port)
    }

    @Test
    fun `parseHostPort defaults to 443 for rtmps URLs`() {
        val (_, port) = tester.parseHostPort("rtmps://secure.example.com/app/stream")
        assertEquals(443, port)
    }

    // ── Custom port ───────────────────────────────

    @Test
    fun `parseHostPort uses explicit port for rtmp`() {
        val (_, port) = tester.parseHostPort("rtmp://live.example.com:1936/app/stream")
        assertEquals(1936, port)
    }

    @Test
    fun `parseHostPort uses explicit port for rtmps`() {
        val (_, port) = tester.parseHostPort("rtmps://secure.example.com:8443/app/stream")
        assertEquals(8443, port)
    }

    // ── Edge cases ────────────────────────────────

    @Test
    fun `parseHostPort handles URL with only app path and no stream key`() {
        val (host, port) = tester.parseHostPort("rtmp://server.io/live")
        assertEquals("server.io", host)
        assertEquals(1935, port)
    }

    @Test
    fun `parseHostPort handles IP address as host`() {
        val (host, port) = tester.parseHostPort("rtmp://192.168.1.100/live/key")
        assertEquals("192.168.1.100", host)
        assertEquals(1935, port)
    }

    @Test
    fun `parseHostPort handles IP address with custom port`() {
        val (host, port) = tester.parseHostPort("rtmp://192.168.1.100:1936/live/key")
        assertEquals("192.168.1.100", host)
        assertEquals(1936, port)
    }

    @Test
    fun `parseHostPort falls back to default port for non-numeric port`() {
        val (host, port) = tester.parseHostPort("rtmp://host.com:abc/app/key")
        assertEquals("host.com", host)
        assertEquals(1935, port)
    }

    @Test
    fun `parseHostPort handles deep path correctly`() {
        val (host, port) = tester.parseHostPort("rtmp://cdn.example.com/app/instance/key123")
        assertEquals("cdn.example.com", host)
        assertEquals(1935, port)
    }
}
