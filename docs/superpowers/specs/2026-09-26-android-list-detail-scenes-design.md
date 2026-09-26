# Android — list-detail Scenes on large screens (#940)

Milestone M61 · Android Refacto. Follow-up to #855 (readable column, navigation
rail, `isWideWindow()`, shipped as #941–#943). Depends on #852 (Navigation 3).

## Problem

#855 made Android usable on large screens but stopped at one pane: on a tablet,
opening a soul, a collection, a pebble or a glyph still replaces the list it was
opened from. M3's adaptive guidance for this is list-detail, with the extra
rule that a pane **docked** on a compact window becomes **co-planar** on a large
one.

Since #940 was written, #852 made People and Collections top-level tabs rather
than pushes from Profile, so the pairs sit on a tab's own stack.

## Decisions

- **Each detail keeps its iOS presentation on phones and becomes a co-planar
  pane on large screens.** Soul and collection details are pushes on iOS
  (`NavigationLink`) and stay pushes. Pebble and glyph details are sheets on iOS
  (`PathView` → `.sheet { PebbleDetailSheet }`, `GlyphDetailDrawer` at the
  `.large` detent) and become docked sheets on phones.
- **Pebble detail stops being a full-screen cover.** Its "input-opaque like the
  iOS fullScreenCover" comment describes an iOS presentation that no longer
  exists; the sheet is parity, not divergence. This supersedes M38 D5's cover
  for this one screen.
