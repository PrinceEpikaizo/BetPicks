package com.bettingpicker.app.analysis

import com.bettingpicker.app.model.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit test (no Android framework needed) covering the pure
 * rule math the whole tiering system depends on. Run with `./gradlew test`
 * or `gradle test` (also runs automatically in the CI workflow).
 */
class RuleMathTest {

    @Test
    fun `winRatePct handles zero sample size`() {
        assertEquals(0.0, RuleMath.winRatePct(0, 0), 0.0001)
    }

    @Test
    fun `winRatePct computes basic percentage`() {
        assertEquals(65.0, RuleMath.winRatePct(13, 20), 0.0001)
        assertEquals(75.0, RuleMath.winRatePct(15, 20), 0.0001)
        assertEquals(100.0, RuleMath.winRatePct(20, 20), 0.0001)
    }

    @Test
    fun `classifyTier is LOW below threshold`() {
        assertEquals(Tier.LOW, RuleMath.classifyTier(64.9, 65.0))
    }

    @Test
    fun `classifyTier is MEDIUM at or above threshold but below threshold plus 10`() {
        assertEquals(Tier.MEDIUM, RuleMath.classifyTier(65.0, 65.0))
        assertEquals(Tier.MEDIUM, RuleMath.classifyTier(74.9, 65.0))
    }

    @Test
    fun `classifyTier is HIGH at threshold plus 10 or above`() {
        assertEquals(Tier.HIGH, RuleMath.classifyTier(75.0, 65.0))
        assertEquals(Tier.HIGH, RuleMath.classifyTier(100.0, 65.0))
    }

    @Test
    fun `weightedWinRatePct matches raw win-rate when there are no draws`() {
        assertEquals(RuleMath.winRatePct(13, 20), RuleMath.weightedWinRatePct(13, 0, 20), 0.0001)
        assertEquals(RuleMath.winRatePct(40, 50), RuleMath.weightedWinRatePct(40, 0, 50), 0.0001)
    }

    @Test
    fun `weightedWinRatePct distinguishes draws from losses at equal raw win-rate`() {
        // 40W-10D-0L and 40W-0D-10L both have an 80% raw win-rate...
        assertEquals(80.0, RuleMath.winRatePct(40, 50), 0.0001)
        // ...but weighting a draw as half a win separates them: the draw-only
        // record (never lost) scores higher than the loss-heavy one.
        val neverLost = RuleMath.weightedWinRatePct(40, 10, 50)
        val lossHeavy = RuleMath.weightedWinRatePct(40, 0, 50)
        assertEquals(90.0, neverLost, 0.0001)
        assertEquals(80.0, lossHeavy, 0.0001)
        assertTrue("a draw-only record must weight strictly higher than an equal-win, loss-heavy record",
            neverLost > lossHeavy)
    }

    @Test
    fun `weightedWinRatePct never scores below raw win-rate`() {
        // A draw is never worse than a loss, so the weighted score can only match
        // or exceed the raw win-rate -- it should never pull a score down.
        val cases = listOf(Triple(13, 4, 3), Triple(7, 5, 8), Triple(0, 0, 5), Triple(20, 0, 0))
        for ((w, d, l) in cases) {
            val n = w + d + l
            assertTrue(
                "weighted(${w}W-${d}D-${l}L) should be >= raw win-rate",
                RuleMath.weightedWinRatePct(w, d, n) >= RuleMath.winRatePct(w, n) - 0.0001
            )
        }
    }

    @Test
    fun `weightedWinRatePct handles zero sample size`() {
        assertEquals(0.0, RuleMath.weightedWinRatePct(0, 0, 0), 0.0001)
    }
}
