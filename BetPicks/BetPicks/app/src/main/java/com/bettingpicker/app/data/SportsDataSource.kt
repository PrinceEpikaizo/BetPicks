package com.bettingpicker.app.data

import com.bettingpicker.app.model.Fixture
import com.bettingpicker.app.model.Match
import com.bettingpicker.app.model.Sport

/**
 * Contract a live data provider implements for exactly one [Sport].
 * PickRepository falls back to [SampleData] for any sport that has no
 * registered [SportsDataSource].
 */
interface SportsDataSource {
    val sport: Sport
    suspend fun upcomingFixtures(dateFrom: String, dateTo: String): List<Fixture>
    suspend fun lastMatches(teamId: Long, limit: Int): List<Match>
    suspend fun finishedFixtures(): List<Match>
    fun headToHead(): List<SampleData.H2HRow>
}

/**
 * Wires [FootballApi] (football-data.org) as a [SportsDataSource] for
 * [Sport.SOCCER]. This is the one concrete "real API" example; every
 * network call is wrapped so a transient failure degrades to sample data
 * for that call instead of crashing the scan.
 *
 * Get a free API key at https://www.football-data.org/client/register
 * and pass it in below.
 */
class FootballDataOrgSource(
    private val api: FootballApi,
    private val apiKey: String
) : SportsDataSource {

    override val sport: Sport = Sport.SOCCER

    override suspend fun upcomingFixtures(dateFrom: String, dateTo: String): List<Fixture> =
        runCatching {
            api.upcomingFixtures(apiKey, dateFrom = dateFrom, dateTo = dateTo).matches.map { it.toDomain() }
        }.getOrElse { SampleData.upcomingFixtures(sport) }

    override suspend fun lastMatches(teamId: Long, limit: Int): List<Match> =
        runCatching {
            api.lastMatches(apiKey, teamId = teamId, limit = limit).matches.map { it.toDomain() }
        }.getOrElse { SampleData.lastMatches(sport, teamId).take(limit) }

    override suspend fun finishedFixtures(): List<Match> =
        // football-data.org's team/matches endpoint (used above for lastMatches)
        // already returns FINISHED results; a dedicated "recent results across
        // all competitions" lookup needs per-competition polling, which is out
        // of scope for this reference wiring -- sample data covers the demo.
        SampleData.finishedFixtures(sport)

    override fun headToHead(): List<SampleData.H2HRow> =
        // Same story as above: H2H needs per-pair historical queries against
        // the live API. Sample data covers the demo until that's wired in.
        SampleData.h2hRows(sport)

    private fun MatchDto.toDomain() = Match(
        id = id, sport = sport, utcDate = utcDate,
        homeTeamId = homeTeam.id, homeTeamName = homeTeam.name,
        awayTeamId = awayTeam.id, awayTeamName = awayTeam.name,
        homeScore = score?.fullTime?.home, awayScore = score?.fullTime?.away,
        status = status
    )

    private fun FixtureDto.toDomain() = Fixture(
        id = id, sport = sport, utcDate = utcDate,
        competitionName = competition?.name ?: "\u2014",
        homeTeamId = homeTeam.id, homeTeamName = homeTeam.name,
        awayTeamId = awayTeam.id, awayTeamName = awayTeam.name
    )
}
