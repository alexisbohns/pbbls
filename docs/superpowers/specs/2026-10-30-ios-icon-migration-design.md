# iOS icon migration — design

**Date:** 2026-10-30
**Issue:** #1314 (follow-up of #1311, the icons package)
**Milestone:** M66 · Brand icons
**Package spec:** `docs/superpowers/specs/2026-10-28-icon-package-design.md` (D1–D11, R4)
**Inventory:** 84 distinct SF Symbols drawn under `apps/ios/Pebbles/` (82 in release builds; 62 as `systemName:`/`systemImage:` literals, 22 only through ternaries and computed helpers), counted on branch `chore/1311-icons-ios` on 2026-10-30. Reviewed the same day (Fable); the review's blockers are folded in below.

## Summary

The iOS app draws 84 SF Symbols where the brand now has 121 hand-drawn icons. This spec maps every one of them to a `PbblsIcon`, to a second drawing wave, or to a short list that stays SF Symbols on purpose; fixes the size an icon draws at per place it appears; decides how the tab bar lights its selected tab; and lands the migration lint the package spec deferred (D10). The work is one stack of seven parts.

## Goal

After the stack merges:

- no quoted literal under `apps/ios/Pebbles/` names a symbol that the manifest lists in `replaces.ios`, in any form (literal argument, ternary, `return`, `case`), and `npm run lint:replaces --workspace=@pbbls/icons` proves it;
- every icon that carries meaning in the product (a tab, a pebble, a soul, a collection, karma, a glyph, a date, an achievement, a camera) is a Pebbles drawing;
- the icons that draw with no drawing of their own are named, so a second wave can draw them;
- the three size tokens of `PebblesIcon` are gone, replaced by sizes that keep the 1.75 stroke readable.

## Non-goals

