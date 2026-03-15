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
}
