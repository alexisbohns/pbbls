# Android icon migration — design

**Date:** 2026-10-30
**Issue:** #1315 (follow-up of #1311, the icons package; sibling of #1314, iOS)
**Milestone:** M66 · Brand icons
**iOS spec:** `docs/superpowers/specs/2026-10-30-ios-icon-migration-design.md` (D1–D10), merged today; its tables are the per-site record this spec reads from.
**Package spec:** `docs/superpowers/specs/2026-10-28-icon-package-design.md` (§5 Android, D9, D10; D4 and D10 already superseded by the iOS spec's D8).
**PR kind:** `chore`, `no-lab-note`. Arkaik: no change (no view, route, model or endpoint).
**Inventory:** counted on branch `docs/1315-android-icon-migration-spec` (from `origin/main` at `ffbc1aff`) on 2026-10-30. `grep -rnoE 'R\.drawable\.[a-z_0-9]+|@drawable/[a-z_0-9]+' --include='*.kt' --include='*.xml' apps/android/app/src/main | grep -v ic_pbbls_ | wc -l` prints **121**: 113 references to 38 hand-traced UI drawables, 8 to the launcher, splash, logo and Google marks. The app draws **no Material icon** (no `androidx.compose.material.icons` import, no `material-icons` dependency). `src/debug` and `src/test` name no drawable; `src/screenshotTest` names 11 in one gallery (`DesignSystemScreenshots.kt` `IconGallery`). No notification, widget or `AndroidManifest.xml` site draws an icon besides `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

Paths in the tables are relative to `apps/android/app/src/main/kotlin/app/pbbls/android/`.

## Summary

The Android app draws its icons from 38 hand-traced `ic_*` VectorDrawables, most of them tracings of the SF Symbols iOS has just retired. This spec maps each of the 113 references to a `PbblsIcon`, to a shape, or to a short list that stays hand-traced on purpose; deletes the 20 drawables that end up with no reference; fixes the size an icon draws at per role in dp; lights the navigation bar's selected stone; and flips `android` into the manifest's `migrated` marker so `lint:replaces` polices the surface. It reads the iOS spec decision by decision and names where Android differs. The work is one stack of seven parts.

## Goal

After the stack merges:

- the 20 replaced drawables are deleted, no source set under `apps/android/app/src/` names one, and `npm run lint:replaces --workspace=@pbbls/icons` prints `replaces: 27 rows, 69 names, 0 hits`;
- every icon that carries meaning (a tab, a pebble, a soul, a collection, karma, a glyph, a date, an achievement, a photo) is a Pebbles drawing, and it is the drawing iOS uses for the same meaning;
- the drawables that stay are named: seven chrome tracings, eleven waiting for the chrome wave, four brand and launcher marks;
- `PebblesIconToken` and `PebblesIcon(painter, token, …)` (`core/designsystem/PebblesIcon.kt`) are gone, replaced by `PbblsIconSize`.

## Non-goals

- Web (#1313) and iOS (#1314, done).
- Redrawing anything. A concept with no drawing keeps its tracing and goes to the wave brief (D2).
- The launcher icon, the splash icon and Welcome's logo: generated from the iOS brand mark by `apps/android/scripts/logo-svg-to-launcher-icon.mjs` (#846, #856), not icons of the set.
- Material component internals (D1) and `PebblesCheckbox`, which stays the stock M3 `Checkbox` (divergence #854; iOS's `PebblesCheckbox` row does not apply).
- Notifications and widgets: none exist. A future notification small icon is alpha-only, which an `ic_pbbls_*` drawable already is (white strokes).
- An Android Icon lab. iOS added the five sizes to its Settings → Developer lab; on Android the Part 1 gallery preview and the Studio preview pane play that role (D6).

## Decisions

Each decision names its iOS counterpart: **same**, **same, Android reading**, or **differs**, with the reason.

**D1. Chrome stays hand-traced; meaning becomes Pebbles.** *Same as iOS D1.* Seven tracings are platform grammar and stay: `ic_arrow_back` (back, `autoMirrored`, 12 sites), `ic_chevron_right` (disclosure, `autoMirrored`, 9), `ic_arrow_right` (the Lab's "See all", `autoMirrored`, 1), `ic_more_vert` (overflow, 1), `ic_share` (1), `ic_undo` (carve undo, 1), and `ic_x_circle` at its one close site (`RecordFlowChrome.kt:70`). They are the Android spellings of iOS's `chevron.*`, `arrow.right`, `ellipsis.circle`, `square.and.arrow.up`, `arrow.uturn.backward` and `xmark`. Also native and untouched: the system bars and predictive back; everything Material draws inside its own components (`Checkbox` in `PebblesCheckbox`, `RadioButton`, `Switch`, `DatePicker`/`TimePicker` chevrons and mode toggle, the bottom-sheet drag handle, the navigation indicator pill); and four brand marks (`ic_google_g`, required as drawn; `ic_launcher_foreground`, `ic_launcher_monochrome`, `pbbls_logo`). The list is closed and lands in the decisions log (Part 7): a new hand-traced drawable outside it needs a drawing or a wave-brief line. *Differs in kind only:* Android's chrome is our own tracings, not system glyphs, so the three directional ones keep `autoMirrored` and must not be swapped for an `ic_pbbls_*` drawable, which mirrors nothing. *Rejected:* drawing the back arrow; it sits beside the system back gesture and the predictive-back animation, and a wobbly one reads as a bug.

**D2. No drawing, no replacement; the gap is a wave brief.** *Same as iOS D2.* Eleven drawables mean something the library does not draw and stay until `packages/icons/waves/2026-11-chrome/` lands: `ic_arrow_up_circle` (*upload* at `AttachedPhotoView.kt:73`, *upvote* at `ReactionButton.kt:63`), `ic_arrow_up_circle_fill` (*upvote*), `ic_refresh` and `ic_replay` (*again*), `ic_warning` (*something went wrong*), `ic_lock` (*secret* at `VisibilityUi.kt:16`, *locked* at `AchievementsScreen.kt:220`), `ic_people` at `VisibilityUi.kt:17` and `ic_globe` (the *visibility trio*), `ic_alternating_current` at `GlyphDetailDrawer.kt:279` (*swap*, between "me" and the creator), `ic_pebble_domain` at `PebbleForm.kt:360` (*life domain*), and `ic_pebble_when` at `RecordWhenStep.kt:91` (the time pill). Part 1 adds the Android drawable to each figure's line in `brief.md`, and one figure iOS never needed: **15. hour — a time of day, beside a date** (`ic_pebble_when`); iOS draws no icon on its When step. Five drawables are *mixed*: some of their sites map, one stays (`ic_x_circle`, `ic_people`, `ic_alternating_current`, `ic_pebble_domain`, `ic_pebble_when`). Their mapped sites switch **by site**, like iOS's `person.2.fill`, and their names never enter `replaces.android`, or the lint would fail on the site that stays. This is why Part 1 removes `ic_people` from `people.replaces.android`, where the package wave put it.

**D3. The pick is iOS's pick for the same meaning.** *Same as iOS D3, Android reading.* Where an Android site has an iOS twin, it takes the `PbblsIcon` the iOS tables give that meaning; no Android site reopens a choice. Android-only sites get the nearest iOS meaning, named here: `WhenRow`'s lead (date then time) is `calendar-date`; `PebbleForm`'s "Add photo" row is `camera` (iOS `photo.badge.plus`); the Lab community card's `ic_chat_bubbles` is `people` (iOS `bubble.left.and.bubble.right.fill`); the Lab card's `ic_lightbulb` is `sparkle` (iOS `lightbulb.max`); the drawer's usage tile is `chart-double-grow`; the profile card with no collection is `plus-circle`. The manifest carries one primary row per drawable (package rule, iOS D8c); for a drawable with two meanings the row is its majority: `ic_stack` → `collection` (2 of 3 sites; the Path tab takes `path` by site), `ic_image` → `camera-focus` (2 of 3; the form row takes `camera` by site), `ic_sparkle` → `karma-dot` (7 of 9), `ic_trophy` → `pebble-glow-md` (2 of 3), `ic_check_circle` → `check-circle` (3 of 5; owned takes `check-circle-dot` by site).

**D4. Moments are flat, pebbles are round.** *Same as iOS D4.* The Lab timeline's backlog is `pebble-round`, in progress `pebble-round.lit-1`, shipped `check-circle`. `AssiduityGrid` (7dp cells) stops drawing `ic_fossil_shell` / `ic_alternating_current` and draws a plain `Circle` in `primary` (active) or `outlineVariant`, as iOS does. `achievementFamilyIcon` returns exactly iOS's `familySymbol` table, which adds three branches Android lacks today (`first_moment` → `pebble-flat`, `first_pebble` → `pebble-round`, `first_five` → `stack.lit-3`) and drops two (`domain_first`, `glyph_sales` fall to the default `pebble-glow-md`): a drift fix, not a new design. *Differs:* no checkbox row; `PebblesCheckbox` is the stock M3 `Checkbox` (#854).

**D5. The navigation bar lights its stone.** *Same as iOS D5, Android reading.* Four tabs (divergence #852): Path `path`, People `people`, Collections `collection`, You `person`; the selected item draws the `active` twin. `PebblesKey.iconRes()` (`navigation/PebblesNavigationSuite.kt:136`) becomes `internal fun PebblesKey.icon(): PbblsIcon`, and the item draws `PbblsIconImage(if (selected) tab.icon().activeTwin ?: tab.icon() else tab.icon(), contentDescription = null)` at the generated default 24dp, the M3 navigation icon slot; the label `Text` carries the name for TalkBack. The rail on large screens takes the same items. `NavigationSuiteItem` already sets `selectedIconColor`/`unselectedIconColor` through `LocalContentColor`, so no tint is passed. *Differs:* the M3 indicator pill stays behind the selected icon (chrome, D1); a filled icon over an indicator is Material's own selected pattern, so the twin adds no second vocabulary. *Rejected:* a fill animation between twins; nothing on iOS animates either.

**D6. Five sizes in dp; only `INLINE` follows font scale.** *Same names and values as iOS D6; differs on scaling.* A hand-written `core/designsystem/PbblsIconSize.kt` (the generator owns `PbblsIcons.kt` only) adds

```kotlin
enum class PbblsIconSize(val value: Float, val followsFontScale: Boolean) {
    INLINE(16f, true), ROW(20f, false), TOOLBAR(22f, false), TILE(28f, false), HERO(48f, false),
}
fun PbblsIconSize.toDp(density: Density): Dp = if (followsFontScale) with(density) { value.sp.toDp() } else value.dp
@Composable fun PbblsIconImage(icon: PbblsIcon, size: PbblsIconSize, contentDescription: String?,
    modifier: Modifier = Modifier, tint: Color = LocalContentColor.current)
```

an overload that delegates to the generated `PbblsIconImage(icon, contentDescription, modifier, size: Dp, tint)` with `size.toDp(LocalDensity.current)`. `contentDescription` stays required, as in the generated one. *Why dp:* Material icons do not follow font scale (package spec D9), and the M3 slots around them (48dp `IconButton`, 24dp navigation icon) do not grow. *Why `INLINE` is the exception:* an inline icon is part of a line of text (a karma amount, a price, a soul's count); at 200% the text doubles and a 16dp stone beside it shrinks to a speck. `value.sp.toDp()` applies the platform's own font-scale curve (non-linear on API 34+). This narrows package D9 for one size; the decisions-log entry says so.

Rules per role, mirroring iOS: **INLINE** beside text (karma, prices, the soul count, photo states, the timeline nodes, the menu's selected check, `DataTile`); **ROW** list-row and pill leads and menu items (the four menus' trash, the When pills, the carve pills, the collection row, the drawer's owned mark); **TOOLBAR** an icon alone in an `IconButton` (the three top-bar pluses, the gear, remove photo); **TILE** `SurfaceTile` (30 → 28), form leads, card leads, achievement family marks; **HERO** `DetailPlaceholder`, the Capture photo step, the achievement celebration (48 inside its 88dp disc). Three further cases, Android-only:

- *An M3 slot with its own icon token keeps it*: `ButtonDefaults.IconSize` (Complete CTA on `BareMomentPage`), `FilterChipDefaults.IconSize` (emotion chip check), `ToggleButtonDefaults.IconSize` (glyph store tabs), the 24dp navigation item (D5) and the 24dp FAB icon (`NewPebbleFab`). This is iOS's "UIKit does not scale a tab image", generalised.
- *A fixed slot passes a `Dp`*, through the generated `size:`, never `INLINE` (iOS lesson: a fixed frame is the signal for the bare image): the band mark (`CollectionBand.kt`, `BAND_MARK` 14dp, the one sub-16 exception, as on iOS), the glyph cell placeholders (`GlyphView.kt`, `max(side * 0.4, 18.dp)`), and the shelf badge (16dp in a 32dp disc, `ProfileAchievementsCard.kt:122`). Each carries a one-line comment saying why.
- *Size goes through `size`, never through `Modifier.size`*: the generated helper applies `modifier.size(size)`, so an outer `Modifier.size` silently wins.

The floor is 16: the sites drawn at 11, 13 and 14dp today (soul count, prices, balance, photo states, timeline nodes, "date from photo") move to `INLINE`. `SoulItem`'s count text stays `labelSmall` (11sp, already M3's smallest style; iOS moved its text to `.caption2` for the same pairing). *Differs:* `DataTile` takes `INLINE`, not iOS's `tile`: its icon sits in the label's line (`Row { icon; Text(bodyMedium) }`), which is D6's own definition of inline, and the iOS plan's lessons list the 28 there as owed to the device pass. If iOS keeps 28, Android follows in one line. Kept tracings inside a shared helper take the helper's new size (`MediaStateLabel` 14 → 16, `CarvePillButton` 16 → 20): one helper, one size.

**D7. Helpers take a `PbblsIcon`, not an `Int`.** *Same as iOS D7, Android reading.* Nine sources switch their parameter or return type, and the `Int`/`Painter` version is deleted, not kept beside: `SurfaceTile(iconPainter:)`, `DetailPlaceholder(iconRes:)`, `DataTile(iconRes:)`, `ShortcutTile(iconRes:)` (private, `ProfileShortcutsRow.kt`), `GlyphTab.iconRes`, the `LogTimeline` local, `achievementFamilyIcon`, `PebblesKey.iconRes()` and `GlyphView`'s private `PlaceholderIcon(resId:)`. Three helpers still serve a kept tracing beside a Pebbles icon and keep `@DrawableRes iconRes: Int`, their Pebbles callers passing `PbblsIcon.X.drawableRes`: `MediaStateLabel` (upload and failed stay), `WhenPill` (the time pill stays), `CarvePillButton` (undo stays). `Visibility.iconRes` (`core/ui/VisibilityUi.kt`) is untouched, all three grades being wave drawables. *Differs:* iOS rejected an `IconSource` enum and left its mixed sites alone; Android does not need one either, because `PbblsIcon.drawableRes` is already the common currency.

**D8. The lint: Android's form is already right, its scope stays `src/main`, and the compiler backs it.** *Same as iOS D8, Android reading.* (a) `migrated` and the validator are in place since #1330; Android flips its rows the same way. (b) `TREES.android` in `packages/icons/scripts/lib/replaces.ts` already greps `R\.drawable\.<name>\b|@drawable/<name>\b` in every `.kt`/`.xml` under `apps/android/app/src/main`, with no skip; `ic_pbbls_*` never matches a bare name, and `\b` keeps `ic_check` off `ic_check_circle`. No change. The scope is right: `src/debug` names no drawable; `src/test` and `src/screenshotTest` may legitimately name a kept one; and every name in `replaces.android` is a deleted file by the end of Part 6, so any other source set naming it fails to compile, a stronger gate than a grep. Widening the roots buys nothing and makes the lint read fixtures. (c) The primary rows (D3) give `replaces.android` 20 names on 16 rows: the 10 existing rows keep their names minus `ic_people`; `pebble-round` gains `ic_circle_dashed`, `ic_circle_inset_filled`; `collection` gains `ic_stack`; `people` gains `ic_chat_bubbles`; six rows gain their first Android name: `plus-circle` `ic_plus`, `camera-focus` `ic_image`, `heart` `ic_pebble_emotion`, `sparkle` `ic_lightbulb`, `karma-dot` `ic_sparkle`, `pebble-glow-md` `ic_trophy`. Invariant: `replaces.android` lists exactly the drawables this stack deletes. (d) *Differs:* a drawable is deleted **in the part that removes its last reference**, not at the end. Android Lint runs `UnusedResources` under `warningsAsErrors`, so a part that orphans a tracing and keeps the file fails `./gradlew lint`. Part 1 rewrites the screenshot gallery that names 11 of them, so later deletions compile.

**D9. References are re-baselined once per part, on CI.** *Same as iOS D9, Android mechanics.* Each part turns its references red; after the part's last code commit, add the `rebaseline-screenshots` label, then approve the held `action_required` runs or push a further commit. Never re-run the failed run, never commit a reference rendered locally, never widen the threshold (`apps/android/CLAUDE.md`, `android-screenshot-gate` skill). Before any stack push: fetch and fast-forward each branch to its bot commit, rebase the parts above it, and let the upper branch win a PNG conflict. `ListDetailScreenshotsKt` (20) renders the navigation rail and is re-recorded in four parts; that is expected churn, not a sign of a wrong part split. `PbblsIconsScreenshotsKt` (the sheet) does not change.

**D10. Debug rows: nothing to migrate.** *Differs from iOS D10 by fact:* `src/debug` (the Spine Lab activity) draws no drawable, so there is no carve-out to avoid and no row to move.

**D11. Tint follows `LocalContentColor`; a role colour only says a role.** *Same as iOS's `style:` story, Android reading.* The generated helper defaults `tint = LocalContentColor.current`, iOS's ambient foreground. Inside an M3 component that sets the content colour (`IconButton` in a top bar, `NavigationSuiteItem`, `Button`, `FilterChip`, `ToggleButton`), pass no tint. Pass one only to say a role the slot does not: `primary` for karma and brand marks, `colorScheme.error` for the destructive menu items, `onSurfaceVariant` for muted marks, `onPrimaryContainer` on `primaryContainer`. Existing explicit tints carry over unchanged. Never `Color.Unspecified` and never `Image(painterResource(icon.drawableRes))`: the generated vectors stroke `#FFFFFFFF` and would draw white.

## The mapping

Sizes are D6 names; *M3* is the component's own icon token; *fixed* is a `Dp` slot. *Keep* rows are D1, *wave* rows are D2, *shape* rows are D4. Line numbers are on `ffbc1aff`.

### Navigation shell and top bars

| Drawable | Sites | PbblsIcon | Size |
|---|---|---|---|
| `ic_stack` | `navigation/PebblesNavigationSuite.kt:138` Path tab | `path` / `path.active` (by site) | M3 24 |
| `ic_people` | `PebblesNavigationSuite.kt:139` People tab; `navigation/PebblesSceneStrategies.kt:165` souls placeholder | `people` / `people.active`; `people` (by site) | M3 24; HERO |
| `ic_pebble_collection` | `PebblesNavigationSuite.kt:140` Collections tab; `PebblesSceneStrategies.kt:169` | `collection` / `collection.active`; `collection` | M3 24; HERO |
| `ic_person` | `PebblesNavigationSuite.kt:141` You tab | `person` / `person.active` | M3 24 |
| `ic_scribble` | `PebblesSceneStrategies.kt:173` glyphs placeholder | `pebble-dotted-glyph` | HERO |
| `ic_plus` | `features/profile/SoulsListScreen.kt:142`, `features/profile/CollectionsListScreen.kt:149`, `features/glyph/store/GlyphsListScreen.kt:150` | `plus-circle` | TOOLBAR |
| `ic_gear` | `features/profile/ProfileScreen.kt:112` | `slider` | TOOLBAR |
| `ic_arrow_back` ×12, `ic_chevron_right` ×9, `ic_arrow_right`, `ic_more_vert`, `ic_share`, `ic_undo`, `ic_x_circle` (`features/path/flow/RecordFlowChrome.kt:70`) | 26 sites | keep | as today |

### Pebbles, moments, done

| Drawable | Sites | PbblsIcon | Size |
|---|---|---|---|
| `ic_fossil_shell` | `core/ui/SoulItem.kt:107` count; `features/profile/components/ProfileCountersRow.kt:33` | `pebble-round` | INLINE (text stays `labelSmall`); INLINE (`DataTile`) |
| `ic_fossil_shell` | `core/ui/AchievementCopy.kt:87` `pebble_count` | `stack` | TILE |
| `ic_fossil_shell`, `ic_alternating_current` | `features/profile/components/AssiduityGrid.kt:44` | shape: `Circle` `primary` / `outlineVariant` | the 7dp cell |
| `ic_check_circle`, `ic_circle_inset_filled`, `ic_circle_dashed` | `features/lab/components/LogTimeline.kt:79–81` | `check-circle`, `pebble-round.lit-1`, `pebble-round` | INLINE |
| `ic_check_circle` | `features/path/create/AttachedPhotoView.kt:79` ready; `features/path/create/ExistingSnapRow.kt:92` saved | `check-circle` | INLINE (`MediaStateLabel`) |
| `ic_check_circle` | `features/glyph/store/GlyphTabBar.kt:35` Owned; `GlyphDetailDrawer.kt:373` owned mark | `check-circle-dot` (by site) | M3; ROW |
| `ic_check` | `features/path/create/VisibilityChip.kt:99` selected grade; `features/path/create/pickers/EmotionPickerSheet.kt:174` selected chip | `checkmark-dot` | INLINE; M3 |
| `ic_x_circle` | `AttachedPhotoView.kt:175` `RemovePhotoButton` (pending and existing rows) | `remove` (by site) | TOOLBAR |
| `ic_trash` | `core/ui/PebbleRow.kt:115`, `features/path/spine/SpinePebble.kt:360`, `SoulsListScreen.kt:236`, `CollectionsListScreen.kt:285` menus; `features/glyph/carve/GlyphCarveScreen.kt:187` clear | `trash` | ROW |

### Create and capture

| Drawable | Sites | PbblsIcon | Size |
|---|---|---|---|
| `ic_plus` | `features/path/card/BareMomentPage.kt:306` Complete CTA; `features/path/components/NewPebbleFab.kt:46` | `plus-circle` | M3 (`ButtonDefaults.IconSize`); M3 24 |
| `ic_plus` | `core/ui/GlyphView.kt:95` create cell; `features/profile/components/ProfileCollectionCard.kt:84` no collection | `plus-circle` | fixed `max(side * 0.4, 18.dp)`; ROW |
| `ic_image` | `features/path/flow/steps/RecordPhotoStep.kt:106` add, `:220` attached | `camera-focus` | HERO |
| `ic_image` | `features/path/create/PebbleForm.kt:278` add photo | `camera` (by site) | ROW |
| `ic_calendar` | `features/path/flow/steps/RecordWhenStep.kt:86` date pill | `calendar-date` | ROW (`WhenPill`) |
| `ic_pebble_when` | `features/path/create/WhenRow.kt:59` form lead | `calendar-date` (by site) | TILE |
| `ic_sparkle` | `RecordWhenStep.kt:103` date from photo | `sparkle` (by site) | INLINE |
| `ic_sparkle` | `core/ui/KarmaEarnedCapsule.kt:149`; `features/path/flow/steps/RecordSuccessStep.kt:94` | `karma-dot` | INLINE |
| `ic_pebble_collection` | `PebbleForm.kt:404` form lead; `features/path/flow/steps/RecordCollectionStep.kt:79` | `collection` | TILE; ROW |
| `ic_pebble_collection` | `features/path/card/CollectionBand.kt:92` band mark | `collection` | fixed `BAND_MARK` 14dp |

### Souls, collections, you

| Drawable | Sites | PbblsIcon | Size |
|---|---|---|---|
| `ic_person_pair` | `features/profile/components/ProfileShortcutsRow.kt:34` souls, `:48` connections | `people` | TILE (`SurfaceTile`) |
| `ic_person_pair` | `GlyphTabBar.kt:36` Community; `GlyphDetailDrawer.kt:241` owners | `people` | M3; TILE |
| `ic_person_pair` | `AchievementCopy.kt:92` `first_soul` | `people` | TILE |
| `ic_person` | `GlyphTabBar.kt:34` Mine | `person` | M3 |
| `ic_chat_bubbles` | `features/lab/components/FeaturedCommunityCard.kt:42` | `people` | TILE |
| `ic_stack` | `AchievementCopy.kt:90` `first_collection`; `ProfileCollectionCard.kt:84` | `collection` | TILE; ROW (32dp box) |

### Glyphs, karma, dates, achievements, ideas

| Drawable | Sites | PbblsIcon | Size |
|---|---|---|---|
| `ic_scribble` | `GlyphView.kt:93` carve cell; `ProfileShortcutsRow.kt:41`; `AchievementCopy.kt:91` `first_glyph`, `:93` `glyph_count` | `pebble-dotted-glyph` | fixed cell; TILE; TILE |
| `ic_sparkle` | `ProfileCountersRow.kt:39`; `features/glyph/picker/GlyphPickerContent.kt:301`, `GlyphsListScreen.kt:299` prices; `GlyphDetailDrawer.kt:266` balance, `:380` price | `karma-dot` | INLINE |
| `ic_calendar` | `ProfileCountersRow.kt:27`; `GlyphDetailDrawer.kt:228` created | `calendar-date` | INLINE (`DataTile`); TILE |
| `ic_alternating_current` | `GlyphDetailDrawer.kt:235` usage (Soon) | `chart-double-grow` (by site) | TILE |
| `ic_lightbulb` | `features/profile/components/ProfileLabCard.kt:46` | `sparkle` | TILE |
| `ic_trophy` | `core/ui/AchievementMomentOverlay.kt:174` celebration | `pebble-glow-lg` (by site) | HERO |
| `ic_trophy` | `features/profile/components/ProfileAchievementsCard.kt:66`; `AchievementCopy.kt:95` default | `pebble-glow-md` | TILE |
| `ic_pebble_emotion` | `AchievementCopy.kt:88` `emotion_first` | `heart` | TILE |
| `ic_pebble_domain`, `ic_sparkle` | `AchievementCopy.kt:89` `domain_first`, `:94` `glyph_sales` | branches removed: default `pebble-glow-md` (D4) | TILE |
| (new branches) | `achievementFamilyIcon`: `first_moment`, `first_pebble`, `first_five` | `pebble-flat`, `pebble-round`, `stack.lit-3` | TILE |

`achievementFamilyIcon` draws at `features/profile/AchievementsScreen.kt:210` (TILE, was `LARGE` 17dp) and at `ProfileAchievementsCard.kt:122` (fixed 16dp in its 32dp badge).

### Wave (D2), kept until drawn

`ic_arrow_up_circle` (`AttachedPhotoView.kt:73`, `features/lab/components/ReactionButton.kt:63`), `ic_arrow_up_circle_fill` (`ReactionButton.kt:63`), `ic_refresh` (`AttachedPhotoView.kt:103`), `ic_replay` (`features/path/wrap/month/MonthDeckEnd.kt:78`), `ic_warning` (`AttachedPhotoView.kt:85`), `ic_lock` (`core/ui/VisibilityUi.kt:16`, `AchievementsScreen.kt:220`), `ic_people` (`VisibilityUi.kt:17`), `ic_globe` (`VisibilityUi.kt:18`), `ic_alternating_current` (`GlyphDetailDrawer.kt:279`), `ic_pebble_domain` (`PebbleForm.kt:360`), `ic_pebble_when` (`RecordWhenStep.kt:91`). Eleven drawables, thirteen references; `Visibility.iconRes` fans out to four call sites (`CardFooter.kt:72`, `VisibilityChip.kt:58` and `:78`, `RecordPrivacyStep.kt:116`).

### Brand and launcher marks, out of scope

`ic_launcher_foreground` (`res/mipmap-anydpi/ic_launcher.xml:12`, `ic_launcher_round.xml:10`, `res/values/themes.xml:28` splash), `ic_launcher_monochrome` (`ic_launcher.xml:13`, `ic_launcher_round.xml:11`), `pbbls_logo` (`core/designsystem/PebblesLogo.kt:26`, and its KDoc at `:17`), `ic_google_g` (`core/designsystem/GoogleSignInButton.kt:55`).

**Tally.** 38 hand-traced UI drawables = 20 replaced and deleted + 7 chrome + 11 wave (5 of the 18 kept are mixed, D2). 113 references = 72 to a `PbblsIcon` + 2 to shapes + 26 chrome + 13 wave. `PbblsIcon` rows drawn: 24 (`checkmark-dot`, `check-circle`, `check-circle-dot`, `remove`, `plus-circle`, `camera`, `camera-focus`, `path`, `people`, `collection`, `person`, `stack`, `slider`, `pebble-dotted-glyph`, `pebble-round`, `pebble-flat`, `karma-dot`, `calendar-date`, `trash`, `pebble-glow-md`, `pebble-glow-lg`, `sparkle`, `heart`, `chart-double-grow`), all already `migrated: ["ios"]`; 16 of them carry a `replaces.android` name. Twins drawn: `path.active`, `people.active`, `collection.active`, `person.active`, `pebble-round.lit-1`, `stack.lit-3`.

**Deleted, by part** (D8d): Part 2 `ic_gear`; Part 3 `ic_image`, `ic_check`; Part 4 `ic_pebble_collection`; Part 5 `ic_trophy`, `ic_lightbulb`, `ic_fossil_shell`, `ic_pebble_emotion`, `ic_stack`, `ic_calendar`; Part 6 `ic_check_circle`, `ic_circle_dashed`, `ic_circle_inset_filled`, `ic_chat_bubbles`, `ic_person`, `ic_person_pair`, `ic_plus`, `ic_scribble`, `ic_sparkle`, `ic_trash`. All under `apps/android/app/src/main/res/drawable/`, as `<name>.xml`.

## Accessibility

- **Decorative is `null`.** An icon beside text that says the same thing, or inside a node that merges or clears semantics, passes `contentDescription = null`: navigation items (the label speaks), menu items, chips, form and list rows, `DataTile`, `SoulItem`, the karma rows and `CardFooter` (both `clearAndSetSemantics`).
- **Standalone carries a label**, and every label already exists: the top-bar pluses (`souls_add_a11y`, `collections_add_a11y`, `glyphs_carve_a11y`), the gear (`settings_title`), remove photo (`photo_remove_a11y`), the FAB (its `label`), the shelf badge (the achievement's title), the glyph tabs in icon-only mode. The migration adds no string, so `LocalizationParityTest` is untouched.
- **Touch targets do not move.** `IconButton` guarantees 48dp; `NewPebbleFab` keeps its 56dp disc; an icon never sizes its parent. Pre-existing and not changed here: `CarvePillButton`'s 10dp vertical padding around a `labelLarge` line keeps it under 48dp tall; worth its own issue.
- **Font scale.** `INLINE` follows it (D6); every `_fs2` reference that shows an inline icon changes and is the review surface for it.
- **State never rides on the twin alone.** The selected tab also has the indicator and `selected` semantics; the owned mark sits under an "owned" caption; the locked achievement keeps its lock and caption.

## The manifest and the lint

Part 1 edits `packages/icons/icons.json` **as text**, line by line on the `replaces` line of each named row (iOS lesson: `JSON.stringify` rewrites 1,100 lines). No script change. The last commit of Part 6 adds `"android"` to `migrated` on the 24 rows above, in the same commit that deletes the last ten drawables, so the lint goes live with the code it polices. The implementer's checks, run from the repo root:

```
npm run lint:replaces --workspace=@pbbls/icons      # replaces: 27 rows, 69 names, 0 hits
grep -rnE '(R\.drawable\.|@drawable/)(ic_check|ic_check_circle|ic_circle_dashed|ic_circle_inset_filled|ic_fossil_shell|ic_plus|ic_image|ic_calendar|ic_pebble_emotion|ic_pebble_collection|ic_stack|ic_person_pair|ic_person|ic_chat_bubbles|ic_gear|ic_lightbulb|ic_scribble|ic_sparkle|ic_trash|ic_trophy)\b' apps/android/app/src
                                                    # no output: every source set, wider than the lint
ls apps/android/app/src/main/res/drawable | grep -v '^ic_pbbls_' | wc -l   # 22 (18 kept + 4 marks)
grep -rn 'PebblesIconToken\|PebblesIcon(' apps/android/app/src            # no output after Part 7
```

`npm run test --workspace=@pbbls/icons` keeps `manifest.test.ts`'s "each name on one row" check green, which is what catches a drawable listed under two rows.

## The Android lint baseline

`apps/android/app/lint-baseline.xml` holds 53 issues and none is on a drawable (its 8 `UnusedResources` are strings). Deleting the 20 files removes no entry. The trap runs the other way (D8d): `UnusedResources` fails a part that orphans a drawable it did not delete. Three touched files carry entries (`core/ui/PebbleRow.kt`, `features/glyph/store/GlyphDetailDrawer.kt`, `features/lab/components/LogTimeline.kt`); edits in place keep them, and no part moves a file. No part should need `./gradlew updateLintBaseline`. If one does, compare `grep -o 'id="[^"]*"' app/lint-baseline.xml | sort | uniq -c` before and after, by issue id, never by line, and justify every new id in the PR.

## Parts (one stack, `chore/1315-…`)

| Part | Branch | Content | References (about) |
|---|---|---|---|
| 1 | `chore/1315-icon-sizes` | `replaces.android` per D8c (incl. removing `ic_people` from `people`); wave brief lines for the eleven kept drawables and figure 15; `core/designsystem/PbblsIconSize.kt` + overload + `PbblsIconSizeTest`; `DesignSystemScreenshots.kt` `IconGallery` redrawn as `PbblsIcon`s at the five sizes (frees the 11 tracings and `PebblesIconToken.entries`) | `DesignSystemScreenshotsKt` 4 |
| 2 | `chore/1315-navigation` | `PebblesKey.icon()` + active twin; `DetailPlaceholder(icon:)` + its three callers; top-bar pluses ×3; gear; delete `ic_gear` | `NavigationSuiteScreenshotsKt` 8, `ListDetailScreenshotsKt` 20 |
| 3 | `chore/1315-capture-complete` | `RecordPhotoStep`, `RecordWhenStep` (`WhenPill` keeps `Int`), `RecordCollectionStep`, `RecordSuccessStep`, `AttachedPhotoView` (`MediaStateLabel` keeps `Int`, `RemovePhotoButton` → `remove`), `ExistingSnapRow`, `WhenRow`, `PebbleForm` (domain lead stays), `VisibilityChip` check, `EmotionPickerSheet` check, `KarmaEarnedCapsule`; delete `ic_image`, `ic_check` | `CaptureFlow` 24, `EditPebble` 17, `CompleteFlow` ~10, `Picker` 7 (emotion, karma flash), `Media` 2: ~60 |
| 4 | `chore/1315-path-card` | `BareMomentPage` CTA, `CollectionBand` (fixed 14), `NewPebbleFab`, `PebbleRow` and `SpinePebble` menus; delete `ic_pebble_collection` | `PebbleCard` ~10, `PathScreen` 8, `PebbleDetail` ~5, `ListDetail` path 3: ~25 |
| 5 | `chore/1315-profile` | `SurfaceTile(icon:)` with all six callers (three shortcuts, three drawer tiles), `DataTile(icon:)`, `ProfileCountersRow`, Lab, achievements and collection cards, `ShelfBadge`, `AchievementsScreen`, `achievementFamilyIcon: PbblsIcon` (D4 table), `AchievementMomentOverlay`, `AssiduityGrid` shapes, `SoulItem` count, souls and collections menus; delete `ic_trophy`, `ic_lightbulb`, `ic_fossil_shell`, `ic_pebble_emotion`, `ic_stack`, `ic_calendar` | `Profile` 5, `Achievements` ~5, `Souls` 3, `Store` drawer 2, `Picker` souls 3, `ListDetail` souls and collections ~13: ~30 |
| 6 | `chore/1315-glyph-lab` | `GlyphView` cells (fixed), `GlyphTab.icon`, drawer balance, price and owned mark (swap stays), glyph prices in list and picker, `CarvePillButton` (keeps `Int`, undo stays), `LogTimeline`, `FeaturedCommunityCard`; delete the last ten; then `migrated` gains `"android"` on the 24 rows, lint live | `Store` 7, `GlyphChrome` 2, `Picker` glyph 3, `Lab` 5, `ListDetail` glyphs ~5: ~22 |
| 7 | `chore/1315-retire-tokens` | delete `core/designsystem/PebblesIcon.kt`; its seven `MEDIUM` callers (six kept chevrons and the wave lock) take `Modifier.size(15.dp)` inline; decisions-log entry (D1 list, D6 sizes and the `INLINE` exception to package D9, D8d delete-when-orphaned); plan "Lessons learned" | none |

Each part passes `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` (with `ANDROID_HOME` exported) and its re-baselined references alone; a chrome or wave drawable left in a file is not a reason to touch that file. Only Part 1 and Part 6's last commit touch `packages/icons`; every other change is Android-only and reviewable screen by screen. The reference counts are estimates from the screenshot classes each part's composables appear in; the bot's commit is the count. Konsist is satisfied throughout: `PbblsIconSize.kt` imports only androidx, and `core/ui` (`AchievementCopy`, `GlyphView`, `SoulItem`) reads `core/designsystem` downward. The detailed task plan goes in `docs/superpowers/plans/2026-10-30-android-icon-migration.md`, written next.

## Testing

- `PbblsIconSizeTest` (plain JVM, `app/src/test/.../core/designsystem/`): the five values; `INLINE.toDp(Density(1f, fontScale = 2f))` is 32dp; `ROW.toDp(Density(1f, fontScale = 2f))` is 20dp.
- `PebblesNavigationIconTest` (plain JVM): each of the four `PebblesKey.tabs` has an `icon()` with an `activeTwin`; mirrors iOS `AppTabTests`.
- `AchievementFamilyIconTest` (plain JVM): the iOS `familySymbol` table, family by family, plus the default for `domain_first`, `glyph_sales` and an unknown family; the cross-surface mirror written down as a test.
- `PbblsIconsTest` (Robolectric, existing) stays green: the manifest parity it reads is unchanged by `replaces` and `migrated`.
- Screenshot references per part (D9), recorded on CI only.
- `npm run test` and `npm run lint:replaces` in `packages/icons` (Part 1 and Part 6).

## Device pass (the maintainer's Pixel 8)

The maintainer runs it on the build of each merged part; the account is real, so the pass reads and does not write beyond ordinary use. Eyeball: the navigation bar's lit stone in light, dark and wallpaper colour; the FAB's ring in its disc; Capture's photo hero, date and time pills and "date from photo"; the karma flash and success row; the held card's band mark at 14dp; the four menus' trash in the error colour; profile counters (`INLINE` beside `bodyMedium`), shortcuts and the Lab card; the achievement celebration; the glyph store tabs, prices and drawer; the Lab timeline. Then once at 200% font scale (inline icons beside soul counts and prices) and once with TalkBack across the bar, the top-bar actions, the FAB and a shelf badge.

## Risks

- **R1. Seventy-two references change on screens users know.** Mitigated by the parts, each one area with its references diffed in review. Not mitigated: the maintainer's eye on the device is the gate.
- **R2. `INLINE` at 200%.** A 16sp stone grows with its text; a row with a fixed height could crowd. None is known (the inline sites sit in wrapping rows and capsules), and the fallback is one flag (`followsFontScale = false`), recorded as a decisions-log supersede.
- **R3. A lit twin over the M3 indicator.** Material pairs a filled icon with the indicator, so this is its pattern; if the two read heavy on the device, the indicator stays (chrome) and the twin goes, the reverse of iOS R3, because the indicator is what Android users read as selection.
- **R4. Eleven drawables wait for the wave.** Errors, visibility, upload, upvote and the time pill are the visible ones. The brief gains its Android lines in Part 1 so the wave can start before this stack ends.
- **R5. `ListDetailScreenshotsKt` re-baselines in Parts 2, 4, 5 and 6.** Each bot commit must be fast-forwarded and rebased up before the next push, or the next part goes red on the last part's PNGs (iOS Part 4's lesson). Label a part only after the part below has its bot commit.
