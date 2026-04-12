package com.port80.app.data.model

import org.junit.Assert.*
import org.junit.Test

class StreamProtocolTest {

    @Test
    fun `fromUrl parses rtmp scheme`() {
        assertEquals(StreamProtocol.RTMP, StreamProtocol.fromUrl("rtmp://host/app"))
    }

    @Test
    fun `fromUrl parses rtmps scheme`() {
        assertEquals(StreamProtocol.RTMPS, StreamProtocol.fromUrl("rtmps://host/app"))
    }

    @Test
    fun `fromUrl parses srt scheme`() {
        assertEquals(StreamProtocol.SRT, StreamProtocol.fromUrl("srt://host:9000"))
    }

    @Test
    fun `fromUrl is case insensitive`() {
        assertEquals(StreamProtocol.RTMPS, StreamProtocol.fromUrl("RTMPS://host/app"))
        assertEquals(StreamProtocol.SRT, StreamProtocol.fromUrl("SRT://host:9000"))
        assertEquals(StreamProtocol.RTMP, StreamProtocol.fromUrl("Rtmp://host/app"))
    }

    @Test
    fun `fromUrl trims whitespace`() {
        assertEquals(StreamProtocol.RTMPS, StreamProtocol.fromUrl("  rtmps://host/app  "))
    }

    @Test
    fun `fromUrl defaults to RTMP for unknown scheme`() {
        assertEquals(StreamProtocol.RTMP, StreamProtocol.fromUrl("http://example.com"))
    }

    @Test
    fun `fromUrl defaults to RTMP for empty string`() {
        assertEquals(StreamProtocol.RTMP, StreamProtocol.fromUrl(""))
    }
}
