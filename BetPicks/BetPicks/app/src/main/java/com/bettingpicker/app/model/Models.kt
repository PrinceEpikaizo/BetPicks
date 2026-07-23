package com.bettingpicker.app.model

/**
 * A finished or in-progress match/game/bout for any supported [Sport].
 * "home"/"away" are generic slot names — for individual sports (tennis,
 * MMA/boxing) they simply mean "competitor A" / "competitor B", there is
 * no implied home advantage.
 */
data class Match(
    val id: Long,
    val sport: Sport,
    val utcDate: String,
    val homeTeamId: Long,
    val homeTeamName: String,
    val awayTeamId: Long,
    val awayTeamName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val status: String // e.g. "FINISHED", "SCHEDULED"
) {
    val isFinished: Boolean get() = homeScore != null && awayScore != null

    /** Returns "HOME" / "AWAY" / "DRAW" for finished matches, else null. */
    fun winner(): String? {
        if (!isFinished) return null
        return when {
            homeScore!! > awayScore!! -> "HOME"
            awayScore > homeScore -> "AWAY"
            else -> "DRAW"
        }
    }
}

/** Scheduled (future) match/game/bout the engine evaluates. */
data class Fixture(
    val id: Long,
    val sport: Sport,
    val utcDate: String,
    val competitionName: String,
    val homeTeamId: Long,
    val homeTeamName: String,
    val awayTeamId: Long,
    val awayTeamName: String
)

/** A competitor's performance summary across its last N matches. */
data class TeamForm(
    val teamId: Long,
    val teamName: String,
    val matchesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val winRate: Double,        // 0..100 -- raw win-rate (wins / matchesPlayed), for display
    val weightedWinRate: Double // 0..100 -- draws count as half a win; THIS drives thresholds/tiers
)

/**
 * A tiered pick produced by the analysis engine. [weightedWinRate] --
 * the competitor's empirical win-rate over its last N (default 20) matches,
 * with draws counted as half a win -- is what actually drives the
 * threshold/tier decision. [winRateLast20] is the raw (unweighted)
 * win-rate, kept alongside it purely for display/audit so you can see both
 * numbers behind a pick. Neither is a bookmaker conversion from odds.
 * Optional H2H metrics are attached when computed.
 */
data class Pick(
    val fixtureId: Long,
    val sport: Sport,
    val competition: String,
    val kickoffUtc: String,
    val pickKind: PickKind,      // HOME or AWAY (generic slot, see Sport.primaryLabel)
    val pickedTeamId: Long,
    val pickedTeamName: String,
    val opponentName: String,
    val opponentTeamId: Long,
    val winRateLast20: Double,   // percent, 0..100 -- raw, unweighted, for display
    val weightedWinRate: Double, // percent, 0..100 -- draws = half a win; drives threshold/tier
    val sampleSize: Int,         // actual matches used (≤ window size)
    val tier: Tier,              // High / Medium / Low
    val h2hMatches: Int = 0,
    val h2hWinRate: Double = 0.0,          // raw H2H win-rate, for display
    val h2hWeightedWinRate: Double = 0.0,  // draw-weighted H2H score; drives h2hFavourable
    val h2hFavourable: Boolean = false
)

enum class PickKind { HOME, AWAY }

enum class Tier(val display: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}
