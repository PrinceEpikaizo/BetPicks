package com.bettingpicker.app.analysis

import com.bettingpicker.app.model.Tier

/**
 * Pure functions with no Android/Retrofit/coroutine dependencies, kept
 * separate so they can be exercised by a plain JVM unit test
 * (see app/src/test/.../RuleMathTest.kt).
 */
object RuleMath {

    /** Win-rate percentage (0..100) given wins and total decided matches. */
    fun winRatePct(wins: Int, total: Int): Double =
        if (total <= 0) 0.0 else (wins.toDouble() * 100.0 / total.toDouble())

    /**
     * Draw-weighted score (0..100): each draw counts as half a win instead
     * of being lumped in as a non-win alongside losses.
     *
     * This is what separates, e.g., a 40W-10D-0L record from a 40W-0D-10L
     * record — both have an identical 80% raw win-rate, but the first team
     * has never actually lost, while the second has lost a fifth of its
     * matches outright. Raw win-rate alone can't tell them apart; this can:
     *
     *   weightedWinRatePct(40, 10, 50) = 90.0   (40 + 5) / 50 * 100
     *   weightedWinRatePct(40, 0, 50)  = 80.0   (40 + 0) / 50 * 100
     *
     * For sports with no draws, draws is always 0, so this is identical to
     * [winRatePct] -- the weighting only ever pulls a score UP relative to
     * raw win-rate, never down, since a draw is strictly better than a loss.
     */
    fun weightedWinRatePct(wins: Int, draws: Int, total: Int): Double =
        if (total <= 0) 0.0 else ((wins.toDouble() + draws.toDouble() * 0.5) * 100.0 / total.toDouble())

    /**
     * A pick is HIGH tier once it clears the threshold by 10+ points,
     * MEDIUM once it clears the threshold at all, otherwise LOW (and,
     * upstream, LOW-tier picks that don't even meet the threshold are
     * never surfaced as picks in the first place).
     *
     * Callers pass [weightedWinRatePct] here (not raw win-rate) so the
     * draw-vs-loss distinction actually reaches the tier decision.
     */
    fun classifyTier(winRate: Double, threshold: Double): Tier = when {
        winRate >= threshold + 10.0 -> Tier.HIGH
        winRate >= threshold -> Tier.MEDIUM
        else -> Tier.LOW
    }
}
