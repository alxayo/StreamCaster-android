package com.port80.app.service

import com.port80.app.data.model.Resolution
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for AbrLadder quality ladder.
 */
class AbrLadderTest {

    @Test
    fun `default ladder has 6 rungs`() {
        assertEquals(6, AbrLadder.DEFAULT_LADDER.size)
    }

    @Test
    fun `ladder is ordered from highest to lowest quality`() {
        val ladder = AbrLadder.DEFAULT_LADDER
        // First rung should be highest quality (1080p)
        assertEquals(Resolution(1920, 1080), ladder.first().resolution)
        // Last rung should be lowest quality (360p)
        assertEquals(Resolution(640, 360), ladder.last().resolution)
    }

    @Test
    fun `bitrates decrease down the ladder`() {
        val ladder = AbrLadder.DEFAULT_LADDER
        for (i in 0 until ladder.size - 1) {
            assertTrue(
                "Bitrate at rung $i (${ladder[i].bitrateKbps}) should be >= rung ${i + 1} (${ladder[i + 1].bitrateKbps})",
                ladder[i].bitrateKbps >= ladder[i + 1].bitrateKbps
            )
        }
    }

    @Test
    fun `all rungs have labels`() {
        for (rung in AbrLadder.DEFAULT_LADDER) {
            assertTrue("Label should not be blank", rung.label.isNotBlank())
        }
    }

    @Test
    fun `all rungs have positive bitrates`() {
        for (rung in AbrLadder.DEFAULT_LADDER) {
            assertTrue("Bitrate should be positive", rung.bitrateKbps > 0)
        }
    }

    @Test
    fun `all rungs have positive fps`() {
        for (rung in AbrLadder.DEFAULT_LADDER) {
            assertTrue("FPS should be positive", rung.fps > 0)
        }
    }

    @Test
    fun `findClosestRung returns correct index for 1080p30`() {
        val index = AbrLadder.findClosestRung(Resolution(1920, 1080), 30)
        assertEquals(0, index)
    }

    @Test
    fun `findClosestRung returns correct index for 720p30`() {
        val index = AbrLadder.findClosestRung(Resolution(1280, 720), 30)
        assertEquals(1, index)
    }

    @Test
    fun `findClosestRung returns correct index for 720p15`() {
        val index = AbrLadder.findClosestRung(Resolution(1280, 720), 15)
        assertEquals(2, index)
    }

    @Test
    fun `findClosestRung returns correct index for 480p30`() {
        val index = AbrLadder.findClosestRung(Resolution(854, 480), 30)
        assertEquals(3, index)
    }

    @Test
    fun `findClosestRung returns correct index for 480p15`() {
        val index = AbrLadder.findClosestRung(Resolution(854, 480), 15)
        assertEquals(4, index)
    }

    @Test
    fun `findClosestRung returns correct index for 360p15`() {
        val index = AbrLadder.findClosestRung(Resolution(640, 360), 15)
        assertEquals(5, index)
    }

    @Test
    fun `findClosestRung defaults to 720p30 for unknown resolution`() {
        val index = AbrLadder.findClosestRung(Resolution(999, 999), 30)
        assertEquals(1, index)
    }

    @Test
    fun `findClosestRung defaults to 720p30 for unknown fps`() {
        val index = AbrLadder.findClosestRung(Resolution(1280, 720), 60)
        assertEquals(1, index) // 720p60 not in ladder, falls back to index 1
    }

    @Test
    fun `rung labels match expected format`() {
        val ladder = AbrLadder.DEFAULT_LADDER
        assertEquals("1080p30", ladder[0].label)
        assertEquals("720p30", ladder[1].label)
        assertEquals("720p15", ladder[2].label)
        assertEquals("480p30", ladder[3].label)
        assertEquals("480p15", ladder[4].label)
        assertEquals("360p15", ladder[5].label)
    }

    @Test
    fun `highest rung bitrate is 4500 kbps`() {
        assertEquals(4500, AbrLadder.DEFAULT_LADDER.first().bitrateKbps)
    }

    @Test
    fun `lowest rung bitrate is 500 kbps`() {
        assertEquals(500, AbrLadder.DEFAULT_LADDER.last().bitrateKbps)
    }

    // ── Codec-aware ladders ─────────────────────────────

    @Test
    fun `DEFAULT_LADDER equals H264_LADDER`() {
        assertEquals(AbrLadder.H264_LADDER, AbrLadder.DEFAULT_LADDER)
    }

    @Test
    fun `forCodec H264 returns H264 ladder`() {
        assertEquals(AbrLadder.H264_LADDER, AbrLadder.forCodec(com.port80.app.data.model.VideoCodec.H264))
    }

    @Test
    fun `forCodec H265 returns H265 ladder`() {
        assertEquals(AbrLadder.H265_LADDER, AbrLadder.forCodec(com.port80.app.data.model.VideoCodec.H265))
    }

    @Test
    fun `forCodec AV1 returns AV1 ladder`() {
        assertEquals(AbrLadder.AV1_LADDER, AbrLadder.forCodec(com.port80.app.data.model.VideoCodec.AV1))
    }

    @Test
    fun `all codec ladders have 6 rungs`() {
        assertEquals(6, AbrLadder.H264_LADDER.size)
        assertEquals(6, AbrLadder.H265_LADDER.size)
        assertEquals(6, AbrLadder.AV1_LADDER.size)
    }

    @Test
    fun `H265 bitrates are lower than H264`() {
        for (i in AbrLadder.H264_LADDER.indices) {
            assertTrue(
                "H265 rung $i bitrate should be lower than H264",
                AbrLadder.H265_LADDER[i].bitrateKbps < AbrLadder.H264_LADDER[i].bitrateKbps
            )
        }
    }

    @Test
    fun `AV1 bitrates are lower than H265`() {
        for (i in AbrLadder.H265_LADDER.indices) {
            assertTrue(
                "AV1 rung $i bitrate should be lower than H265",
                AbrLadder.AV1_LADDER[i].bitrateKbps < AbrLadder.H265_LADDER[i].bitrateKbps
            )
        }
    }

    @Test
    fun `findClosestRung works with codec parameter`() {
        val h265Index = AbrLadder.findClosestRung(
            Resolution(1920, 1080), 30, com.port80.app.data.model.VideoCodec.H265
        )
        assertEquals(0, h265Index)
        assertEquals(3000, AbrLadder.H265_LADDER[h265Index].bitrateKbps)
    }
}
