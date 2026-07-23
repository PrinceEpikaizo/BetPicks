package com.bettingpicker.app.analysis

import com.bettingpicker.app.data.PickRepository
import com.bettingpicker.app.model.Fixture
import com.bettingpicker.app.model.Match
import com.bettingpicker.app.model.Pick
import com.bettingpicker.app.model.PickHistoryEntry
import com.bettingpicker.app.model.PickKind
import com.bettingpicker.app.model.Sport
import com.bettingpicker.app.model.TeamForm
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rule engine that turns upcoming fixtures — for ANY supported [Sport] —
 * into tiered picks.
 *
 * Each [Sport] carries its own tuned home/away win-rate thresholds and
 * minimum sample size (see Sport.kt), so the same engine logic applies
 * uniformly across soccer, basketball, tennis, cricket, rugby, ice hockey,
 * baseball, American football, volleyball and MMA/boxing:
 *
 *   - "HOME"/primary-slot pick when that competitor's DRAW-WEIGHTED
 *     win-rate over its last N matches is ≥ sport.homeThreshold.
 *   - "AWAY"/secondary-slot pick when that competitor's draw-weighted
 *     win-rate over its last N matches is ≥ sport.awayThreshold.
 *
 * Draw-weighted means each draw counts as half a win (see
 * RuleMath.weightedWinRatePct) rather than being lumped in as an
 * undifferentiated "non-win" alongside losses -- a 40W-10D-0L record and a
 * 40W-0D-10L record share the same 80% raw win-rate but are not equally
 * strong picks, since the first has never lost outright. The raw
 * (unweighted) win-rate is still carried on every TeamForm/Pick for
 * display, so nothing about the underlying record is hidden -- only the
 * threshold/tier decision itself uses the weighted figure.
 *
 * For individual sports (tennis, MMA/boxing) "home"/"away" are just slot
 * A/B — there's no real home advantage, so both thresholds are equal and
 * the UI shows Sport.primaryLabel/secondaryLabel instead of "Home"/"Away".
 *
 * H2H filter: optional. When `requireFavourableH2H = true`, only picks whose
 * head-to-head record against the opponent (≥5 H2H meetings with ≥60%
 * draw-weighted win-rate) are kept. The default is to KEEP all picks that
 * match the form rule; the UI exposes a switch that toggles this filter at
 * view time.
 */
