package com.bettingpicker.app.analysis

import com.bettingpicker.app.data.PickRepository

/**
 * Head-to-head strength summary for a pair of teams.
 * winRatePct = raw % of the pair's historical meetings won by the picked
 * team (for display). weightedWinRatePct additionally credits draws as
 * half a win -- same reasoning as TeamForm.weightedWinRate -- and is what
 * [favourable] actually checks.
 */
data class H2HStats(
    val matchesPlayed: Int,
    val wins: Int,           // picked team's wins vs opponent
    val draws: Int,
    val losses: Int,
    val winRatePct: Double,        // 0..100, raw, for display
    val weightedWinRatePct: Double // 0..100, draws = half a win, drives `favourable`
) {
    /** Favourable = at least 5 H2H meetings AND ≥ 60% draw-weighted win-rate. */
    val favourable: Boolean get() = matchesPlayed >= 5 && weightedWinRatePct >= 60.0
}

/** Engine that turns H2H rows into per-pair stats, flipping perspective when needed. */
class H2HEngine(private val repo: PickRepository) {

    fun statsFor(pickedTeamId: Long, opponentTeamId: Long): H2HStats {
        val rows = repo.headToHead()
        val row = rows.firstOrNull {
            (it.teamId == pickedTeamId && it.oppId == opponentTeamId) ||
            (it.teamId == opponentTeamId && it.oppId == pickedTeamId)
        } ?: return H2HStats(0, 0, 0, 0, 0.0, 0.0)

        val flipped = row.teamId != pickedTeamId
        val w = if (flipped) row.losses else row.wins
        val l = if (flipped) row.wins   else row.losses
        val n = w + row.draws + l
        val rate = RuleMath.winRatePct(w, n)
        val weighted = RuleMath.weightedWinRatePct(w, row.draws, n)
        return H2HStats(n, w, row.draws, l, rate, weighted)
    }
}
