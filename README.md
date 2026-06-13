# Uma Android Automation — Custom Trackblazer Fork (v1.0.3)

![GitHub last commit](https://img.shields.io/github/last-commit/Jacob-Met/uma-android-automation/custom?logo=GitHub) ![GitHub release](https://img.shields.io/github/v/release/Jacob-Met/uma-android-automation?include_prereleases&label=release&logo=GitHub)

Jacob-Met’s **custom Trackblazer fork**, branched from upstream **v5.6.1** and released as **1.0.3** (`custom-v1.0.3`).

This is **not** upstream [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) **v5.7.3**. It installs as a **separate app** (`com.steve1316.uma_android_automation.custom`) so you can run it alongside the official build.

> For general bot architecture (OCR, racing, UI, etc.), see [HOW_IT_WORKS.md](HOW_IT_WORKS.md) and the [upstream README](https://github.com/steve1316/uma-android-automation/blob/master/README.md).

---

## Download

Get APKs from **[Releases → custom-v1.0.3](https://github.com/Jacob-Met/uma-android-automation/releases/tag/custom-v1.0.3)**:

| APK | Use for |
|-----|---------|
| `v1.0.3-UmaAndroidAutomation-Custom-arm64-v8a-release.apk` | Physical phones / most emulators |
| `v1.0.3-UmaAndroidAutomation-Custom-x86_64-release.apk` | x86 emulators (e.g. LDPlayer) |

**Package ID:** `com.steve1316.uma_android_automation.custom`  
**Logcat filter:** `package:com.steve1316.uma_android_automation.custom [UAA]`

---

## vs Upstream v5.7.3

This fork targets **Trackblazer tuning and reliability**, not parity with the latest upstream release.

### You get (custom only)

Everything in the sections below — deep Trackblazer item/training logic, Wit rules, failure mitigation, run summary export, advanced tuning, overlay resume handling, custom defaults, and side-by-side packaging.

### Upstream v5.7.3 has (not in this fork)

- **Training scoring refactor** — shared Kotlin/JS `scoring-shared` module (5.7.2+)
- **Smart Race Solver UI** — scrollable popover layout fixes (5.7.3)
- **Swipe-based scrolling** improvements and related settings polish from 5.7.x
- Ongoing upstream bugfixes and version bumps after 5.6.1

If you need upstream 5.7.3 features, use the official app. If you want this fork’s Trackblazer behavior, use the Custom APK.

---

## Custom features (not in upstream 5.7.3)

### Advanced settings & overlay resume

- **Advanced Settings page** — per-action delay overrides, delay calibration telemetry, and +/- tuning controls
- **Delay calibration** — collect timing stats from Home-button start/stop sessions; approve suggested delays per action
- **Overlay pause/resume toggles** (all off by default) — control whether overlay stop/start forces skill checks, agenda reload, or shop visits
- **Conditional overlay rechecks** — shop when turn changes; skills when over skill-point threshold
- **Mid-flow resume** — continues skill list, shop, or agenda UI if paused mid-flow
- **Live settings reload** — SQLite saves push into a running bot session without Home restart

### Trackblazer training & items

- **Failure mitigation system** — Good-Luck Charm vs energy items (Vita 20/40/65, Kale), with explicit priority rules, pool reserve for Finale, and Climax force-charm mode
- **Post-item recheck hardening** — charm-backed selection keep, stat-specific megaphone/ankle lock, execute block when mitigation was assumed but not applied
- **Cached training OCR** — re-scores the board from cache after charm/energy items; **full stat-tab scan only after Reset Whistle** (faster, fewer GUTS/WIT tab timeouts)
- **Reset Whistle controls** — save for Summer/Finale, rainbow priority before whistle, post-shuffle recovery thresholds, optional “forces training” mode
- **Megaphone logic** — summer reserve %, surplus burn mode, per-type min main-stat gain gates, single decrement per turn (race + train paths)
- **Ankle weight gates** — per-stat min gain (Speed/Stamina/Power/Guts), summer reserve count
- **Low mood item floor** — refuses whistle/charm/megaphone when BAD/AWFUL mood caps returns
- **High-failure energy train path** — Vita tier thresholds above max fail %, energy-item dialog gating when mitigation is NONE
- **Irregular training** — optional pre-screen on race-prep days with min-gain filter; agenda-linked schedules; Wit sweep; megaphone-aware irregular gain
- **Agenda race calendar** — per-agenda turn→race map (Racing Settings); autofill decoupled from irregular; powers forecast + hammer conservation
- **Race-forecast megaphones** — force megaphone when effective race count in buff window ≤ threshold; full-window item-aware irregular simulation; ignores min-gain
- **Irregular energy toggles** — separate execution vs forecast simulation; combo energy over reserves (3×+20, +40+20 = +65)
- **Tier overwrite** — upgrade megaphone tier on base gain (respects 60%/40% outside summer)
- **G1 hammer conservation** — spare last Artisan/Master when G1 upcoming on agenda
- **Revalidate after items** — optional always recheck after item pass (whistle always rechecks)
- **Finals failure-filter bypass** — trains during Finale even above normal fail thresholds when selected
- **GUTS min-gain parity** — charm low-gain rules apply consistently across all stats including Guts

### Wit & friendship training rules

- **Skip low-priority Wit** when Speed/Stamina/Power/Guts all exceed fail threshold and Wit is not top-3 priority
- **Never click empty Wit** — avoids zero-bar Wit traps
- **Prefer rest over Wit** for energy recovery when Wit is low priority
- **Friendship bar exceptions** — configurable min below-orange bars for Wit and top-3 stats
- **Junior/pre-debut priority** — top-3 friendship + main-gain override rules

### Racing & shop (Trackblazer)

- **Summer race flags** — scenario-specific race handling retained/fixed
- **SRS off-screen fallback** — Smart Race Solver when preferred race UI is off-screen
- **Retry / grade gates** — configurable race retries and pre-final grade checks
- **Expanded shop overrides** — item reserves (cupcakes, hammers, glow sticks), excluded items, shop check frequency/grades
- **Unique race strategy overrides** — per-race strategy overrides UI

### Skills & presets

- **Per-skill hint level targets** in skill plans (buy hints only at configured levels)
- **Auto-load Uma preset** — matches OCR trainee name to saved presets (optional, off by default)
- **Trainer friendship influence** slider (0–100)

### Debug & QoL

- **Run summary CSV export** — training click counts + stat gains + race rows at career end (optional)
- **Purchased label detection** — shop sync improvements
- **First-run wizard** — storage folder setup, legacy file migration, permission gate on first launch
- **Custom default settings** — see [docs/fork-custom-defaults-comparison.csv](docs/fork-custom-defaults-comparison.csv) for fork vs vanilla defaults

### Packaging

- **Side-by-side install** — `com.steve1316.uma_android_automation.custom` (release) or `.custom.dev` (dev builds)
- **Custom app label** — “ウマ娘 Android Automation (Custom)” in launcher

---

## Recent changes (custom branch — post 1.0.3)

- **Agenda race calendar** moved to Racing Settings; autofill records whenever user agenda is enabled
- **Race-forecast megaphones** with per-type thresholds and irregular-aware full-window simulation
- **Irregular energy** split into execution vs forecast toggles
- **G1 hammer conservation** for last spare Artisan/Master before upcoming G1 races
- **Revalidate training after items** (default off; Reset Whistle always rechecks)
- **Megaphone tier overwrite** on base gain (does not bypass summer 60/40 rule)

## Recent changes (1.0.3)

- **Energy OCR reconciliation** — infer or re-read energy when training failure % disagrees with a stale 100% default; uniform tab failures are trusted
- **No rest at full energy** after a failed training selection when mood/energy are already fine
- **Pre-item failure snapshot** for safe fallback and execute blocking; low-priority Wit still excluded
- **Stale training cache** cleared on turn change and training back-out
- **Overlay resume fixes** — persisted flags restored, prefs cleared on stop
- **Delay calibration** — counter reset to 0/0 when applying suggested delays
- **Training loop fix** — no infinite re-train on failed execute; Finale execute matches analysis; one train per Finale/Summer turn before race

## Prior changes (1.0.2)

- **Advanced Settings** — delay calibration, per-action delay tuning, overlay pause/resume toggles
- **Overlay resume handling** — no forced skill/agenda/shop on overlay restart unless toggled on; mid-flow resume for skill/shop/agenda screens
- **Live settings reload** — bot picks up SQLite changes while running
- **First-run wizard** — storage folder picker and legacy log/recording migration
- **Game data** — Taiki Shuttle / Sweep Tosho training events (Camping 2026-06-10)
- **Unique race strategy overrides** UI
- Automation library **2.5.7** (storage bridge)
- **Maiden race detection** — treat any completed non-maiden race as maiden done; no longer relies on fan-tier OCR alone
- **Advanced Settings crash fix** — corrected CustomSlider min/max props and SQLite JSON override parsing on page open

## Prior fixes (1.0.1)

- Charm no longer assumed globally when in inventory; per-stat queue rules (min gain, pool, mood, Wit)
- No rest at high energy when a safe stat exists; safe fallback + pre-item mitigation retry
- Full stat scan skipped only when failure exceeds threshold and no charm/energy mitigation items available
- Pool-reserve override (min gain ≥ override) still triggers full scan when charms are at reserve floor
- Removed zero-energy low-gain charm bypass; min stat gain for charm always applies outside Climax
- Accurate logs for depleted energy vs high failure on first tab

## Prior fixes (1.0.0)

- Charm vs energy blocking when charm does not queue
- Forced-train trap — backs out when execute would be blocked
- Megaphone double decrement on mandatory race paths
- Post-item recheck no longer switches charmed stat (e.g. Speed → Guts after charm)
- Energy dialog skipped when failure mitigation choice is NONE
- Stat-specific item re-target after OCR shifts selection

---

## Quick start

1. Install a Custom APK from [Releases](https://github.com/Jacob-Met/uma-android-automation/releases).
2. On first launch, complete the **first-run wizard** (storage folder + permissions).
3. Open the app → set scenario to **Trackblazer** → configure Training + Scenario Overrides.
4. Grant Overlay, Accessibility, and MediaProjection when prompted.
5. Place the overlay button away from HUD edges; start from the Uma main training screen.

### Import settings from upstream 5.7.x

You can import a settings JSON exported from **official upstream v5.7.x** (Settings → Export). The custom app remaps fork-specific keys automatically:

- `trackblazerSkipRiskyCharmTrainingBelowGain` → `trackblazerMinStatGainForCharm`
- `trackblazerSkipBadMoodItemsBelowGain` → `trackblazerLowMainStatGainItemFloor`
- `enablePrioritizeNearMaxFriendship` → `trainerFriendshipInfluence` (on → 100, off → 0)
- `debug.enableMessageIdDisplay` / `overlayButtonSizeDP` → `misc.*`

Unsupported upstream-only options (swipe scrolling, training level weighting, stat-target disable, pause/resume, force-train energy floor) are dropped on import. Your Discord token is **not** overwritten when re-importing an export that omits it.

Requirements match upstream: **1080×1920 @ 240 DPI**, in-game graphics **Standard**, Android 7.0+.

> **Resolution / ADB / Logcat setup:** see the [upstream README instructions](https://github.com/steve1316/uma-android-automation/blob/master/README.md#instructions).

---

## Build from source

```bash
yarn install --ignore-scripts   # if tree-sitter native build fails on Node 26+
# Ensure android/local.properties points at your SDK
cd android && ./gradlew assembleRelease
```

APKs export to `../apk/` as `v1.0.3-UmaAndroidAutomation-Custom-<arch>-release.apk`.

---

## Branch & tags

| Ref | Meaning |
|-----|---------|
| `custom` | Active development branch |
| `custom-v1.0.3` | Release tag for 1.0.3 APKs |
| `custom-v1.0.2` | Release tag for 1.0.2 APKs |
| `custom-v1.0.1` | Release tag for 1.0.1 APKs |
| `custom-v1.0.0` | Release tag for 1.0.0 APKs |

Upstream reference: [steve1316/uma-android-automation @ v5.6.1](https://github.com/steve1316/uma-android-automation/tree/v5.6.1)

---

## Disclaimer

Educational automation project. Use at your own risk; no one is responsible for account or device issues except you.
