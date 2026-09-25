# Android — stock Material 3 components replace the hand-rolled chrome (#854)

Milestone M61 · Android Refacto. Depends on #853 (theme, shipped as #922–#929).

## Problem

Every chrome primitive on Android is a SwiftUI idiom rebuilt from `Box`, `Row`
and `clickable`. They miss what stock M3 components carry for free: 48 dp
targets, ripples, roles, state descriptions, focus and error states. The three
open accessibility findings on the Android A11Y cell (SlideToConfirm inoperable
with TalkBack, targets below 48 dp, fixed heights under sp text) cluster in
exactly these controls.

Since the issue was written, #852 replaced `PathBottomBar` with the stock
four-tab `NavigationBar`, and #928 moved `Spacing` to the 4 dp grid. Both rows
drop out of scope.

## Decisions

- **Whole stack in one effort**, one component family per PR, chained with
  `gh stack`.
- **Expressive where the issue lists it.** The theme is already
  `MaterialExpressiveTheme` on `material3 1.5.0-alpha27`, and every Expressive
  API named below ships in that artifact (verified against the cached AAR).
- **`GlyphTabBar` becomes a `HorizontalFloatingToolbar` of `ToggleButton`s** in
  a `selectableGroup`, not a `NavigationBar`: the store already sits inside the
  app's four-tab bar, and the same control lives in the glyph picker sheet,
  where a navigation bar has no place. It keeps its floating-over-the-grid
  position.
- **Wrappers are deleted, not re-skinned**, where the stock component covers the
  call site. A thin wrapper survives only where a real Pebbles default repeats
  across call sites (e.g. full-width primary button with a loading state).
- **Chrome is an Android divergence from iOS**, recorded as an extension of the
  #853 M3-evo divergence. Behaviour and funnel still mirror iOS; no data
  contract moves.

## The stack

| # | Branch | Change |
|---|---|---|
| 1 | `feat/854-custom-control-a11y` | Controls that stay custom get fixed. `SlideToConfirm`: a `CustomAccessibilityAction` that confirms, and drag math with a `LayoutDirection` multiplier. `ValenceRoll`: same RTL fix. `WeekHeader` chevron and `CheckGlyph`: `Icons.Rounded` in place of Canvas, the chevron becomes an `IconButton`. `minimumInteractiveComponentSize()` on remaining custom clickables (Lab `ReactionButton`, record-flow chrome, …); `ValenceFan`'s `MinimumHitTarget` 44 → 48 dp. `WelcomeCarousel` 110 dp and `WeekRoll` 96 dp fixed heights → `heightIn(min = …)`. |
| 2 | `feat/854-buttons` | `PebblesPrimaryButton` → `Button` with `ButtonDefaults.shapes()`; `WelcomeOutlineButton`, `GoogleSignInButton` → `OutlinedButton` with brand colours; `NewPebbleButton` → `FilledTonalButton`. Decision-log entry for the chrome divergence. |
| 3 | `feat/854-selection` | `PebblesCheckbox` → `Checkbox` in `Row.toggleable` (announces "checked"); `PebblesAuthSwitcher` → connected `ButtonGroup` of `ToggleButton`s in a `selectableGroup`; `GlyphTabBar` → floating toolbar (above); `EmotionChip` → `FilterChip(selected)`; privacy / collection / domain rows → `RadioButton` rows in a `selectableGroup`. |
| 4 | `feat/854-text-fields` | `PebblesTextInput` and the raw `BasicTextField` rows in Settings → `OutlinedTextField` (focus, error and supporting-text states). The hand-lettered name inputs that use `PebblesTheme.hand` keep their typography through the field's `textStyle`. |
| 5 | `feat/854-lists-cards` | `PebblesList`, `profileCard`, `SurfaceTile` → `OutlinedCard` + `ListItem` + `HorizontalDivider`. |
| 6 | `feat/854-top-bars` | `PebblesScreen` → `Scaffold`; `PebblesTopBar`, `SheetToolbar` → `CenterAlignedTopAppBar`; Profile → `LargeFlexibleTopAppBar`; `RecordFlowChrome` → top app bar with `LinearWavyProgressIndicator` in place of `ProgressDots`. Inset handling under the outer four-tab `NavigationBar` is the risk here: inner `Scaffold`s take `contentWindowInsets` that exclude what the outer one consumed. |
| 7 | `feat/854-loading` | `CircularProgressIndicator` → `LoadingIndicator` across the 27 files. Mechanical; last so it does not conflict with every part above. |

Order: Part 1 is independent of every swap and closes P1 findings, so it goes
first. Top bars touch 20 screens and carry the inset risk, so they go late.
Loading is a pure sweep and goes last.

Each part must pass `ktlintCheck`, `./gradlew lint` (no new baseline entries),
unit tests and `assembleDebug` on its own.

## Cross-surface and bookkeeping

- **iOS `SlideToConfirm`**: check `apps/ios/Pebbles/Features/Glyph/Views/SlideToConfirm.swift`
  for the same VoiceOver gap during Part 1. If present, file a separate
  `[Fix]` issue (`fix`, `ios`, `ui`) — not fixed in this stack.
- **Arkaik**: at the start of each part, move the acceptances and views it
  touches to `development` over MCP. PR bodies list those node ids in `nodes:`
  and never cite a finding id. The three findings are resolved with
  `kritik_resolve_finding` once Part 1 merges.
- **Lab Note**: Parts 1 and 2 carry one. Parts 3–7 are covered by Arkaik and
  get the `no-lab-note` label.
- **Labels / milestone**: inherited from the issue — `feat`, `ui`, `android`,
  M61 · Android Refacto.

## Verification per part

1. `ktlintCheck`, `./gradlew lint`, unit tests, `assembleDebug` locally with
   `ANDROID_HOME` exported.
2. `validateDebugScreenshotTest` locally to see which previews moved; the
   committed references are regenerated by the `rebaseline-screenshots` label,
   never from the local machine.
3. Part 1: TalkBack and Accessibility Scanner smoke on the Pixel 7 AVD (glyph
   purchase, week chevrons, record-flow chrome) and an RTL pass under the
   `ar-XB` pseudo-locale.
4. Before any `gh stack` submit or push: fetch and fast-forward over the
   re-baseline bot's commits.

The "no clipped labels at font scale 2.0" criterion is read off the existing
`@PreviewLargeFont` renders in each part's diff artifact.

## Acceptance (from #854)

- TalkBack completes a glyph purchase, toggles the consent checkbox
  ("checked"), and switches auth mode with correct role announcements.
- No interactive element below 48 dp on Path, record flow, Settings, store.
- RTL: drag controls move toward the end edge.
- Font scale 2.0 renders show no clipped labels.
- The three related Kritik findings resolved by the closing PRs.
