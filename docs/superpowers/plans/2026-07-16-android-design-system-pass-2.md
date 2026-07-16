# Android design-system pass 2 — screen/toolbar/list/card idioms, icon tokens, shared rows

> Sub-project **A** of the Android Profile milestone (M41 on GitHub, drafted "M40"; issue #565). Umbrella design: `docs/superpowers/specs/2026-07-16-android-profile-design.md` §A. Ports the second wave of iOS theme idioms so B–E compose from tokens, never hand-rolled chrome.

## Approach

Each iOS idiom becomes one Android symbol, structure-mirrored per `apps/android/CLAUDE.md`; the shipped M39 create bar refactors onto the new top bar with zero visual drift (its non-iOS styling — headline title, accent buttons — is carried by override parameters and documented; the idiom defaults follow iOS: `meta` title + `system.secondary` buttons, matching `pebblesToolbarTitle`/`PebbleToolbarButton`).

## Deliverables

- `theme/PebblesScreen.kt` — `PebblesScreen` scaffold (`pebblesScreen()` analog: background + safe insets + `LocalContentColor = system.secondary`), `PebblesTopBar` (leading/title/trailing, `heading()` semantics), `PebblesTopBarTextButton`.
- `theme/PebblesList.kt` — `PebblesListRowPosition` + pure `pebblesRowPosition(index, count)` (JVM-tested, mirrors the iOS function), `Modifier.pebblesListRow(position)` segmented 1dp border, `PebblesListSection` (rows overlap by 1dp so adjacent borders coincide into single dividers — the Compose translation of iOS's stroked `listRowBackground` overlays), `PebblesSectionHeader`.
- `theme/ProfileCard.kt` — `Modifier.profileCard()` (border → clip → inner padding; chain order reversed vs iOS because Compose modifiers wrap inward).
- `theme/PebblesIcon.kt` — `PebblesIconToken` (SMALL 13 / MEDIUM 15 / LARGE 17 dp; weight is baked into each drawable's stroke since Android has no SF-Symbol font weights) + `PebblesIcon` wrapper.
- `components/PebbleRow.kt` — shared list row (`Components/PebbleRow.swift`): `PebbleThumbnail` at 40dp + name/date, long-press Delete menu (M39 D8 idiom); silhouette fallback when `render_svg` is null (Android's established treatment, richer than iOS's gray box — noted deviation). Parent owns the destructive flow.
- `components/DashedPlaceholder.kt` — extracted from `PebbleForm`'s private placeholder, byte-identical visuals.
- `features/glyph/views/GlyphView.kt` — six-case chrome wrapper (#459/#515 spec: dashed `Spacing.xxl` frame only on CARVE/CREATE; case-mapped tints through the existing `GlyphImage` pipeline). `features/glyph/views/GlyphBanner.kt` — identity banner (meta / handwritten-byline subtitles; CARVE fallback when glyph-less). New string pair `glyph_banner_by` (en/fr).
- Icon batch (9 new vector drawables, 24dp viewport, call-site tint): `ic_gear`, `ic_person`, `ic_person_pair`, `ic_calendar`, `ic_stack`, `ic_plus` (Material-standard paths), `ic_scribble`, `ic_fossil_shell`, `ic_alternating_current` (hand-drawn strokes); `ic_chevron_right`/`ic_sparkle` already existed.
- Refactor: `CreateTopBar` onto `PebblesTopBar` (shipped look preserved); `PebbleForm` imports the extracted placeholder.
- Screenshot previews: `DesignSystemScreenshots` (bars, list sections, card, icon grid × 3 tokens), `GlyphChromeScreenshots` (all six cases + banner variants, iOS preview stroke), `PebbleRowScreenshots` (render/silhouette/accent-fallback) — light + dark.

## Verification

CI green (`ktlintCheck testDebugUnitTest assembleDebug` + screenshot artifact); `PebblesListPositionTest` mirrors the iOS position rule; maintainer reviews the `ui-screenshots` artifact — the create bar frames must match the prior run's pixel-for-pixel, the glyph-case grid compares against the iOS `GlyphView` preview, and the hand-drawn icons (scribble, fossil shell, wave) get a taste-check with the option to supply traced SF exports later (drop-in XML swaps).

## Lessons learned

- (fill at review)
