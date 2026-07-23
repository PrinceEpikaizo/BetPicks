package com.bettingpicker.app.data

import com.bettingpicker.app.model.Fixture
import com.bettingpicker.app.model.Match
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for football-data.org -- the one concrete, wired
 * reference implementation of [SportsDataSource] (see FootballDataOrgSource
 * in SportsDataSource.kt). Swap in API-Football using the same DTO shapes
 * if preferred.
 *
 * football-data.org sample (free tier):
 *   GET https://api.football-data.org/v4/teams/{id}/matches?limit=20&status=FINISHED
 *   GET https://api.football-data.org/v4/matches?status=SCHEDULED&dateFrom=today&dateTo=+7d
 *
 * API-Football sample:
 *   GET https://v3.football.api-sports.io/fixtures?team=33&last=20
 *   GET https://v3.football.api-sports.io/fixtures?next=20
 *
 * To wire a LIVE source for the other sports, implement [SportsDataSource]
 * per sport and register it with PickRepository's `dataSources` map. Real
 * multi-sport data providers worth looking at:
 *   - API-SPORTS family (api-sports.io): API-Basketball, API-Baseball,
 *     API-Hockey, API-Rugby, API-Volleyball, API-MMA -- same auth/shape as
 *     API-Football, one API key per sport.
 *   - TheSportsDB -- broad multi-sport coverage, simpler free tier.
 * Until you wire one in, that sport falls back to the built-in SampleData.
 */
interface FootballApi {
    /** Last `limit` (≤20) FINISHED matches for a given team (home+away). */
    @GET("v4/teams/{teamId}/matches")
    suspend fun lastMatches(
        @Header("X-Auth-Token") token: String,
        @Path("teamId") teamId: Long,
        @Query("status") status: String = "FINISHED",
        @Query("limit") limit: Int = 20
    ): MatchListDto

    /** Upcoming scheduled fixtures across major competitions. */
    @GET("v4/matches")
    suspend fun upcomingFixtures(
        @Header("X-Auth-Token") token: String,
        @Query("status") status: String = "SCHEDULED",
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String
    ): FixtureListDto
}

/* --- DTOs kept minimal; map to domain Match / Fixture in the repository. --- */

data class TeamMini(val id: Long, val name: String)
data class ScoreDto(val fullTime: ScorePair?)
data class ScorePair(val home: Int?, val away: Int?)

data class MatchDto(
    val id: Long,
    val utcDate: String,
    val status: String,
    val homeTeam: TeamMini,
    val awayTeam: TeamMini,
    val score: ScoreDto?
)

data class MatchListDto(val matches: List<MatchDto> = emptyList())

data class CompetitionMini(val name: String)
data class FixtureDto(
    val id: Long,
    val utcDate: String,
    val status: String,
    val competition: CompetitionMini?,
    val homeTeam: TeamMini,
    val awayTeam: TeamMini
)
data class FixtureListDto(val matches: List<FixtureDto> = emptyList())
