package com.bettingpicker.app.analysis

import com.bettingpicker.app.data.PickRepository
import com.bettingpicker.app.model.Match
import com.bettingpicker.app.model.PickHistoryEntry
import com.bettingpicker.app.model.PickKind
import com.bettingpicker.app.model.PickOutcome

/**
 * Resolves PENDING pick history entries against finished fixture results.
 *
 * Once a fixture's final score is known:
 *   - HOME pick → WON if home score > away score
 *   - AWAY pick → WON if away score > home score
 *   - Otherwise LOST (a draw is also a loss for a 1X2-style pick)
 */
class OutcomeChecker(private val repo: PickRepository) {

    suspend fun resolve(history: List<PickHistoryEntry>): List<PickHistoryEntry> {
        val finished = repo.finishedFixtures().associateBy { it.id }
        return history.map { entry ->
            val won = finished[entry.fixtureId]?.let { isWin(entry.pickKind, it) }
            if (won == null) entry           // still PENDING (no result yet)
            else entry.resolve(
                status = if (won) PickOutcome.WON else PickOutcome.LOST,
                finalScore = "${finished[entry.fixtureId]!!.homeScore}–${finished[entry.fixtureId]!!.awayScore}"
            )
        }
    }

    private fun isWin(kind: PickKind, m: Match): Boolean {
        if (!m.isFinished) return false
        val h = m.homeScore!!; val a = m.awayScore!!
        return when (kind) {
            PickKind.HOME -> h > a
            PickKind.AWAY -> a > h
        }
    }

    private fun PickHistoryEntry.resolve(
        status: PickOutcome, finalScore: String
    ) = copy(
        status = status,
        finalScore = finalScore,
        resolvedAtUtc = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.UK
        ).format(java.util.Date())
    )
}