- **`PebbleDetail` and the new `GlyphDetail` are `BarKey`s.** On a phone the
  sheet's scrim covers the bar; on a large screen the rail stays beside the
  panes. Bar visibility is still read straight off the key (D5 of #852), and D6
  (a non-current tab never has a modal on top) still holds because neither key
  is a modal any more. `EditPebble` stays modal.
- **Path is full width until a pebble opens.** Unlike People, Collections and
  Glyphs, Path shows no detail placeholder when idle: the timeline keeps its
  readable column, and opening a pebble splits the window.
- **Stateless content layers first.** The four soul/collection screens gain the
  `XContent(uiState, …)` overload #849 prescribes, so the 840/1024 dp renders
  show the real panes and their real chrome.
- **Glyph detail is promoted to a navigation entry**, as the last part of the
  stack.

## Presentation matrix

| Pair | Compact (phone) | Two panes (≥ 840 dp, or book posture) |
|---|---|---|
| People → `SoulDetail` | push, as today | list + detail, placeholder when idle |
| Collections → `CollectionDetail` | push, as today | list + detail, placeholder when idle |
| Path → `PebbleDetail` | docked sheet (was: full-screen cover) | Path + detail; Path full width when idle |
| Glyphs → `GlyphDetail` (new key) | docked sheet (was: in-screen `ModalBottomSheet`) | grid + detail, placeholder when idle |

`CollectionDetail` opened from the You tab has no list pane under it and stays a
single-pane push at every size.

## Mechanism

### Scene strategies

`RootScreen`'s `NavDisplay` takes
`sceneStrategies = listOf(pathAwareListDetail, bottomSheet)`, tried in order;
anything neither claims falls through to the default single pane, unchanged.

- **List-detail**: `rememberListDetailSceneStrategy` from
  `androidx.compose.material3.adaptive:adaptive-navigation3`, added to the
  catalog on the existing `material3Adaptive = "1.3.0"` ref (1.3.0 exists and
  depends on `navigation3-ui` 1.0.0; our 1.2.0 wins resolution). Entries carry
  `ListDetailSceneStrategy.listPane(sceneKey, detailPlaceholder)` or
  `detailPane(sceneKey)`, one scene key per pair. With the default
  `shouldHandleSinglePaneLayout = false` the strategy only returns a scene when
  two panes are shown, so phones fall through.
- **Path wrapper**: a small `SceneStrategy` that delegates to the list-detail
  strategy but returns `null` when the last entry is `PebblesKey.Path`. That is
  what keeps idle Path full width. The split and the un-split animate as a scene
  change (NavDisplay's transition), not as a pane slide.
- **Bottom sheet**: the Navigation 3 `BottomSheetSceneStrategy` recipe (an
  `OverlayScene` hosting a `ModalBottomSheet`), copied into `navigation/`. It
  claims entries whose metadata carries `bottomSheet()`: `PebbleDetail` and
  `GlyphDetail`. The sheet's own back handling calls `onBack`, so predictive
  back and scrim taps pop the stack.

### Posture-aware directive

`calculatePaneScaffoldDirective` (adaptive-layout 1.3.0) returns one horizontal
partition for Compact **and Medium** widths, regardless of posture. A foldable
in book posture is usually Medium, so the default would render a single pane
straight across the hinge.

`pebblesPaneDirective(info: WindowAdaptiveInfo): PaneScaffoldDirective` starts
from the default and raises `maxHorizontalPartitions` to 2 when
`info.windowPosture.hingeList` contains a separating vertical hinge. The hinge
is already in `excludedBounds` under the default `HingePolicy.AvoidSeparating`,
so the two panes sit either side of it. It is a pure function with a JVM test.

### Pane-aware chrome

- **Back arrow.** The entry provider reads
  `LocalListDetailSceneScope.current != null` and passes `showBack: Boolean` to
  `SoulDetailScreen` and `CollectionDetailScreen`; screens never import the
  adaptive library. Pebble and glyph details show no back arrow at all: in a
  sheet, the drag handle, scrim and system back dismiss; in a pane, the list is
  beside them. System back still pops in every case.
- **Pebble detail in a sheet or pane** drops the pointer-swallowing overlay
  (the scene, not the screen, now owns opacity) and `safeDrawingPadding()` (the
  sheet and the scaffold pad for insets). The emotion tint fills the sheet or
  pane.
- **Glyph store in a pane** puts its tab toolbar back at the bottom of the list
  pane: `isWideWindow()` answers for the window, but the grid is pane-wide
  there. The grid's content padding follows the toolbar.
- **Picking another item beside a list** replaces the detail
  (`Navigator.navigateToDetail`) rather than stacking it.
- **Placeholders** are a shared `DetailPlaceholder(icon, text)` in
  `core/designsystem`, one string per pair in `values/` and `values-fr/`.

### Glyph detail as an entry

`GlyphDetail(item: GlyphGridItem)` replaces `GlyphsListViewModel.covers.selected`.
Two deliberate choices, amended during Part 4:

- **The key carries the whole grid item, not an id.** There is no "one glyph
  by id" read in `GlyphMarketServicing`, and opening from the grid must stay
  instant. `GlyphGridItem` becomes `@Serializable` (its `Glyph` and strokes
  already are). A stale item after process death is harmless: `buy_glyph` is
  server-authoritative and the panel morphs from the server's answer. So
  `GlyphDetailViewModel` has nothing to load; it holds the buyer's karma
  balance and records a purchase. The buy itself stays in `GlyphSwapPanel`,
  which the composer's glyph picker also hosts.
- **A purchase reaches the list through `GlyphMarketServicing.purchases`.** In
  a pane the list stays resumed beside the detail, so the resume refresh the
  other pairs rely on never fires. The market service emits every successful
  buy; `GlyphsListViewModel` applies its existing bookkeeping from it.

`GlyphDetailDrawerContent` is already stateless and is reused as-is.

## The stack

| # | Branch | Change |
|---|---|---|
| 1 | `quality/940-stateless-detail-content` | `SoulsListContent`, `SoulDetailContent`, `CollectionsListContent`, `CollectionDetailContent`, `PebbleDetailContent` taking their `UiState`; the screens become VM wiring around them. No behavior change, no reference PNG moves. This spec rides here. |
| 2 | `feat/940-list-detail-souls-collections` | Catalog entry, list-detail strategy, `pebblesPaneDirective` + test, metadata and placeholders for the two pairs, `showBack`. Screenshots at 840 and 1024 dp for both pairs, idle and with a detail open. |
| 3 | `feat/940-pebble-detail-sheet-pane` | Bottom-sheet strategy, `PebbleDetail` → `BarKey`, the Path wrapper, pebble detail chrome. Screenshots: phone sheet, and the pair at 840 and 1024 dp. Decision-log entry. |
| 4 | `feat/940-glyph-detail-entry` | `GlyphDetail` key, `GlyphDetailViewModel` + JVM test, sheet and pane, toolbar in the list pane. Screenshots. |

Each part passes `ktlintCheck`, `./gradlew lint` (no new baseline entries), unit
tests and `assembleDebug` on its own.

## Screenshots

`@PreviewWide` / `@PreviewWideTall` already exist in `PreviewVariants.kt`. The
scene renders compose a `NavDisplay` over stateless entries built from the Part
1 content layers, with the directive **passed explicitly** (two partitions)
rather than read from the window, the way `NavigationSuiteScreenshots` pins the
suite type: layoutlib's reported window size is not trustworthy. References are
rendered locally for review only; the committed set comes from the
`rebaseline-screenshots` label.

## Limits (stated, not solved)

- **Tabletop posture.** List-detail is side by side, so a horizontal hinge runs
  across both panes. Nothing in the pairs is designed for a top/bottom split.
- **Book posture outside the pairs.** Idle Path, Settings and other single-pane
  screens still span the hinge. The acceptance criterion is scoped to the pairs.
- **Edit from the pebble sheet** is still a full-screen modal; the sheet
  re-enters when it is dismissed.

## Cross-surface and bookkeeping

- **No data contract moves.** iOS is iPhone-only and already presents pebble
  and glyph details as sheets; nothing to mirror back.
- **Decision log** (Part 3): pebble detail is a sheet on compact and a pane on
  large screens, superseding the M38 D5 cover for that screen; `PebbleDetail`
  and `GlyphDetail` are `BarKey`s, and why D6 still holds.
- **Arkaik**: the affected view nodes move to `development` when Part 2 starts;
  the GitHub App does the rest from the PRs.
- **Issue #940** acceptance is amended: "a glyph" is added, and the hinge
  criterion is scoped to the list-detail pairs.

## Verification per part

- ktlint, `./gradlew lint`, unit tests, `assembleDebug`.
- `validateDebugScreenshotTest` locally to see which previews moved; CI
  re-baselines.
- Parts 2–4: smoke on the Pixel 7 AVD (phone: nothing changes for souls and
  collections; pebble and glyph open as sheets) and a resizable or foldable AVD
  (two panes at ≥ 840 dp, book posture split at the hinge, system back pops the
  detail, no back arrow beside a list).

## Acceptance (from #940, amended)

- [ ] On a tablet in landscape, opening a soul, a collection, a pebble or a
      glyph shows it beside its list; on a phone, souls and collections are
      unchanged and pebbles and glyphs open as docked sheets.
- [ ] A detail pane has no back arrow while its list is visible; system back
      still pops it.
- [ ] Nothing in a list-detail pair renders across a foldable's hinge in book
      posture.
- [ ] 840 and 1024 dp screenshots exist for each pair and are gated.
