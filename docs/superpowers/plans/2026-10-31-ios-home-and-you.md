# iOS Home and You — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retire SF Pro Rounded for a bundled Inclusive Sans, then rebuild Home and You as the c3 mock: the month's weeks, the greeting and the cut quick actions on top of Home, the first five in a box, the wraps as cards, About as a list; on You the pentagon glyph, the hand name, the Ripples line, the counters as one split stone, then Glyphs, Achievements and the Lab. Spec: `docs/superpowers/specs/2026-10-31-ios-home-and-you-design.md` (D1–D22). Issue 1382, milestone M67.

**Architecture:** `SpineType` gains a third face, `.inclusive`, and the ten SF Pro Rounded `PebblesFont` tokens become ranks on it, resolved (and scaled) by the Spine resolver. The rock geometry moves from `Features/Home/Shortcuts/` to `Features/Shared/Stones/` and learns to cut `n` pieces and to draw single stones and a squircle pentagon; `.pebblesCard()` and `.pebblesBox()` name the card and the box; `PebblesSectionHead` and `PebblesLinkRow` are the two shared rows. Home reads the live month through `PathWrapService` and opens a week on the Path through a new `TabIntent.focusWeek`. You becomes `ProfileView` (routes, sheet, intents) around a pure `YouContent` fed by `YouModel`. One back-end PR adds two glyph counts to `get_profile_engagement`.

**Tech stack:** SwiftUI (iOS 17+), Swift Testing, swiftlint, the CI-recorded reference images in `apps/ios/PebblesTests/References/`; Postgres + Deno harness in `packages/supabase` for part B.

**Stack (gh stack, seven parts = seven PRs, bottom first; part B is its own PR on `main`):**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 1 | `feat/1382-inclusive-sans-ios` | `feat(ios): set every rounded token in inclusive sans` | 1–5 |
| 2 | `chore/1382-stones-move-ios` | `chore(ios): move the split-stone geometry out of home` | 6–7 |
| 3 | `feat/1382-surface-kit-ios` | `feat(ios): add the stones, cards, heads and ranks home and you share` | 8–12 |
| 4 | `feat/1382-home-head-ios` | `feat(ios): open home on the month's weeks, the greeting and the cut quick actions` | 13–17 |
| 5 | `feat/1382-home-body-ios` | `feat(ios): box the first five, card the wraps and list about pebbles` | 18–21 |
| B | `feat/<B>-profile-glyph-counts` | `feat(db): count carved and owned glyphs in get_profile_engagement` | B1–B3 |
| 6 | `feat/1382-you-header-ios` | `feat(ios): draw you's header, counters and glyphs` | 22–27 |
| 7 | `feat/1382-you-achievements-lab-ios` | `feat(ios): card the achievements and bring the lab onto you` (`Resolves #1382`) | 28–33 |

`<B>` is the back-end issue, filed at the start under M67 with labels `feat`, `db`, `supabase` (one back-end issue for the three clients, per root CLAUDE.md). Labels on the iOS parts: `feat`, `ui`, `ios` (Part 2: `chore`, `ios`). Milestone: `M67 · Home & You`. PR bodies for Parts 1–6 say `Part N of #1382`, never `Resolves`; Part 7 says `Resolves #1382`. Lab Note: one, on Part 7, written with the repo `lab-note` skill; Parts 1–6 carry `no-lab-note` (confirm with the maintainer when Part 1 opens: if the Arkaik coverage replaces the note, Part 7 takes `no-lab-note` too). Never write a Kritik finding id in a body.

**Before Part 1:**
- Work in a worktree with the gitignored config copied in (`project_worktree_gitignored_config`). Branch from an updated `main`: `git fetch origin && git switch -c feat/1382-inclusive-sans-ios origin/main`.
- Arkaik: move the Home and You view nodes (and their acceptances) to `development` with `arkaik-mcp` `update_node` (send `gherkin` and `values` back: `update_node` replaces metadata wholesale). If the `arkaik-mcp` tools are not available, say so and stop.
- Copy the fonts from the maintainer's originals (`~/Downloads/Inclusive Sans (1)/`) or the exploration folder; check the sizes: 112 064 and 114 308 bytes.
- Own simulator and derived data for this stack (`project_ios_parallel_simulators`): `xcrun simctl create "iPhone 17 home-you" "iPhone 17"`.

**The iOS gate, run at the end of every part** (from `apps/ios`):

```bash
xcodegen generate
swiftlint 2>&1 | tail -5                   # zero errors
xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17 home-you' \
  -derivedDataPath "$HOME/Library/Developer/Xcode/DerivedData/pbbls-home-you" \
  -skip-testing:PebblesTests/References 2>&1 | grep -E "Test Suite|passed|failed|error:" | tail -20
```

Expected: `** TEST SUCCEEDED **`, zero `error:` lines. References are skipped locally on purpose (`project_ios_local_reference_baseline`); they are recorded on CI.

**Re-baselining a part:** after the part's last code commit is pushed and its iOS run is red only on `References`, open the run's failure list and check each red name against the spec's reference table (a name outside the "re-baselined" or "new" column is a bug, not a re-baseline). Then `gh pr edit <n> --add-label rebaseline-ios-screenshots` (the label alone). The bot pushes `test(ios): re-baseline reference images`. Before any further stack command: `git fetch origin && git branch -f <branch> origin/<branch>`, `gh stack rebase --no-trunk` from the part above, `git diff --stat origin/<branch> <branch>` empty, `gh stack push`; approve any run left at `action_required` (`gh api -X POST repos/alexisbohns/pbbls/actions/runs/<id>/approve`). Put the label on a part only after the part below has its bot commit in.

**Merging:** the classifier blocks `gh stack merge` and stack pushes for the agent; hand the maintainer the command.

---

## Part 1 — Inclusive Sans and the tokens (`feat/1382-inclusive-sans-ios`)

### Task 1: Bundle the font and its licence