class PickEngine(
    private val repo: PickRepository,
    private val lastN: Int = 20,
    /** Optional per-sport threshold overrides; falls back to Sport's own defaults. */
    private val thresholdOverrides: Map<Sport, Pair<Double, Double>> = emptyMap()
) {
    val windowSize: Int get() = lastN
    var requireFavourableH2H: Boolean = false

    private fun homeThresholdFor(sport: Sport) = thresholdOverrides[sport]?.first ?: sport.homeThreshold
    private fun awayThresholdFor(sport: Sport) = thresholdOverrides[sport]?.second ?: sport.awayThreshold

    suspend fun computePicks(fixtures: List<Fixture>): List<Pick> {
        val h2h = H2HEngine(repo)
        val out = ArrayList<Pick>()
        val seen = HashSet<String>()

        for (fx in fixtures) {
            val homeThreshold = homeThresholdFor(fx.sport)
            val awayThreshold = awayThresholdFor(fx.sport)
            val minSample = fx.sport.minSample

            val homeForm = formFor(fx.sport, fx.homeTeamId, fx.homeTeamName)
            val awayForm = formFor(fx.sport, fx.awayTeamId, fx.awayTeamName)

            if (homeForm.matchesPlayed >= minSample && homeForm.weightedWinRate >= homeThreshold) {
                val stats = h2h.statsFor(fx.homeTeamId, fx.awayTeamId)
                if (requireFavourableH2H && !stats.favourable) {
                    // filtered out: form met the bar but H2H doesn't back it up
                } else if (seen.add("${fx.id}|HOME")) {
                    out += Pick(
                        fixtureId = fx.id, sport = fx.sport, competition = fx.competitionName,
                        kickoffUtc = fx.utcDate, pickKind = PickKind.HOME,
                        pickedTeamId = fx.homeTeamId, pickedTeamName = fx.homeTeamName,
                        opponentName = fx.awayTeamName, opponentTeamId = fx.awayTeamId,
                        winRateLast20 = homeForm.winRate,
                        weightedWinRate = homeForm.weightedWinRate,
                        sampleSize = homeForm.matchesPlayed,
                        tier = RuleMath.classifyTier(homeForm.weightedWinRate, homeThreshold),
                        h2hMatches = stats.matchesPlayed,
                        h2hWinRate = stats.winRatePct,
                        h2hWeightedWinRate = stats.weightedWinRatePct,
                        h2hFavourable = stats.favourable
                    )
                }
            }
            if (awayForm.matchesPlayed >= minSample && awayForm.weightedWinRate >= awayThreshold) {
                val stats = h2h.statsFor(fx.awayTeamId, fx.homeTeamId)
                if (requireFavourableH2H && !stats.favourable) {
                    // filtered out: form met the bar but H2H doesn't back it up
                } else if (seen.add("${fx.id}|AWAY")) {
                    out += Pick(
                        fixtureId = fx.id, sport = fx.sport, competition = fx.competitionName,
                        kickoffUtc = fx.utcDate, pickKind = PickKind.AWAY,
                        pickedTeamId = fx.awayTeamId, pickedTeamName = fx.awayTeamName,
                        opponentName = fx.homeTeamName, opponentTeamId = fx.homeTeamId,
                        winRateLast20 = awayForm.winRate,
                        weightedWinRate = awayForm.weightedWinRate,
                        sampleSize = awayForm.matchesPlayed,
                        tier = RuleMath.classifyTier(awayForm.weightedWinRate, awayThreshold),
                        h2hMatches = stats.matchesPlayed,
                        h2hWinRate = stats.winRatePct,
                        h2hWeightedWinRate = stats.weightedWinRatePct,
                        h2hFavourable = stats.favourable
                    )
                }
            }
        }

        return out.sortedWith(
            compareByDescending<Pick> { it.tier.ordinal }.thenByDescending { it.weightedWinRate }
        )
    }

    /** Aggregate stats for a team across its last `lastN` matches. */
    suspend fun formFor(sport: Sport, teamId: Long, teamName: String): TeamForm {
        val matches: List<Match> = repo.lastMatches(sport, teamId, lastN)
        var w = 0; var d = 0; var l = 0
        for (m in matches) {
            if (!m.isFinished) continue
            val isHome = m.homeTeamId == teamId
            val tg = if (isHome) m.homeScore!! else m.awayScore!!
            val og = if (isHome) m.awayScore!! else m.homeScore!!
            when { tg > og -> w++; tg == og -> d++; else -> l++ }
        }
        val n = w + d + l
        val rate = RuleMath.winRatePct(w, n)
        val weighted = RuleMath.weightedWinRatePct(w, d, n)
        return TeamForm(teamId, teamName, n, w, d, l, rate, weighted)
    }

    /** Build a stable, deterministic record id so re-scans don't duplicate. */
    fun entryIdFor(pick: Pick): String =
        "${pick.fixtureId}|${pick.pickKind.name}"

    fun toHistoryEntry(pick: Pick): PickHistoryEntry = PickHistoryEntry(
        id = entryIdFor(pick),
        issuedAtUtc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK).format(Date()),
        fixtureId = pick.fixtureId,
        sport = pick.sport,
        competition = pick.competition,
        kickoffUtc = pick.kickoffUtc,
        pickKind = pick.pickKind,
        pickedTeamName = pick.pickedTeamName,
        opponentName = pick.opponentName,
        winRateAtIssue = pick.winRateLast20,
        weightedWinRateAtIssue = pick.weightedWinRate,
        tier = pick.tier,
        h2hFavourable = pick.h2hFavourable,
        h2hWinRate = pick.h2hWinRate,
        h2hWeightedWinRate = pick.h2hWeightedWinRate
    )
}
