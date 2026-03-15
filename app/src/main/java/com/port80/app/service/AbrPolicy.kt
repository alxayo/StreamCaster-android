package com.port80.app.service

import com.port80.app.util.RedactingLogger

/**
 * ABR decision logic — determines when to step up or down the quality ladder.
 *
 * The policy monitors network congestion signals:
 * - High dropped frame rate → step down
 * - Sustained good conditions → step up
 *
 * It uses a simple "consecutive samples" approach:
 * - 3 consecutive bad samples → step down
 * - 5 consecutive good samples → step up (more conservative to prevent oscillation)
 */
class AbrPolicy(
    private val ladder: List<AbrRung> = AbrLadder.DEFAULT_LADDER,
    private val stepDownThreshold: Int = 3,   // Bad samples before stepping down
    private val stepUpThreshold: Int = 5,     // Good samples before stepping up
    private val maxDroppedFrameRatio: Float = 0.1f  // 10% dropped = congestion
) {
    companion object {
        private const val TAG = "AbrPolicy"
    }

    var currentRungIndex: Int = 0
        private set

    private var consecutiveBadSamples = 0
    private var consecutiveGoodSamples = 0

    val currentRung: AbrRung
        get() = ladder[currentRungIndex]

    /**
     * Report a network quality sample.
     * @param droppedFrames frames dropped in this sample period
     * @param totalFrames total frames in this sample period
     * @return the action to take (StepDown, StepUp, or Hold)
     */
    fun onSample(droppedFrames: Long, totalFrames: Long): AbrAction {
        if (totalFrames == 0L) return AbrAction.Hold

        val dropRatio = droppedFrames.toFloat() / totalFrames

        return if (dropRatio > maxDroppedFrameRatio) {
            // Bad conditions
            consecutiveGoodSamples = 0
            consecutiveBadSamples++

            if (consecutiveBadSamples >= stepDownThreshold && currentRungIndex < ladder.size - 1) {
                consecutiveBadSamples = 0
                currentRungIndex++
                RedactingLogger.i(TAG, "Stepping DOWN to ${currentRung.label}")
                AbrAction.StepDown(currentRung)
            } else {
                AbrAction.Hold
            }
        } else {
            // Good conditions
            consecutiveBadSamples = 0
            consecutiveGoodSamples++

            if (consecutiveGoodSamples >= stepUpThreshold && currentRungIndex > 0) {
                consecutiveGoodSamples = 0
                currentRungIndex--
                RedactingLogger.i(TAG, "Stepping UP to ${currentRung.label}")
                AbrAction.StepUp(currentRung)
            } else {
                AbrAction.Hold
            }
        }
    }

    /** Set the starting position on the ladder. */
    fun setStartingRung(index: Int) {
        currentRungIndex = index.coerceIn(0, ladder.size - 1)
        consecutiveBadSamples = 0
        consecutiveGoodSamples = 0
    }

    /** Reset counters (e.g., after manual quality change). */
    fun reset() {
        consecutiveBadSamples = 0
        consecutiveGoodSamples = 0
    }
}

/** Action returned by AbrPolicy after analyzing a sample. */
sealed class AbrAction {
    data object Hold : AbrAction()
    data class StepDown(val rung: AbrRung) : AbrAction()
    data class StepUp(val rung: AbrRung) : AbrAction()
}
