package com.bettingpicker.app.data

import com.bettingpicker.app.model.Fixture
import com.bettingpicker.app.model.Match
import com.bettingpicker.app.model.Sport
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic sample data so the rule engine can be verified without any
 * API key, across every supported [Sport]. Fixture/kickoff dates are
 * generated relative to "now" (not hardcoded), so the app keeps working
 * correctly no matter when it's installed or run.
 *
 * The soccer numbers here are IDENTICAL to the original single-sport
 * dataset (same team IDs 101-110, fixture IDs 1-5, W/D/L profile and H2H
 * figures) -- only the generation is now shared/generic across sports.
 */
object SampleData {

    data class H2HRow(val teamId: Long, val oppId: Long, val wins: Int, val draws: Int, val losses: Int)

    private data class SportSeed(
        val idBase: Long,
        val fixtureIdBase: Long,
        val competition: String,
        val teamNames: List<String> // exactly 10 entries, index 0..9
    )

    private val SEEDS: Map<Sport, SportSeed> = mapOf(
        Sport.SOCCER to SportSeed(
            100, 0, "Premier League", listOf(
                "Manchester City", "Arsenal", "Liverpool", "Tottenham", "Newcastle",
                "Aston Villa", "Brighton", "West Ham", "Crystal Palace", "Chelsea"
            )
        ),
        Sport.BASKETBALL to SportSeed(
            200, 1000, "NBA", listOf(
                "Boston Celtics", "LA Lakers", "Golden State Warriors", "Milwaukee Bucks", "Denver Nuggets",
                "Miami Heat", "Phoenix Suns", "Dallas Mavericks", "New York Knicks", "Philadelphia 76ers"
            )
        ),
        Sport.TENNIS to SportSeed(
            300, 2000, "ATP / WTA Tour", listOf(
                "L. Ferreira", "N. Sorensen", "D. Kimura", "A. Petrov", "J. Mensah",
                "T. Alvarado", "S. Bergqvist", "M. Okafor", "P. Laurent", "H. Takahashi"
            )
        ),
        Sport.CRICKET to SportSeed(
            400, 3000, "International Cricket", listOf(
                "India", "Australia", "England", "New Zealand", "Pakistan",
                "South Africa", "Sri Lanka", "West Indies", "Bangladesh", "Afghanistan"
            )
        ),
        Sport.RUGBY to SportSeed(
            500, 4000, "Rugby Test Championship", listOf(
                "New Zealand", "South Africa", "France", "Ireland", "England",
                "Australia", "Wales", "Scotland", "Argentina", "Fiji"
            )
        ),
        Sport.ICE_HOCKEY to SportSeed(
            600, 5000, "NHL", listOf(
                "Toronto Maple Leafs", "Montreal Canadiens", "Boston Bruins", "Edmonton Oilers", "Colorado Avalanche",
                "Tampa Bay Lightning", "New York Rangers", "Vegas Golden Knights", "Pittsburgh Penguins", "Detroit Red Wings"
            )
        ),
        Sport.BASEBALL to SportSeed(
            700, 6000, "MLB", listOf(
                "New York Yankees", "Los Angeles Dodgers", "Boston Red Sox", "Houston Astros", "Atlanta Braves",
                "Chicago Cubs", "San Francisco Giants", "St. Louis Cardinals", "Philadelphia Phillies", "Toronto Blue Jays"
            )
        ),
        Sport.AMERICAN_FOOTBALL to SportSeed(
            800, 7000, "NFL", listOf(
                "Kansas City Chiefs", "San Francisco 49ers", "Philadelphia Eagles", "Dallas Cowboys", "Buffalo Bills",
                "Baltimore Ravens", "Miami Dolphins", "Detroit Lions", "Green Bay Packers", "Cincinnati Bengals"
            )
        ),
        Sport.VOLLEYBALL to SportSeed(
            900, 8000, "FIVB Nations League", listOf(
                "Poland", "Italy", "Brazil", "USA", "France",
                "Japan", "Serbia", "Argentina", "Slovenia", "Netherlands"
            )
        ),
        Sport.MMA_BOXING to SportSeed(
            1000, 9000, "Global Combat Series", listOf(
                "K. Draven", "M. Okonkwo", "T. Silvertree", "B. Wexford", "J. Marrow",
                "D. Kastellan", "A. Brannigan", "N. Fuentez", "C. Holstrand", "E. Wraithe"
            )
        )
    )

