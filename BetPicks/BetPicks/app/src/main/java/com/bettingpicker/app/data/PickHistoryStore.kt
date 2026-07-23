package com.bettingpicker.app.data

import android.content.Context
import com.bettingpicker.app.model.PickHistoryEntry

/**
 * SharedPreferences-backed store for pick history. We intentionally do not
 * pull in Room for this version: the dataset is small (one entry per fixture
 * per scan) and the JSON serialisation keeps the schema flexible.
 */
class PickHistoryStore(ctx: Context) {
    private val prefs = ctx.applicationContext
        .getSharedPreferences("betpicks.history", Context.MODE_PRIVATE)

    fun load(): MutableList<PickHistoryEntry> {
        val raw = prefs.getString(KEY_ENTRIES, null)
        val list = PickHistoryEntry.listFromJson(raw).toMutableList()
        // Newest first
        list.sortByDescending { it.issuedAtUtc }
        return list
    }

    fun save(list: List<PickHistoryEntry>) {
        prefs.edit().putString(KEY_ENTRIES, PickHistoryEntry.listToJson(list)).apply()
    }

    /** Idempotent upsert keyed by `id`. */
    fun upsert(entry: PickHistoryEntry) {
        val list = load()
        val i = list.indexOfFirst { it.id == entry.id }
        if (i >= 0) list[i] = entry else list.add(0, entry)
        save(list)
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
    }
}
