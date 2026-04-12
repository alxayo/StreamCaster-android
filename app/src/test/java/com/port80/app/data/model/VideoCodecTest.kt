package com.port80.app.data.model

import org.junit.Assert.*
import org.junit.Test

class VideoCodecTest {

    @Test
    fun `H264 supports SRT`() {
        assertTrue(VideoCodec.H264.supportsSrt())
    }

    @Test
    fun `H265 supports SRT`() {
        assertTrue(VideoCodec.H265.supportsSrt())
    }

    @Test
    fun `AV1 does not support SRT`() {
        assertFalse(VideoCodec.AV1.supportsSrt())
    }

    @Test
    fun `H264 is not Enhanced RTMP`() {
        assertFalse(VideoCodec.H264.isEnhancedRtmp())
    }

    @Test
    fun `H265 is Enhanced RTMP`() {
        assertTrue(VideoCodec.H265.isEnhancedRtmp())
    }

    @Test
    fun `AV1 is Enhanced RTMP`() {
        assertTrue(VideoCodec.AV1.isEnhancedRtmp())
    }

    @Test
    fun `displayName returns expected strings`() {
        assertEquals("H.264", VideoCodec.H264.displayName())
        assertEquals("H.265 (HEVC)", VideoCodec.H265.displayName())
        assertEquals("AV1", VideoCodec.AV1.displayName())
    }
}