**Files:**
- Create: `apps/ios/Pebbles/Resources/InclusiveSans-VariableFont_wght.ttf`, `apps/ios/Pebbles/Resources/InclusiveSans-Italic-VariableFont_wght.ttf`
- Create: `apps/ios/Pebbles/Resources/Licenses/InclusiveSans-OFL.txt` (the maintainer's `OFL.txt`, byte for byte)
- Modify: `apps/ios/Pebbles/Resources/Info.plist` (`UIAppFonts`)
- Create: `apps/ios/PebblesTests/InclusiveSansFontTests.swift`

- [ ] **Step 1: Write the failing test** (the `ReenieBeanieFontTests` shape):

```swift
import Testing
import UIKit

/// The body face (#1382 D2). A wrong PostScript name or an unbundled file falls
/// back to the system font with no build error, so the bundle and the names are proved.
@Suite struct InclusiveSansFontTests {
    @Test("both variable files and the OFL are bundled",
          arguments: [("InclusiveSans-VariableFont_wght", "ttf"), ("InclusiveSans-Italic-VariableFont_wght", "ttf"),
                      ("InclusiveSans-OFL", "txt")])
    func bundled(name: String, ext: String) {
        #expect(Bundle.main.url(forResource: name, withExtension: ext) != nil, "missing \(name).\(ext)")
    }

    @Test("the roman and the italic resolve", arguments: ["InclusiveSans-Regular", "InclusiveSans-Italic"])
    func resolves(name: String) {
        #expect(UIFont(name: name, size: 17)?.familyName == "Inclusive Sans")
    }
}
```

- [ ] **Step 2: Run it to see it fail** (gate command with `-only-testing:PebblesTests/InclusiveSansFontTests`). Expected: four failures.
- [ ] **Step 3: Copy the three files**, then add to `UIAppFonts`, after `Caveat-VariableFont_wght.ttf`:

```xml
		<string>InclusiveSans-VariableFont_wght.ttf</string>
		<string>InclusiveSans-Italic-VariableFont_wght.ttf</string>
```

No `project.yml` edit (spec D2): `- path: Pebbles` already copies `.ttf` and `.txt` as resources. Run `xcodegen generate`; the test passes. If the `.txt` is not in the bundle, add it under the target's `sources` as `- path: Pebbles/Resources/Licenses/InclusiveSans-OFL.txt` with `buildPhase: resources` (the privacy-manifest precedent) and say so in the PR.
- [ ] **Step 4: Commit**: `git add apps/ios/Pebbles/Resources apps/ios/PebblesTests/InclusiveSansFontTests.swift && git commit -m "feat(ios): bundle inclusive sans and its licence"`

### Task 2: `InclusiveFace` and the `.inclusive` Spine face

**Files:**
- Create: `apps/ios/Pebbles/Theme/InclusiveFace.swift`
- Modify: `apps/ios/Pebbles/Theme/SpineType.swift` (`Face`, `Style.isUppercase`)
- Modify: `apps/ios/Pebbles/Theme/SpineTypeModifier.swift` (`resolve`, the modifier's `textCase`)
- Modify: `apps/ios/PebblesTests/SpineTypeResolveTests.swift`, `apps/ios/PebblesTests/InclusiveSansFontTests.swift`

- [ ] **Step 1: Failing tests.** In `InclusiveSansFontTests`: 300, 400, 600 and 700 from `InclusiveFace.uiFont(size: 17, weight:, italic: false)` give four strictly growing ink widths of `"l"` and `CTProbe.wght` equal to the asked weight (the `YsabeauFaceTests` probe, `CTProbe` is shared). In `SpineTypeResolveTests`: an `.inclusive` style at 600 resolves to 700 under `legibilityWeight: .bold` and a 700 one stays 700; an `isUppercase` style is resolved unchanged (the case is the modifier's).
- [ ] **Step 2: Implement.** `InclusiveFace` reuses the Ysabeau builder, which already takes the face name (no refactor of `YsabeauFace`):

```swift
/// The variable Inclusive Sans at an exact `wght` (#1382 D1, D2). Both bundled
/// files carry one axis, 300 to 700, default 400; `UIFont(name:)` resolves only
/// the default instance, so the weight goes through the variation attribute, as
/// for Ysabeau, whose memoised builder this calls with Inclusive's names.
/// No OpenType feature is asked: figures are always Ysabeau (spec D22).
enum InclusiveFace {
    static let romanName = "InclusiveSans-Regular"
    static let italicName = "InclusiveSans-Italic"

    static func uiFont(size: CGFloat, weight: Int, italic: Bool) -> UIFont {
        YsabeauFace.uiFont(size: size, weight: weight, italic: italic, features: [],
                           faceName: italic ? italicName : romanName)
    }
}
```

`SpineType.Face` gains `case inclusive` with `maxWeight` 700; `Style` gains `var isUppercase: Bool = false` beside `isLowercase`; `resolve` gains `case .inclusive: uiFont = InclusiveFace.uiFont(size: size, weight: weight, italic: style.italic)`; the modifier's case becomes `.textCase(style.isLowercase ? .lowercase : style.isUppercase ? .uppercase : nil)`. Update `YsabeauFace`'s doc comment in one line: its builder serves Inclusive Sans too.
- [ ] **Step 3: Gate the two suites; commit**: `git commit -am "feat(ios): add inclusive sans as a spine face"` (add the new file first).

### Task 3: The token table

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Font+Pebbles.swift`
- Create: `apps/ios/PebblesTests/Theme/FontTokensTests.swift`

- [ ] **Step 1: Failing tests** in `FontTokensTests`:
  - `@Test(arguments: PebblesFont.allCases)` (add `CaseIterable` to the enum): the token's resolved `UIFont.familyName` at `.large` is in `["Inclusive Sans", "Ysabeau", "Reenie Beanie", "Caveat"]`.
  - the spec D3 table as rows `(token, size, weight, trackingEm, uppercase)` for the ten Inclusive tokens; each token's `inclusiveStyle` matches its row.
  - `.body` resolves to 17 at `.large` and to the same size at `.accessibility3` as at `.accessibility2` (the ceiling).
- [ ] **Step 2: Implement.** Replace the ten SF cases of `font` with a style table; the modifier picks the path:

```swift
extension PebblesFont {
    /// The Inclusive Sans tokens (spec D3) as Spine ranks: scaled by the resolver,
    /// thickened by Bold Text. Nil for the Ysabeau and handwritten tokens.
    var inclusiveStyle: SpineType.Style? {
        switch self {
        case .body:                  .inclusive(17, 400)
        case .bodyEmphasized:        .inclusive(17, 600)
        case .subhead:               .inclusive(15, 400)
        case .subheadEmphasized:     .inclusive(15, 600)
        case .headline:              .inclusive(17, 600)
        case .headlineEmphasized:    .inclusive(17, 700)
        case .callout:               .inclusive(16, 500)
        case .calloutEmphasized:     .inclusive(16, 600)
        case .meta:                  .inclusive(12, 500, caps: true)
        case .metaEmphasized:        .inclusive(12, 700, caps: true)
        case .cardHeading:           .inclusive(15, 600, caps: true)
        case .cardHeadingEmphasized: .inclusive(15, 700, caps: true)
        case .counterLg:             .inclusive(17, 600)
        case .captionEmphasized:     .inclusive(12, 600)
        default:                     nil
        }
    }
}

extension SpineType.Style {
    /// An Inclusive Sans rank at `size` / `weight`, scaling with its size band;
    /// capitals carry 0.08 em.
    static func inclusive(_ size: CGFloat, _ weight: Int, caps: Bool = false) -> Self {
        Self(size: size, weight: weight, trackingEm: caps ? 0.08 : 0,
             scaling: .text(SpineType.textStyle(forSize: size)), face: .inclusive, isUppercase: caps)
    }
}
```

`PebblesFontModifier.body` applies `content.spineType(style)` when `token.inclusiveStyle` is set, else the existing `font` / `tracking` / `textCase` path (now covering only `title`, `buttonLabel` and the hand tokens). Delete `sfProRounded`, `sfCompactRounded` and their cases in `tracking` / `isUppercase`. The `PebblesList` doc comment that names "SF Compact Rounded 15 semibold" now says "Inclusive Sans 15 / 600, capitals".
- [ ] **Step 3: Build.** Every caller compiles unchanged (the token names stay). Gate.
- [ ] **Step 4: Commit**: `git commit -am "feat(ios): set the rounded tokens in inclusive sans"`

### Task 4: The rounded rule and the three inline sites

**Files:**
- Modify: `apps/ios/PebblesTests/ThemeLiteralsTests.swift`
- Modify: `apps/ios/Pebbles/Features/Achievements/Sheets/SheetParts.swift:189`, `apps/ios/Pebbles/Features/Profile/Components/ProfileAchievementsCard.swift:71`, `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift:18`

- [ ] **Step 1: Add the rule** at the end of `rules`, and its cases to the `rule(line:offends:)` arguments:

```swift
        Rule(#"design:\s*\.rounded|fontDesign\(\s*\.rounded|withDesign\(\s*\.rounded"#,
             "SF Pro Rounded is retired: use pebblesFont or spineType")
```

Cases: `(".font(.system(size: 15, weight: .medium, design: .rounded))", true)`, `(".fontDesign(.rounded)", true)`, `("base.fontDescriptor.withDesign(.rounded)", true)`, `(".clipShape(RoundedRectangle(cornerRadius: 8))", false)`, `("let n = (x * 4).rounded()", false)`.
- [ ] **Step 2: Run the ratchet**: it fails on the three sites.
- [ ] **Step 3: Fix them** with plain SF, the chrome face: `SheetParts` `.font(.subheadline.weight(.medium))`; the two Profile chevrons `.font(.system(size: 15, weight: .medium))`. Ratchet green, no allowlist entry.
- [ ] **Step 4: Commit**: `git commit -am "test(ios): retire the rounded design with a theme rule"`

### Task 5: Part 1 gate, device pass, follow-up, PR

- [ ] **Step 1:** Gate. Build to the maintainer's iPhone (`project_ios_device_deploy`) and look at Record, Complete, Connections, Settings and a navigation title at default size and at AX2 (Settings → Accessibility → Larger Text). A frame that clips at AX2 (spec R2) gets `.dynamicTypeSize(...DynamicTypeSize.xxxLarge)` on that view, in this part, with a one-line comment; list each in the PR body.
- [ ] **Step 2:** File the follow-up issue for D5: `[Quality] iOS: move the plain SF text styles to ranks` (labels `quality`, `ios`, `ui`, milestone M67), listing the 55 files (`grep -rlE '\.font\(\.(largeTitle|title|title2|title3|headline|subheadline|body|callout|footnote|caption|caption2|system)' apps/ios/Pebbles`).
- [ ] **Step 3:** `gh stack init` on this branch, push, open the PR (`Part 1 of #1382`; body: the D3 table, the AX2 caps added, the follow-up issue). Re-baseline per the spec table.

---

## Part 2 — move the stone geometry (`chore/1382-stones-move-ios`)

### Task 6: Pure move

**Files:**
- Create: `apps/ios/Pebbles/Features/Shared/Stones/StoneGeometry.swift`
- Modify: `apps/ios/Pebbles/Features/Home/Shortcuts/ShortcutStones.swift`

- [ ] **Step 1:** Move, byte for byte, from `ShortcutStones` into `enum StoneGeometry`: `rockOutline`, `polygonSdf`, `halfSpace`, `roundIntersection`, `piece`, `jitter`, `superellipse`, `fit`, `resample`, `normals`, `path(_:)`, and the `StoneRandom` struct (its own file-level type, moved under the enum's file). Widen only `private` to `internal` where `ShortcutStones` now calls across files. `ShortcutStones` keeps `gutter`, `row`, `bento`, `rowCells`, `bentoCells`, `splitRow`, `splitBento`, `clip`, `smooth` and its cache, calling `StoneGeometry.*`. `ShortcutStones.path` and `resample` stay as one-line forwards so no caller changes.
- [ ] **Step 2:** `git diff --stat` shows only the two files; `git diff -M` reads as a move. Gate (`HomeShortcutsTests` unchanged and green).
- [ ] **Step 3: Commit**: `git add -A apps/ios/Pebbles/Features && git commit -m "chore(ios): move the split-stone geometry to shared stones"`

### Task 7: Part 2 gate, PR

- [ ] Gate; push; PR `Part 2 of #1382`. Its iOS run must be green with **no** reference change; a red reference means the move changed a number, so fix the move, never re-baseline.

---

## Part 3 — the surface kit (`feat/1382-surface-kit-ios`)

### Task 8: `split(count:)` with a golden row

**Files:**
- Create: `apps/ios/PebblesTests/Stones/StoneGeometryTests.swift`, `apps/ios/PebblesTests/Stones/Fixtures/row-358x100.json`
- Modify: `apps/ios/Pebbles/Features/Shared/Stones/StoneGeometry.swift`, `apps/ios/Pebbles/Features/Home/Shortcuts/ShortcutStones.swift`
- Modify: `apps/ios/project.yml` (exclude `Stones/Fixtures/**` from the test sources, as the Spine and Achievements fixtures are)

- [ ] **Step 1: Record the golden before changing anything.** A test `rowMatchesGolden` that, with `PBBLS_RECORD_STONES=1`, writes `ShortcutStones.splitRow(width: 358, height: 100)` as `[[[x, y]]]` (rounded to 0.001) to the fixture through `#filePath`, and otherwise compares to it point by point within 0.001. Run once with the variable set, commit the fixture: `git commit -m "test(ios): pin the quick actions rock"`.
- [ ] **Step 2: Failing tests** for the generalisation: `StoneGeometry.split(size:count:seed:options:)` returns `count` closed outlines for 2, 3 and 5; every point lies inside the box (±0.5); neighbouring outlines keep at least `gutter − 0.5` apart along the cut; the same seed gives the same outlines twice.
- [ ] **Step 3: Implement** the kit's `split` (spec D6): `struct StoneOptions { var corner: CGFloat = 34; var bow: CGFloat = 1; var lean: CGFloat? = nil; var gutter: CGFloat = 8; var fillet: CGFloat = 20 }`; `wn = (w − (n − 1)·gutter) / n`, cut lines at `(k + 1)·wn + (k + 0.5)·gutter`, one half-space pair per inner piece, as `splitRow` does for three. `rockOutline` takes `corner` and `bow` (defaults reproduce today's draws exactly: the draw order of `StoneRandom` must not change). `ShortcutStones.splitRow` becomes `StoneGeometry.split(size:count: 3, seed: "camera|fixed|rock|q11", options: .init())`.
- [ ] **Step 4:** The golden test still passes, unchanged. Gate. Commit: `git commit -am "feat(ios): cut a stone into any number of pieces"`

### Task 9: Single stones, the split view, the pentagon

**Files:**
- Modify: `apps/ios/Pebbles/Features/Shared/Stones/StoneGeometry.swift` (`single`)
- Create: `apps/ios/Pebbles/Features/Shared/Stones/StoneShape.swift`, `SplitStone.swift`, `SquirclePentagon.swift`
- Modify: `apps/ios/PebblesTests/Stones/StoneGeometryTests.swift`

- [ ] **Step 1: Failing tests:** `single(size:seed:options:)` fills its box (bounds within 0.5 of the size); `SquirclePentagon().path(in: 128×122)` has its top vertex region at y ≈ 2 ± 1 and is symmetric about x = 64 within 0.5.
- [ ] **Step 2: Implement.** `single` is `fit(rockOutline(...), in: rect)` with the kit's `d()` defaults. `StoneShape: Shape { let seed: String; var options = StoneOptions() }` caches by `(seed, size)` like `ShortcutStones`. `SplitStone<Cell: View>: View` lays out `count` equal cells (`ShortcutStones.rowCells` generalised) over a `GeometryReader` background of the pieces, fills from a `[Color]`, hides the shapes from accessibility, and exposes `minHeight`. `SquirclePentagon: Shape` ports c3's `pentagon()` (R 64, centre (64, 66) in a 128 × 122 design box scaled to the rect, `t` 0.28, `k` 0.04, bow 2 pt).
- [ ] **Step 3:** Gate. Commit: `git commit -m "feat(ios): add single stones, a split stone view and the squircle pentagon"`

### Task 10: The card and the box

**Files:**
- Create: `apps/ios/Pebbles/Theme/PebblesCard.swift`

- [ ] **Step 1: Implement** `.pebblesCard(radius: CGFloat = 18)` and `.pebblesBox()` (spec D7). The card's ground follows `SpineCardShell.ground`: a `ZStack` of the shape filled with `SpineColors.shadowInk(scheme:)` at each layer's alpha, offset by `dy`, blurred at `blur / 2`; on Night a `Color.pebbles.rule` stroke of 1 pt and one layer at 0.35; then `Color.pebbles.stock`; the whole ground hidden from accessibility. Layers as constants on a `PebblesCardShadow` enum so a test can read them. The box: `padding(EdgeInsets(top: 24, leading: 18, bottom: 10, trailing: 16))` over `Color.pebbles.surfaceContainer` in a 26 pt continuous rounded rectangle.
- [ ] **Step 2:** `ThemeLiteralsTests` green (roles only). Commit: `git commit -m "feat(ios): name the card and the box"`

### Task 11: The ranks, the section head, the link row

**Files:**
- Modify: `apps/ios/Pebbles/Theme/SpineType.swift` (a `// MARK: - Home and You (#1382)` block)
- Modify: `apps/ios/PebblesTests/SpineTypeTests.swift` (the rank table rows)
- Create: `apps/ios/Pebbles/Features/Shared/Sections/PebblesSectionHead.swift`, `PebblesLinkRow.swift`

- [ ] **Step 1: Failing tests:** add the 24 rows of spec D22 to `SpineTypeTests.rows` (name, size, weight, tracking, italic, features, face). The table's existing rules (no italic with small caps, bands) then cover them.
- [ ] **Step 2: Implement the ranks**, Ysabeau ones with `.lining` where the spec says lining and no feature where it says old-style; Inclusive ones through `.inclusive(_:_:)`. Line heights: `greeting` 41.6, `actionLabel` 17.25, `rowLine` 21, others natural.
- [ ] **Step 3: `PebblesSectionHead`**: `init(_ title: String, figure: Figure? = nil, action: (() -> Void)? = nil)` with `enum Figure { case count(Int); case ratio(done: Int, total: Int, word: String); case counted(Int, word: String) }`. The title in `sectionTitle` `onSurface` with `.isHeader`; the figure on the first baseline, numbers in `sectionFigure` `primary`, the word in `sectionFigureWord` `onSurfaceVariant` with 5 / 6 pt either side; the element's label joins title and figure. With `action`, the whole head is a plain button.
- [ ] **Step 4: `PebblesLinkRow`**: `init(title: String, line: String, chip: Chip, done: Bool = false)` where `Chip` is `.icon(PbblsIcon)` (26 in `primary`) or `.view(AnyView)`; the chip sits on a `StoneShape(seed: "chip-" + title)` 48 × 46 in `surfaceContainer`; title `rowTitle` / `rowTitleDone`, line `rowLine`; an SF `chevron.right` at 13 semibold in `outline` unless `done`; `padding(.vertical, 10)`, one combined element. Used inside a `NavigationLink` or `Button` by the caller.
- [ ] **Step 5:** Gate. Commit: `git commit -m "feat(ios): add the home and you ranks, section head and link row"`

### Task 12: Kit references, gate, PR

**Files:**
- Create: `apps/ios/PebblesTests/References/SurfaceKitReferences.swift`

- [ ] **Step 1:** One suite drawing on `surface`, both schemes, English (French where text shows): the split stone at 3 (quick actions seed, 358 × 100), at 3 (`days|pebbles|karma`, 358 × 88) and at 5 (`first-five`, 324 × 6); three chips; the settings stone; the community stone (corner 30); the pentagon with the carve mark; a card holding text; the box; the head plain, with `.ratio(2, 5, "of")` and with `.counted(12, "earned")`; a link row, done and not.
- [ ] **Step 2:** Gate; push; PR `Part 3 of #1382`; record the new references with the label (they are new, nothing else may move).

---

## Part 4 — Home's head (`feat/1382-home-head-ios`)

### Task 13: A week opens on the Path

**Files:**
- Modify: `apps/ios/Pebbles/Features/Tabs/TabRoutes.swift` (`TabIntent`)
- Modify: `apps/ios/Pebbles/Features/Path/PathModel.swift`, `apps/ios/Pebbles/Features/Path/PathView.swift`
- Modify: `apps/ios/Pebbles/Features/Home/HomeView.swift` (the wrap sheet's `onWeek`)
- Modify: `apps/ios/PebblesTests/Spine/PathModelTests.swift`, `apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift`

- [ ] **Step 1: Failing tests.** `PathModelTests`: after a load, `focus(weekStarting: CalendarDay)` focuses `.week(start)` for a week in `pages`, and focuses the current week for one that is not. `TabNavigationTests`: `go(.path, intent: .focusWeek(day))` selects Path and `takeIntent(for: .path)` returns the intent once.
- [ ] **Step 2: Implement.** `TabIntent` gains `case focusWeek(CalendarDay)` (`CalendarDay` is `Equatable`). `PathModel`:

```swift
    /// Home's week columns (#1382 D9): the Monday's page, or now when the Path has none.
    func focus(weekStarting day: CalendarDay) {
        guard let start = day.date(in: calendar.timeZone), pages.contains(.week(start)) else {
            focusCurrentWeek()
            return
        }
        focus(.week(start))
    }
```

`PathView` takes the intent when it appears and again when its pages first land (`.onChange(of: model.pages.isEmpty)`), with `peekIntent` until then so a cold Path does not drop it:

```swift
    private func takeWeekIntent() {
        guard case let .focusWeek(day)? = nav.peekIntent(for: .path), !model.pages.isEmpty else { return }
        _ = nav.takeIntent(for: .path)
        model.focus(weekStarting: day)
    }
```

`HomeView`'s wrap sheet: `onWeek: { nav.go(.path, intent: .focusWeek($0)) }` (it drops the week today).
- [ ] **Step 3:** Gate. Commit: `git commit -am "feat(ios): open a week on the path from home"`

### Task 14: `WeeksColumns` learns Home's metrics

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Wrap/Month/WeeksColumns.swift`
- Create: `apps/ios/PebblesTests/Wrap/WeeksColumnsMetricsTests.swift`

- [ ] **Step 1: Failing tests:** `WeeksColumns.Metrics.card` equals today's constants (label 34, gap 8, cairnRoom 64, cellCap 40, legend 16, pipCap 16, sidePadding `MonthLayout.sectionPadX`, fixed cell nil); `.home` is label 34, gap 8, cairnRoom 46, legend 16, fixed cell 28, pip 15, side padding 0; `Metrics.home.height` is 292.
- [ ] **Step 2: Implement.** `struct Metrics: Equatable` with the constants above and `static let card`, `static let home`; `WeeksColumns` takes `var metrics: Metrics = .card` and `var opens: (MonthWeek) -> Bool = { _ in true }`. The cell is `metrics.cell ?? min(max(...), metrics.cellCap)`; the pip is `metrics.pip ?? min(cell * 0.5, ..., metrics.pipCap)`; the frame height and cairn frame read `metrics`. A week for which `opens` is false draws its column without the `Button` and without the button trait. The card path is arithmetic-identical: no `WrapMonthReferences` or `WrapIntroReferences` PNG may move.
- [ ] **Step 3:** Gate. Commit: `git commit -am "feat(ios): let the week columns draw at home's size"`

### Task 15: Home loads the month and draws the grid

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/HomeService.swift` (`HomeServicing.month`, `.cachedMonth`)
- Modify: `apps/ios/Pebbles/Features/Home/HomeModel.swift`, `HomeView.swift`, `HomeContent.swift`
- Create: `apps/ios/Pebbles/Features/Home/Month/HomeMonthGrid.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Spine/Debug/HomeFixture.swift`, `apps/ios/PebblesTests/Features/Home/FakeHomeService.swift`, `HomeModelTests.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings` (`home.month.weekHint`)

- [ ] **Step 1: Failing tests** in `HomeModelTests`: `loadMonth(_:)` sets `month` from the kept copy, then from the fetch; a failing fetch keeps the last month and logs; a different `CalendarMonth` replaces it.
- [ ] **Step 2: Implement.** `HomeServicing` gains `func month(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap` and `func cachedMonth(_:zone:) async -> MonthWrap?`; `HomeService` holds a `PathWrapService(client:disk: .standard, owner:)` and forwards (so the disk copy is the Path's). `HomeModel` gains `private(set) var month: MonthWrap?` and `loadMonth(_ live: CalendarMonth)`, timed with `ContinuousClock` and logged at `.info` under `home.month` (spec D8's measurement). `HomeView` runs `.task(id: liveMonth) { await model.loadMonth(liveMonth) }` and reloads the month beside `reloadChecklist()` on every revision bump.
- [ ] **Step 3: `HomeMonthGrid`**: `WeeksColumns(wrap:lookups:room:metrics: .home, opens:onWeek:)` inside a fixed `frame(height: Metrics.home.height)`, `.dynamicTypeSize(...DynamicTypeSize.large)`, `opens` true for weeks whose `weekStart` is on or before today in `SpineCalendar.current`; with no month yet it is the empty frame. Accessibility: `.accessibilityElement(children: .contain)` labelled "\(WrapCopy.monthName) , \(WrapCopy.line(...))", each opening week with `.accessibilityHint(Text("home.month.weekHint"))` (EN "Opens this week on the Path", FR "Ouvre cette semaine sur le Chemin").
- [ ] **Step 4: Fixture.** `HomeFixture.clock` moves to `2026-10-31T19:30:00+01:00` (Saturday evening, as c3), `liveMonth` to October 2026, and gains `static var month: MonthWrap? { SpineFixtureMonth.october }`; `HomeFixtureTests` follows. `HomeContent` takes `month: MonthWrap?` and draws `HomeMonthGrid` first (`onWeek` → `nav.go(.path, intent: .focusWeek(day))` from `HomeView`).
- [ ] **Step 5:** Gate. Commit: `git commit -am "feat(ios): draw the month's weeks at the top of home"`

### Task 16: The greeting

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/HomeView.swift` (`HomeGreetingView`)
- Modify: `apps/ios/PebblesTests/References/HomeReferences.swift` (`greetingLongFr`)

- [ ] **Step 1:** `HomeGreetingView`: dateline `homeDateline` in `primary`; title `greeting` in `onSurface`, `lineLimit(1)` and `minimumScaleFactor(0.75)` below accessibility sizes, `lineLimit(2)` at them; line `greetingLine` in `onSurfaceVariant`. Spacing 8 / 8, 22 below the grid, 26 above the quick actions. The accessibility label is unchanged.
- [ ] **Step 2:** Reference `greetingLongFr`: the evening greeting with the name "Alexandrine" in French, Paper, at 358 pt.
- [ ] **Step 3:** Commit: `git commit -am "feat(ios): set the home greeting in ysabeau's display cut"`

### Task 17: The quick actions; gate; PR

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/Shortcuts/HomeShortcuts.swift`
- Modify: `apps/ios/PebblesTests/HomeShortcutsTests.swift`

- [ ] **Step 1:** Colours (spec D11): `ink` is `Color.pebbles.onPrimaryContainer`, `field` is `Color.pebbles.primaryContainer`; the `colorScheme` environment read goes. The doc comment says "on the accent surface (`primaryContainer`)".
- [ ] **Step 2:** `rowContent`: `VStack(alignment: .leading)` with the icon at `.tile` on top, a `Spacer`, the label (`actionLabel`, two lines, leading) at the bottom; padding 15 / 12 / 14 / 16, the second and third cells 20 on the leading edge; `rowMinHeight` 100. `label` uses `.spineType(.actionLabel)`. The bento keeps its layout, with the new colours and rank.
- [ ] **Step 3:** `HomeShortcutsTests`: the row's minimum height is 100; the stones path still reads `ShortcutStones.row`.
- [ ] **Step 4:** Gate; push; PR `Part 4 of #1382` (body: D8's measurement plan, D9 and the wrap sheet fix). Re-baseline per the spec table (`greeting`, `shortcuts*`, `page*`; new `monthGrid`, `greetingLongFr`).

---

## Part 5 — Home's body (`feat/1382-home-body-ios`)

### Task 18: The order and the heads

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/HomeContent.swift`

- [ ] **Step 1:** Order: month grid, greeting, quick actions (one header group), the first five, the month wraps, About, then the camera states. Spacing between sections 44 (`HomeLayout.sectionGap`, a constant in this file); the page keeps `Spacing.lg` sides.
- [ ] **Step 2:** Commit: `git commit -am "feat(ios): put the first five before the wraps on home"`

### Task 19: The first five

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/Checklist/HomeChecklistView.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings` (`home.firstFive.of`, `home.firstFive.a11y`)
- Modify: `apps/ios/PebblesTests/Features/Home/HomeChecklistTests.swift`

- [ ] **Step 1:** Head: `PebblesSectionHead(title, figure: .ratio(done: doneCount, total: 5, word: String(localized: "home.firstFive.of")))` with the accessibility label from `home.firstFive.a11y` ("%lld of 5 done" / "%lld sur 5 faites"). The five dots go.
- [ ] **Step 2:** Under the head, `SplitStone(count: 5, seed: "first-five", options: StoneOptions(corner: 3, lean: 0.9, gutter: 5, fillet: 2), fills: …)` 6 pt tall, done pieces `primary`, others `surfaceContainerHighest`, hidden from VoiceOver; 2 above, 8 below.
- [ ] **Step 3:** Rows: the dividers go; the title takes `rowTitle` / `rowTitleDone`, the line `rowLine` (`outline` when done); the done mark's ink becomes `primary`; padding horizontal 0 (the box pads), vertical 13, gap 12. The claim row stays the sixth row with its primary button. The whole is wrapped in `.pebblesBox()`; the hairline card and its overlay go. `PeopleOnboardingView`'s comment that points at "the hairline card `HomeChecklistView` draws" now names the card it still draws itself.
- [ ] **Step 4:** Test: a checklist with two done rows renders the head figure's accessibility label "2 of 5 done" (resolve the key through `SpineStrings` in both languages).
- [ ] **Step 5:** Commit: `git commit -am "feat(ios): box the first five with a cut-stone stepper"`

### Task 20: The month wraps as cards

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/Wraps/HomeWrapsRow.swift`, `HomeWrapCard.swift`

- [ ] **Step 1:** `HomeWrapsRow`: `PebblesSectionHead(title, figure: months.isEmpty ? nil : .count(months.count))`; the scroll view's content gets `padding(.bottom, 22)` and the row `padding(.bottom, -22)` so the shadow is not clipped; card spacing 12.
- [ ] **Step 2:** `HomeWrapCard`: width 152, padding 18 / 16 / 18 / 18, `.pebblesCard()`; the garland at 116 (`scaleEffect(116 / 262)`), the count in `wrapCount`; the month in `wrapMonth`, the year in `wrapYear` (hidden at accessibility sizes, as today), both leading. The `surfaceContainer` fill and the `cardRule` stroke go; the folded corner stays, clipped to the card's 18 pt shape. `HomeWrapPromise` keeps its dashed outline at the new height.
- [ ] **Step 3:** Commit: `git commit -am "feat(ios): draw the month wraps as cards"`

### Task 21: About Pebbles; gate; PR

**Files:**
- Modify: `apps/ios/Pebbles/Features/Home/Guides/GuidesList.swift`

- [ ] **Step 1:** Head `PebblesSectionHead(String(localized: "home.about.title"))` with 8 below; each guide a `NavigationLink(value: HomeRoute.guide(slug))` around `PebblesLinkRow(title:line:chip: .view(AnyView(GuideIllustrationView(kind:size: .mark))))`; the dividers and the italic summary go.
- [ ] **Step 2:** Gate; push; PR `Part 5 of #1382`; re-baseline per the spec table (`checklist*`, `wrapCards`, `wrapsEmpty`, `guidesList`, `page*`).

---

## Part B — the glyph counts (back-end issue `<B>`, own PR on `main`)

### Task B1: The migration

**Files:**
- Create: `packages/supabase/supabase/migrations/<timestamp>_profile_engagement_glyph_counts.sql` (`npm run db:migration:new --workspace=packages/supabase -- profile_engagement_glyph_counts`)

- [ ] **Step 1:** The body of `20260516104231_profile_glyph_and_engagement.sql`'s function, unchanged, plus two counts. The return type changes, so drop then create (`create or replace` cannot change `returns table`); the grant is re-issued:

```sql
-- #1382 D19: You's Glyphs rows count what the caller carved and owns.
-- returns table changes shape: create or replace cannot do that.
drop function if exists public.get_profile_engagement(text);

create function public.get_profile_engagement(p_tz text)
returns table (
  days_practiced int,
  assiduity      boolean[],
  glyphs_carved  int,
  glyphs_owned   int
)
language sql
security invoker
stable
set search_path = public
as $$
  with
    today_local as (select (now() at time zone p_tz)::date as d),
    window_days as (
      select generate_series(
        (select d from today_local) - interval '27 days',
        (select d from today_local),
        interval '1 day'
      )::date as d
    ),
    active_days as (
      select distinct (p.created_at at time zone p_tz)::date as d
        from public.pebbles p
       where p.user_id = auth.uid()
    ),
    grid as (
      select array_agg((ad.d is not null) order by w.d) as assiduity
        from window_days w
        left join active_days ad using (d)
    ),
    counts as (select count(*)::int as days_practiced from active_days),
    carved as (select count(*)::int as glyphs_carved from public.glyphs g where g.user_id = auth.uid()),
    owned  as (select count(*)::int as glyphs_owned from public.glyph_entitlements e where e.user_id = auth.uid())
  select counts.days_practiced, grid.assiduity, carved.glyphs_carved, owned.glyphs_owned
    from counts, grid, carved, owned;
$$;

grant execute on function public.get_profile_engagement(text) to authenticated;
```

- [ ] **Step 2:** Replay it on the native local Postgres (`project_local_postgres_for_migrations`), then hand the maintainer `npm run db:push --workspace=packages/supabase` (the classifier blocks it for the agent).

### Task B2: Types and the three readers

- [ ] **Step 1:** `npm run db:types:remote --workspace=packages/supabase` (not `db:types`, which targets Docker), commit `packages/supabase/types/database.ts`.
- [ ] **Step 2:** Web: `apps/web/lib/data/supabase-provider.ts` reads the row by name; `npm run lint --workspace=apps/web` and its Vitest stay green with the wider type.
- [ ] **Step 3:** Android: confirm that `decodeList<ProfileEngagement>()` ignores unknown keys (supabase-kt's `KotlinXSerializer` default `Json`); if it does not, the PR adds `ignoreUnknownKeys` on that decode before the push, because a shipped Android build would fail on the new columns. Note the answer in the PR body.

### Task B3: The harness; PR

**Files:**
- Modify: `packages/supabase/scripts/verify-home.ts` (section 5), its `Expected` count in the header
- Create (by `HOME_DUMP=1`): `packages/supabase/scripts/oracles/home/payloads/profile-engagement.json`

- [ ] **Step 1:** Section 5: a throwaway user sees `glyphs_carved = 0, glyphs_owned = 0`; after carving one glyph (the `glyphs` insert the clients make), `1, 0`; a second user's carve does not move the first user's count; `days_practiced` and the 28 `assiduity` booleans are unchanged in shape; anon is refused. With `HOME_DUMP=1` the authenticated row is written verbatim to the payload file.
- [ ] **Step 2:** `npm run db:verify:home --workspace=packages/supabase` green against the linked project; PR `feat(db): …` with `Resolves #<B>`, labels `feat`, `db`, `supabase`, M67. It merges before Part 6 opens.

---

## Part 6 — You's head (`feat/1382-you-header-ios`)

### Task 22: `YouModel`, `YouContent`, the shell

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/YouModel.swift`, `YouContent.swift`, `apps/ios/Pebbles/Features/Profile/Debug/YouFixture.swift` (`#if DEBUG`)
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- Create: `apps/ios/PebblesTests/Features/Profile/YouModelTests.swift`

- [ ] **Step 1: Failing tests:** `YouModel.loadProfile()` sets the name, `memberSince` and glyph strokes from a fake; a failed read logs and leaves them nil; a second call is a no-op once loaded (today's `hasLoadedProfile`).
- [ ] **Step 2: Implement.** `ProfileRow` and the two reads move from `ProfileView` into `YouModel` behind a `YouServicing` protocol (the `HomeServicing` pattern); stats stay in `PathStatsService` (read by `YouContent` through the model's `stats` snapshot so the fixture can supply them). `ProfileView` keeps `navigationDestination`, the Settings sheet (fed from `model.profile`), the carve intent and `.toolbar(.hidden, for: .navigationBar)`; its body is `ScrollView { YouContent(model:onSettings:) }`. The `pebblesToolbarTitle("Profile")` and the toolbar button go.
- [ ] **Step 3:** Commit: `git commit -am "feat(ios): split you into a shell and a testable page"`

### Task 23: The header

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Header/YouHeader.swift`
- Modify: `apps/ios/Pebbles/Features/Shared/Ripples/RippleBadge.swift`, `apps/ios/Pebbles/Theme/Font+Pebbles.swift` (`largeTitleHand`)
- Create: `apps/ios/PebblesTests/RippleBadgeTests.swift`

- [ ] **Step 1: Failing tests:** `RippleBadge.showsDigit(side:)` is true at 44 and 32, false at 22; `RippleBadge.strokeWidth(side:)` is 2 at 44 and 1.25 at 22.
- [ ] **Step 2:** `RippleBadge` gains `var side: CGFloat = 44` and the two static rules; the frame, the stroke and the digit read them. Every existing caller passes nothing and is unchanged.
- [ ] **Step 3:** `YouHeader`: the settings stone top-right (`StoneShape(seed: "settings")` 46 × 44 in `surfaceContainerHigh`, `slider` at `.toolbar` in `onSurfaceVariant`, a `Button` labelled "Settings", bottom margin −18 as c3); the `SquirclePentagon` 128 × 122 in `primaryContainer` holding `GlyphThumbnail(strokes:side: 58, strokeColor: onPrimaryContainer)` or `PbblsIconImage(.pebbleDottedGlyph, size: 58)` (8 pt lower, as c3), hidden from VoiceOver; the name `pebblesFont(.largeTitleHand)` with `largeTitleHand` retuned to Reenie Beanie 60, tracking −4.5 pt (−0.075 em), `lineLimit(1)`, `minimumScaleFactor(0.5)`, `.isHeader`, 14 above; "Member since" in `memberSince` (the existing `Member since %@` key, month and year), 8 above; the Ripples line `HStack(spacing: 7) { RippleBadge(level:activeToday:side: 22); Text("Ripples Level \(level)").spineType(.rowLabel) }` as one element labelled with the badge's own phrase, 14 above.
- [ ] **Step 4:** Commit: `git commit -am "feat(ios): crown you with the pentagon glyph and the hand name"`

### Task 24: The counters

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Header/YouCounters.swift`

- [ ] **Step 1:** `SplitStone(count: 3, seed: "days|pebbles|karma", fills: [surfaceContainer ×3], minHeight: 88)`; each cell a leading `VStack` (padding 12 / 10 / 12 / 16, the second and third 20 on the leading edge): `Text(value, format: .number)` (or "—") in `counterFigure` capped `.dynamicTypeSize(...DynamicTypeSize.xxxLarge)`, then `HStack(spacing: 6) { PbblsIconImage(icon, size: 18, style: primary); Text(label).spineType(.rowLine) }`; each cell `.accessibilityElement(children: .combine)`. Icons and labels as today (`calendarDate` Days, `pebbleRound` Pebbles, `karmaDot` Karma). 10 pt above (c3's `closer`).
- [ ] **Step 2:** Commit: `git commit -am "feat(ios): cut you's counters from one stone"`

### Task 25: The glyph counts reach iOS

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Models/ProfileEngagement.swift`, `apps/ios/Pebbles/Features/Path/Services/PathStatsService.swift`
- Modify: `apps/ios/PebblesTests/ProfileEngagementDecodingTests.swift`

- [ ] **Step 1: Failing tests:** decode B's `oracles/home/payloads/profile-engagement.json` verbatim (read through `#filePath`, the `HomePayloads` pattern) and expect both counts; decode the same row with the two keys removed and expect `nil` for both.
- [ ] **Step 2:** `ProfileEngagement` gains `let glyphsCarved: Int?` and `let glyphsOwned: Int?` (`decodeIfPresent`); `PathStatsService` gains the two optionals, set and reset like `daysPracticed`.
- [ ] **Step 3:** Commit: `git commit -am "feat(ios): read the carved and owned glyph counts"`

### Task 26: The Glyphs section

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sections/YouGlyphsSection.swift`
- Modify: `apps/ios/Pebbles/Features/Tabs/TabRoutes.swift` (`YouRoute.glyphTab`), `apps/ios/Pebbles/Features/Glyph/Models/GlyphMarketModels.swift` (`GlyphTab: Codable`), `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift` (`initialTab`), `ProfileView.swift` (destination)
- Modify: `Localizable.xcstrings` (the `you.glyphs.*` keys of the spec's French table)
- Modify: `apps/ios/PebblesTests/Features/Tabs/` (route round trip)

- [ ] **Step 1: Failing test:** a `[YouRoute]` holding `.glyphTab(.owned)` survives the snapshot encode / decode `TabNavigation` uses; an unknown tab falls back as the existing rule says.
- [ ] **Step 2:** `YouRoute` gains `case glyphTab(GlyphTab)`; `GlyphsListView(initialTab: GlyphTab = .mine)` seeds its `@State`; `ProfileView` maps `.glyphs` to `.mine` and `.glyphTab(t)` to `t`.
- [ ] **Step 3:** The section: `PebblesSectionHead(String(localized: "Glyphs"))` (8 below) and three `NavigationLink(value: .glyphTab(...))` rows with chips `drawDotted`, `pouch`, `petroglyph`. The lines pick, per row: the plural count key when the count is known and above 0, the `none` key at 0, the `plain` key when the count is nil.
- [ ] **Step 4:** Commit: `git commit -am "feat(ios): list my, owned and community glyphs on you"`

### Task 27: Log out to Settings, deletions, references, gate, PR

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` (Account section)
- Delete: `Profile/Components/ProfileBanner.swift`, `ProfileStatsCard.swift`, `ProfileCountersRow.swift`, `DataTile.swift`, `RipplesRow.swift`, `AssiduityGrid.swift`, `ProfileShortcutsRow.swift`, `ProfileShortcutTile.swift`, `ProfileLogoutButton.swift`; `apps/ios/PebblesTests/AssiduityGridLayoutTests.swift`
- Modify: `Font+Pebbles.swift` (delete `counterLg`), the doc comments that name the deleted types (`SurfaceTile`, `Icon+Pebbles.swift`, `GlyphBanner`)
- Create: `apps/ios/PebblesTests/References/YouReferences.swift`

- [ ] **Step 1:** Settings' Account section: a "Log out" row (`Label { Text("Log out") } icon: { PbblsIconImage(.remove, .row) }`, `pebblesListRow(position: .top)`) above "Delete account" (now `.bottom`), which dismisses and calls `supabase.signOut()`.
- [ ] **Step 2:** Delete the files; remove the catalog keys that lost their last caller (spec French section); `grep -rn` each deleted type name in `apps/ios` returns nothing but this plan's history.
- [ ] **Step 3:** `YouReferences` from `YouFixture` (name "Alexis", March 2026, a glyph, Ripples 3 active, 112 / 318 / 1240, counts 12 / 5): `header` (with glyph, without), `counters` (loaded, loading), `glyphs` (counts, plain), `page` (Paper, Night, French, AX2).
- [ ] **Step 4:** Gate; device check of Log out; push; PR `Part 6 of #1382`; record the new references.

---

## Part 7 — Achievements and the Lab on You (`feat/1382-you-achievements-lab-ios`)

**Before Part 7:** `PbblsIcon.whatsapp` is on `main` (the `feat/icon-whatsapp` PR merged and `apps/ios/Pebbles/Theme/PbblsIcon.swift` regenerated). If not, stop here: Parts 1–6 can merge without it.

### Task 28: Achievements

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sections/YouAchievementsSection.swift`
- Modify: `YouModel.swift` (the shelf), `Localizable.xcstrings` (`achievement.shelf.earnedWord`, one / many / other)
- Delete: `Profile/Components/ProfileAchievementsCard.swift` (move `ProfileShelf` into the new file first, unchanged)
- Modify: `apps/ios/PebblesTests/Features/Profile/YouModelTests.swift`

- [ ] **Step 1: Failing tests:** the shelf loads through `loadIfStale(maxAge: 15 min)` after the retroactive check, never more often; a failed catalog read keeps the served rows; the section shows `min(4, shelf.count)` badges.
- [ ] **Step 2:** Move the load (`AchievementsModel`, catalog once) from `ProfileAchievementsCard` into `YouModel.loadShelf()` as it is; `ProfileShelf` moves unchanged.
- [ ] **Step 3:** The section: `PebblesSectionHead("Achievements", figure: count.map { .counted($0, word: earnedWord($0)) }, action: open)`; a `LazyVGrid` of four flexible columns (two at accessibility sizes), spacing 10, bottom room 22 for the shadow; each badge a `NavigationLink(value: YouRoute.achievements)` around a `.pebblesCard()` cell (padding 16 / 4 / 12, `PbblsIconImage(record.familySymbol, size: 30, style: primary)`, the title in `badgeLabel`, two lines, centred). No badge: one card with "Badges along your path".
- [ ] **Step 4:** Commit: `git commit -am "feat(ios): card the achievements on you"`

### Task 29: The announcement route

**Files:**
- Modify: `TabRoutes.swift` (`YouRoute.announcement(UUID)`), `ProfileView.swift`, `apps/ios/Pebbles/Features/Lab/Services/LogsService.swift` (`announcement(id:)`)
- Create: `apps/ios/Pebbles/Features/Lab/Views/AnnouncementRoute.swift`

- [ ] **Step 1:** `LogsService.announcement(id:)`: one `v_logs_with_counts` read, `eq("id")`, published, `LossyLogArray`, first. `AnnouncementRoute(id:)` resolves it (loading / found / missing, the `SoulRoute` shape) and shows `AnnouncementDetailView(log:coverImageURL:)`; the cover URL helper moves from `LabView` to `LabConfig.coverURL(for:)` so both use it (one-function move, same commit).
- [ ] **Step 2:** Route round-trip test as in Task 26. Commit: `git commit -am "feat(ios): route to an announcement by id"`

### Task 30: The Lab section

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sections/YouLabSection.swift`
- Modify: `YouModel.swift` (`loadLab()`), `Localizable.xcstrings` (`you.lab.*`)
- Modify: `YouModelTests.swift`

- [ ] **Step 1: Failing tests:** `loadLab()` runs `announcements(limit: 1)` and `changelog(limit: 3)` in parallel; one failing leaves the other shown and logs; both failing leave only the community stone.
- [ ] **Step 2:** The section under `PebblesSectionHead("Lab")`:
  - the announcement: `NavigationLink(value: .announcement(id))` around a `.pebblesCard()` with the cover band (an `AsyncImage` 96 tall when the log has a cover, else a `SplitStone(count: 3, options: StoneOptions(corner: 22, gutter: 6, fillet: 14))` 286 × 64 in `primaryContainer`, centred on `Color.pebbles.stock` under `Color.pebbles.primaryContainer.opacity(0.35)`, 22 above and below), then padding 14 / 18 / 18: the kicker `you.lab.kicker` with the date (`.dateTime.day().month(.wide)`) in `labKicker`, the title in `labTitle` one line, the summary in `rowLine` two lines; 22 below;
  - the timeline: three rows (`HStack(alignment: .top, spacing: 12)`: the date `.dateTime.day().month(.abbreviated)` right-aligned in a 46 column in `labDate`; the dot, 9, `primary` filled for the first, `surface` with a 1.5 `outline` ring for the others; the title `labEntry` and the summary `labEntryLine` one line), a 1 pt `rule` line behind the dots, 14 between rows; each row one element ("31 October, title, summary");
  - "All updates": a `NavigationLink(value: YouRoute.lab)` styled as a capsule (`outlineVariant` 1 pt border, `pillLabel` in `primary`, SF `chevron.right` 13), leading 62, 24 below;
  - the community stone: a `Button { openURL(LabConfig.whatsappInviteURL) }` over `StoneShape(seed: "whatsapp", options: .init(corner: 30))` in `tertiaryContainer`, padding 18 / 18 / 18 / 20: `PbblsIconImage(.whatsapp, size: 32)`, the title "Join the community" in `communityTitle`, the line `you.lab.community.line` in `rowLine` at 85 %, an SF chevron, all `onTertiaryContainer`.
- [ ] **Step 3:** Commit: `git commit -am "feat(ios): bring the lab's latest onto you"`

### Task 31: Retire the old cards

- [ ] Delete `Profile/Components/ProfileLabCard.swift` and `Theme/ProfileCard.swift` (`.profileCard()` has no caller left: `grep -rn "profileCard()" apps/ios` is empty); remove `News & community` from the catalog. Commit: `git commit -am "chore(ios): retire the profile cards"`

### Task 32: References, decisions log, lessons learned

**Files:**
- Modify: `apps/ios/PebblesTests/References/YouReferences.swift`
- Modify: `docs/decisions/log.md` (append)
- Modify: this plan (`## Lessons learned`)

- [ ] **Step 1:** References: `achievements` (four, empty, AX2), `lab` (drawn band, no announcement), `community`; `page*` re-baselined.
- [ ] **Step 2:** Append one entry: `## 2026-11-xx — iOS sets its body text in Inclusive Sans; Home and You speak in stones, cards and one box (#1382)`. Decision bullets: SF Pro Rounded and SF Compact Rounded are retired and a theme rule keeps them out; the former SF tokens are Spine ranks on the Inclusive face and scale with Dynamic Type; the vocabulary (stones for actions and side-by-side tiles, flat; cards for content you browse, `stock` with shadow on Paper and a hairline at Night; one box for a list); a Home week column opens its week on the Path (`TabIntent.focusWeek`); the glyph counts live in `get_profile_engagement`; Log out lives in Settings. **Refs:** #1382, part B's issue, the spec, this plan.
- [ ] **Step 3:** Lessons learned in this plan (left empty until here): what the device pass changed (AX2 caps added in Part 1, the card shadow, the greeting's scale floor), the measured `path_month_wrap` time on Home and whether the slim projection issue was filed, how many references each part re-baselined.
- [ ] **Step 4:** Commit: `git add docs && git commit -m "docs: log inclusive sans and the home and you vocabulary"`

### Task 33: Part 7 gate, PR, merge

- [ ] **Step 1:** Gate; push; PR (`Resolves #1382`; the Lab Note from the `lab-note` skill, or `no-lab-note` per the maintainer's call at Part 1); re-baseline.
- [ ] **Step 2:** Before merging: every bot commit fast-forwarded in, `gh stack rebase`, `git diff --stat origin/<b> <b>` empty on every branch, `gh stack push`, every PR clean; hand the maintainer `gh stack merge --yes --squash`. Arkaik moves to `releasing` through the App at merge; never set `live`.
- [ ] **Step 3:** Drop the build-journal pebble (`node scripts/journal/drop-pebble.mjs`, `--size medium --valence highlight --collection iOS`).

---

## Self-review against the spec

- **D1–D4** fonts, tokens, rounded rule: Tasks 1–4. **D5** follow-up issue: Task 5. **D6** move then generalise: Tasks 6, 8, 9. **D7** vocabulary: Tasks 9, 10, 31. **D8** month data: Task 15. **D9** column tap and the wrap sheet: Tasks 13–15. **D10** greeting: Task 16. **D11** quick actions: Task 17. **D12** first five: Task 19. **D13** wraps: Task 20. **D14** About: Task 21. **D15** head: Task 11. **D16** shell and Log out: Tasks 22, 27. **D17** header and `RippleBadge`: Task 23. **D18** counters: Task 24. **D19** glyph counts and rows: B1–B3, Tasks 25–26. **D20** achievements: Task 28. **D21** Lab: Tasks 29–30. **D22** ranks: Task 11.
- **Types used with the same names throughout:** `InclusiveFace`, `SpineType.Face.inclusive`, `Style.isUppercase`, `PebblesFont.inclusiveStyle`, `StoneGeometry.split(size:count:seed:options:)`, `StoneOptions`, `StoneShape`, `SplitStone`, `SquirclePentagon`, `.pebblesCard()`, `.pebblesBox()`, `PebblesSectionHead.Figure`, `PebblesLinkRow.Chip`, `TabIntent.focusWeek`, `PathModel.focus(weekStarting:)`, `WeeksColumns.Metrics`, `HomeMonthGrid`, `YouModel`, `YouContent`, `YouRoute.glyphTab`, `YouRoute.announcement`, `RippleBadge(side:)`.

## Lessons learned

(Written in Part 7, Task 32.)