    // Canonical per-index win/draw/loss pattern across the last 20 matches
    // (this is exactly the original soccer profileFor() data, by index).
    private val BASE_PROFILE: List<Triple<Int, Int, Int>> = listOf(
        Triple(13, 4, 3), Triple(15, 3, 2), Triple(13, 3, 4), Triple(10, 5, 5), Triple(14, 4, 2),
        Triple(12, 5, 3), Triple(11, 5, 4), Triple(8, 6, 6), Triple(7, 5, 8), Triple(16, 2, 2)
    )

    // (home-index, away-index) pairs used for both upcoming fixtures and their
    // "already played" mirror + head-to-head rows -- exactly the original soccer pairing.
    private val FIXTURE_INDEX_PAIRS: List<Pair<Int, Int>> = listOf(0 to 8, 1 to 9, 6 to 4, 5 to 7, 2 to 3)

    private val H2H_PATTERN: List<Triple<Int, Int, Int>> =
        listOf(Triple(6, 2, 0), Triple(5, 3, 2), Triple(4, 3, 3), Triple(3, 2, 5), Triple(7, 2, 1))

    private val FINISHED_SCORES: List<Pair<Int, Int>> = listOf(3 to 0, 1 to 1, 0 to 2, 2 to 1, 2 to 2)

    /** Zeroes out draws for sports where a finished match can't end level, folding them into losses. */
    private fun fold(hasDraws: Boolean, t: Triple<Int, Int, Int>): Triple<Int, Int, Int> =
        if (hasDraws) t else Triple(t.first, 0, t.second + t.third)

    fun teamName(sport: Sport, id: Long): String {
        val seed = SEEDS.getValue(sport)
        val idx = (id - seed.idBase - 1).toInt()
        return seed.teamNames.getOrNull(idx) ?: "Team $id"
    }

    fun teamIds(sport: Sport): List<Long> {
        val seed = SEEDS.getValue(sport)
        return (1..seed.teamNames.size).map { seed.idBase + it }
    }

    /** Upcoming fixtures for one sport, dated over the next ~week from now. */
    fun upcomingFixtures(sport: Sport): List<Fixture> {
        val seed = SEEDS.getValue(sport)
        return FIXTURE_INDEX_PAIRS.mapIndexed { i, (hIdx, aIdx) ->
            Fixture(
                id = seed.fixtureIdBase + i + 1,
                sport = sport,
                utcDate = isoDate(dayOffset = 2 + i, hourUtc = 18 + (i % 4)),
                competitionName = seed.competition,
                homeTeamId = seed.idBase + hIdx + 1, homeTeamName = seed.teamNames[hIdx],
                awayTeamId = seed.idBase + aIdx + 1, awayTeamName = seed.teamNames[aIdx]
            )
        }
    }

    /**
     * "Already played" mirror of the upcoming fixtures (same fixture IDs, a
     * date in the past, and a final score) so a fresh install can
     * immediately demonstrate a PENDING pick resolving to WON/LOST on the
     * next scan -- this is a demo convenience, not live results.
     */
    fun finishedFixtures(sport: Sport): List<Match> {
        val seed = SEEDS.getValue(sport)
        return FIXTURE_INDEX_PAIRS.mapIndexed { i, (hIdx, aIdx) ->
            val raw = FINISHED_SCORES[i % FINISHED_SCORES.size]
            val (h, a) = if (sport.hasDraws || raw.first != raw.second) raw else (raw.first + 1) to raw.second
            Match(
                id = seed.fixtureIdBase + i + 1,
                sport = sport,
                utcDate = isoDate(dayOffset = -100 + i, hourUtc = 20),
                homeTeamId = seed.idBase + hIdx + 1, homeTeamName = seed.teamNames[hIdx],
                awayTeamId = seed.idBase + aIdx + 1, awayTeamName = seed.teamNames[aIdx],
                homeScore = h, awayScore = a, status = "FINISHED"
            )
        }
    }