- Android and web (#1315, #1313). They reuse this spec's mapping tables, which are the per-site record; the manifest holds only the primary row per symbol (D8).
- Redrawing any icon. Where the library has nothing that means the thing, the SF Symbol stays and the concept goes to the wave brief (D2). Drawing it is the wave's job, not this stack's.
- Picking a different candidate than the one named in D3. A swap is a one-line manifest change later.
- Animating icons, symbol effects, or the lit series anywhere but the tab bar and the places D4 names.

## Decisions

**D1. Chrome stays SF; meaning becomes Pebbles.** Directional and dismissal glyphs are platform grammar, not brand: `chevron.backward`, `chevron.down`, `chevron.forward`, `chevron.left`, `chevron.right`, `xmark`, `ellipsis.circle`, `square.and.arrow.up`, `doc.on.doc`, `arrow.uturn.backward`, `arrow.right` (the "becomes" arrow inside the guide illustrations and the Lab's "See all"). They stay SF Symbols, as do the three brand marks the platform requires as drawn (`applelogo` and `apple.logo` for Sign in with Apple, `g.circle` for Google). The list is closed and lives here and in the decisions log (Part 7): an agent adding a new SF Symbol outside it needs a drawing or a wave-brief line. *Rejected:* drawing chevrons and crosses. A hand-drawn chevron inside a `List` row fights the one `NavigationLink` draws by itself, and a wobbly `xmark` on a sheet reads as a bug.

**D2. No drawing, no replacement; the gap is a wave brief.** Twelve concepts draw today with nothing in the library that means them. They keep their SF Symbol in this stack and are written into `packages/icons/waves/2026-11-chrome/brief.md` as the next wave's figure list (one line each, the exploration brief format): *again* (`arrow.clockwise.circle.fill` retry, `arrow.counterclockwise` replay), *swap* (`arrow.left.arrow.right`), *upload* (`arrow.up.circle` as a status), *upvote* (`arrow.up.circle`/`.fill` in the Lab), *something went wrong* (`exclamationmark.triangle`×8, `.fill`, `person.2.slash`, `link.badge.plus`, `book.closed`), *document* (`doc.text`), *privacy* (`hand.raised`), *the visibility trio* (`lock.fill` secret, `person.2.fill` private, `globe` public: three drawings made together, or none), *locked* (`lock`), *invite* (`person.badge.plus`), *life domain* (`safari`), *market* (`storefront`). Mapping one of a set while its siblings stay SF would give a picker two vocabularies, which is why the trio and the error family move together or not at all. `person.2.fill` is therefore a wave symbol everywhere, including the one site outside the trio (the glyph owners tile, which switches to `people` by site, not by manifest), and Part 1 removes `person.2.fill` from `people.replaces.ios`, where the package wave had put it.

**D3. The pick is the first row of a meaning group, with seven named exceptions.** `icons.json` keeps the maintainer's order; the first row of each meaning is the official drawing and the rows after it are candidates (package spec D4: "available, not yet adopted"). Where the library already had a `replaces.ios` entry (eleven rows), it is kept, which is why `checkmark.circle.fill` is `check-circle` and not the group's first row `checkmark-dot` (which takes the plain `checkmark`). Candidates adopted beyond the first row, each because the symbol it replaces is a distinct thing: `pebble-flat` (the moment, D4), `plus-circle` (a plain plus; `plus-circle-dot` is the pen's seal and stays unused), `camera-focus` (the photo placeholder), `draw-dotted` (edit), `chart-double-grow` (a usage chart, a stretch accepted for a muted "Soon" tile), `pebble-glow-lg` (the celebration hero, the same drawing as `pebble-glow-md` at a larger stone), `sparkles` (the Debug icon-lab row only). `check-circle-dot` is also adopted, for *owned* beside `check-circle` *done*.

**D4. Moments are flat, pebbles are round.** Since the moments/pebbles split (#1107), the app records *moments* that become *pebbles*. `pebble-flat` is the moment (`clock` quick-moment shortcut, the `first_moment` achievement) and `pebble-round` is the pebble (`fossil.shell`, `first_pebble`, "Edit pebble" in the held-card menu). The lit twin is the pebble that exists or counts: an in-progress Lab entry (`circle.inset.filled`), the blue pebble to claim (`drop.fill`). The outline is the one still to come: a backlog entry and an unchecked checklist row (`circle.dashed`), an unchecked `PebblesCheckbox` (`square`). A checked row or box is `check-circle`: the tick lands in the stone. The practice grid (`AssiduityGrid`, 7pt cells) is the one place a stone cannot draw (D6) and keeps a plain `Circle` in its two colours, lit and outline, in place of `fossil.shell.fill` and `alternatingcurrent`.

**D5. The tab bar lights its stone.** The five tabs take `home`, `path`, `people`, `collection`, `person`, and the selected tab draws the `active` twin: `.tabItem { Label { Text("Home") } icon: { Image(nav.selected == .home ? PbblsIcon.homeActive.assetName : PbblsIcon.home.assetName) } }`. `AppTab` gains `icon: PbblsIcon` so the five literals leave `PebblesTabView`. UIKit renders a tab image as a template and does not scale it; the asset is 24 on 24 with `preserves-vector-representation`, within the HIG size, so no frame is set and `.renderingMode(.template)` is not needed. The ternary re-evaluates all five `tabItem`s on each selection, which is cheap. Caveat accepted: a custom tab image does not follow Dynamic Type or the large-content viewer the way an SF Symbol does. The `lit-n` series is used in one place only in this stack: the `first_five` achievement is `stack.lit-3`. *Rejected:* `symbolEffect` / `.symbolVariant(.fill)` for selection: SF-only, and the lit seal is the brand's own filled state.

**D6. Five sizes, named, in place of three font tokens.** A 24-unit drawing with a 1.75 stroke draws a 1.1pt line at 15pt, the thinnest `PebblesIcon` token (R4), and 0.5pt at 7pt. The migration retires `enum PebblesIcon` and `.pebblesIcon(_:)` (`Theme/Icon+Pebbles.swift`) and adds, in that same non-generated file (the generator does not own it), `enum PbblsIconSize: CGFloat { case inline = 16, row = 20, toolbar = 22, tile = 28, hero = 48 }` with `extension PbblsIconImage { init(_ icon: PbblsIcon, _ size: PbblsIconSize, relativeTo: Font.TextStyle = .body, …) }`, which delegates to the generated initializer with `size.rawValue`; `@ScaledMetric` scales it as before. Rules per place: *inline* with text (karma amounts, "date from photo", week links) at 16 relative to the surrounding text style; *row* for a list row's leading icon, a segmented pill and a menu item (the `PebblesMenuItem` frame moves from 24 to 20 wide); *toolbar* for toolbar and chrome buttons inside their 44×44 hit frame (at accessibility sizes the scaled icon may pass 44; accepted, the frame clips nothing); *tile* for `SurfaceTile`, `DataTile`, the Home shortcuts and card leading icons; *hero* for `ContentUnavailableView` images, the achievement celebration and the invalid-invite page. The floor is 16: anything drawn smaller today is chrome (D1), stays a shape (`AssiduityGrid`, D4), or is resized: `SoulItem`'s pebble count takes `pebble-round` at 16 with its text at `.caption2`; `CollectionMark`'s default side of 14 is accepted (1.0pt stroke, the one exception, noted in code). Two initializers cannot take a `PbblsIcon` and change shape at 18 sites: `ContentUnavailableView(_:systemImage:)` becomes `ContentUnavailableView { Label { Text(…) } icon: { PbblsIconImage(…, .hero) } }`, and `Label(_:systemImage:)` becomes `Label { Text } icon: { PbblsIconImage }` (a mixed `Label`, Pebbles tick beside SF `doc.on.doc`, is fine in `InviteSheet`). The three `.pebblesIcon(.medium)` callers that stay SF under D1 and D2 (`AchievementsView` lock, `ProfileAchievementsCard` and `ProfileLabCard` chevrons) take `.font(.system(size: 15, weight: .medium, design: .rounded))` inline when the token goes.

**D7. The helpers take a `PbblsIcon`, not a string.** Ten computed sources draw symbols today: `SurfaceTile(systemImage:)`, `ProfileShortcutTile(systemImage:)`, `DataTile(icon:)`, `PebblesMenuItem(systemImage:)`, `HomeShortcuts.tile(_:)`, `Achievement.familySymbol`, `LogTimeline.iconName`, `GlyphTabBar.symbol(_:)`, `Visibility.systemImageName` and `LinkedProvider.systemImage`. The first eight switch their parameter or return type to `PbblsIcon` (the string version is deleted, not kept beside it). `familySymbol` stays total: `domain_first` and `glyph_sales`, whose symbols are wave rows (*life domain*, *market*), take the family default `pebble-glow-md` until the wave draws them. `Visibility` (the trio waits for the wave) and `LinkedProvider` (brand logos, forever SF) are not touched: their names are never in `replaces.ios`, so the lint never looks at them, and a wrapper enum would be code written to be deleted. *Rejected:* an `IconSource { pbbls | system }` enum for the two.

**D8. The lint lands now, reads a per-surface marker, and matches bare literals.** Package spec D10 keyed the lint on `status: shipped` and on the `systemName: "<name>"` form. Both change. (a) The manifest gains `"migrated": []` per row (surfaces ⊆ `ios|android|web`, unique); the lint forbids `replaces.<surface>` names only for surfaces in `migrated`; `status: shipped` stays a value but the validator fails a row that says `shipped` while `migrated` lacks a surface, and `packages/icons/CLAUDE.md` step 3 says so. Package spec D4 and D10 are superseded, in one decisions-log entry (Part 7). (b) For iOS the lint greps the bare quoted literal `"<name>"` in every `.swift` under `apps/ios/Pebbles/` except the generated `Theme/PbblsIcon.swift` (its raw values `"trash"`, `"heart"`, `"checkmark"` would be false hits), so a ternary, a `return "lock.fill"`, a `case "pebble_count": "circle.grid.3x3"` and a `DataTile(icon: "fossil.shell")` are all hits; the `systemName:` form alone misses 22 of the 84 symbols today. Dotless names collide with ordinary strings in one place (`SpineLabView.swift`, `homeLog = "camera"`); that string is renamed in Part 1 rather than allowlisted. (c) The manifest carries one primary row per replaced symbol, since `replaces` is a per-row list and cannot say "`sparkles` is `karma-dot` on the counter and `sparkle` on the photo date". The per-site choice is this spec's tables; the primary rows are: `sparkles` → `karma-dot`, `square.stack` → `path`, `circle.hexagongrid` → `collection`, `drop.fill` → `pebble-round`, `trophy` → `pebble-glow-md`, `person.2.badge.plus` → `people`, `clock` → `pebble-flat`. `icons.yml` gains a fifth step, `npm run lint:replaces --workspace=@pbbls/icons`, and `package.json` the script.

**D9. References are re-recorded once per part, on CI.** Most screens that draw an icon have a reference image (`PebblesTests/References/`), so each part turns some references red. Each part is re-baselined once, with the `rebaseline-ios-screenshots` label after its last code commit; the bot's commit is kept by fetching and fast-forwarding before any stack push and rebasing the parts above it (Part 3 of #1319 lost its two PNGs by skipping that). The sheet reference `IconSheetReferences` does not change. No reference is recorded locally (27 fail on an untouched main on the maintainer's Mac). No reference renders the tab bar today, so Part 2 is checked on the device, not by a reference.

**D10. Debug rows migrate too.** The three Developer rows (`sparkles`, `line.3.horizontal.decrease`, `square.grid.3x3`) become `pebble-round`, `path`, `sparkles`. They cost nothing and keep the lint free of a `#if DEBUG` carve-out.

## The mapping

Sizes are D6 names. *Keep* rows are D1; *wave* rows are D2 and keep their symbol until the wave lands; *shape* rows are D4.

### Tabs and navigation shell

| SF Symbol | Sites | PbblsIcon | Size |
|---|---|---|---|
| `house` | tab Home | `home` / `home.active` | tab |
| `square.stack` | tab Path | `path` / `path.active` | tab |
| `person.2` | tab People | `people` / `people.active` | tab |
| `circle.hexagongrid` | tab Collections | `collection` / `collection.active` | tab |
| `person.crop.circle` | tab You | `person` / `person.active` | tab |
| `chevron.*` (5), `xmark`, `ellipsis.circle`, `square.and.arrow.up`, `doc.on.doc`, `arrow.uturn.backward`, `arrow.right` | 31 sites | keep | as today |
| `applelogo`, `apple.logo`, `g.circle` | 3 sites | keep (brand marks) | as today |

### Pebbles, moments, done

| SF Symbol | Sites | PbblsIcon | Size |
|---|---|---|---|
| `fossil.shell` | SoulItem count, ProfileCountersRow | `pebble-round` | inline (text to `.caption2`) / tile |
| `fossil.shell.fill`, `alternatingcurrent` | AssiduityGrid cells (7pt) | shape: `Circle` lit / outline | the cell |
| `drop.fill` | HomeChecklist claim button, `first_five` | `pebble-round.lit-1` / `stack.lit-3` | row / tile |
| `clock` | HomeShortcuts quick moment, `first_moment` | `pebble-flat` | tile |
| `circle.grid.3x3` | `pebble_count` | `stack` | tile |
| `circle.hexagongrid` | HeldCardMenu "Edit pebble", `first_pebble` | `pebble-round` | row / tile |
| `checkmark` | InviteSheet copied | `checkmark-dot` | row |
| `checkmark.circle` | LogTimeline shipped | `check-circle` | inline |
| `checkmark.circle.fill` | HomeChecklist done, RecordPhotoStep, AttachedPhotoView, ExistingSnapRow | `check-circle` | row / inline |
| `circle.dashed` | HomeChecklist not done, LogTimeline backlog | `pebble-round` | row / inline |
| `circle.inset.filled` | LogTimeline in progress | `pebble-round.lit-1` | inline |
| `checkmark.square` / `square` | PebblesCheckbox | `check-circle` / `pebble-round` | the box frame |
| `checkmark.seal` / `.fill` | Glyph owned (empty ×2, pill, badge) | `check-circle-dot` | hero / row / inline |
| `xmark.circle.fill` | remove photo ×3 | `remove` | row |
| `person.badge.minus` | remove connection | `remove` | row |

### Create and capture

| SF Symbol | Sites | PbblsIcon | Size |
|---|---|---|---|
| `plus` | BareMomentPage, NewPebbleFab, three toolbars, GlyphView cell | `plus-circle` | row / toolbar / cell `max(side*0.4, 18)` |
| `plus.circle` | HomeShortcuts | `plus-circle` | tile |
| `camera` | HomeShortcuts | `camera` | tile |
| `photo.badge.plus` | EditMomentSheet | `camera` | row |
| `photo`, `photo.on.rectangle.angled` | thumbnail placeholders, RecordPhotoStep hero | `camera-focus` | tile / hero |
| `pencil` | HeldCardMenu edit | `draw-dotted` | row |

### Souls, collections, you

| SF Symbol | Sites | PbblsIcon | Size |
|---|---|---|---|
| `person.2` | ConnectionsList empty, SoulsList empty, `first_soul` | `people` | hero / tile |
| `person.2.fill` | GlyphDetailDrawer owners tile | `people` by site (the symbol itself is a wave row, D2) | tile |
| `person.2.badge.plus` | SoulsList open Circle | `people` | toolbar |
| `person.3`, `person.3.fill` | Glyph community (empty ×2, pill) | `people` | hero / row |
| `person.fill` | Glyph pill Mine | `person` | row |
| `person.circle` | Connections avatar fallback | `person` | row inside the 32 frame |
| `bubble.left.and.bubble.right.fill` | FeaturedCommunityCard | `people` | tile |
| `square.stack` | CompleteCollectionStep row | `collection` | row |
| `square.stack.3d.up` | CollectionMark fallback, `first_collection` | `collection` | the mark's `side` / tile |
| `gear` | ProfileView settings | `slider` | toolbar |
| `trash` | PebblesMenu `.delete`, CollectionsList swipe, SettingsSheet, GlyphCarveSheet | `trash` | row |

### Glyphs, karma, dates, achievements, ideas

| SF Symbol | Sites | PbblsIcon | Size |
|---|---|---|---|
| `scribble` | ProfileShortcutsRow, GlyphsList empty, GlyphView cell, `glyph_count` | `pebble-dotted-glyph` | tile / hero / cell |
| `pencil.and.outline` | `glyph_count` | `pebble-dotted-glyph` | tile |
| `sparkle` | karma amounts ×6 | `karma-dot` | inline |
| `sparkles` | ProfileCountersRow karma | `karma-dot` | tile |
| `sparkles` | RecordWhenStep date from photo | `sparkle` | inline |
| `lightbulb.max` | ProfileLabCard | `sparkle` | tile |
| `calendar` | GlyphDetailDrawer created, ProfileCountersRow days | `calendar-date` | tile |
| `chart.bar.fill` | GlyphDetailDrawer usage (Soon) | `chart-double-grow` | tile |
| `trophy` | AchievementMomentView hero / ProfileAchievementsCard / family fallback (incl. `domain_first`, `glyph_sales`) | `pebble-glow-lg` / `pebble-glow-md` / `pebble-glow-md` | hero / tile / tile |
| `heart` | `emotion_first` | `heart` | tile |
| `waveform.path.ecg` | EmotionPickerContent header | `heart` (a stretch: "I liked this" for "emotion") | row |
| `circle.grid.2x1` | Collection/Soul detail empty | `hourglass-end` | hero |

### Debug rows (D10)

`sparkles` → `pebble-round`, `line.3.horizontal.decrease` → `path`, `square.grid.3x3` → `sparkles`, all *row*.

### Wave brief (D2), kept as SF until drawn

`arrow.clockwise.circle.fill`, `arrow.counterclockwise`, `arrow.left.arrow.right`, `arrow.up.circle`, `arrow.up.circle.fill`, `exclamationmark.triangle`, `exclamationmark.triangle.fill`, `person.2.slash`, `link.badge.plus`, `book.closed`, `doc.text`, `hand.raised`, `lock.fill`, `person.2.fill`, `globe`, `lock`, `person.badge.plus`, `safari`, `storefront`. Nineteen symbols, twelve concepts.

Tally: 84 symbols = 49 replaced + 16 kept (14 chrome and brand marks, 2 grid shapes) + 19 to the wave. `PbblsIcon` rows used: 28, of which 11 already carry a `replaces.ios` entry and 17 gain one (`path`, `pebble-flat`, `stack`, `checkmark-dot`, `check-circle-dot`, `remove`, `plus-circle`, `camera-focus`, `draw-dotted`, `karma-dot`, `sparkle`, `sparkles`, `chart-double-grow`, `pebble-glow-lg`, `pebble-glow-md`, `heart`, `hourglass-end`); `people` loses `person.2.fill` and gains `person.2.badge.plus`, `person.3.fill`, `bubble.left.and.bubble.right.fill`.

## The manifest and the lint

`icons.json` rows gain `"migrated": []`. The 28 mapped rows list the symbols they are the primary row for under `replaces.ios` (D8c). `scripts/lib/manifest.ts` validates `migrated` and the `shipped`/`migrated` agreement; `scripts/lint-replaces.ts` is:

```
for each row, for each surface in row.migrated:
  for each name in row.replaces[surface]:
    grep the surface's tree for the bare quoted literal "<name>"
      ios:     apps/ios/Pebbles/**/*.swift minus Theme/PbblsIcon.swift
      android: apps/android/app/src/main/**/*.{kt,xml} for R.drawable.<name> (D10 form, unchanged)
      web:     apps/{web,admin}/**/*.{ts,tsx} outside components/ui/ for <Name> imported from lucide-react (unchanged)
    any hit fails, printed as path:line
exit 1 on any hit; print "replaces: N rows, M names, 0 hits" otherwise
```

It is `tsx`, tested with Vitest on a fixture tree: a `systemImage: "x"` hit, a ternary hit, a `return "x"` hit, a `case` hit, the generated file excluded, a name in a comment still a hit (literals only, no parsing), and zero hits. Part 1 ships it with `migrated` empty everywhere; Part 6 sets `["ios"]` on the 28 rows in the same commit that deletes the last listed literal, so the lint goes from vacuous to live with the code it polices.

## Parts (one stack, `chore/1314-…`)

| Part | Branch | Content | References |
|---|---|---|---|
| 1 | `chore/1314-replaces-lint` | manifest `migrated` + `replaces.ios` per D8c (incl. removing `person.2.fill` from `people`), validator rule, `lint-replaces.ts` + tests, `icons.yml` step, `package.json` script, `packages/icons/CLAUDE.md` step 3, `PbblsIconSize` + overload, wave brief file, `SpineLabView` string rename | none |
| 2 | `chore/1314-tab-bar` | `AppTab.icon`, `PebblesTabView` with active twins, `PebbleToolbarButton` callers (gear, plus ×3, people) | device check (no tab-bar reference exists) |
| 3 | `chore/1314-home-record` | Home (checklist, shortcuts, toast; guides' arrows stay), Record and Complete steps, `PebblesCheckbox`, AttachedPhoto/ExistingSnap/SnapImage | home, record, complete |
| 4 | `chore/1314-path` | HeldCardMenu, `PebblesMenu` row width, CollectionMark, SoulPill, SoulItem badge, EmotionPicker, EditMomentSheet (card top bar and wrap chrome stay) | path, card, wraps |
| 5 | `chore/1314-profile` | ProfileView, counters, shortcuts, Lab card, achievements (`familySymbol: PbblsIcon`), AssiduityGrid shapes, Settings rows incl. Debug, Souls/Collections lists and details | profile, achievements, settings |
| 6 | `chore/1314-glyph-connections-lab` | Glyph list/picker/detail/tab bar/carve, Connections list/invite, Lab timeline and community card (upvote stays); then `migrated: ["ios"]` on the 28 rows, lint live | glyph, connections, lab |
| 7 | `chore/1314-retire-tokens` | delete `PebblesIcon` tokens and `.pebblesIcon(_:)` (the three D6 inline fonts), decisions-log entry (D1 list, D6 sizes, D8 superseding package D4/D10), plan "Lessons learned" | none |

Each part builds and passes `swiftlint`, the unit tests and its re-recorded references alone; chrome symbols (D1) left in a file are not a reason to touch that file. Part 1 is the only part that touches `packages/icons` before Part 6's manifest flip; Parts 2–6 are iOS-only and reviewable screen by screen. After each part's re-baseline commit, the parts above it are rebased before the next push (D9).

## Testing

- `PbblsIconSizeTests`: the five raw values, and that the overload passes `relativeTo` through.
- `AppTabTests`: each tab's icon has an `activeTwin`.
- `lint-replaces.test.ts`: the fixture cases above; and a manifest test that every `replaces.ios` name is a bare symbol name (no `:` or quote; the literal form is built by the lint, not stored) and that no symbol is listed by two rows.
- Reference images per part (D9), recorded on CI.
- The Icon lab (Settings → Developer) is the visual check at each size before a part is opened: the five sizes are added to its slider as detents.

## Risks

- **R1. Forty-nine icons change at once on screens users know.** Mitigated by the parts: each PR is one area, with its references diffed in review. Not mitigated: the eye of the maintainer is the gate, on the phone (Icon lab, then each part's build).
- **R2. A 16pt inline stone next to 13pt caption text is big.** D6 moves the one case that would need 11pt (`SoulItem`) to `.caption2` text beside a 16 icon; if it still reads heavy, `inline` becomes 14 and the stroke is accepted at 1.0pt there, a one-line change.
- **R3. A custom tab image and the bar's tint.** UIKit tints a template tab image with the bar's tint, as it does an SF Symbol; verified by the first device build of Part 2. If the selection tint and the active twin disagree visually, the twin wins and unselected tabs take `onSurfaceVariant`.
- **R4. Nineteen symbols stay SF until the next wave.** Error states, visibility and invites are the visible ones. The brief is written in Part 1 so the wave can start before this stack finishes.
- **R5. The bare-literal lint can false-hit a dotless name.** Today one string does (`"camera"` in the Spine lab, renamed in Part 1). A future collision is a rename or, as a last resort, an allowlist line in the lint with the file path; never a weaker pattern.
