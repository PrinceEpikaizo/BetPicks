# BetPicks Analyzer — Android (Kotlin)

Scans upcoming fixtures across **10 sports** and surfaces **tiered empirical-form
picks** based on each competitor's draw-weighted win-rate in its last **20**
finished matches (draws count as half a win — see below).
Soccer, basketball, tennis, cricket, rugby, ice hockey, baseball, American
football, volleyball, and MMA/boxing are all supported (athletics is
intentionally excluded — see note below). Ships with a deterministic offline
sample dataset, so it's fully usable with zero configuration.

This is a **pick/analysis tool**, not a betting product — it doesn't place bets
or move money. The in-app disclaimer is not user-editable from a config flag.

## Getting the APK onto your phone

There are two ways to get an installable `.apk`. **Neither requires you to
compile anything by hand.**

### Option A — GitHub Actions (recommended, no Android Studio needed)
1. Create a new **public** GitHub repo and push this project to it:
   ```bash
   cd BetPicks
   git init && git add . && git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<you>/BetPicks.git
   git push -u origin main
   ```
2. On GitHub, open the **Actions** tab — a "Build APK" workflow run starts
   automatically (it's also triggerable manually via "Run workflow").
3. When it finishes (~3–5 min), open the run and download the
   **BetPicks-debug-apk** artifact from the run summary page. That zip
   contains `app-debug.apk`.
4. Transfer the `.apk` to your phone (email it to yourself, upload to
   Drive, use `adb push`, etc.), then tap it to install. Android will ask
   you to allow installs from that source the first time — that's normal
   for anything installed outside the Play Store.

This works because GitHub's own servers build the APK — they have full
internet access to Google's Maven repository, which is what actually
compiles Android projects.

### Option B — Android Studio, on your own machine
1. Install **Android Studio** (Hedgehog 2023.1.1 or newer).
2. Open the `BetPicks/` folder. If it prompts about a missing Gradle
   wrapper, let it generate one (or `File → Sync Project with Gradle Files`)
   — Android Studio bundles its own Gradle and will fetch AGP 8.2.0 / Kotlin
   1.9.22 automatically.
3. Build & run on an API 24+ device or emulator, or
   `Build → Build App Bundle(s) / APK(s) → Build APK(s)` for an installable file.

> **Why not build it for you directly?** This project was assembled in a
> sandboxed environment with no network access to Google's Maven repo or
> Gradle's distribution servers — only a small allowlist (GitHub, PyPI, npm).
> Compiling an Android project needs both, so producing the `.apk` had to be
> handed off to GitHub Actions (or your own machine) rather than done in-chat.

## What changed from the original zip

The uploaded project had a few bugs that would have failed to build:
- `cardview` and `retrofit` were used in code but never declared as Gradle
  dependencies.
- `PickRepository.kt` imported `MatchDto`/`FixtureDto` from the wrong
  package (`model` instead of `data`, where they're actually defined).
- `finishedFixtures()` made a network call and silently discarded the result.
- Fixture dates were hardcoded to a fixed week, so the sample data would
  have gone stale outside that window; dates are now generated relative to
  "now".
- No launcher icon was set.

Everything else below is new: multi-sport support, per-sport thresholds, the
sport filter, pluggable live-data architecture, error handling, dark-mode-safe
card colors, a share-history action, and a unit test for the rule math.

## How picks are computed

**Draw-weighted scoring.** Each draw counts as HALF a win, not an
undifferentiated non-win alongside losses. A 40W-10D-0L record and a
40W-0D-10L record both have the same 80% raw win-rate, but they aren't
equally strong: the first team has never actually lost. Weighting draws
separates them:

```
weightedWinRate = (wins + draws × 0.5) / total × 100

40W-10D-0L  →  (40 + 5)  / 50 × 100 = 90.0%   (never lost)
40W-0D-10L  →  (40 + 0)  / 50 × 100 = 80.0%   (lost 1 in 5)
```

The **weighted score drives every threshold/tier decision** (and the H2H
"favourable" check, which has the identical blind spot and gets the same
fix). The **raw win-rate is kept alongside it everywhere** — on pick cards,
history rows, and in the info dialog — so nothing is hidden; you can always
see both numbers behind a decision. Weighted can only ever be ≥ raw, never
below it, since a draw is never worse than a loss. For sports with no draws
the two numbers are always identical.

Each sport has its own tuned thresholds (`Sport.kt`), applied uniformly:

| Sport | Primary (Home/A) threshold | Secondary (Away/B) threshold | Draws possible? |
|---|---|---|---|
| Soccer | 65.0% | 70.0% | Yes |
| Basketball | 70.0% | 75.0% | No |
| Tennis | 68.0% | 68.0% | No |
| Cricket | 62.0% | 66.0% | Yes |
| Rugby | 63.0% | 68.0% | Yes |
| Ice Hockey | 60.0% | 65.0% | No |
| Baseball | 58.0% | 62.0% | No |
| American Football | 68.0% | 72.0% | No |
| Volleyball | 64.0% | 68.0% | No |
| MMA / Boxing | 65.0% | 65.0% | No |

For individual sports (tennis, MMA/boxing) "primary/secondary" are just slot
A/B — there's no real home advantage, so both thresholds match and the UI
shows "Player A/B" or "Fighter A/B" instead of "Home"/"Away".

**Tiers:**
- **High** — weighted score ≥ threshold **+ 10 pp**
- **Medium** — weighted score ≥ threshold
- Below-threshold competitors are excluded (no Low tier is ever shown).

These are starting defaults, not values calibrated on real historical
results — tune them in `Sport.kt` (or pass `thresholdOverrides` into
`PickEngine`) to match what you observe.

> Both percentages shown are **empirical** (derived from actual win/draw/loss
> counts), *not* an implied probability from bookmaker odds.

## In-app features
- **Sport filter chips** — uncheck a sport to exclude it from scans
  (persisted between launches; unchecking everything falls back to scanning
  all sports rather than showing a blank screen).
- **H2H toggle** — narrow the list to picks where the competitor also has a
  strong (≥60% draw-weighted score over ≥5 meetings) head-to-head record
  against this opponent.
- **Pick history** — every scan is recorded; once a fixture's result is
  known the record flips to WON/LOST automatically, with a running lifetime
  W–L record shown above the list.
- **Share** — export your pick history as plain text via Android's share sheet.
- Pull-to-refresh, and a network-failure toast that falls back to last-known
  data instead of crashing.

## Project layout
```
BetPicks/
├── .github/workflows/build-apk.yml   ← builds the APK on every push
├── settings.gradle / build.gradle / gradle.properties
└── app/
    ├── build.gradle
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/bettingpicker/app/
        │   │   ├── MainActivity.kt
        │   │   ├── analysis/
        │   │   │   ├── PickEngine.kt        ← rule engine (sport-aware)
        │   │   │   ├── RuleMath.kt          ← pure, unit-tested math
        │   │   │   ├── H2HEngine.kt
        │   │   │   └── OutcomeChecker.kt    ← resolves PENDING → WON/LOST
        │   │   ├── data/
        │   │   │   ├── SportsDataSource.kt  ← pluggable per-sport live data contract
        │   │   │   ├── FootballApi.kt       ← football-data.org Retrofit interface
        │   │   │   ├── PickRepository.kt    ← routes each sport to a source or SampleData
        │   │   │   ├── SampleData.kt        ← deterministic offline dataset, all 10 sports
        │   │   │   └── PickHistoryStore.kt  ← SharedPreferences-backed history
        │   │   ├── model/
        │   │   │   ├── Sport.kt             ← the 10 supported sports + per-sport config
        │   │   │   ├── Models.kt            ← Match, Fixture, Pick, Tier
        │   │   │   └── PickHistory.kt
        │   │   └── ui/
        │   │       ├── PicksAdapter.kt
        │   │       └── HistoryAdapter.kt
        │   └── res/{layout,values,drawable,mipmap-*}/…
        └── test/java/.../analysis/RuleMathTest.kt   ← run with `gradle test`
```

## Wiring a live data source

By default every sport uses the built-in sample data — no API key needed.
To wire a real source for a sport, implement `SportsDataSource` and register
it in `PickRepository`'s `dataSources` map. `FootballDataOrgSource` (in
`SportsDataSource.kt`) is the one concrete example, for soccer via
football-data.org:

```kotlin
val moshi = Moshi.Builder()
    .add(KotlinJsonAdapterFactory())   // needed: moshi-kotlin uses reflection, not codegen
    .build()

val footballApi = Retrofit.Builder()
    .baseUrl("https://api.football-data.org/")
    .addConverterFactory(MoshiConverterFactory.create(moshi))
    .build()
    .create(FootballApi::class.java)

val repo = PickRepository(
    dataSources = mapOf(
        Sport.SOCCER to FootballDataOrgSource(footballApi, apiKey = "YOUR_TOKEN")
    )
)
```

Any sport not present in `dataSources` automatically falls back to
`SampleData` — nothing crashes if you only wire up one or two sports.

Real multi-sport providers worth a look for the other sports:
- **API-SPORTS family** (api-sports.io) — API-Basketball, API-Baseball,
  API-Hockey, API-Rugby, API-Volleyball, API-MMA. Same auth/response shape
  as API-Football, one key per sport.
- **TheSportsDB** — broader multi-sport coverage, simpler free tier.

## Running the unit test
```bash
gradle test          # or ./gradlew test once Android Studio has generated a wrapper
```
Covers the win-rate, draw-weighted scoring, and tier-classification math in `RuleMath.kt`.

## Responsible gambling
The app displays: *"Draw-weighted win-rate over each competitor's last 20
finished matches... Not a guarantee of winning. No betting advice is
provided. If gambling affects you or someone you know, seek help."* This
text is intentionally fixed in the UI and not editable from a config flag.
