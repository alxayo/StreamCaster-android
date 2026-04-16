package com.port80.app.service

import com.port80.app.data.model.SrtKeyLength
import com.port80.app.data.model.SrtMode
import com.port80.app.data.model.VideoCodec
import org.junit.Assert.*
import org.junit.Test

class ConnectionParamsTest {

    @Test
    fun `Rtmp params carry all fields`() {
        val params = ConnectionParams.Rtmp(
            baseUrl = "rtmp://host/app",
            streamKey = "key123",
            username = "user",
            password = "pass",
            videoCodec = VideoCodec.H265,
        )
        assertEquals("rtmp://host/app", params.baseUrl)
        assertEquals("key123", params.streamKey)
        assertEquals("user", params.username)
        assertEquals("pass", params.password)
        assertEquals(VideoCodec.H265, params.videoCodec)
    }

    @Test
    fun `Srt params carry all fields`() {
        val params = ConnectionParams.Srt(
            host = "srt.example.com",
            port = 9000,
            passphrase = "secret",
            srtKeyLength = SrtKeyLength.AES_256,
            latencyMs = 200,
            mode = SrtMode.LISTENER,
            streamId = "stream1",
            videoCodec = VideoCodec.H264,
        )
        assertEquals("srt.example.com", params.host)
        assertEquals(9000, params.port)
        assertEquals("secret", params.passphrase)
        assertEquals(SrtKeyLength.AES_256, params.srtKeyLength)
        assertEquals(200, params.latencyMs)
        assertEquals(SrtMode.LISTENER, params.mode)
        assertEquals("stream1", params.streamId)
        assertEquals(VideoCodec.H264, params.videoCodec)
    }

    @Test
    fun `Srt default latency is 120`() {
        assertEquals(120, ConnectionParams.Srt.DEFAULT_LATENCY_MS)
    }

    @Test
    fun `Srt srtKeyLength defaults to AES_128`() {
        val params = ConnectionParams.Srt(
            host = "h", port = 9000, passphrase = null,
            latencyMs = 120, mode = SrtMode.CALLER,
            streamId = null, videoCodec = VideoCodec.H264,
        )
        assertEquals(SrtKeyLength.AES_128, params.srtKeyLength)
    }

    @Test
    fun `both variants expose videoCodec`() {
        val rtmp: ConnectionParams = ConnectionParams.Rtmp(
            baseUrl = "rtmp://h/a", streamKey = "k",
            username = null, password = null, videoCodec = VideoCodec.H264,
        )
        val srt: ConnectionParams = ConnectionParams.Srt(
            host = "h", port = 9000, passphrase = null,
            latencyMs = 120, mode = SrtMode.CALLER,
            streamId = null, videoCodec = VideoCodec.H265,
        )
        assertEquals(VideoCodec.H264, rtmp.videoCodec)
        assertEquals(VideoCodec.H265, srt.videoCodec)
    }
}
