# Android path stats — PathStatsService, ripple badge, bottom bar

> Sub-project **B** of the Android Profile milestone (M41 on GitHub, drafted "M40"; issue #566, blocked by #565/A). Umbrella design: `docs/superpowers/specs/2026-07-16-android-profile-design.md` §B, decisions D2–D4.

## Approach

Pure client work over three existing DB objects (`v_karma_summary`, `v_ripple`, `get_profile_engagement(p_tz)`). Each iOS file becomes one Android symbol; the badge's six bezier ring shapes transcribe coordinate-for-coordinate from `RippleStrokes.swift` (SwiftUI `addCurve(to:c1:c2:)` reorders to Compose `cubicTo(c1, c2, to)`); the `active_today` UTC workaround ports verbatim per design D3.

## Deliverables

- `services/PathStatsService.kt` (D2) — shared plain-class service: `karma`/`pebbles`/`ripple`/`daysPracticed`/`assiduity` Compose state, idempotent `load()` + guarded `refresh()`, three parallel fetches with per-source error isolation (`runCatching` + log), new CompositionLocal, constructed in `PebblesApp`, provided in `MainActivity`. Display-only: flash amounts stay edge-function `karma_delta` (M39 D10).
- Models: `features/profile/models/{KarmaSummary,ProfileEngagement}.kt`, `features/shared/ripples/RippleSummary.kt` (thresholds [1,5,9,13,17,21] with the migration-pointing comment; `nextLevel`/`pebblesToNextLevel`).
- Ripple stack (D4): `features/shared/ripples/{RippleStrokeColor,RippleStrokes,RippleBadge}.kt` — pure tone truth table, six transcribed 44-viewBox paths, Canvas badge (outermost-first, opacities 0.33/0.66/1.0, 2dp round caps, centered digit, combined a11y label via `clearAndSetSemantics`).
- `features/path/components/PathBottomBar.kt` — profile icon button + karma stat (sparkle, Ysabeau `buttonLabel` number with the iOS dark-mode accent tint, "karma" caption) + badge; all taps route to `onProfile` (stubbed until C).
- PathScreen wiring: `stats.load()` on entry (own effect so a slow stats fetch never delays the timeline); `reload()` now also fires `stats.refresh()` — covering create/edit/delete, all of which already route through it; `rippleWithLocalActiveToday` port (device-local override of the UTC `active_today`, D3); bar composed between NewPebbleButton and the temporary sign-out. `PathContent` gains defaulted `karma`/`ripple`/`onProfile` params so existing screenshot fixtures compile unchanged.
- `features/profile/components/ChunkAssiduity.kt` — pure helper for C's grid, tested now.
- Strings (en/fr): `path_profile`, `path_karma_caption`, `path_karma_a11y`, `ripple_badge_active`, `ripple_badge_inactive` ("karma"/"Ripple" kept as product vocabulary in fr — flag for maintainer review).
- Tests: `RippleStrokeColorTest` (full 6×7×2 matrix), `RippleSummaryTest` (decode + threshold math incl. overshoot clamp + terminal level), `ChunkAssiduityTest`.
- Screenshots: `RippleBadgeScreenshots` — the 7-level × active/inactive grid (compare against iOS `RipplePreviewGrid`) + bar with stats and in the null/loading state.
- Arkaik: `V-timeline` description updated for the bar.

## Verification

CI green; unit suites above; screenshot artifact — badge grid vs iOS side-by-side is the acceptance gate; on device: bar shows real totals, creating a pebble moves karma and ripple without an app restart, `active_today` follows the device calendar near local midnight.

## Lessons learned

- (fill at review)
