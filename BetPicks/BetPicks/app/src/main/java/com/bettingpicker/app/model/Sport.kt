package com.bettingpicker.app.model

/**
 * Every sport the analyzer supports, each with its own tuned rule
 * thresholds. Athletics is intentionally excluded (head-to-head / fixture
 * based win-rate scoring doesn't map cleanly onto individual track & field
 * events).
 *
 * `homeThreshold` / `awayThreshold` are the minimum empirical win-rate
 * (over the last [PickEngine.windowSize] matches) required for a HOME or
 * AWAY pick. For individual (non-team) sports there's no real home/away
 * advantage, so both thresholds are set equal and the UI shows
 * [primaryLabel]/[secondaryLabel] instead of "Home"/"Away".
 *
 * These starting thresholds are reasonable defaults, not calibrated on
 * real historical data — tune them in-app (Settings) or here to match
 * what you observe for each league/sport.
 */
enum class Sport(
    val displayName: String,
    val emoji: String,
    val individual: Boolean,   // true = player/fighter vs player/fighter, no team "home ground"
    val hasDraws: Boolean,     // true = a finished match can end level (soccer, some cricket/rugby)
    val homeThreshold: Double,
    val awayThreshold: Double,
    val minSample: Int         // minimum finished matches required before a pick is considered
) {
    SOCCER("Soccer", "\u26bd", individual = false, hasDraws = true,
        homeThreshold = 65.0, awayThreshold = 70.0, minSample = 5),

    BASKETBALL("Basketball", "\ud83c\udfc0", individual = false, hasDraws = false,
        homeThreshold = 70.0, awayThreshold = 75.0, minSample = 5),

    TENNIS("Tennis", "\ud83c\udfbe", individual = true, hasDraws = false,
        homeThreshold = 68.0, awayThreshold = 68.0, minSample = 5),

    CRICKET("Cricket", "\ud83c\udfcf", individual = false, hasDraws = true,
        homeThreshold = 62.0, awayThreshold = 66.0, minSample = 5),

    RUGBY("Rugby", "\ud83c\udfc9", individual = false, hasDraws = true,
        homeThreshold = 63.0, awayThreshold = 68.0, minSample = 5),

    ICE_HOCKEY("Ice Hockey", "\ud83c\udfd2", individual = false, hasDraws = false,
        homeThreshold = 60.0, awayThreshold = 65.0, minSample = 5),

    BASEBALL("Baseball", "\u26be", individual = false, hasDraws = false,
        homeThreshold = 58.0, awayThreshold = 62.0, minSample = 8),

    AMERICAN_FOOTBALL("American Football", "\ud83c\udfc8", individual = false, hasDraws = false,
        homeThreshold = 68.0, awayThreshold = 72.0, minSample = 4),

    VOLLEYBALL("Volleyball", "\ud83c\udfd0", individual = false, hasDraws = false,
        homeThreshold = 64.0, awayThreshold = 68.0, minSample = 5),

    MMA_BOXING("MMA / Boxing", "\ud83e\udd4a", individual = true, hasDraws = false,
        homeThreshold = 65.0, awayThreshold = 65.0, minSample = 4);

    /** UI label for the "HOME"-slot pick kind. */
    val primaryLabel: String get() = if (individual) "Fighter/Player A" else "Home"

    /** UI label for the "AWAY"-slot pick kind. */
    val secondaryLabel: String get() = if (individual) "Fighter/Player B" else "Away"

    companion object {
        fun fromNameOrNull(name: String?): Sport? = entries.firstOrNull { it.name == name }
    }
}