    /** Synthesises exactly N (<=20) finished matches for one team, matching its BASE_PROFILE win rate. */
    fun lastMatches(sport: Sport, teamId: Long): List<Match> {
        val seed = SEEDS.getValue(sport)
        val teamIdx = (teamId - seed.idBase - 1).toInt().coerceIn(0, seed.teamNames.size - 1)
        val (wins, draws, losses) = fold(sport.hasDraws, BASE_PROFILE[teamIdx])
        val oppIdx = (teamIdx + 1) % seed.teamNames.size
        val oppId = seed.idBase + oppIdx + 1

        val out = ArrayList<Match>(20)
        var i = 0
        repeat(wins) { out += mk(sport, seed, i++, teamId, teamIdx, oppId, oppIdx, homeWin = (i % 2 == 0)) }
        repeat(draws) { out += mk(sport, seed, i++, teamId, teamIdx, oppId, oppIdx, draw = true) }
        repeat(losses) { out += mk(sport, seed, i++, teamId, teamIdx, oppId, oppIdx, homeLose = (i % 2 == 0)) }
        return out.take(20)
    }

    /**
     * Head-to-head rows. Each row is one team's W/D/L vs a specific
     * opponent; the engine sums both directions to derive pair win-rates.
     */
    fun h2hRows(sport: Sport): List<H2HRow> {
        val seed = SEEDS.getValue(sport)
        val rows = ArrayList<H2HRow>()
        FIXTURE_INDEX_PAIRS.forEachIndexed { i, (hIdx, aIdx) ->
            val (w, d, l) = fold(sport.hasDraws, H2H_PATTERN[i])
            val homeId = seed.idBase + hIdx + 1
            val awayId = seed.idBase + aIdx + 1
            rows += H2HRow(homeId, awayId, w, d, l)
            rows += H2HRow(awayId, homeId, l, d, w) // reverse perspective
        }
        return rows
    }

    // ---- aggregation across every requested sport (used by the repository) ----

    fun allUpcomingFixtures(sports: Collection<Sport> = Sport.entries.toList()): List<Fixture> =
        sports.flatMap { upcomingFixtures(it) }

    fun allFinishedFixtures(sports: Collection<Sport> = Sport.entries.toList()): List<Match> =
        sports.flatMap { finishedFixtures(it) }

    fun allH2HRows(sports: Collection<Sport> = Sport.entries.toList()): List<H2HRow> =
        sports.flatMap { h2hRows(it) }

    // ---- internal helpers ----

    private fun isoDate(dayOffset: Int, hourUtc: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)
        cal.set(Calendar.HOUR_OF_DAY, hourUtc)
        cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.UK)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(cal.time)
    }

    private fun mk(
        sport: Sport, seed: SportSeed, i: Int,
        teamId: Long, teamIdx: Int, oppId: Long, oppIdx: Int,
        homeWin: Boolean = false, draw: Boolean = false, homeLose: Boolean = false
    ): Match {
        val isHome = (i % 2 == 0)
        val (h, a) = when {
            draw -> 1 to 1
            homeWin -> if (isHome) 2 to 0 else 0 to 2
            homeLose -> if (isHome) 0 to 2 else 2 to 0
            else -> if (isHome) 2 to 1 else 1 to 2
        }
        val homeId = if (isHome) teamId else oppId
        val awayId = if (isHome) oppId else teamId
        val homeName = if (isHome) seed.teamNames[teamIdx] else seed.teamNames[oppIdx]
        val awayName = if (isHome) seed.teamNames[oppIdx] else seed.teamNames[teamIdx]
        return Match(
            id = seed.idBase * 1000L + teamId * 100L + i,
            sport = sport,
            utcDate = isoDate(dayOffset = -30 + i, hourUtc = 20),
            homeTeamId = homeId, homeTeamName = homeName,
            awayTeamId = awayId, awayTeamName = awayName,
            homeScore = h, awayScore = a, status = "FINISHED"
        )
    }
}
