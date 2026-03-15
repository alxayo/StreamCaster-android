package com.port80.app.service

import com.port80.app.data.model.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AbrPolicyTest {

    private lateinit var policy: AbrPolicy
    private val ladder = listOf(
        AbrRung(Resolution(1920, 1080), 30, 4500, "1080p30"),
        AbrRung(Resolution(1280, 720), 30, 2500, "720p30"),
        AbrRung(Resolution(640, 360), 15, 500, "360p15")
    )

    @Before
    fun setUp() {
        policy = AbrPolicy(
            ladder = ladder,
            stepDownThreshold = 3,
            stepUpThreshold = 5,
            maxDroppedFrameRatio = 0.1f
        )
    }

    @Test
    fun `steps down after consecutive bad samples`() {
        // Start at top rung
        policy.setStartingRung(0)

        // Send 2 bad samples — should hold
        repeat(2) {
            val action = policy.onSample(droppedFrames = 20, totalFrames = 100)
            assertTrue("Expected Hold before threshold", action is AbrAction.Hold)
        }

        // 3rd bad sample should trigger step down
        val action = policy.onSample(droppedFrames = 20, totalFrames = 100)
        assertTrue("Expected StepDown", action is AbrAction.StepDown)
        assertEquals(1, policy.currentRungIndex)
        assertEquals("720p30", (action as AbrAction.StepDown).rung.label)
    }

    @Test
    fun `steps up after consecutive good samples`() {
        // Start at middle rung
        policy.setStartingRung(1)

        // Send 4 good samples — should hold
        repeat(4) {
            val action = policy.onSample(droppedFrames = 1, totalFrames = 100)
            assertTrue("Expected Hold before threshold", action is AbrAction.Hold)
        }

        // 5th good sample should trigger step up
        val action = policy.onSample(droppedFrames = 1, totalFrames = 100)
        assertTrue("Expected StepUp", action is AbrAction.StepUp)
        assertEquals(0, policy.currentRungIndex)
        assertEquals("1080p30", (action as AbrAction.StepUp).rung.label)
    }

    @Test
    fun `does not step below lowest rung`() {
        // Start at lowest rung
        policy.setStartingRung(ladder.size - 1)

        // Send many bad samples — should all hold
        repeat(10) {
            val action = policy.onSample(droppedFrames = 50, totalFrames = 100)
            assertTrue("Expected Hold at lowest rung", action is AbrAction.Hold)
        }
        assertEquals(ladder.size - 1, policy.currentRungIndex)
    }

    @Test
    fun `does not step above highest rung`() {
        // Start at highest rung
        policy.setStartingRung(0)

        // Send many good samples — should all hold
        repeat(20) {
            val action = policy.onSample(droppedFrames = 0, totalFrames = 100)
            assertTrue("Expected Hold at highest rung", action is AbrAction.Hold)
        }
        assertEquals(0, policy.currentRungIndex)
    }

    @Test
    fun `reset clears counters`() {
        policy.setStartingRung(0)

        // Accumulate 2 bad samples (just below threshold)
        repeat(2) {
            policy.onSample(droppedFrames = 20, totalFrames = 100)
        }

        // Reset
        policy.reset()

        // Next bad sample should NOT trigger step down (counter was reset)
        val action = policy.onSample(droppedFrames = 20, totalFrames = 100)
        assertTrue("Expected Hold after reset", action is AbrAction.Hold)
        assertEquals(0, policy.currentRungIndex)
    }

    @Test
    fun `zero total frames returns Hold`() {
        val action = policy.onSample(droppedFrames = 0, totalFrames = 0)
        assertTrue(action is AbrAction.Hold)
    }

    @Test
    fun `good sample resets bad counter`() {
        policy.setStartingRung(0)

        // 2 bad samples
        repeat(2) {
            policy.onSample(droppedFrames = 20, totalFrames = 100)
        }

        // 1 good sample resets bad counter
        policy.onSample(droppedFrames = 0, totalFrames = 100)

        // Next bad sample should not trigger step down (counter was reset by good sample)
        val action = policy.onSample(droppedFrames = 20, totalFrames = 100)
        assertTrue("Expected Hold — bad counter was reset", action is AbrAction.Hold)
        assertEquals(0, policy.currentRungIndex)
    }

    @Test
    fun `setStartingRung coerces out-of-bounds index`() {
        policy.setStartingRung(-5)
        assertEquals(0, policy.currentRungIndex)

        policy.setStartingRung(100)
        assertEquals(ladder.size - 1, policy.currentRungIndex)
    }
}
