package com.bettingpicker.app.data

import com.bettingpicker.app.model.Fixture
import com.bettingpicker.app.model.Match
import com.bettingpicker.app.model.Sport

/**
 * Centralises access to sports data for every supported [Sport] and
 * converts provider DTOs to domain models. For any sport without a
 * registered [SportsDataSource], returns a deterministic in-memory sample
 * dataset so the app compiles, runs, and the engine logic is verifiable
 * end-to-end with zero configuration.
 *
 * `activeSports` controls which sports are scanned; pass a subset to let
 * the user filter (see the sport chips in MainActivity). Defaults to all
 * supported sports.
 */
class PickRepository(
    private val dataSources: Map<Sport, SportsDataSource> = emptyMap()
) {
    /** Returns upcoming fixtures the app will analyse (next ~7 days), for the given sports. */
    suspend fun upcomingFixtures(
        dateFrom: String,
        dateTo: String,
        activeSports: Collection<Sport> = Sport.entries.toList()
    ): List<Fixture> = activeSports.flatMap { sport ->
        dataSources[sport]?.upcomingFixtures(dateFrom, dateTo) ?: SampleData.upcomingFixtures(sport)
    }

    /** Returns the last (<=limit) finished matches for a team of the given sport. */
    suspend fun lastMatches(sport: Sport, teamId: Long, limit: Int = 20): List<Match> =
        dataSources[sport]?.lastMatches(teamId, limit) ?: SampleData.lastMatches(sport, teamId).take(limit)

    /** Returns finished fixtures with results (used by the outcome checker), for the given sports. */
    suspend fun finishedFixtures(
        activeSports: Collection<Sport> = Sport.entries.toList()
    ): List<Match> = activeSports.flatMap { sport ->
        dataSources[sport]?.finishedFixtures() ?: SampleData.finishedFixtures(sport)
    }

    /** Returns head-to-head records between team pairs, for the given sports. */
    fun headToHead(activeSports: Collection<Sport> = Sport.entries.toList()): List<SampleData.H2HRow> =
        activeSports.flatMap { sport ->
            dataSources[sport]?.headToHead() ?: SampleData.h2hRows(sport)
        }
}
