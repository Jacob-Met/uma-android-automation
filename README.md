# Uma Android Automation — Custom Trackblazer Fork (v1.0.1)

![GitHub last commit](https://img.shields.io/github/last-commit/Jacob-Met/uma-android-automation/custom?logo=GitHub) ![GitHub release](https://img.shields.io/github/v/release/Jacob-Met/uma-android-automation?include_prereleases&label=release&logo=GitHub)

Jacob-Met’s **custom Trackblazer fork**, branched from upstream **v5.6.1** and released as **1.0.1** (`custom-v1.0.1`).

This is **not** upstream [steve1316/uma-android-automation](https://github.com/steve1316/uma-android-automation) **v5.7.3**. It installs as a **separate app** (`com.steve1316.uma_android_automation.custom`) so you can run it alongside the official build.

> For general bot architecture (OCR, racing, UI, etc.), see [HOW_IT_WORKS.md](HOW_IT_WORKS.md) and the [upstream README](https://github.com/steve1316/uma-android-automation/blob/master/README.md).

---

## Download

Get APKs from **[Releases → custom-v1.0.1](https://github.com/Jacob-Met/uma-android-automation/releases/tag/custom-v1.0.1)**:

| APK | Use for |
|-----|---------|
| `v1.0.1-UmaAndroidAutomation-Custom-arm64-v8a-release.apk` | Physical phones / most emulators |
| `v1.0.1-UmaAndroidAutomation-Custom-x86_64-release.apk` | x86 emulators (e.g. LDPlayer) |

**Package ID:** `com.steve1316.uma_android_automation.custom`  
**Logcat filter:** `package:com.steve1316.uma_android_automation.custom [UAA]`

---

## vs Upstream v5.7.3

This fork targets **Trackblazer tuning and reliability**, not parity with the latest upstream release.

### You get (custom only)

Everything in the sections below — deep Trackblazer item/training logic, Wit rules, failure mitigation, run summary export, custom defaults, and side-by-side packaging.

### Upstream v5.7.3 has (not in this fork)

- **Training scoring refactor** — shared Kotlin/JS `scoring-shared` module (5.7.2+)
- **Smart Race Solver UI** — scrollable popover layout fixes (5.7.3)
- **Swipe-based scrolling** improvements and related settings polish from 5.7.x
- Ongoing upstream bugfixes and version bumps after 5.6.1

If you need upstream 5.7.3 features, use the official app. If you want this fork’s Trackblazer behavior, use the Custom APK.

---

## Custom features (not in upstream 5.7.3)

### Trackblazer training & items

- **Failure mitigation system** — Good-Luck Charm vs energy items (Vita 20/40/65, Kale), with explicit priority rules, pool reserve for Finale, and Climax force-charm mode
- **Post-item recheck hardening** — charm-backed selection keep, stat-specific megaphone/ankle lock, execute block when mitigation was assumed but not applied
- **Cached training OCR** — re-scores the board from cache after charm/energy items; **full stat-tab scan only after Reset Whistle** (faster, fewer GUTS/WIT tab timeouts)
- **Reset Whistle controls** — save for Summer/Finale, rainbow priority before whistle, post-shuffle recovery thresholds, optional “forces training” mode
- **Megaphone logic** — summer reserve %, surplus burn mode, per-type min main-stat gain gates, single decrement per turn (race + train paths)
- **Ankle weight gates** — per-stat min gain (Speed/Stamina/Power/Guts), summer reserve count
- **Low mood item floor** — refuses whistle/charm/megaphone when BAD/AWFUL mood caps returns
- **High-failure energy train path** — Vita tier thresholds above max fail %, energy-item dialog gating when mitigation is NONE
- **Irregular training** — optional pre-screen on race-prep days with min-gain filter
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

### Skills & presets

- **Per-skill hint level targets** in skill plans (buy hints only at configured levels)
- **Auto-load Uma preset** — matches OCR trainee name to saved presets (optional, off by default)
- **Trainer friendship influence** slider (0–100)

### Debug & QoL

- **Run summary CSV export** — training click counts + stat gains + race rows at career end (optional)
- **Purchased label detection** — shop sync improvements
- **Custom default settings** — see [docs/fork-custom-defaults-comparison.csv](docs/fork-custom-defaults-comparison.csv) for fork vs vanilla defaults

### Packaging

- **Side-by-side install** — `com.steve1316.uma_android_automation.custom` (release) or `.custom.dev` (dev builds)
- **Custom app label** — “ウマ娘 Android Automation (Custom)” in launcher

---

## Recent fixes (1.0.1)

- Charm no longer assumed globally when in inventory; per-stat queue rules (min gain, pool, mood, Wit)
- No rest at high energy when a 0%-failure stat exists; safe fallback + pre-item mitigation retry
- Full stat scan skipped only when failure exceeds threshold and no charm/energy mitigation items available
- Pool-reserve override (min gain ≥ override) still triggers full scan when charms are at reserve floor
- `allowLowGainCharmAtZeroEnergy` only at 0% energy (was ≤50%)
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
2. Open the app → set scenario to **Trackblazer** → configure Training + Scenario Overrides.
3. Grant Overlay, Accessibility, and MediaProjection when prompted.
4. Place the overlay button away from HUD edges; start from the Uma main training screen.

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

APKs export to `../apk/` as `v1.0.1-UmaAndroidAutomation-Custom-<arch>-release.apk`.

---

## Branch & tags

| Ref | Meaning |
|-----|---------|
| `custom` | Active development branch |
| `custom-v1.0.1` | Release tag for 1.0.1 APKs |
| `custom-v1.0.0` | Release tag for 1.0.0 APKs |

Upstream reference: [steve1316/uma-android-automation @ v5.6.1](https://github.com/steve1316/uma-android-automation/tree/v5.6.1)

---

## Disclaimer

Educational automation project. Use at your own risk; no one is responsible for account or device issues except you.
