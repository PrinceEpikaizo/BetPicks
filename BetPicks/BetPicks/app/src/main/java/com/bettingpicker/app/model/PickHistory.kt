package com.bettingpicker.app.model

import org.json.JSONArray
import org.json.JSONObject

/** Outcome verification status for a previously issued pick. */
enum class PickOutcome { PENDING, WON, LOST }

/**
 * One stored pick record. Persisted across app launches.
 * - At creation time status = PENDING and finalScore = null.
 * - Once the fixture's match result is known, status flips to WON or LOST
 *   and finalScore is set to "X–Y".
 */
data class PickHistoryEntry(
    val id: String,                      // stable hash of fixtureId+pickKind+issuedAt
    val issuedAtUtc: String,             // ISO timestamp when pick was issued
    val fixtureId: Long,
    val sport: Sport,
    val competition: String,
    val kickoffUtc: String,
    val pickKind: PickKind,
    val pickedTeamName: String,
    val opponentName: String,
    val winRateAtIssue: Double,          // raw win-rate when pick was generated (display/audit)
    val weightedWinRateAtIssue: Double,  // draw-weighted score when pick was generated (drove the tier)
    val tier: Tier,
    val h2hFavourable: Boolean,          // true when H2H favoured the pick at issue time
    val h2hWinRate: Double,              // 0..100, raw h2h strength at issue time, for display
    val h2hWeightedWinRate: Double = 0.0, // 0..100, draw-weighted h2h score; drove h2hFavourable
    var status: PickOutcome = PickOutcome.PENDING,
    var finalScore: String? = null,      // "2–1", null while pending
    var resolvedAtUtc: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("issuedAtUtc", issuedAtUtc)
        put("fixtureId", fixtureId); put("sport", sport.name); put("competition", competition)
        put("kickoffUtc", kickoffUtc); put("pickKind", pickKind.name)
        put("pickedTeamName", pickedTeamName); put("opponentName", opponentName)
        put("winRateAtIssue", winRateAtIssue); put("weightedWinRateAtIssue", weightedWinRateAtIssue)
        put("tier", tier.name)
        put("h2hFavourable", h2hFavourable); put("h2hWinRate", h2hWinRate)
        put("h2hWeightedWinRate", h2hWeightedWinRate)
        put("status", status.name)
        put("finalScore", finalScore ?: JSONObject.NULL)
        put("resolvedAtUtc", resolvedAtUtc ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(o: JSONObject) = PickHistoryEntry(
            id = o.getString("id"),
            issuedAtUtc = o.getString("issuedAtUtc"),
            fixtureId = o.getLong("fixtureId"),
            sport = Sport.fromNameOrNull(o.optString("sport")) ?: Sport.SOCCER,
            competition = o.getString("competition"),
            kickoffUtc = o.getString("kickoffUtc"),
            pickKind = PickKind.valueOf(o.getString("pickKind")),
            pickedTeamName = o.getString("pickedTeamName"),
            opponentName = o.getString("opponentName"),
            winRateAtIssue = o.getDouble("winRateAtIssue"),
            weightedWinRateAtIssue = o.optDouble("weightedWinRateAtIssue", o.getDouble("winRateAtIssue")),
            tier = Tier.valueOf(o.getString("tier")),
            h2hFavourable = o.optBoolean("h2hFavourable", false),
            h2hWinRate = o.optDouble("h2hWinRate", 0.0),
            h2hWeightedWinRate = o.optDouble("h2hWeightedWinRate", o.optDouble("h2hWinRate", 0.0)),
            status = PickOutcome.valueOf(o.optString("status", "PENDING")),
            finalScore = o.optString("finalScore").takeIf { it.isNotBlank() && it != "null" },
            resolvedAtUtc = o.optString("resolvedAtUtc").takeIf { it.isNotBlank() && it != "null" }
        )

        fun listToJson(list: List<PickHistoryEntry>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(s: String?): List<PickHistoryEntry> {
            if (s.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(s)
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}

/** Aggregated lifetime counters for the history screen. */
data class LifetimeRecord(
    val total: Int,
    val won: Int,
    val lost: Int,
    val pending: Int,
    val wonRate: Double // 0..100 over resolved
)
