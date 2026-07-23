package com.bettingpicker.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bettingpicker.app.analysis.OutcomeChecker
import com.bettingpicker.app.analysis.PickEngine
import com.bettingpicker.app.data.PickHistoryStore
import com.bettingpicker.app.data.PickRepository
import com.bettingpicker.app.model.Pick
import com.bettingpicker.app.model.PickHistoryEntry
import com.bettingpicker.app.model.PickOutcome
import com.bettingpicker.app.model.Sport
import com.bettingpicker.app.ui.HistoryAdapter
import com.bettingpicker.app.ui.PicksAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var engine: PickEngine
    private lateinit var picksAdapter: PicksAdapter
    private lateinit var historyAdapter: HistoryAdapter
    private lateinit var swr: SwipeRefreshLayout
    private lateinit var emptyPicks: TextView
    private lateinit var emptyHistory: TextView
    private lateinit var historyRecycler: RecyclerView
    private lateinit var picksRecycler: RecyclerView
    private lateinit var lifetimeRecord: TextView
    private lateinit var tabPicks: TextView
    private lateinit var tabHistory: TextView
    private lateinit var toggleH2H: ToggleButton
    private lateinit var sportChipGroup: ChipGroup

    private val repo = PickRepository()
    private lateinit var historyStore: PickHistoryStore
    private val outcomeChecker by lazy { OutcomeChecker(repo) }
    private val settingsPrefs by lazy { getSharedPreferences("betpicks.settings", Context.MODE_PRIVATE) }

    /** The full, unfiltered pick list as last produced by the engine.
     *  Used to re-filter in memory when the H2H toggle flips. */
    private var lastFullPicks: List<Pick> = emptyList()

    /** Sports currently excluded from scans (persisted). Empty = every sport included. */
    private var excludedSports: MutableSet<Sport> = mutableSetOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = PickEngine(repo)
        picksAdapter = PicksAdapter()
        historyAdapter = HistoryAdapter()
        historyStore = PickHistoryStore(this)
        swr = findViewById(R.id.swipeRefresh)
        emptyPicks = findViewById(R.id.txtEmpty)
        emptyHistory = findViewById(R.id.txtEmptyHistory)
        historyRecycler = findViewById(R.id.recyclerHistory)
        picksRecycler = findViewById(R.id.recycler)
        lifetimeRecord = findViewById(R.id.txtLifetime)
        tabPicks = findViewById(R.id.tabPicks)
        tabHistory = findViewById(R.id.tabHistory)
        toggleH2H = findViewById(R.id.toggleH2H)
        sportChipGroup = findViewById(R.id.sportChipGroup)

        picksRecycler.layoutManager = LinearLayoutManager(this)
        picksRecycler.adapter = picksAdapter
        historyRecycler.layoutManager = LinearLayoutManager(this)
        historyRecycler.adapter = historyAdapter

        findViewById<TextView>(R.id.txtDisclaimer).text =
            "Draw-weighted win-rate over each competitor's last ${engine.windowSize} finished matches, " +
            "across soccer, basketball, tennis, cricket, rugby, ice hockey, baseball, American football, " +
            "volleyball and MMA/boxing. Not a guarantee of winning. No betting advice is provided. " +
            "If gambling affects you or someone you know, seek help."

        findViewById<View>(R.id.btnInfo).setOnClickListener { showInfo() }
        findViewById<View>(R.id.btnShare).setOnClickListener { shareHistory() }
        swr.setOnRefreshListener { runScan() }

        toggleH2H.setOnCheckedChangeListener { _, _ -> applyVisibleFilter() }

        tabPicks.setOnClickListener { showTab(true) }
        tabHistory.setOnClickListener { showTab(false) }

        loadExcludedSports()
        buildSportChips()

        showTab(true)
        runScan()
    }

    // ---- sport filter ----

    private fun loadExcludedSports() {
        val saved = settingsPrefs.getStringSet(KEY_EXCLUDED_SPORTS, emptySet()) ?: emptySet()
        excludedSports = saved.mapNotNull { Sport.fromNameOrNull(it) }.toMutableSet()
    }

    private fun saveExcludedSports() {
        settingsPrefs.edit()
            .putStringSet(KEY_EXCLUDED_SPORTS, excludedSports.map { it.name }.toSet())
            .apply()
    }

    private fun buildSportChips() {
        sportChipGroup.removeAllViews()
        for (sport in Sport.entries) {
            val chip = Chip(this).apply {
                text = "${sport.emoji} ${sport.displayName}"
                isCheckable = true
                isChecked = sport !in excludedSports
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) excludedSports.remove(sport) else excludedSports.add(sport)
                    saveExcludedSports()
                    runScan()
                }
            }
            sportChipGroup.addView(chip)
        }
    }

    /** Sports the next scan should include. Falls back to "every sport" if the user unchecked all of them. */
    private fun activeSports(): List<Sport> {
        val active = Sport.entries.filter { it !in excludedSports }
        return active.ifEmpty { Sport.entries.toList() }
    }

    // ---- scanning ----

    private fun runScan() {
        swr.isRefreshing = true
        lifecycleScope.launch {
            try {
                val sports = activeSports()
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(Date())
                val plus7 = SimpleDateFormat("yyyy-MM-dd", Locale.UK)
                    .format(Date(System.currentTimeMillis() + 7L * 24 * 3600 * 1000))
                val fixtures = repo.upcomingFixtures(today, plus7, sports)
                val picks = engine.computePicks(fixtures)
                lastFullPicks = picks

                applyVisibleFilter()
                emptyPicks.visibility = if (picksAdapter.itemCount == 0) View.VISIBLE else View.GONE

                // Persist picks into history (idempotent via stable entry id).
                val existing = historyStore.load()
                val existingIds = existing.map { it.id }.toHashSet()
                for (p in picks) {
                    val e = engine.toHistoryEntry(p)
                    if (existingIds.add(e.id)) historyStore.upsert(e)
                }

                // Resolve PENDING entries whose fixtures have a result now.
                val afterResolve = outcomeChecker.resolve(historyStore.load())
                historyStore.save(afterResolve)
                historyAdapter.submit(afterResolve)
                renderLifetime(afterResolve)
                emptyHistory.visibility =
                    if (afterResolve.isEmpty()) View.VISIBLE else View.GONE
            } catch (t: Throwable) {
                Toast.makeText(
                    this@MainActivity,
                    "Couldn't complete the scan (${t.localizedMessage ?: "network error"}). Showing last known data.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                swr.isRefreshing = false
            }
        }
    }

    /** Re-render the picks list using the current H2H toggle state. */
    private fun applyVisibleFilter() {
        val onlyStrong = toggleH2H.isChecked
        engine.requireFavourableH2H = false   // base pick list is always full
        val visible = if (onlyStrong) lastFullPicks.filter { it.h2hFavourable }
                      else lastFullPicks
        picksAdapter.submit(visible)
        emptyPicks.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderLifetime(list: List<PickHistoryEntry>) {
        val resolved = list.filter { it.status == PickOutcome.WON || it.status == PickOutcome.LOST }
        val won = resolved.count { it.status == PickOutcome.WON }
        val lost = resolved.count { it.status == PickOutcome.LOST }
        val pending = list.count { it.status == PickOutcome.PENDING }
        val winPct = if (resolved.isEmpty()) 0.0 else won.toDouble() * 100.0 / resolved.size.toDouble()
        lifetimeRecord.text =
            "Lifetime: ${won}W\u2013${lost}L (${"%.1f%%".format(winPct)}) \u2022 ${pending} pending"
    }

    private fun showTab(showPicks: Boolean) {
        picksRecycler.visibility    = if (showPicks) View.VISIBLE else View.GONE
        emptyPicks.visibility       = if (showPicks && lastFullPicks.isEmpty()) View.VISIBLE else View.GONE
        historyRecycler.visibility  = if (!showPicks) View.VISIBLE else View.GONE
        emptyHistory.visibility     = if (!showPicks && historyAdapter.itemCount == 0) View.VISIBLE else View.GONE
        lifetimeRecord.visibility   = if (!showPicks) View.VISIBLE else View.GONE
        tabPicks.isSelected   = showPicks
        tabHistory.isSelected = !showPicks
    }

    private fun shareHistory() {
        val list = historyAdapter.current()
        if (list.isEmpty()) {
            Toast.makeText(this, "No pick history yet \u2014 run a scan first.", Toast.LENGTH_SHORT).show()
            return
        }
        val won = list.count { it.status == PickOutcome.WON }
        val lost = list.count { it.status == PickOutcome.LOST }
        val pending = list.count { it.status == PickOutcome.PENDING }
        val body = buildString {
            appendLine("BetPicks Analyzer \u2014 lifetime record: ${won}W-${lost}L, $pending pending")
            appendLine()
            list.take(20).forEach { e ->
                val resultTag = when (e.status) {
                    PickOutcome.WON -> "WON"; PickOutcome.LOST -> "LOST"; PickOutcome.PENDING -> "PENDING"
                }
                appendLine("${e.sport.emoji} ${e.pickedTeamName} vs ${e.opponentName} \u2014 $resultTag")
            }
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, "Share pick history"))
    }

    private fun showInfo() {
        AlertDialog.Builder(this)
            .setTitle("How picks are computed")
            .setMessage(
                "Draw-weighted scoring:\n" +
                "\u2022 Each draw counts as HALF a win, not a non-win. A 40W-10D-0L\n" +
                "  record (weighted 90%) is scored higher than a 40W-0D-10L\n" +
                "  record (weighted 80%), even though both have the same 80%\n" +
                "  raw win-rate \u2014 the first team has never actually lost.\n" +
                "  Pick cards show both numbers: the weighted score (which\n" +
                "  drives the tier below) and the raw win-rate alongside it.\n\n" +
                "Each sport has its own tuned thresholds (see Sport.kt), applied uniformly:\n\n" +
                "\u2022 Primary-slot pick (Home / Player A / Fighter A) if that competitor's weighted\n" +
                "  score in its last 20 matches meets the sport's home threshold.\n" +
                "\u2022 Secondary-slot pick (Away / Player B / Fighter B) if that competitor's weighted\n" +
                "  score meets the sport's away threshold.\n\n" +
                "Tiers:\n" +
                "\u2022 High   \u2014 weighted score \u2265 threshold + 10%\n" +
                "\u2022 Medium \u2014 weighted score \u2265 threshold\n" +
                "Matches below threshold are excluded (no Low tier is shown).\n\n" +
                "H2H filter (toggle in toolbar):\n" +
                "\u2022 Shows only picks where the picked competitor has \u2265 60% draw-weighted\n" +
                "  win-rate in \u2265 5 head-to-head meetings against the opponent.\n\n" +
                "Sport chips (below the tabs):\n" +
                "\u2022 Uncheck a sport to exclude it from scans. Unchecking every sport\n" +
                "  falls back to scanning all of them.\n\n" +
                "Pick history (History tab):\n" +
                "\u2022 Every scan stores a record. When a fixture result is known,\n" +
                "  the record flips to WON or LOST automatically. Lifetime\n" +
                "  W/L record is displayed above the list.\n\n" +
                "This is empirical form, not a guarantee."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    companion object {
        private const val KEY_EXCLUDED_SPORTS = "excluded_sports"
    }
}
