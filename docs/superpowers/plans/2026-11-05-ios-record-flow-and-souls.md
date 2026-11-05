# iOS record flow and souls chooser Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship, on iOS, the souls chooser (picker sheet, edit half sheet, People tab) and the redesigned record flow (photo → moment page → valence → emotion → Life → Make it richer) as one stack of nine parts, souls first.

**Architecture:** One `@Observable` model per concern, views own no flow state (the M58 discipline already in `CaptureFlowModel` / `CompleteFlowModel`). A new `Features/Souls/` folder holds the chooser (`SoulChooserModel` + `SoulChooser` + its atoms) with three hosts. The Capture flow shrinks to two steps and gains a device-local draft store modelled on `CompleteDraftStore`; the Complete flow shrinks to three steps plus the end screen. No schema or RPC change: `save_moment` and `complete_pebble` are called as today.

**Tech Stack:** SwiftUI, iOS 17 deployment target (the one `if #available(iOS 26)` exception is Liquid Glass, precedent `NewPebbleFab`), `@Observable`, Swift Testing (`@Suite`, `@Test`, `#expect`), `ImageRenderer` reference PNGs under `PebblesTests/References`, xcodegen, SwiftLint.

**Specs:** `docs/superpowers/specs/2026-11-05-souls-picker-and-people-design.md` (S1–S11) and `docs/superpowers/specs/2026-11-04-record-flow-redesign-design.md` (D1–D16). Issues #1518 (souls, iOS) and #1514 (record flow, iOS), milestone M70 · Record flow.

---

## Working rules for every task

- **Branching:** one stack, `gh stack` (read the `gh-stack` skill before the first push). Parts are branches `feat/1518-souls-chooser-ios` → `feat/1518-people-tab-ios` → `feat/1518-picker-hosts-ios` → `feat/1514-moment-page-ios` → `feat/1514-photo-step-ios` → `feat/1514-valence-ios` → `feat/1514-life-screen-ios` → `feat/1514-make-it-richer-ios` → `feat/1514-copy-renders-ios`. The `-ios` suffix is required (Vercel ignores native branches by that suffix). Fork the first branch from an up-to-date `main`; each later branch forks from the previous part.
- **Build and test** from `apps/ios`:
  ```bash
  cd apps/ios && xcodegen generate
  xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' \
    -only-testing:PebblesTests/<SuiteType> -quiet 2>&1 | tail -20
  ```
  Replace `<SuiteType>` with the Swift type name of the suite (e.g. `SoulChooserModelTests`). Without `-only-testing` the whole suite runs (several minutes). If xcodebuild serves stale objects after xcodegen, `rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build` and rebuild.
- **Reference renders** (`PebblesTests/References/*`) fail on this Mac even on untouched `main` (27 suites differ by anti-aliasing). Never re-record references locally. To record a new reference for CI, run the suite with `TEST_RUNNER_PBBLS_RECORD_REFERENCES=1`, commit the PNGs under `PebblesTests/References/__References__/<Suite>/`, and let CI confirm. Gate locally by "the new suite records without an `Issue`", not by byte identity.
- **Lint:** `cd apps/ios && swiftlint` must be clean on the files you touched.
- **Localization:** every user-facing literal in `Text("…")`, `Button("…")`, `LocalizedStringResource` is auto-extracted into `Pebbles/Resources/Localizable.xcstrings` on build with state `new`. The French value must be added by hand. Add it as text at the right alphabetical anchor in the JSON (never re-dump the whole file: its key order and formatting are load-bearing, see the project memory on formatting-sensitive catalogs). An entry looks like:
  ```json
  "Just save" : {
    "extractionState" : "manual",
    "localizations" : {
      "en" : { "stringUnit" : { "state" : "translated", "value" : "Just save" } },
      "fr" : { "stringUnit" : { "state" : "translated", "value" : "Juste enregistrer" } }
    }
  },
  ```
  Part 9 is the full copy pass; parts 1–8 add the English and the French for the strings they introduce so each part ships bilingual.
- **Learned in Part 1 (binding for later parts):** (a) `xcodebuild … build -quiet` does NOT write new keys into `Localizable.xcstrings`; add every new key by hand (EN + FR, `extractionState: manual`) at its alphabetical anchor and validate the JSON with `python3 -I -c "import json;json.load(open(...))"`. (b) French reference renders are a second pass with `-testLanguage fr`; the English pass alone records only `-en` PNGs. (c) `ImageRenderer` draws neither `ScrollView` content nor `TextField`s: views that scroll expose a `scrolls: Bool` (the chooser does) and the suite renders them with `scrolls: false`. (d) `AchievementsService(client:)` is the init; the PebblesTests target's `Fixtures/**` exclude was narrowed to `Fixtures/*.json` so Swift fixtures compile. (e) The plan lives on the docs branch, not the feature worktree: subagents read it from the scratchpad copy. (f) `swiftlint lint <path>` takes a positional path.
- **Colours:** `Color.pebbles.<role>` only (`ThemeLiteralsTests` fails on a literal). Fonts: `.pebblesFont(<token>)` with the tokens in `Pebbles/Theme/Font+Pebbles.swift`. Spacing: `Spacing.xs/sm/md/lg/xl/xxl` = 3/10/13/17/22/34.
- **Icons:** `PbblsIconImage(.<case>, size:relativeTo:style:)`, cases in `Pebbles/Theme/PbblsIcon.swift`. There is no search icon in the set: the lens is `Image(systemName: "magnifyingglass")` and the close is `Image(systemName: "xmark")`, both already used by `RecordFlowChrome`. The `remove` icon draws a plus (known bug) so pills use `xmark`.
- **Commits:** conventional, lowercase, no period, `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>` as the last line. One logical change per commit.
- **Arkaik:** at the end of each part, move the touched view nodes to `development` with the `arkaik-mcp` tools (`ARKAIK_TOKEN` exported). If the tools are unavailable, say so and continue; never edit `docs/arkaik/bundle.json`.
- **PR body:** starts with `Resolves #1518` (parts 1–3) or `Resolves #1514` (parts 4–9) only on the last part of each issue; earlier parts say `Part of #1518` / `Part of #1514`. Never write a Kritik finding id. Labels `feat`, `ui`, `ios`, milestone `M70 · Record flow`. Lab Note: the stack's last PR (part 9) carries one `## Lab Note (EN/FR)` for the whole flow; the other eight get `no-lab-note`.

## File map

| Area | File | Responsibility |
|---|---|---|
| Souls (new) | `Pebbles/Features/Souls/SoulChooserModel.swift` | Ranking, grouping, folded search, Add-row rule, selection, button label. Pure, tested. |
| | `Pebbles/Features/Souls/SoulChooser.swift` | The composed screen: header slot, Usual tiles, A–Z or results, bottom bar. |
| | `Pebbles/Features/Souls/SoulTile.swift` | One Usual tile (glyph, name, tick badge, tonal fill). |
| | `Pebbles/Features/Souls/SoulRow.swift` | One A–Z row (glyph, name, count, plus/tick). |
| | `Pebbles/Features/Souls/SoulPillTray.swift` | Selected pills + Done/Skip button. |
| | `Pebbles/Features/Souls/SoulChooserBottomBar.swift` | Lens/plus orbs, the search field, the Add row, glass on 26. |
| | `Pebbles/Features/Souls/SoulInlineCreate.swift` | The one-row `souls` insert with the default glyph. |
| | `Pebbles/Features/Souls/SoulChooserSheet.swift` | Picker host by detent (Life screen `.large`, edit sheet `.medium`). |
| | `Pebbles/Features/Souls/UsualSoulsStrip.swift` | Life screen strip: four tiles + More. (Part 7) |
| People tab | `Pebbles/Features/Profile/Lists/SoulsListView.swift` | Hosts the chooser in browse mode, keeps prompts. |
| Edit sheet | `Pebbles/Features/Path/SelectedSoulsRow.swift` | Opens `SoulChooserSheet` at `.medium`. |
| Deleted (part 3) | `Pebbles/Features/Path/SoulPickerSheet.swift`, `SoulPickerContent.swift`, `Pebbles/Features/Shared/SoulItem.swift`, `Pebbles/Features/Complete/Steps/CompleteSoulsStep.swift` (part 7) | |
| Capture | `Pebbles/Features/Capture/CaptureDraft.swift` | + `description`. |
| | `Pebbles/Features/Capture/CaptureStep.swift`, `CaptureStep+Copy.swift` | `.photo`, `.moment`. |
| | `Pebbles/Features/Capture/CaptureFlowModel.swift` | + description, when chip state, `restore`. |
| | `Pebbles/Services/CaptureDraftStore.swift` (new) | One file per owner; name, words, when, snap reference. |
| | `Pebbles/Features/Record/Steps/RecordMomentStep.swift` (new) | The Ledger moment page. |
| | `Pebbles/Features/Record/Steps/RecordWhenChip.swift` (new) | The chip + calendar sheet (absorbs `RecordWhenStep`). |
| | `Pebbles/Features/Record/Steps/RecordPhotoStep.swift` | Albums on, fixed slot, auto-advance. |
| | `Pebbles/Features/Record/RecordStepScaffold.swift` | + `fixedSlot`, + `pill`. |
| | `Pebbles/Components/Buttons/PebblesTonalButtonStyle.swift` (new) | Tonal pill for "Just write". |
| Complete | `Pebbles/Features/Complete/CompleteStep.swift`, `+Copy` | `.valence`, `.emotion`, `.life`, `.success`. |
| | `Pebbles/Features/Complete/CompleteDraft.swift` | `domainIds: [UUID]` (max 3). |
| | `Pebbles/Features/Complete/CompleteFlowModel.swift` | No seed; `toggleDomain`; `afterSave` header. |
| | `Pebbles/Features/Complete/CompleteFlowView.swift` | Pill, help sheet, Life, Make it richer. |
| | `Pebbles/Features/Complete/Steps/CompleteLifeStep.swift` (new) | Domains (recent + all, cap 3) + Usual strip. |
| | `Pebbles/Features/Complete/Steps/CompleteRicherStep.swift` (new, replaces `CompleteSuccessStep`) | Lit stone, karma, week row, three rows, Done / Record another. |
| | `Pebbles/Features/Complete/RecordSummaryPill.swift` (new) | "Highlight · week · Joyful" with Edit. |
| | `Pebbles/Features/Complete/RecentDomains.swift` (new) | Device-local last-picked domains per owner. |
| Valence | `Pebbles/Features/Path/Models/Valence.swift` | Neutral word; `spanWord`. |
| | `Pebbles/Features/Path/ValencePickerContent.swift` | Axis labels, ghost caption when nil. |
| | `Pebbles/Features/Path/Valence/ValenceHelpSheet.swift` (new) | The four lines + Got it. |
| Emotion | `Pebbles/Features/Path/EmotionPickerContent.swift` | Retry state. |
| Entry points | `Pebbles/Features/Home/HomeView.swift`, `Pebbles/Features/Path/PathView.swift`, `Pebbles/Features/Path/Card/BareMomentPage.swift`, `Pebbles/Features/Path/Card/HeldCardPage.swift` | D13, D14. |
| Tests | `PebblesTests/Features/Souls/*`, `PebblesTests/Features/Capture/*`, `PebblesTests/Features/Complete/*`, `PebblesTests/References/SoulChooserReferences.swift`, `PebblesTests/References/RecordFlowReferences.swift` | |

---

# Part 1 — `SoulChooserModel` + `SoulChooser` (branch `feat/1518-souls-chooser-ios`)

### Task 1.1: Branch

- [ ] **Step 1: Fork from main**

```bash
cd /Users/alexis/code/pbbls && git checkout main && git pull --ff-only
git checkout -b feat/1518-souls-chooser-ios
```

### Task 1.2: The model's ranking and grouping (S1)

**Files:**
- Create: `apps/ios/Pebbles/Features/Souls/SoulChooserModel.swift`
- Test: `apps/ios/PebblesTests/Features/Souls/SoulChooserModelTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("SoulChooserModel")
@MainActor
struct SoulChooserModelTests {

    private static let glyph = Glyph(
        id: SystemGlyph.default, name: nil,
        strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
        viewBox: "0 0 200 200", userId: nil
    )

    private func soul(_ name: String, _ count: Int) -> SoulWithGlyph {
        SoulWithGlyph(id: UUID(), name: name, glyphId: SystemGlyph.default, glyph: Self.glyph, pebblesCount: count)
    }

    private func names(_ souls: [SoulWithGlyph]) -> [String] { souls.map(\.name) }

    @Test("usual = count desc then name asc, capped at eight, zero counts excluded")
    func usual() {
        let souls = [soul("Zoé", 3), soul("Adèle", 3), soul("Ben", 0), soul("Camille", 29),
                     soul("Alex", 24), soul("Léa", 5), soul("Papa", 5), soul("Inès", 4),
                     soul("Sam", 4), soul("Nour", 2), soul("Mamie Jo", 2)]
        let model = SoulChooserModel(mode: .browse, souls: souls)
        #expect(names(model.usual) == ["Camille", "Alex", "Léa", "Papa", "Inès", "Sam", "Adèle", "Zoé"])
    }

    @Test("no usual section when nobody has a pebble")
    func usualEmpty() {
        let model = SoulChooserModel(mode: .browse, souls: [soul("Ben", 0), soul("Ana", 0)])
        #expect(model.usual.isEmpty)
    }

    @Test("A–Z sections fold diacritics, keep duplicates, and put non-letters under #")
    func sections() {
        let souls = [soul("Élise", 1), soul("alex", 2), soul("Alex", 3), soul("1er voisin", 0), soul("Ben", 0)]
        let model = SoulChooserModel(mode: .browse, souls: souls)
        #expect(model.sections.map(\.letter) == ["A", "B", "E", "#"])
        #expect(names(model.sections[0].souls) == ["alex", "Alex"])
        #expect(names(model.sections[2].souls) == ["Élise"])
        #expect(names(model.sections[3].souls) == ["1er voisin"])
    }

    @Test("search is case- and diacritic-insensitive and matches inside the name")
    func search() {
        let souls = [soul("Mathilde", 1), soul("Théo", 2), soul("Mamie Jo", 0)]
        let model = SoulChooserModel(mode: .browse, souls: souls)
        model.query = "theo"
        #expect(names(model.results) == ["Théo"])
        model.query = "  MIE  "
        #expect(names(model.results) == ["Mamie Jo"])
        model.query = ""
        #expect(model.results.isEmpty)
    }

    @Test("the Add row appears only without an exact folded match")
    func canAdd() {
        let model = SoulChooserModel(mode: .browse, souls: [soul("Théo", 2)])
        #expect(!model.canAdd)
        model.query = "theo"
        #expect(!model.canAdd)
        model.query = "Théo "
        #expect(!model.canAdd)
        model.query = "Mathilde"
        #expect(model.canAdd)
        #expect(model.trimmedQuery == "Mathilde")
    }

    @Test("selection toggles in order; the pick button counts, skip when empty and allowed")
    func selection() {
        let a = soul("Ana", 1), b = soul("Ben", 1)
        let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [a, b])
        #expect(model.buttonLabel == .skip)
        model.toggle(b.id)
        model.toggle(a.id)
        #expect(model.selection == [b.id, a.id])
        #expect(model.isSelected(a.id))
        #expect(model.buttonLabel == .done(2))
        model.remove(b.id)
        #expect(model.selection == [a.id])
        model.toggle(a.id)
        #expect(model.selection.isEmpty)
        #expect(model.buttonLabel == .skip)
    }

    @Test("the edit sheet says Done with nothing chosen; browse has no button")
    func buttonLabels() {
        #expect(SoulChooserModel(mode: .pick(allowsSkip: false), souls: []).buttonLabel == .doneEmpty)
        #expect(SoulChooserModel(mode: .browse, souls: []).buttonLabel == nil)
    }

    @Test("a created soul lands in the list, chosen in pick mode, and the search closes")
    func didCreate() {
        let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [soul("Ben", 0)])
        model.isSearching = true
        model.query = "Mathilde"
        let created = soul("Mathilde", 0)
        model.didCreate(created)
        #expect(model.souls.map(\.name).contains("Mathilde"))
        #expect(model.selection == [created.id])
        #expect(model.query.isEmpty)
        #expect(!model.isSearching)

        let browse = SoulChooserModel(mode: .browse, souls: [])
        browse.didCreate(created)
        #expect(browse.selection.isEmpty)
    }

    @Test("replacing the souls keeps a selection that still exists and drops one that does not")
    func replaceSouls() {
        let a = soul("Ana", 1), b = soul("Ben", 1)
        let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: [a, b], selection: [a.id, b.id])
        model.replaceSouls([a])
        #expect(model.selection == [a.id])
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/SoulChooserModelTests -quiet 2>&1 | tail -20
```
Expected: compile error, `cannot find 'SoulChooserModel' in scope`.

- [ ] **Step 3: Write the model**

```swift
import Foundation
import Observation

/// The souls chooser's state (souls spec S1–S3): ranking, grouping, folded
/// search, the Add-row rule and the ordered selection. Pure: hosts feed it
/// `ReferenceDataService.souls` and read the selection back. Views own no
/// state of their own beyond focus.
@Observable
@MainActor
final class SoulChooserModel {

    enum Mode: Equatable {
        /// Tiles and rows toggle; the tray shows pills and a button.
        /// `allowsSkip`: the record flow says "Skip" with nothing chosen, the
        /// edit sheet says "Done".
        case pick(allowsSkip: Bool)
        /// The People tab: tiles and rows open a person, no selection.
        case browse
    }

    struct Section: Identifiable, Equatable {
        let letter: String
        let souls: [SoulWithGlyph]
        var id: String { letter }
    }

    enum ButtonLabel: Equatable {
        case done(Int)
        case doneEmpty
        case skip
    }

    /// Two rows of four.
    static let usualCap = 8

    let mode: Mode
    private(set) var souls: [SoulWithGlyph]
    /// Ordered as chosen, so the pills keep their order across re-renders.
    private(set) var selection: [UUID]
    var query = ""
    var isSearching = false

    init(mode: Mode, souls: [SoulWithGlyph], selection: [UUID] = []) {
        self.mode = mode
        self.souls = souls
        self.selection = selection
    }

    // MARK: Ranking and grouping (S1)

    /// The most-tagged people: count descending, name ascending, at most eight,
    /// nobody with zero pebbles. The Life screen's strip takes the first four.
    var usual: [SoulWithGlyph] { Self.usual(from: souls) }

    static func usual(from souls: [SoulWithGlyph]) -> [SoulWithGlyph] {
        souls
            .filter { $0.pebblesCount > 0 }
            .sorted {
                if $0.pebblesCount != $1.pebblesCount { return $0.pebblesCount > $1.pebblesCount }
                return $0.name.localizedStandardCompare($1.name) == .orderedAscending
            }
            .prefix(usualCap)
            .map { $0 }
    }

    /// Everyone, grouped under the folded initial; "#" last for names that do
    /// not start with a letter. Names sort locale-aware inside a group.
    var sections: [Section] {
        let sorted = souls.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        var order: [String] = []
        var grouped: [String: [SoulWithGlyph]] = [:]
        for soul in sorted {
            let letter = Self.letter(for: soul.name)
            if grouped[letter] == nil { order.append(letter) }
            grouped[letter, default: []].append(soul)
        }
        let letters = order.filter { $0 != "#" }.sorted() + (order.contains("#") ? ["#"] : [])
        return letters.map { Section(letter: $0, souls: grouped[$0] ?? []) }
    }

    static func folded(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current).lowercased()
    }

    static func letter(for name: String) -> String {
        guard let first = folded(name.trimmingCharacters(in: .whitespaces)).first, first.isLetter else { return "#" }
        return String(first).uppercased()
    }

    // MARK: Search (S3)

    var trimmedQuery: String { query.trimmingCharacters(in: .whitespacesAndNewlines) }

    /// Matches anywhere in the folded name. Empty without a query.
    var results: [SoulWithGlyph] {
        let needle = Self.folded(trimmedQuery)
        guard !needle.isEmpty else { return [] }
        return souls
            .filter { Self.folded($0.name).contains(needle) }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    /// The Add row and the lit plus: a query nobody's folded name equals.
    var canAdd: Bool {
        let needle = Self.folded(trimmedQuery)
        guard !needle.isEmpty else { return false }
        return !souls.contains { Self.folded($0.name) == needle }
    }

    // MARK: Selection (S2)

    func isSelected(_ id: UUID) -> Bool { selection.contains(id) }

    func toggle(_ id: UUID) {
        guard case .pick = mode else { return }
        if let index = selection.firstIndex(of: id) {
            selection.remove(at: index)
        } else {
            selection.append(id)
        }
    }

    func remove(_ id: UUID) {
        selection.removeAll { $0 == id }
    }

    var buttonLabel: ButtonLabel? {
        switch mode {
        case .browse:
            return nil
        case .pick(let allowsSkip):
            if selection.isEmpty { return allowsSkip ? .skip : .doneEmpty }
            return .done(selection.count)
        }
    }

    // MARK: Souls lifecycle

    /// A fresh fetch (reference data refreshed behind the sheet). A chosen soul
    /// that is gone is dropped, which costs a pill, not a `soul_not_owned`.
    func replaceSouls(_ souls: [SoulWithGlyph]) {
        self.souls = souls
        let known = Set(souls.map(\.id))
        selection = selection.filter { known.contains($0) }
    }

    /// The inline create (S4): the new soul joins the list, arrives chosen in a
    /// picker, and the field closes.
    func didCreate(_ soul: SoulWithGlyph) {
        souls.append(soul)
        if case .pick = mode { selection.append(soul.id) }
        query = ""
        isSearching = false
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Same command as step 2. Expected: `Test Suite 'SoulChooserModelTests' passed`, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Souls/SoulChooserModel.swift apps/ios/PebblesTests/Features/Souls/SoulChooserModelTests.swift
git commit -m "feat(ios): souls chooser model with usual ranking, folded search and ordered selection

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 1.3: The inline create (S4)

**Files:**
- Create: `apps/ios/Pebbles/Features/Souls/SoulInlineCreate.swift`

`SoulInsertPayload(userId:name:glyphId:)` already exists in `Pebbles/Features/Profile/Models/SoulPayloads.swift` and is what `CreateSoulSheet.save()` sends. This is the same single-table insert without the sheet.

- [ ] **Step 1: Write the helper**

```swift
import Foundation
import Supabase

/// The chooser's "Add <name>" (souls spec S4): one `souls` row with the
/// system default glyph, the payload `CreateSoulSheet` sends. A single-table,
/// single-row insert, so no RPC (AGENTS.md).
enum SoulInlineCreate {
    static let select = "id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)"

    static func create(name: String, userId: UUID, client: SupabaseClient) async throws -> SoulWithGlyph {
        let payload = SoulInsertPayload(
            userId: userId,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            glyphId: SystemGlyph.default
        )
        return try await client
            .from("souls")
            .insert(payload)
            .select(select)
            .single()
            .execute()
            .value
    }
}
```

- [ ] **Step 2: Build**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build -quiet 2>&1 | grep -E "error|warning: unused" | head
```
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Souls/SoulInlineCreate.swift
git commit -m "feat(ios): inline soul create with the default glyph

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 1.4: The atoms — `SoulTile`, `SoulRow`, `SoulPillTray`

**Files:**
- Create: `apps/ios/Pebbles/Features/Souls/SoulTile.swift`
- Create: `apps/ios/Pebbles/Features/Souls/SoulRow.swift`
- Create: `apps/ios/Pebbles/Features/Souls/SoulPillTray.swift`

- [ ] **Step 1: `SoulTile`**

```swift
import SwiftUI

/// One Usual tile (souls spec S2): the drawing, the name, and when chosen a
/// tonal fill with a tick badge at the top right. Four across; `GlyphThumbnail`
/// keeps the 6 px stroke of the glyph space.
struct SoulTile: View {
    let soul: SoulWithGlyph
    let isSelected: Bool
    /// Browse mode: no selection, the tile opens the person.
    var showsSelection = true
    let onTap: () -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.xs + 2) {
                GlyphThumbnail(strokes: soul.glyph.strokes, side: 44,
                               strokeColor: isSelected ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                    .overlay(alignment: .topTrailing) {
                        if isSelected && showsSelection { badge }
                    }
                Text(verbatim: soul.name)
                    .pebblesFont(isSelected ? .subheadEmphasized : .subhead)
                    .foregroundStyle(isSelected ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 88)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(isSelected && showsSelection ? Color.pebbles.primary.opacity(0.20) : Color.clear)
            )
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: soul.name))
        .accessibilityAddTraits(isSelected && showsSelection ? [.isSelected] : [])
    }

    private var badge: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Color.pebbles.onPrimary)
            .frame(width: 20, height: 20)
            .background(Circle().fill(Color.pebbles.primary))
            .offset(x: 8, y: -4)
            .accessibilityHidden(true)
    }
}

#Preview {
    let glyph = Glyph(id: SystemGlyph.default, name: nil,
                      strokes: [GlyphStroke(d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)],
                      viewBox: "0 0 200 200", userId: nil)
    let soul = SoulWithGlyph(id: UUID(), name: "Maman", glyphId: glyph.id, glyph: glyph, pebblesCount: 12)
    return HStack {
        SoulTile(soul: soul, isSelected: true, onTap: {})
        SoulTile(soul: soul, isSelected: false, onTap: {})
    }
    .padding()
}
```

- [ ] **Step 2: `SoulRow`**

```swift
import SwiftUI

/// One A–Z row (souls spec S1–S2): the drawing, the name in regular type, the
/// pebble count, then a plus or a tick. In browse mode the trailing control is
/// a chevron and the row opens the person.
struct SoulRow: View {
    enum Trailing { case plus, tick, chevron }

    let soul: SoulWithGlyph
    let trailing: Trailing
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.md) {
                GlyphThumbnail(strokes: soul.glyph.strokes, side: 30, strokeColor: Color.pebbles.onSurfaceVariant)
                    .accessibilityHidden(true)
                Text(verbatim: soul.name)
                    .pebblesFont(trailing == .tick ? .bodyEmphasized : .body)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                    .truncationMode(.tail)
                Spacer(minLength: Spacing.sm)
                HStack(spacing: Spacing.xs) {
                    PbblsIconImage(.pebbleRound, size: 13, relativeTo: .footnote,
                                   style: AnyShapeStyle(Color.pebbles.outline))
                    Text(verbatim: "\(soul.pebblesCount)")
                        .pebblesFont(.subhead)
                        .monospacedDigit()
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                control
            }
            .frame(minHeight: 52)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: soul.name))
        .accessibilityValue(Text("\(soul.pebblesCount) pebbles"))
        .accessibilityAddTraits(trailing == .tick ? [.isButton, .isSelected] : [.isButton])
    }

    @ViewBuilder
    private var control: some View {
        switch trailing {
        case .plus:
            PbblsIconImage(.plusCircle, size: 26, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
        case .tick:
            PbblsIconImage(.checkCircle, size: 26, style: AnyShapeStyle(Color.pebbles.primary))
        case .chevron:
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Color.pebbles.outline)
                .accessibilityHidden(true)
        }
    }
}
```

- [ ] **Step 3: `SoulPillTray`**

```swift
import SwiftUI

/// The chosen people as pills in one scrolling line (souls spec S2), each with
/// a close that removes it, beside the Done / Skip button. The right-edge fade
/// says there is more. Nothing is drawn in browse mode.
struct SoulPillTray: View {
    let selected: [SoulWithGlyph]
    let label: SoulChooserModel.ButtonLabel
    let onRemove: (UUID) -> Void
    let onConfirm: () -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            if !selected.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(selected) { soul in pill(soul) }
                    }
                }
                .mask(
                    LinearGradient(stops: [.init(color: .black, location: 0.86), .init(color: .clear, location: 1)],
                                   startPoint: .leading, endPoint: .trailing)
                )
            } else {
                Spacer(minLength: 0)
            }
            button
        }
        .frame(minHeight: 48)
    }

    private func pill(_ soul: SoulWithGlyph) -> some View {
        Button { onRemove(soul.id) } label: {
            HStack(spacing: Spacing.xs + 2) {
                GlyphThumbnail(strokes: soul.glyph.strokes, side: 24, strokeColor: Color.pebbles.onSurface)
                Text(verbatim: soul.name)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                Image(systemName: "xmark")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .padding(.leading, 7)
            .padding(.trailing, 8)
            .frame(height: 44)
            .background(Capsule().fill(Color.pebbles.primary.opacity(0.20)))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Remove \(soul.name)"))
    }

    @ViewBuilder
    private var button: some View {
        switch label {
        case .done(let count):
            Button(action: onConfirm) { Text("Done · \(count)") }
                .buttonStyle(SoulTrayButtonStyle(outlined: false))
        case .doneEmpty:
            Button(action: onConfirm) { Text("Done") }
                .buttonStyle(SoulTrayButtonStyle(outlined: false))
        case .skip:
            Button(action: onConfirm) { Text("Skip") }
                .buttonStyle(SoulTrayButtonStyle(outlined: true))
        }
    }
}

/// A 48 pt pill that hugs its label: filled for Done, outlined for Skip.
struct SoulTrayButtonStyle: ButtonStyle {
    let outlined: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .pebblesFont(.buttonLabel)
            .foregroundStyle(outlined ? Color.pebbles.primary : Color.pebbles.onPrimary)
            .padding(.horizontal, Spacing.xl)
            .frame(height: 48)
            .background(Capsule().fill(outlined ? Color.clear : Color.pebbles.primary))
            .overlay(Capsule().strokeBorder(Color.pebbles.primary, lineWidth: outlined ? 1.5 : 0))
            .opacity(configuration.isPressed ? 0.85 : 1)
    }
}
```

- [ ] **Step 4: Build**

Same build command as Task 1.3 step 2. Expected: no errors. The three literals `"Done · %lld"`, `"Done"`, `"Skip"`, `"Remove %@"`, `"%lld pebbles"` are extracted on build; `"Done"` and `"Skip"` already exist in the catalog.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Souls/SoulTile.swift apps/ios/Pebbles/Features/Souls/SoulRow.swift apps/ios/Pebbles/Features/Souls/SoulPillTray.swift
git commit -m "feat(ios): soul tile, row and pill tray atoms

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 1.5: The bottom bar — lens, plus, field, Add row (S3, S6)

**Files:**
- Create: `apps/ios/Pebbles/Features/Souls/SoulChooserBottomBar.swift`

- [ ] **Step 1: Write it**

```swift
import SwiftUI

/// The lens and the plus in thumb reach (souls spec S3, S6). The lens opens
/// into a search field in its place, anchored above the keyboard because the
/// bar lives in the host's bottom safe-area inset; the close sits inside the
/// field. The plus lights up when the query names nobody, because it then does
/// what the Add row does. Glass on iOS 26, material below: the one
/// `if #available` exception, as `NewPebbleFab`.
struct SoulChooserBottomBar: View {
    let model: SoulChooserModel
    /// Fired with the trimmed query; the host inserts the row.
    let onAdd: (String) -> Void
    var addError: String?

    @FocusState private var fieldFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            if let addError {
                Text(verbatim: addError)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.error)
                    .padding(.horizontal, Spacing.lg)
            }
            HStack(spacing: Spacing.sm) {
                if model.isSearching {
                    field
                } else {
                    Spacer(minLength: 0)
                    orb(systemName: "magnifyingglass", label: "Search souls", lit: false) {
                        model.isSearching = true
                    }
                }
                orb(systemName: "plus", label: "Add a soul", lit: model.canAdd) { plusTapped() }
            }
            .padding(.horizontal, Spacing.lg)
        }
        .onChange(of: model.isSearching) { _, searching in fieldFocused = searching }
    }

    private var field: some View {
        HStack(spacing: Spacing.sm) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .accessibilityHidden(true)
            TextField("Search souls", text: Binding(get: { model.query }, set: { model.query = $0 }))
                .pebblesFont(.body)
                .focused($fieldFocused)
                .submitLabel(.done)
                .autocorrectionDisabled()
                .onSubmit { if model.canAdd { onAdd(model.trimmedQuery) } }
            Button {
                model.query = ""
                model.isSearching = false
            } label: {
                Image(systemName: "xmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Close search")
        }
        .padding(.leading, Spacing.lg)
        .padding(.trailing, Spacing.xs)
        .frame(height: 52)
        .frame(maxWidth: .infinity)
        .glassCapsule()
    }

    private func orb(systemName: String, label: LocalizedStringResource, lit: Bool,
                     action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 22, weight: .medium))
                .foregroundStyle(lit ? Color.pebbles.onPrimary : Color.pebbles.onSurface)
                .frame(width: 52, height: 52)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glassCircle(tinted: lit)
        .accessibilityLabel(Text(label))
    }

    private func plusTapped() {
        if model.canAdd {
            onAdd(model.trimmedQuery)
        } else {
            model.isSearching = true
        }
    }
}

/// "Add <name> · A drawing can wait": the first result slot when the query
/// names nobody (souls spec S3).
struct SoulAddRow: View {
    let name: String
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: Spacing.md) {
                PbblsIconImage(.plusCircle, size: 32, style: AnyShapeStyle(Color.pebbles.onPrimaryContainer))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Add \(name)")
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(Color.pebbles.onPrimaryContainer)
                        .lineLimit(1)
                    Text("A drawing can wait")
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.pebbles.onPrimaryContainer)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .frame(height: 64)
            .frame(maxWidth: .infinity)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Color.pebbles.primary.opacity(0.20)))
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}

private extension View {
    @ViewBuilder
    func glassCircle(tinted: Bool) -> some View {
        if #available(iOS 26.0, *) {
            if tinted {
                glassEffect(.regular.tint(Color.pebbles.primary).interactive(), in: Circle())
            } else {
                glassEffect(.regular.interactive(), in: Circle())
            }
        } else {
            background(Circle().fill(tinted ? Color.pebbles.primary : Color.pebbles.surfaceContainerHigh))
                .shadow(color: Color.pebbles.scrim.opacity(0.16), radius: 12, y: 4)
        }
    }

    @ViewBuilder
    func glassCapsule() -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(.regular, in: Capsule())
        } else {
            background(Capsule().fill(Color.pebbles.surfaceContainerHigh))
                .shadow(color: Color.pebbles.scrim.opacity(0.16), radius: 12, y: 4)
        }
    }
}
```

- [ ] **Step 2: Build, then commit**

```bash
git add apps/ios/Pebbles/Features/Souls/SoulChooserBottomBar.swift
git commit -m "feat(ios): souls chooser bottom bar with lens, plus, field and add row

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 1.6: The composed `SoulChooser`

**Files:**
- Create: `apps/ios/Pebbles/Features/Souls/SoulChooser.swift`

- [ ] **Step 1: Write the view**

```swift
import SwiftUI
import os

/// The one chooser, three hosts (souls spec Summary): a header slot (the
/// People tab's prompts), the Usual tiles, then everyone A to Z or the search
/// results, and the bottom bar. In `.pick` the tray carries the pills and the
/// button; in `.browse` tiles and rows open a person.
///
/// The host passes `model` (its selection is the host's answer), the souls to
/// render come from `ReferenceDataService` and are refreshed on appear because
/// the cache only refreshes on soul mutations, so counts would lag every pebble
/// recorded since launch.
struct SoulChooser<Header: View>: View {
    let model: SoulChooserModel
    let title: LocalizedStringResource?
    /// Browse mode: a tile or row was tapped.
    var onOpen: (SoulWithGlyph) -> Void = { _ in }
    /// Pick mode: the tray's button.
    var onConfirm: () -> Void = {}
    /// A soul was created inline; the host decides what follows (a picker
    /// selects it, the People tab opens it).
    var onCreated: (SoulWithGlyph) -> Void = { _ in }
    @ViewBuilder var header: () -> Header

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(AchievementsService.self) private var achievements

    @State private var addError: String?
    @State private var isAdding = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "souls.chooser")

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0, pinnedViews: []) {
                if let title {
                    Text(title)
                        .pebblesFont(.title)
                        .foregroundStyle(Color.pebbles.onSurface)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.bottom, Spacing.md)
                }
                header()
                if model.isSearching && !model.trimmedQuery.isEmpty {
                    results
                } else {
                    usual
                    sections
                }
            }
            .padding(.bottom, Spacing.xxl)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            VStack(spacing: Spacing.sm) {
                if let label = model.buttonLabel {
                    SoulPillTray(
                        selected: model.selection.compactMap { id in model.souls.first { $0.id == id } },
                        label: label,
                        onRemove: { model.remove($0) },
                        onConfirm: onConfirm
                    )
                    .padding(.horizontal, Spacing.lg)
                }
                SoulChooserBottomBar(model: model, onAdd: { name in Task { await add(name) } }, addError: addError)
                    .disabled(isAdding)
            }
            .padding(.vertical, Spacing.sm)
            .background(
                LinearGradient(colors: [Color.pebbles.surface.opacity(0), Color.pebbles.surface],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 24),
                alignment: .top
            )
            .background(Color.pebbles.surface.opacity(0.92).ignoresSafeArea(edges: .bottom))
        }
        .task {
            await refs.refreshSouls()
            model.replaceSouls(refs.souls)
        }
        .onChange(of: refs.souls) { _, souls in model.replaceSouls(souls) }
    }

    // MARK: Sections

    @ViewBuilder
    private var usual: some View {
        let people = model.usual
        if !people.isEmpty {
            sectionTitle("Usual")
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 4), spacing: 4) {
                ForEach(people) { soul in
                    SoulTile(soul: soul, isSelected: model.isSelected(soul.id), showsSelection: model.mode != .browse) {
                        tapped(soul)
                    }
                }
            }
            .padding(.horizontal, Spacing.md)
            .padding(.bottom, Spacing.sm)
        }
    }

    @ViewBuilder
    private var sections: some View {
        ForEach(model.sections) { section in
            sectionTitle(LocalizedStringResource(stringLiteral: section.letter))
            VStack(spacing: 0) {
                ForEach(section.souls) { soul in
                    row(soul)
                }
            }
            .padding(.horizontal, Spacing.lg)
        }
    }

    @ViewBuilder
    private var results: some View {
        VStack(spacing: 0) {
            if model.canAdd {
                SoulAddRow(name: model.trimmedQuery) { Task { await add(model.trimmedQuery) } }
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
            }
            ForEach(model.results) { soul in
                row(soul)
            }
            .padding(.horizontal, Spacing.lg)
        }
        usual
    }

    private func row(_ soul: SoulWithGlyph) -> some View {
        SoulRow(soul: soul, trailing: trailing(for: soul)) { tapped(soul) }
            .overlay(alignment: .bottom) { Divider().overlay(Color.pebbles.rule) }
    }

    private func trailing(for soul: SoulWithGlyph) -> SoulRow.Trailing {
        if model.mode == .browse { return .chevron }
        return model.isSelected(soul.id) ? .tick : .plus
    }

    private func sectionTitle(_ text: LocalizedStringResource) -> some View {
        Text(text)
            .pebblesFont(.cardHeading)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .padding(.horizontal, Spacing.lg)
            .padding(.top, Spacing.md)
            .padding(.bottom, Spacing.xs)
    }

    // MARK: Actions

    private func tapped(_ soul: SoulWithGlyph) {
        switch model.mode {
        case .browse:
            onOpen(soul)
        case .pick:
            TapHaptics.play(.selection)
            model.toggle(soul.id)
        }
    }

    private func add(_ name: String) async {
        guard !isAdding, let userId = supabase.session?.user.id else { return }
        isAdding = true
        addError = nil
        do {
            let created = try await SoulInlineCreate.create(name: name, userId: userId, client: supabase.client)
            achievements.fireCheck()
            model.didCreate(created)
            onCreated(created)
            await refs.refreshSouls()
        } catch {
            logger.error("inline soul create failed: \(error.localizedDescription, privacy: .private)")
            addError = String(localized: "Couldn't save the soul. Please try again.")
        }
        isAdding = false
    }
}
```

Note: `TapHaptics.play(.selection)` exists (`Features/Record`), as does `refs.souls` being `Equatable` (`SoulWithGlyph: Hashable`).

- [ ] **Step 2: Build, fix any compile error (a generic `Header` with a default needs the `init` below if the compiler complains about the trailing closure)**

If `SoulChooser(model:title:)` without a header fails to infer `Header`, add:

```swift
extension SoulChooser where Header == EmptyView {
    init(model: SoulChooserModel, title: LocalizedStringResource?,
         onOpen: @escaping (SoulWithGlyph) -> Void = { _ in },
         onConfirm: @escaping () -> Void = {},
         onCreated: @escaping (SoulWithGlyph) -> Void = { _ in }) {
        self.init(model: model, title: title, onOpen: onOpen, onConfirm: onConfirm, onCreated: onCreated) { EmptyView() }
    }
}
```

- [ ] **Step 3: Add the French strings** at their alphabetical anchors in `Pebbles/Resources/Localizable.xcstrings` (build once first so the English entries exist, then set each `fr` value and change `"state" : "new"` to `"translated"` on the `en` side too):

| en | fr |
|---|---|
| Usual | Habituels |
| Search souls | Chercher une âme |
| Add a soul | Ajouter une âme |
| Close search | Fermer la recherche |
| Add %@ | Ajouter %@ |
| A drawing can wait | Le dessin peut attendre |
| Done · %lld | OK · %lld |
| Remove %@ | Retirer %@ |
| %lld pebbles | %lld galets |

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Souls/SoulChooser.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): the souls chooser screen

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 1.7: Reference renders for the chooser

**Files:**
- Create: `apps/ios/PebblesTests/References/SoulChooserReferences.swift`
- Create: `apps/ios/PebblesTests/Fixtures/SoulFixtures.swift`

- [ ] **Step 1: Fixtures**

```swift
import Foundation
@testable import Pebbles

/// Eleven people with the counts of the souls canvas (page Converged, screen 01).
enum SoulFixtures {
    static let glyph = Glyph(
        id: SystemGlyph.default, name: nil,
        strokes: [GlyphStroke(d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)],
        viewBox: "0 0 200 200", userId: nil
    )

    static func soul(_ name: String, _ count: Int, _ id: Int) -> SoulWithGlyph {
        SoulWithGlyph(id: UUID(uuidString: String(format: "00000000-0000-4000-8000-%012d", id))!,
                      name: name, glyphId: glyph.id, glyph: glyph, pebblesCount: count)
    }

    static let eleven: [SoulWithGlyph] = [
        soul("Maman", 41, 1), soul("Théo", 33, 2), soul("Léa", 30, 3), soul("Papa", 28, 4),
        soul("Inès", 19, 5), soul("Sam", 12, 6), soul("Mamie Jo", 9, 7), soul("Nour", 8, 8),
        soul("Adèle", 2, 9), soul("Alex", 24, 10), soul("Alex", 22, 11), soul("Antoine", 6, 12),
        soul("Baptiste", 2, 13), soul("Ben", 3, 14), soul("Camille", 29, 15), soul("Chloé", 2, 16)
    ]
}
```

- [ ] **Step 2: The suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The souls chooser (souls spec Screens 01, 02, 04 and 07) at the phone
    /// width, both schemes, English and French on Paper.
    @Suite("Soul chooser references") @MainActor
    struct SoulChooserReferences {
        private static func host(_ model: SoulChooserModel, title: LocalizedStringResource?) -> some View {
            let supabase = SupabaseService()
            return SoulChooser(model: model, title: title)
                .frame(width: ReferenceConfig.phoneWidth, height: 760)
                .background(Color.pebbles.surface)
                .environment(supabase)
                .environment(ReferenceDataService(client: supabase.client))
                .environment(AchievementsService())
        }

        private static func locales(_ scheme: ReferenceScheme) -> Set<ReferenceLocale> {
            scheme == .paper ? [.en, .fr] : [.en]
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pickerTwoChosen(scheme: ReferenceScheme) {
            let souls = SoulFixtures.eleven
            let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: souls,
                                         selection: [souls[0].id, souls[1].id])
            ReferenceImage.assertMatches(Self.host(model, title: "Who was there?"), named: "pickerTwoChosen",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pickerEmpty(scheme: ReferenceScheme) {
            let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: SoulFixtures.eleven)
            ReferenceImage.assertMatches(Self.host(model, title: "Who was there?"), named: "pickerEmpty",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func pickerNobodyByThatName(scheme: ReferenceScheme) {
            let souls = SoulFixtures.eleven
            let model = SoulChooserModel(mode: .pick(allowsSkip: true), souls: souls,
                                         selection: [souls[0].id, souls[1].id])
            model.isSearching = true
            model.query = "Mathilde"
            ReferenceImage.assertMatches(Self.host(model, title: "Who was there?"), named: "pickerNobodyByThatName",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func browse(scheme: ReferenceScheme) {
            let model = SoulChooserModel(mode: .browse, souls: SoulFixtures.eleven)
            ReferenceImage.assertMatches(Self.host(model, title: nil), named: "browse",
                                         config: ReferenceConfig(scheme: scheme, locales: Self.locales(scheme)))
        }
    }
}
```

If `AchievementsService()` has no parameterless init, look at how `PebblesTests/References/HomeReferences.swift` builds its environment and copy that.

- [ ] **Step 3: Record and run**

```bash
cd apps/ios && TEST_RUNNER_PBBLS_RECORD_REFERENCES=1 xcodebuild test -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/References/SoulChooserReferences -quiet 2>&1 | tail -5
ls PebblesTests/References/__References__/SoulChooserReferences
```
Expected: 4 tests pass in record mode; 12 PNGs (4 names × paper en/fr + night en). Open two PNGs and check the tiles are four across, the pills sit beside "Done · 2", the Add row is first on the search render.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/PebblesTests
git commit -m "test(ios): soul chooser fixtures and reference renders

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 1.8: Lint, push, PR

- [ ] **Step 1:** `cd apps/ios && swiftlint` → fix anything in `Features/Souls` and the new tests.
- [ ] **Step 2:** `gh stack push` (or `git push -u origin feat/1518-souls-chooser-ios`), open the PR: title `feat(ios): souls chooser model, atoms and screen`, body `Part of #1518` + the file list, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`.
- [ ] **Step 3:** Arkaik: no view node yet (the chooser has no host). Nothing to move.

---

# Part 2 — People tab on the chooser (branch `feat/1518-people-tab-ios`)

### Task 2.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1518-souls-chooser-ios && git checkout -b feat/1518-people-tab-ios
```

### Task 2.2: Host the chooser in `SoulsListView` (S5 People tab)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

What changes: the `LazyVGrid` of `SoulItem` and its `columns` go; the body hosts `SoulChooser` in `.browse` mode with the prompts in its header; the toolbar loses the Add button (the plus orb replaces it); the `createSoul` intent opens the field; a created soul pushes its page; the row context-menu delete moves to a long-press on the row (`pebblesContextMenu` on `SoulRow` through the chooser is out of scope: keep delete on the soul's page, which `SoulDetailView` already offers — confirm with `grep -n "delete" Pebbles/Features/Profile/Views/SoulDetailView.swift`; if it does not, keep `pendingDeletion` wired to a context menu on each row by passing `onDeleteRequest` through `SoulChooser` as an optional closure applied with `.pebblesContextMenu([.delete { … }])` on `row(_:)`).

- [ ] **Step 1: Replace the state and body**

Replace the `@State private var items`, `isPresentingCreate`, `columns` and the `content` view with:

```swift
    @State private var chooser = SoulChooserModel(mode: .browse, souls: [])
```

and the toolbar with:

```swift
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    PebbleToolbarButton(action: { nav.people.append(.connections) }) {
                        PbblsIconImage(.people, .toolbar)
                    }
                    .accessibilityLabel("Circle")
                }
            }
```

and `content` with:

```swift
    @ViewBuilder
    private var content: some View {
        if isLoading && chooser.souls.isEmpty {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError, chooser.souls.isEmpty {
            ContentUnavailableView {
                Label { Text("Couldn't load souls") } icon: { PbblsIconImage(.people, .hero) }
            } description: {
                Text(loadError)
            } actions: {
                Button("Retry") { Task { await load() } }
            }
        } else {
            ScrollViewReader { proxy in
                SoulChooser(
                    model: chooser,
                    title: nil,
                    onOpen: { nav.people.append(.soul($0.id)) },
                    onCreated: { nav.people.append(.soul($0.id)) }
                ) {
                    if let prompts {
                        onboarding(prompts)
                            .padding(.horizontal, Spacing.lg)
                            .padding(.bottom, Spacing.md)
                    } else if chooser.souls.isEmpty && (promptsFailed || promptsDismissed) {
                        ContentUnavailableView {
                            Label { Text("No souls yet") } icon: { PbblsIconImage(.people, .hero) }
                        } description: {
                            Text("People and beings you tag on your pebbles will appear here.")
                        }
                    }
                }
                .id(TabNavigation.scrollTopAnchor)
                .onChange(of: nav.scrollToTop[.people, default: 0]) {
                    withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
                }
            }
        }
    }
```

`load()` keeps its fetch but writes `chooser.replaceSouls(result)` instead of `items = result`; `SoulRoute(id:known:onChanged:)` receives `known: chooser.souls`. The `.sheet(isPresented: $isPresentingCreate)` goes; the `.onAppear` intent becomes:

```swift
            .onAppear {
                if nav.takeIntent(for: .people) == .createSoul { chooser.isSearching = true }
            }
```

The `EditPebbleSheet` for the "who?" prompt stays as is in this part (part 3 makes its souls row open the new half sheet).

- [ ] **Step 2: Build and run the app on the simulator**, open People: tiles on top, rows below, lens and plus at the bottom, Circle top-left, no Add button. Type a name nobody has → Add row → tapping it pushes the new soul's page.

- [ ] **Step 3: Add the French for `Retry` if missing** (`Réessayer`).

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): people tab on the souls chooser

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 2.3: Update the People reference renders

**Files:**
- Modify: `apps/ios/PebblesTests/References/PeopleReferences.swift`

The three existing renders draw `PeopleOnboardingView` alone and stay valid. Add one render of the tab with prompts above Usual:

```swift
        @Test(arguments: ReferenceScheme.allCases)
        func peopleTabWithPrompts(scheme: ReferenceScheme) throws {
            let supabase = SupabaseService()
            let model = SoulChooserModel(mode: .browse, souls: Array(SoulFixtures.eleven.prefix(2)))
            let view = SoulChooser(model: model, title: nil) {
                Self.view(PeopleOnboardingState.make(try! Self.verbatim(), dismissed: false))
            }
            .frame(width: ReferenceConfig.phoneWidth, height: 760)
            .background(Color.pebbles.surface)
            .environment(supabase)
            .environment(ReferenceDataService(client: supabase.client))
            .environment(AchievementsService())
            ReferenceImage.assertMatches(view, named: "peopleTabWithPrompts",
                                         config: ReferenceConfig(scheme: scheme, locales: scheme == .paper ? [.en, .fr] : [.en]))
        }
```

- [ ] Record it (`TEST_RUNNER_PBBLS_RECORD_REFERENCES=1 … -only-testing:PebblesTests/References/PeopleReferences`), check the PNG, commit:

```bash
git add apps/ios/PebblesTests
git commit -m "test(ios): people tab render with prompts above usual

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 2.4: Lint, push, PR, Arkaik

- [ ] `swiftlint`; `gh stack push`; PR `feat(ios): people tab on the souls chooser`, body `Part of #1518`, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`.
- [ ] Arkaik: `list_nodes` for the iOS People/Souls view node (search "Souls" / "People" with surface iOS) and `update_node` its status to `development` (send the node's existing metadata back whole: `update_node` replaces metadata).

---

# Part 3 — Picker hosts (branch `feat/1518-picker-hosts-ios`)

### Task 3.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1518-people-tab-ios && git checkout -b feat/1518-picker-hosts-ios
```

### Task 3.2: `SoulChooserSheet` (S5 picker and half sheet)

**Files:**
- Create: `apps/ios/Pebbles/Features/Souls/SoulChooserSheet.swift`

```swift
import SwiftUI

/// The picker as a sheet (souls spec S5): `.large` from the Life screen's More
/// tile, `.medium` + `.large` from the edit pebble form, where at half height
/// the tiles answer the usual case and the list peeks underneath. Done writes
/// the selection back; dragging the sheet down keeps the previous one.
struct SoulChooserSheet: View {
    let title: LocalizedStringResource
    let currentSelection: [UUID]
    /// Record flow: "Skip" with nothing chosen. Edit sheet: "Done".
    let allowsSkip: Bool
    let detents: Set<PresentationDetent>
    let onConfirm: ([UUID]) -> Void

    @Environment(ReferenceDataService.self) private var refs
    @Environment(\.dismiss) private var dismiss
    @State private var model: SoulChooserModel?

    var body: some View {
        Group {
            if let model {
                SoulChooser(model: model, title: title, onConfirm: {
                    onConfirm(model.selection)
                    dismiss()
                })
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Color.pebbles.surface)
        .presentationDetents(detents)
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pebbles.surface)
        .task {
            if model == nil {
                model = SoulChooserModel(mode: .pick(allowsSkip: allowsSkip), souls: refs.souls, selection: currentSelection)
            }
        }
    }
}
```

- [ ] Build; commit:

```bash
git add apps/ios/Pebbles/Features/Souls/SoulChooserSheet.swift
git commit -m "feat(ios): souls chooser sheet host by detent

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 3.3: The edit pebble form opens the half sheet

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/SelectedSoulsRow.swift`

- [ ] Replace the `.sheet` with:

```swift
        .sheet(isPresented: $isPresentingPicker) {
            SoulChooserSheet(
                title: "Who was there?",
                currentSelection: soulIds,
                allowsSkip: false,
                detents: [.medium, .large],
                onConfirm: { soulIds = $0 }
            )
        }
```

Update the doc comment (`opens SoulChooserSheet`). The pills in the form (`SoulPill`, `AddSoulPill`) stay: they are the form's summary, not the picker.

- [ ] Fix the known blemish from the souls audit while here: `SelectedSoulsRow.swift:47` drops a freshly created soul because `allSouls` is the form's cached list. With `SoulChooserSheet` reading `refs.souls` and `SoulChooser` calling `refs.refreshSouls()` after a create, the form's `allSouls` (passed from `refs.souls` by `EditPebbleSheet`) updates with it. Verify by creating a soul from the half sheet and seeing its pill after Done.

- [ ] Commit:

```bash
git add apps/ios/Pebbles/Features/Path/SelectedSoulsRow.swift
git commit -m "feat(ios): edit pebble souls open the chooser half sheet

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 3.4: The current souls step uses the chooser inline

**Files:**
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteSoulsStep.swift`

Part 7 deletes this step; until then it must not reference the deleted grid.

- [ ] Replace the body:

```swift
import SwiftUI

/// Who was there. Until the Life screen absorbs it (record-flow D10), the step
/// hosts the chooser inline in pick mode; the scaffold's Skip / Done advances.
struct CompleteSoulsStep: View {
    let model: CompleteFlowModel

    @Environment(ReferenceDataService.self) private var refs
    @State private var chooser: SoulChooserModel?

    var body: some View {
        Group {
            if let chooser {
                SoulChooser(model: chooser, title: nil)
                    .onChange(of: chooser.selection) { _, ids in model.setSouls(ids) }
            } else {
                ProgressView()
            }
        }
        .task {
            if chooser == nil {
                chooser = SoulChooserModel(mode: .pick(allowsSkip: true), souls: refs.souls, selection: model.draft.soulIds)
            }
        }
    }
}
```

and add to `CompleteFlowModel` (next to `toggleSoul`):

```swift
    /// The chooser owns the order; the draft mirrors it.
    func setSouls(_ ids: [UUID]) {
        guard ids != draft.soulIds else { return }
        haptic(.selection)
        draft.soulIds = ids
    }
```

The step's own tray button duplicates the scaffold's Skip/Done; acceptable for the two parts this lives, since part 7 removes the step. Note it in the PR body.

- [ ] Build, commit:

```bash
git add apps/ios/Pebbles/Features/Complete
git commit -m "feat(ios): souls step hosts the chooser until the life screen lands

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 3.5: Delete the old grid

**Files:**
- Delete: `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`, `apps/ios/Pebbles/Features/Path/SoulPickerContent.swift`, `apps/ios/Pebbles/Features/Shared/SoulItem.swift`

- [ ] `grep -rn "SoulPickerSheet\|SoulPickerContent\|SoulItem(" apps/ios/Pebbles apps/ios/PebblesTests` must return only the three files. Then `git rm` them, build, and remove the strings `"Choose souls"`, `"All my souls"`, `"Add the first soul to tag this pebble with"`, `"New soul"`, `"Opens soul creation form"` from the catalog if nothing else uses them (`grep -rn "<string>" apps/ios/Pebbles --include=*.swift`).
- [ ] Commit:

```bash
git commit -am "chore(ios): remove the soul picker grid

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 3.6: Lint, test, push, PR, Arkaik

- [ ] `swiftlint`; run `-only-testing:PebblesTests/CompleteFlowModelTests` and `SoulChooserModelTests` → green.
- [ ] `gh stack push`; PR `feat(ios): souls chooser behind the edit sheet and the souls step`, body `Resolves #1518`, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`.
- [ ] Arkaik: `update_node` the iOS Edit pebble view and the souls step view to `development`.

---

# Part 4 — Capture draft persistence + moment page (branch `feat/1514-moment-page-ios`)

### Task 4.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1518-picker-hosts-ios && git checkout -b feat/1514-moment-page-ios
```

### Task 4.2: The draft and the steps (D1, D2, D4, D5)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureDraft.swift`
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureStep.swift`
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureStep+Copy.swift`
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureFlowModel.swift`
- Test: `apps/ios/PebblesTests/Features/Capture/CaptureFlowModelTests.swift`, `CaptureStepTests.swift`

- [ ] **Step 1: Write the failing tests** (replace the two existing suites' step tests; keep the name clamp and capture-date tests)

`CaptureStepTests.swift`:
```swift
import Testing
@testable import Pebbles

@Suite("CaptureStep")
struct CaptureStepTests {
    @Test("two counted steps: photo then the moment page")
    func order() {
        #expect(CaptureStep.allCases == [.photo, .moment])
        #expect(CaptureStep.photo.next == .moment)
        #expect(CaptureStep.moment.next == nil)
        #expect(CaptureStep.moment.previous == .photo)
        #expect(CaptureStep.photo.dotIndex == 0)
        #expect(CaptureStep.moment.dotIndex == 1)
    }
}
```

In `CaptureFlowModelTests.swift` replace `advance()` with:
```swift
    @Test("photo advances to the moment page, which is the last step")
    func advance() {
        let (model, recorder) = model()
        model.advance()
        #expect(model.step == .moment)
        model.advance()
        #expect(model.step == .moment)
        #expect(recorder.taps == [.advance])
    }

    @Test("the words are clamped to the description limit")
    func descriptionClamp() {
        let (model, _) = model()
        model.setDescription(String(repeating: "x", count: 300))
        #expect(model.draft.description.count == CaptureFlowModel.descriptionLimit)
    }

    @Test("the when chip reads from the photo until the user edits it")
    func whenChip() {
        let (model, _) = model()
        #expect(model.whenSource == .now)
        model.applyCaptureDate(Date(timeIntervalSince1970: 1_700_000_000))
        #expect(model.whenSource == .photo)
        model.setHappenedAt(Date())
        #expect(model.whenSource == .edited)
    }

    @Test("restore lands on the moment page with the saved fields")
    func restore() {
        let (model, _) = model()
        var saved = CaptureDraft()
        saved.name = "Lake"
        saved.description = "Cold water"
        saved.happenedAt = Date(timeIntervalSince1970: 1_700_000_000)
        model.restore(saved, hasEditedDate: true)
        #expect(model.step == .moment)
        #expect(model.draft == saved)
        #expect(model.whenSource == .edited)
    }
```

Delete the `optionalButtonIsSkip` expectation anywhere it appears.

- [ ] **Step 2: Run** `-only-testing:PebblesTests/CaptureFlowModelTests` → compile errors on `.moment`, `setDescription`, `whenSource`, `restore`.

- [ ] **Step 3: Implement**

`CaptureDraft.swift`:
```swift
import Foundation

/// What the moment page asks (record-flow D5): when, a name, a few words. The
/// photo lives on the `SnapUploadCoordinator`, not here.
struct CaptureDraft: Equatable {
    var happenedAt = Date()
    var name = ""
    var description = ""
}
```

`CaptureStep.swift`:
```swift
import Foundation

/// The Capture flow's screens (record-flow D1): the photo, then the moment
/// page. No success screen: saving returns to the origin or chains into the
/// pebble stage.
enum CaptureStep: Int, CaseIterable, Identifiable, Hashable {
    case photo
    case moment

    var id: Int { rawValue }
    var dotIndex: Int { rawValue }
    var next: CaptureStep? { CaptureStep(rawValue: rawValue + 1) }
    var previous: CaptureStep? { rawValue == 0 ? nil : CaptureStep(rawValue: rawValue - 1) }
}
```

`CaptureStep+Copy.swift`:
```swift
import Foundation

extension CaptureStep {
    var title: LocalizedStringResource {
        switch self {
        case .photo:  return "Start with a picture"
        case .moment: return "Your moment"
        }
    }

    var subtitle: LocalizedStringResource? {
        switch self {
        case .photo:  return "Pick one, or write it from memory."
        case .moment: return nil
        }
    }
}
```

`CaptureFlowModel.swift` — add after `nameLimit`:
```swift
    /// Front-end only, as the name. Three visible lines with a counter.
    static let descriptionLimit = 280

    enum WhenSource: Equatable { case now, photo, edited }
```
replace `hasEditedDate` with a computed `whenSource` while keeping the stored flag:
```swift
    /// Set once the user changes the date themselves: a photo's capture date
    /// then never overrides their choice.
    private(set) var hasEditedDate = false
    /// Whether the when chip's date came from the photo.
    private(set) var seededFromPhoto = false

    var whenSource: WhenSource {
        if hasEditedDate { return .edited }
        return seededFromPhoto ? .photo : .now
    }
```
remove `optionalButtonIsSkip`; `applyCaptureDate` sets `seededFromPhoto = true` when it applies; add:
```swift
    func setDescription(_ raw: String) {
        draft.description = String(raw.prefix(Self.descriptionLimit))
    }

    /// A draft from `CaptureDraftStore` after a kill (D2): straight to the page.
    func restore(_ saved: CaptureDraft, hasEditedDate: Bool) {
        draft = saved
        self.hasEditedDate = hasEditedDate
        step = .moment
    }
```

- [ ] **Step 4: Run** both suites → green. `CaptureFlowView` will not compile yet (it references `.when`, `.name`); that is Task 4.5. Build only the test target's dependencies by running the tests; if the app target must compile, temporarily stub (`case .when, .name` → remove) and come back in 4.5.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Capture apps/ios/PebblesTests/Features/Capture
git commit -m "feat(ios): capture draft carries the words; two steps, photo and moment page

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 4.3: `SaveMomentPayload(capture:)` sends the words

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/SaveMomentPayload.swift`
- Test: `apps/ios/PebblesTests/Features/Capture/SaveMomentPayloadCaptureTests.swift`

- [ ] Test (add):
```swift
    @Test("the words go as description, trimmed, omitted when blank")
    func description() {
        var draft = CaptureDraft()
        draft.name = "Lake"
        draft.description = "  Cold water  "
        #expect(SaveMomentPayload(capture: draft, formSnap: nil, userId: userId).description == "Cold water")
        draft.description = "   "
        #expect(SaveMomentPayload(capture: draft, formSnap: nil, userId: userId).description == nil)
    }
```
and change the existing `named()` test's `#expect(payload.description == nil)` to keep holding (the draft there has no words).

- [ ] Implement in the `init(capture:formSnap:userId:)`:
```swift
        let words = draft.description.trimmingCharacters(in: .whitespacesAndNewlines)
        description = words.isEmpty ? nil : words
```
Update the doc comment: "the words only when non-blank".

- [ ] Run `-only-testing:PebblesTests/SaveMomentPayloadCaptureTests` → green. Commit:
```bash
git commit -am "feat(ios): capture sends the moment's words

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 4.4: `CaptureDraftStore` (D2)

**Files:**
- Create: `apps/ios/Pebbles/Services/CaptureDraftStore.swift`
- Test: `apps/ios/PebblesTests/Features/Capture/CaptureDraftStoreTests.swift`
- Modify: `apps/ios/Pebbles/PebblesApp.swift` (inject), and wherever `CompleteDraftStore.clearAll()` is called on sign-out (`grep -rn "clearAll()" apps/ios/Pebbles`) add the same for the capture store.

- [ ] **Step 1: Test**
```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("CaptureDraftStore")
@MainActor
struct CaptureDraftStoreTests {
    private func store() -> (CaptureDraftStore, URL) {
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("capture-draft-\(UUID().uuidString)")
        return (CaptureDraftStore(directory: dir), dir)
    }

    private func draft() -> CaptureDraft {
        var draft = CaptureDraft()
        draft.name = "Lake"
        draft.description = "Cold"
        draft.happenedAt = Date(timeIntervalSince1970: 1_700_000_000)
        return draft
    }

    @Test("round-trips per owner with the snap reference and the edited flag")
    func roundTrip() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID(), other = UUID(), snap = UUID()
        let saved = CaptureDraftSnapshot.Saved(draft: draft(), hasEditedDate: true,
                                               snap: .init(id: snap, storagePath: "u/s"))
        store.save(saved, ownerId: owner)
        #expect(store.load(ownerId: owner) == saved)
        #expect(store.load(ownerId: other) == nil)
    }

    @Test("an empty draft is not kept")
    func empty() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID()
        store.save(.init(draft: CaptureDraft(), hasEditedDate: false, snap: nil), ownerId: owner)
        #expect(store.load(ownerId: owner) == nil)
    }

    @Test("whole-second timestamps survive the file")
    func seconds() {
        let (store, dir) = store()
        defer { try? FileManager.default.removeItem(at: dir) }
        let owner = UUID()
        var d = draft()
        d.happenedAt = Date(timeIntervalSince1970: 1_700_000_000.7)
        store.save(.init(draft: d, hasEditedDate: false, snap: nil), ownerId: owner)
        #expect(store.load(ownerId: owner)?.draft.happenedAt == Date(timeIntervalSince1970: 1_700_000_000))
    }
}
```

- [ ] **Step 2: Implement** (modelled on `CompleteDraftStore`; one file per owner since there is one Capture at a time)
```swift
import Foundation
import Observation
import os

/// Device-local autosave for the unsaved moment page (record-flow D2): name,
/// words, when, whether the user set the date, and the uploaded snap's
/// reference so the page survives a process kill. One file per owner.
@Observable
@MainActor
final class CaptureDraftStore {
    private let directory: URL
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "capture-draft")

    convenience init() {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        self.init(directory: base.appendingPathComponent("capture-draft"))
    }

    init(directory: URL) { self.directory = directory }

    func load(ownerId: UUID) -> CaptureDraftSnapshot.Saved? {
        guard let data = try? Data(contentsOf: fileURL(for: ownerId)) else { return nil }
        do {
            let snapshot = try JSONDecoder().decode(CaptureDraftSnapshot.self, from: data)
            guard snapshot.ownerId == ownerId, !snapshot.saved.isEmpty else {
                clear(ownerId: ownerId)
                return nil
            }
            return snapshot.saved
        } catch {
            logger.warning("discarding unreadable capture draft: \(error.localizedDescription, privacy: .private)")
            clear(ownerId: ownerId)
            return nil
        }
    }

    func save(_ saved: CaptureDraftSnapshot.Saved, ownerId: UUID) {
        guard !saved.isEmpty else {
            clear(ownerId: ownerId)
            return
        }
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let snapshot = CaptureDraftSnapshot(ownerId: ownerId, saved: saved)
            try JSONEncoder().encode(snapshot).write(to: fileURL(for: ownerId), options: .atomic)
        } catch {
            logger.error("capture draft write failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    func clear(ownerId: UUID) { try? FileManager.default.removeItem(at: fileURL(for: ownerId)) }
    func clearAll() { try? FileManager.default.removeItem(at: directory) }

    private func fileURL(for ownerId: UUID) -> URL {
        directory.appendingPathComponent("\(ownerId.uuidString.lowercased()).json")
    }
}

/// The file body. `happened_at` at whole seconds (the standing timestamp rule).
struct CaptureDraftSnapshot: Codable, Equatable {
    struct Snap: Codable, Equatable {
        let id: UUID
        let storagePath: String
        private enum CodingKeys: String, CodingKey { case id; case storagePath = "storage_path" }
    }

    struct Saved: Equatable {
        var draft: CaptureDraft
        var hasEditedDate: Bool
        var snap: Snap?

        var isEmpty: Bool {
            draft.name.trimmingCharacters(in: .whitespaces).isEmpty
                && draft.description.trimmingCharacters(in: .whitespaces).isEmpty
                && snap == nil
        }
    }

    let ownerId: UUID
    var name: String?
    var description: String?
    var happenedAt: String
    var hasEditedDate: Bool
    var snap: Snap?

    private enum CodingKeys: String, CodingKey {
        case name, description, snap
        case ownerId = "owner_id"
        case happenedAt = "happened_at"
        case hasEditedDate = "has_edited_date"
    }

    init(ownerId: UUID, saved: Saved) {
        self.ownerId = ownerId
        name = saved.draft.name.isEmpty ? nil : saved.draft.name
        description = saved.draft.description.isEmpty ? nil : saved.draft.description
        happenedAt = ISO8601Flexible.string(from: saved.draft.happenedAt)
        hasEditedDate = saved.hasEditedDate
        snap = saved.snap
    }

    var saved: Saved {
        var draft = CaptureDraft()
        draft.name = name ?? ""
        draft.description = description ?? ""
        draft.happenedAt = ISO8601Flexible.date(from: happenedAt) ?? Date()
        return Saved(draft: draft, hasEditedDate: hasEditedDate, snap: snap)
    }
}
```
Check `ISO8601Flexible` has `date(from:)` (`grep -n "static func" apps/ios/Pebbles/**/ISO8601Flexible.swift`); if it is named differently (`parse`), use that name.

- [ ] **Step 3: Inject.** In `PebblesApp.swift`, next to `CompleteDraftStore()`, add a `CaptureDraftStore()` `@State` and `.environment(captureDrafts)`; where `completeDrafts.clearAll()` runs on sign-out (`SignedOutPurge`), add `captureDrafts.clearAll()`.

- [ ] **Step 4: Run** `-only-testing:PebblesTests/CaptureDraftStoreTests` → green. Commit:
```bash
git add apps/ios/Pebbles/Services/CaptureDraftStore.swift apps/ios/Pebbles/PebblesApp.swift apps/ios/PebblesTests/Features/Capture/CaptureDraftStoreTests.swift
git add -u apps/ios/Pebbles
git commit -m "feat(ios): device-local draft for the unsaved moment page

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 4.5: `RecordWhenChip` and `RecordMomentStep` (D4, D5)

**Files:**
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordWhenChip.swift`
- Create: `apps/ios/Pebbles/Features/Record/Steps/RecordMomentStep.swift`
- Delete: `apps/ios/Pebbles/Features/Record/Steps/RecordWhenStep.swift` (its `DatePicker` moves into the chip's sheet)

- [ ] **Step 1: `RecordWhenChip`**
```swift
import SwiftUI

/// The When step as a chip (record-flow D4): "Today, 21:10 ▾" or "From your
/// photo · Sun 3 Nov ▾", opening the graphical calendar in a sheet. The EXIF
/// seed and its label stay; a user edit always wins.
struct RecordWhenChip: View {
    @Binding var happenedAt: Date
    let source: CaptureFlowModel.WhenSource

    @State private var isPresented = false

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        Button { isPresented = true } label: {
            HStack(spacing: Spacing.xs + 2) {
                PbblsIconImage(source == .photo ? .sparkle : .calendarDay, size: 16, relativeTo: .subheadline,
                               style: AnyShapeStyle(Color.pebbles.onSecondaryContainer))
                Text(verbatim: label)
                    .pebblesFont(.subheadEmphasized)
                    .foregroundStyle(Color.pebbles.onSecondaryContainer)
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onSecondaryContainer)
            }
            .padding(.horizontal, Spacing.md)
            .frame(height: 36)
            .background(Capsule().fill(Color.pebbles.secondaryContainer))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("When"))
        .accessibilityValue(Text(verbatim: label))
        .sheet(isPresented: $isPresented) {
            NavigationStack {
                DatePicker("When", selection: $happenedAt, displayedComponents: [.date, .hourAndMinute])
                    .datePickerStyle(.graphical)
                    .labelsHidden()
                    .tint(Color.pebbles.primary)
                    .padding(Spacing.lg)
                    .pebblesToolbarTitle("When did it happen?")
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            PebbleToolbarButton("Done") { isPresented = false }
                        }
                    }
                    .pebblesScreen()
            }
            .presentationDetents([.medium, .large])
        }
    }

    /// Today keeps the time; another day shows the weekday and date.
    private var label: String {
        let calendar = SpineCalendar.current
        let day: String
        if calendar.isDateInToday(happenedAt) {
            day = String(localized: "Today")
        } else if calendar.isDateInYesterday(happenedAt) {
            day = String(localized: "Yesterday")
        } else {
            day = happenedAt.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).locale(locale))
        }
        let time = happenedAt.formatted(.dateTime.hour().minute().locale(locale))
        let when = "\(day), \(time)"
        return source == .photo ? String(localized: "From your photo · \(when)") : when
    }
}
```

- [ ] **Step 2: `RecordMomentStep`** (the Ledger page: thumbnail, name, when chip, words, caption)
```swift
import SwiftUI

/// The moment page (record-flow D5, Ledger treatment): the thumbnail with its
/// upload state and "Change photo", the NAME field (focused only when there
/// is no photo), the WHEN chip, A FEW WORDS with a counter and the "+1 karma
/// as a pebble" caption. The buttons live in the scaffold.
struct RecordMomentStep: View {
    let model: CaptureFlowModel
    let snap: FormSnap?
    let blockedMessage: String?
    let onChangePhoto: () -> Void
    let onRetry: () -> Void
    let onRemove: () -> Void

    @State private var name: String
    @State private var words: String
    @FocusState private var focus: Field?

    private enum Field { case name, words }

    init(model: CaptureFlowModel, snap: FormSnap?, blockedMessage: String?,
         onChangePhoto: @escaping () -> Void, onRetry: @escaping () -> Void, onRemove: @escaping () -> Void) {
        self.model = model
        self.snap = snap
        self.blockedMessage = blockedMessage
        self.onChangePhoto = onChangePhoto
        self.onRetry = onRetry
        self.onRemove = onRemove
        _name = State(initialValue: model.draft.name)
        _words = State(initialValue: model.draft.description)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            photoRow
            field("NAME") {
                TextField("Name", text: $name, axis: .vertical)
                    .lineLimit(1...2)
                    .pebblesFont(.nameInputHand)
                    .focused($focus, equals: .name)
                    .submitLabel(.next)
                    .onSubmit { focus = .words }
                    .onChange(of: name) { _, value in
                        let clamped = String(value.prefix(CaptureFlowModel.nameLimit))
                        if clamped != value { name = clamped }
                        model.setName(clamped)
                    }
                    .onChange(of: model.draft.name) { _, value in if value != name { name = value } }
            }
            field("WHEN") {
                RecordWhenChip(
                    happenedAt: Binding(get: { model.draft.happenedAt }, set: { model.setHappenedAt($0) }),
                    source: model.whenSource
                )
            }
            field("A FEW WORDS") {
                VStack(alignment: .trailing, spacing: Spacing.xs) {
                    TextField("What happened?", text: $words, axis: .vertical)
                        .lineLimit(3...6)
                        .pebblesFont(.body)
                        .focused($focus, equals: .words)
                        .onChange(of: words) { _, value in
                            let clamped = String(value.prefix(CaptureFlowModel.descriptionLimit))
                            if clamped != value { words = clamped }
                            model.setDescription(clamped)
                        }
                        .onChange(of: model.draft.description) { _, value in if value != words { words = value } }
                    HStack {
                        Label { Text("+1 karma as a pebble") } icon: {
                            PbblsIconImage(.karmaDot, size: 14, relativeTo: .caption, style: AnyShapeStyle(Color.pebbles.primary))
                        }
                        .pebblesFont(.captionEmphasized)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        Spacer()
                        Text(verbatim: "\(CaptureFlowModel.descriptionLimit - words.count)")
                            .pebblesFont(.captionEmphasized)
                            .monospacedDigit()
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            .opacity(CaptureFlowModel.descriptionLimit - words.count <= 40 ? 1 : 0)
                    }
                }
            }
            if let blockedMessage {
                Text(verbatim: blockedMessage)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            if let error = model.saveError {
                Text(verbatim: error)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.error)
            }
        }
        .onAppear { if snap == nil { focus = .name } }
    }

    private func field<Content: View>(_ label: LocalizedStringResource, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: Spacing.xs + 2) {
            Text(label)
                .pebblesFont(.meta)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            content()
                .padding(Spacing.md)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.pebbles.surfaceContainerLow))
        }
    }

    @ViewBuilder
    private var photoRow: some View {
        switch snap {
        case .pending(let attached):
            HStack(spacing: Spacing.md) {
                thumbnail(attached)
                VStack(alignment: .leading, spacing: 2) {
                    stateLabel(attached.state)
                    Button("Change photo", action: onChangePhoto).pebblesFont(.subheadEmphasized).foregroundStyle(Color.pebbles.primary)
                }
                Spacer(minLength: 0)
                if attached.state == .failed { Button("Retry", action: onRetry).pebblesFont(.subhead) }
                Button(action: onRemove) {
                    Image(systemName: "xmark").foregroundStyle(Color.pebbles.onSurfaceVariant).frame(width: 44, height: 44)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Remove photo")
            }
        case .existing:
            HStack(spacing: Spacing.md) {
                PbblsIconImage(.cameraFocus, size: 32, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                Text("A photo is attached.").pebblesFont(.subhead).foregroundStyle(Color.pebbles.onSurfaceVariant)
                Spacer(minLength: 0)
                Button("Remove", action: onRemove).pebblesFont(.subhead).foregroundStyle(Color.pebbles.error)
            }
        case .none:
            Button(action: onChangePhoto) {
                Label { Text("Add a photo") } icon: { PbblsIconImage(.camera, size: 20, relativeTo: .subheadline) }
                    .pebblesFont(.subheadEmphasized)
                    .foregroundStyle(Color.pebbles.primary)
            }
            .buttonStyle(.plain)
        }
    }

    private func thumbnail(_ attached: AttachedSnap) -> some View {
        ZStack {
            if let image = UIImage(data: attached.localThumb) {
                Image(uiImage: image).resizable().scaledToFill()
            }
            if attached.state == .uploading {
                Circle().trim(from: 0, to: 0.75)
                    .stroke(Color.pebbles.onPrimary, style: StrokeStyle(lineWidth: 3, lineCap: .round))
                    .frame(width: 28, height: 28)
                    .rotationEffect(.degrees(-90))
                    .accessibilityHidden(true)
            }
        }
        .frame(width: 72, height: 72)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.md, style: .continuous))
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func stateLabel(_ state: AttachedSnap.UploadState) -> some View {
        switch state {
        case .uploading: Text("Uploading…").pebblesFont(.subhead).foregroundStyle(Color.pebbles.onSurfaceVariant)
        case .uploaded:  Text("Ready").pebblesFont(.subhead).foregroundStyle(Color.pebbles.onSurfaceVariant)
        case .failed:    Text("Upload failed").pebblesFont(.subhead).foregroundStyle(Color.pebbles.error)
        }
    }
}
```
The ring is static (a 270° arc); a rotating one is a nicety, not required.

- [ ] **Step 3:** `git rm apps/ios/Pebbles/Features/Record/Steps/RecordWhenStep.swift`. Build after Task 4.6 (the flow view still references the old API).

### Task 4.6: `CaptureFlowView` on the two steps (D5, D6, D2)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureFlowView.swift`

- [ ] **Step 1: Environment and state.** Add `@Environment(CaptureDraftStore.self) private var drafts`, `@Environment(\.scenePhase) private var scenePhase`, `@State private var autosaveTask: Task<Void, Never>?`, `@State private var hasRestored = false`.

- [ ] **Step 2: `stepBody`.** The scaffold call becomes:
```swift
        RecordStepScaffold(
            title: model.step.title,
            subtitle: model.step.subtitle,
            action: action(for: model.step),
            contentFillsHeight: model.step == .photo,
            secondaryAction: model.step == .moment && !chainsComplete
                ? RecordStepSecondaryAction(label: "Just save", enabled: canSaveNow,
                                            isLoading: model.isSaving && !savingThenComplete) {
                    Task { await save(thenComplete: false) }
                }
                : nil
        ) { stepContent }
```
`stepContent`'s `.when` and `.name` cases are replaced by:
```swift
        case .moment:
            RecordMomentStep(
                model: model,
                snap: snaps?.formSnap,
                blockedMessage: snapBlockedMessage,
                onChangePhoto: { model.back() },
                onRetry: { if let userId = currentUserId, let snaps { Task { await snaps.retryCurrent(userId: userId) } } },
                onRemove: {
                    pickedPhoto = nil
                    if let userId = currentUserId, let snaps { Task { await snaps.removePending(userId: userId) } }
                }
            )
```
`action(for:)` becomes:
```swift
        case .photo:
            return .primary("Just write", enabled: true, loading: false) { model.advance() }
        case .moment:
            return .primary("Save & make it a pebble", enabled: canSaveNow, loading: model.isSaving && (chainsComplete || savingThenComplete)) {
                Task { await save(thenComplete: true) }
            }
```
(The photo step's own fixed-slot controls arrive in part 5; until then "Just write" is the floating pill.) `failSave` jumps to `.moment` instead of `.name`.

- [ ] **Step 3: Autosave and restore.** On `capture`, add:
```swift
        .task { restoreIfNeeded() }
        .onChange(of: model.draft) { _, _ in scheduleAutosave() }
        .onChange(of: model.hasEditedDate) { _, _ in scheduleAutosave() }
        .onChange(of: snaps?.formSnap) { _, _ in scheduleAutosave() }
        .onChange(of: scenePhase) { _, phase in if phase != .active { flushAutosave() } }
```
and the helpers:
```swift
    private var savedSnap: CaptureDraftSnapshot.Snap? {
        guard let userId = currentUserId else { return nil }
        switch snaps?.formSnap {
        case .existing(let id, let path): return .init(id: id, storagePath: path)
        case .pending(let attached) where attached.state == .uploaded:
            return .init(id: attached.id, storagePath: attached.storagePrefix(userId: userId))
        default: return nil
        }
    }

    private func scheduleAutosave() {
        guard hasRestored else { return }
        autosaveTask?.cancel()
        autosaveTask = Task {
            try? await Task.sleep(for: .milliseconds(800))
            guard !Task.isCancelled else { return }
            flushAutosave()
        }
    }

    private func flushAutosave() {
        autosaveTask?.cancel()
        guard let userId = currentUserId, completing == nil else { return }
        drafts.save(.init(draft: model.draft, hasEditedDate: model.hasEditedDate, snap: savedSnap), ownerId: userId)
    }

    /// A page the user was on when the app died comes back as it was (D2).
    private func restoreIfNeeded() {
        guard !hasRestored else { return }
        hasRestored = true
        guard let userId = currentUserId, let saved = drafts.load(ownerId: userId) else { return }
        model.restore(saved.draft, hasEditedDate: saved.hasEditedDate)
        if let snap = saved.snap {
            snaps?.seedExisting(.existing(id: snap.id, storagePath: snap.storagePath))
        }
    }
```
`save()` success and `discard()` both call `drafts.clear(ownerId:)` (after `snaps?.markCommitted()` and before `dismiss()`). `handleClose`'s "Keep this moment?" dialog stays.

A restored `.existing` snap that the user then removes: `removePending` is for pending snaps; for `.existing` call `snaps?.detachExisting()` in `onRemove` when `formSnap` is `.existing`.

- [ ] **Step 4: Build, run on the simulator:** Path "+" → grid → "Just write" → moment page: name focused, chip "Today, hh:mm", words with counter at 40 left, "Save & make it a pebble" primary, "Just save" under it. Kill the app mid-page, relaunch, reopen "+": the page is back with its fields. Save: the moment appears on the Path with its words on the bare page.

- [ ] **Step 5: French strings** (`Your moment` → `Ton moment`; `Pick one, or write it from memory.` → `Choisis-en une, ou écris de mémoire.`; `Just write` → `Juste écrire`; `Just save` → `Juste enregistrer`; `Save & make it a pebble` → `Enregistrer et en faire un galet`; `NAME` → `NOM`; `WHEN` → `QUAND`; `A FEW WORDS` → `QUELQUES MOTS`; `What happened?` → `Que s’est-il passé ?`; `+1 karma as a pebble` → `+1 karma en galet`; `Change photo` → `Changer la photo`; `A photo is attached.` → `Une photo est attachée.`; `Today` → `Aujourd’hui`; `Yesterday` → `Hier`; `From your photo · %@` → `D’après ta photo · %@`; `When did it happen?` exists; `Ready` → `Prête`).

- [ ] **Step 6: Commit**
```bash
git add -A apps/ios/Pebbles/Features/Capture apps/ios/Pebbles/Features/Record apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): the moment page with the when chip, the words and two save paths

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 4.7: Lint, test, push, PR, Arkaik

- [ ] `swiftlint`; run `CaptureFlowModelTests`, `CaptureStepTests`, `CaptureDraftStoreTests`, `SaveMomentPayloadCaptureTests` → green.
- [ ] `gh stack push`; PR `feat(ios): moment page and capture draft persistence`, body `Part of #1514`, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`.
- [ ] Arkaik: the iOS Capture view nodes (photo, when, name) → the When node is removed or marked deprecated per the `arkaik` skill; the name step node renamed "Moment page"; all to `development`.

---

# Part 5 — Photo step (branch `feat/1514-photo-step-ios`)

### Task 5.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1514-moment-page-ios && git checkout -b feat/1514-photo-step-ios
```

### Task 5.2: Tonal button style and the scaffold's fixed slot (D3)

**Files:**
- Create: `apps/ios/Pebbles/Components/Buttons/PebblesTonalButtonStyle.swift`
- Modify: `apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift`

- [ ] `PebblesTonalButtonStyle`:
```swift
import SwiftUI

/// The quieter pill: primary container fill, on-primary-container label. The
/// photo step's "Just write" sits in it above the library grid.
struct PebblesTonalButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled
    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 48

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .pebblesFont(.buttonLabel)
            .foregroundStyle(Color.pebbles.onPrimaryContainer)
            .frame(maxWidth: .infinity)
            .frame(minHeight: minHeight)
            .background(Capsule().fill(Color.pebbles.primaryContainer))
            .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.4)
    }
}
```

- [ ] Scaffold: add `var fixedSlot: AnyView? = nil` (documented: "Drawn between the title and a `contentFillsHeight` content, never scrolled. The photo step's two controls."). In the `contentFillsHeight` branch, wrap: `VStack(spacing: Spacing.md) { if let fixedSlot { fixedSlot.padding(.horizontal, Spacing.lg) }; content()… }`. The floating `action` overlay becomes optional: when `fixedSlot != nil` and `action == nil` nothing floats.

- [ ] Build, commit:
```bash
git add apps/ios/Pebbles/Components/Buttons/PebblesTonalButtonStyle.swift apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift
git commit -m "feat(ios): tonal button style and a fixed slot above a filling step

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 5.3: The photo step: albums on, two controls, auto-advance (D3)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Record/Steps/RecordPhotoStep.swift`
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureFlowView.swift`

- [ ] **Step 1: The picker** re-enables the album switcher and loses the status card (the moment page shows the upload now):
```swift
        PhotosPicker(selection: $selection, matching: .images, preferredItemEncoding: .current,
                     label: { Text("Add a photo") })
            .photosPickerStyle(.inline)
            // The album switcher stays (Recents ▾ / Albums); search, the
            // selection bar and the staging area belong to the modal it is not.
            .photosPickerDisabledCapabilities([.search, .selectionActions, .stagingArea])
            .photosPickerAccessoryVisibility(.visible, edges: .top)
            .photosPickerAccessoryVisibility(.hidden, edges: .bottom)
```
`RecordPhotoStep`'s body becomes `picker` only (keep the `.existing` branch `attachedWithoutThumb`); remove `statusCard` and its helpers. Update the doc comment: it auto-advances now.

- [ ] **Step 2: The two controls** in `CaptureFlowView.stepBody`, passed as `fixedSlot` for the photo step:
```swift
            fixedSlot: model.step == .photo ? AnyView(photoControls) : nil
```
with
```swift
    private var photoControls: some View {
        HStack(spacing: Spacing.sm) {
            Button("Just write") { model.advance() }
                .buttonStyle(PebblesTonalButtonStyle())
            Button { showsCamera = true } label: {
                PbblsIconImage(.camera, size: 22, style: AnyShapeStyle(Color.pebbles.onPrimaryContainer))
                    .frame(width: 48, height: 48)
                    .background(Circle().fill(Color.pebbles.primaryContainer))
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Take a photo")
        }
    }
```
and `action(for: .photo)` returns `nil` (the scaffold's `action` becomes optional in the Capture call; it already is `RecordStepAction?`).

- [ ] **Step 3: Camera inside the flow.** `@State private var showsCamera = false`; on `capture`:
```swift
        .fullScreenCover(isPresented: $showsCamera) {
            CameraPicker(
                onCapture: { image, shutter in
                    showsCamera = false
                    guard let userId = currentUserId, let snaps, let data = image.jpegData(compressionQuality: 0.9) else { return }
                    model.applyCaptureDate(shutter)
                    Task { await snaps.handleCaptured(data, userId: userId) }
                },
                onCancel: { showsCamera = false }
            )
            .ignoresSafeArea()
        }
```
`CameraPicker` lives in `Features/Home/Shortcuts/`; check its `onCapture` signature there and match it.

- [ ] **Step 4: Auto-advance** 0.4 s after the thumbnail lands:
```swift
        .onChange(of: snaps?.formSnap) { _, snap in
            guard model.step == .photo, case .pending = snap else { return }
            Task {
                try? await Task.sleep(for: .milliseconds(400))
                if model.step == .photo { model.advance() }
            }
        }
```
Coming back from the moment page ("Change photo" → `model.back()`) and picking again swaps the snap, so the same `onChange` advances again.

- [ ] **Step 5: Simulator run:** "+" → the grid shows "Recents ▾" and the albums; pick → 0.4 s → moment page with the thumbnail and its ring; "Change photo" → grid; Camera → shutter → moment page. "Just write" → page with the name focused.

- [ ] **Step 6: French:** `Take a photo` → `Prendre une photo`.

- [ ] **Step 7: Commit, lint, push, PR, Arkaik**
```bash
git add -A apps/ios/Pebbles/Features/Record apps/ios/Pebbles/Features/Capture apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): photo step with albums, just write, camera and auto-advance

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```
PR `feat(ios): the photo step on the instagram model`, body `Part of #1514`, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`. Arkaik: Capture photo node → `development`.

---

# Part 6 — Valence: no seed, labels, ghost, help, Neutral (branch `feat/1514-valence-ios`)

### Task 6.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1514-photo-step-ios && git checkout -b feat/1514-valence-ios
```

### Task 6.2: The model loses its seed (D7)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowModel.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteValenceStep.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift` (restore guard)
- Test: `apps/ios/PebblesTests/Features/Complete/CompleteFlowModelTests.swift`

- [ ] **Step 1: Tests.** Delete every test that calls `seedValenceIfNeeded` or expects `.neutralMedium` on arrival; add:
```swift
    @Test("the valence step arrives empty and Continue is gated on a pick")
    func noSeed() {
        let (model, recorder) = model()
        #expect(model.draft.valence == nil)
        #expect(!model.isAnswered)
        model.advance()
        #expect(model.step == .valence)
        #expect(recorder.taps == [.warning])
        model.select(valence: .highlightMedium)
        #expect(model.isAnswered)
        model.advance()
        #expect(model.step == .emotion)
    }

    @Test("the autosave is the draft, nothing is held back")
    func autosaveIsDraft() {
        let (model, _) = model()
        model.select(valence: .lowlightSmall)
        #expect(model.draftToAutosave == model.draft)
    }
```
(Adapt the helper `model()` to whatever the file already uses.)

- [ ] **Step 2: Run** `-only-testing:PebblesTests/CompleteFlowModelTests` → fails on `isValenceConfirmed`/`seedValenceIfNeeded` references or on the seed.

- [ ] **Step 3: Implement.** In `CompleteFlowModel`: delete `isValenceConfirmed`, `seedValenceIfNeeded()`, the `if step == .valence { isValenceConfirmed = true }` line in `advance()`, and the `isValenceConfirmed = true` in `select(valence:)`. `draftToAutosave` becomes `var draftToAutosave: CompleteDraft { draft }`. `restore` drops its two `isValenceConfirmed` / seed lines. In `CompleteValenceStep` remove `.onAppear { model.seedValenceIfNeeded() }` and the "parked on neutral-medium" comment. In `CompleteFlowView.restoreIfReady()` the guard becomes `guard model.draft.isEmpty, model.step == .valence else { return }`.

- [ ] **Step 4: Run** → green. Commit:
```bash
git add apps/ios/Pebbles/Features/Complete apps/ios/PebblesTests/Features/Complete
git commit -m "feat(ios): the valence step no longer preselects

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 6.3: The neutral word and the span words (D7)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Valence.swift`
- Test: add to `apps/ios/PebblesTests/Features/Complete/CompleteStepTests.swift` or a new `ValenceCopyTests.swift`

- [ ] Test:
```swift
import Testing
@testable import Pebbles

@Suite("Valence copy")
struct ValenceCopyTests {
    @Test("the neutral headline word is Neutral and the span words name the size")
    func words() {
        #expect(String(localized: Valence.neutralMedium.headline.word) == "Neutral")
        #expect(String(localized: Valence.highlightSmall.spanWord) == "day")
        #expect(String(localized: Valence.highlightMedium.spanWord) == "week")
        #expect(String(localized: Valence.highlightLarge.spanWord) == "month")
    }
}
```

- [ ] Implement: `headlineWord`'s `.neutral` returns `"Neutral"` (delete the "nobody calls their afternoon a neutral" comment, replace with: `/// "Neutral" (D7, 2026-11-05): "Moment" now names the thing a pebble is made from.`). Add:
```swift
    /// The size as one lowercase word, for the summary pill ("Highlight · week").
    var spanWord: LocalizedStringResource {
        switch sizeGroup {
        case .small:  return "day"
        case .medium: return "week"
        case .large:  return "month"
        }
    }
```
French: `Neutral` → `Neutre` (already present as `shortLabel`; check the `fr` of the `"Neutral"` key is `Neutre`), `day` → `jour`, `week` → `semaine`, `month` → `mois`. Drop the `"Moment"` key from the catalog only if nothing else uses it (`grep -rn '"Moment"' apps/ios/Pebbles --include=*.swift`).

- [ ] Run, commit:
```bash
git add -A apps/ios/Pebbles/Features/Path/Models/Valence.swift apps/ios/PebblesTests apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): neutral is the neutral word; span words for the pill

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 6.4: Axis labels and the ghost caption (D7)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/ValencePickerContent.swift`

- [ ] In `fan`, overlay the labels on the reference canvas:
```swift
        .overlay(alignment: .bottom) { columnLabels }
        .overlay(alignment: .leading) { rowLabels }
```
```swift
    /// Lowlight · Neutral · Highlight under the fan, Month · Week · Day up its
    /// left margin (D7). Meta type in the variant ink; VoiceOver already hears
    /// both axes in each stone's label, so these are decorative.
    private var columnLabels: some View {
        HStack {
            Text(ValencePolarity.lowlight.axisLabel)
            Spacer()
            Text(ValencePolarity.neutral.axisLabel)
            Spacer()
            Text(ValencePolarity.highlight.axisLabel)
        }
        .pebblesFont(.meta)
        .foregroundStyle(Color.pebbles.onSurfaceVariant)
        .padding(.horizontal, Spacing.sm)
        .offset(y: Spacing.lg)
        .accessibilityHidden(true)
    }

    private var rowLabels: some View {
        VStack(alignment: .leading) {
            Text(ValenceSizeGroup.large.axisLabel)
            Spacer()
            Text(ValenceSizeGroup.medium.axisLabel)
            Spacer()
            Text(ValenceSizeGroup.small.axisLabel)
        }
        .pebblesFont(.meta)
        .foregroundStyle(Color.pebbles.onSurfaceVariant)
        .padding(.vertical, Spacing.xl)
        .offset(x: -Spacing.xxl)
        .accessibilityHidden(true)
    }
```
Add to `Valence.swift`:
```swift
extension ValencePolarity {
    var axisLabel: LocalizedStringResource {
        switch self {
        case .lowlight:  return "Lowlight"
        case .neutral:   return "Neutral"
        case .highlight: return "Highlight"
        }
    }
}

extension ValenceSizeGroup {
    var axisLabel: LocalizedStringResource {
        switch self {
        case .small:  return "Day"
        case .medium: return "Week"
        case .large:  return "Month"
        }
    }
}
```
The row labels sit in the fan's left gutter: the fan is 341 wide in a 393 − 2×17 = 359 column, so there is room on wider phones; on 375 pt devices `offset(x: -Spacing.xxl)` clips. Guard with `GeometryReader`-free logic: give the fan `.padding(.leading, Spacing.xxl)` only when `UIScreen.main.bounds.width >= 390` is **not** allowed (no UIScreen in views); instead put the row labels *inside* the canvas at `x = 4` with `.offset(x: 4)` and no negative offset, over the stones' left margin, which the fan's `halfSpread` leaves empty (the leftmost small stone starts at 341/2 − 62 − 20 ≈ 88). Use that.

- [ ] Caption when nothing is chosen:
```swift
    @ViewBuilder
    private var caption: some View {
        Group {
            if let selected {
                ValenceRollView(valence: selected, onChange: onSelect)
            } else {
                VStack(spacing: Spacing.sm) {
                    GhostStone(width: 56)
                    Text("Swipe or tap to choose")
                        .pebblesFont(.cardHeading)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: Self.captionHeight, alignment: .bottom)
        .animation(nil, value: selected)
    }
```
`GhostStone` is in `Features/Path/Card/BareMomentPage.swift` and is not private. Delete the "The step arrives already parked" comment.

- [ ] French: `Day` → `Jour`, `Week` → `Semaine`, `Month` → `Mois`, `Swipe or tap to choose` → `Glisse ou touche pour choisir`. Build, check the fan in the simulator in both widths (iPhone SE 3rd gen simulator for 375 pt), commit:
```bash
git commit -am "feat(ios): axis labels and a ghost caption on the valence fan

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 6.5: The help sheet and the callout (D8)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Valence/ValenceHelpSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteValenceStep.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift`

- [ ] Sheet:
```swift
import SwiftUI

/// "Two questions, one stone." (D8): opens once over the first valence step of
/// an account with no completed pebble, then lives behind "(i) How to read
/// this". An annotated fan with the chosen stone lit, four lines, Got it.
struct ValenceHelpSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ValencePickerContent(selected: .highlightMedium, onSelect: { _ in })
                .allowsHitTesting(false)
                .scaleEffect(0.8)
                .frame(height: 300)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                line("Two questions, one stone.")
                line("Left to right: lowlight, neutral, highlight.")
                line("Bottom to top: did it fill your day, your week, your month? Bigger moment, bigger stone.")
                line("No wrong stone. You can change it later.")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button("Got it") { dismiss() }
                .buttonStyle(PebblesPrimaryButtonStyle())
        }
        .padding(Spacing.lg)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pebbles.surface)
    }

    private func line(_ text: LocalizedStringResource) -> some View {
        Text(text)
            .pebblesFont(.body)
            .foregroundStyle(Color.pebbles.onSurface)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// The per-account seen flag, so a back-out never reopens the sheet.
enum ValenceHelpSeen {
    static func key(_ owner: UUID) -> String { "valenceHelpSeen.\(owner.uuidString.lowercased())" }
    static func seen(_ owner: UUID) -> Bool { UserDefaults.standard.bool(forKey: key(owner)) }
    static func markSeen(_ owner: UUID) { UserDefaults.standard.set(true, forKey: key(owner)) }
}
```

- [ ] `CompleteValenceStep`:
```swift
struct CompleteValenceStep: View {
    let model: CompleteFlowModel
    /// True on an account with no completed pebble (read once by the flow).
    let isFirstPebble: Bool

    @Environment(SupabaseService.self) private var supabase
    @State private var showsHelp = false

    var body: some View {
        VStack(spacing: Spacing.md) {
            ValencePickerContent(selected: model.draft.valence) { model.select(valence: $0) }
            Button { showsHelp = true } label: {
                Label { Text("How to read this") } icon: { Image(systemName: "info.circle") }
                    .pebblesFont(.captionEmphasized)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
            .buttonStyle(.plain)
        }
        .sheet(isPresented: $showsHelp) { ValenceHelpSheet() }
        .onAppear {
            guard isFirstPebble, let owner = supabase.session?.user.id, !ValenceHelpSeen.seen(owner) else { return }
            ValenceHelpSeen.markSeen(owner)
            showsHelp = true
        }
    }
}
```

- [ ] `CompleteFlowView`: `@State private var isFirstPebble = false`; in `.task`, before `restoreIfReady()`:
```swift
            if let moments = try? await MomentsService(client: supabase.client).pathMoments() {
                isFirstPebble = !moments.contains(where: \.isCompleted)
            }
```
and pass `isFirstPebble: isFirstPebble` where `CompleteValenceStep` is built (in `CompleteStepContent`, add the parameter and thread it from the view).

- [ ] French: `Two questions, one stone.` → `Deux questions, un galet.`; `Left to right: lowlight, neutral, highlight.` → `De gauche à droite : ombre, neutre, lumière.`; `Bottom to top: did it fill your day, your week, your month? Bigger moment, bigger stone.` → `De bas en haut : a-t-il rempli ta journée, ta semaine, ton mois ? Plus grand le moment, plus gros le galet.`; `No wrong stone. You can change it later.` → `Pas de mauvais galet. Tu pourras le changer.`; `Got it` → `Compris`; `How to read this` → `Comment lire ceci`. Check what the existing FR uses for lowlight/highlight (`grep -n '"Lowlight"' -A 12 Localizable.xcstrings`) and reuse those words instead of "ombre/lumière" if they differ.

- [ ] Build, simulator: a fresh account's first valence step opens the sheet; back out and in → no sheet; "(i)" reopens it. Commit:
```bash
git add -A apps/ios/Pebbles apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): valence help sheet on the first pebble and the how-to callout

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 6.6: The emotion Retry state (D9)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/EmotionPickerContent.swift`

- [ ] In `body`, the `groups.isEmpty` branch becomes:
```swift
            if groups.isEmpty {
                if palettes.didFinishLoading {
                    VStack(spacing: Spacing.sm) {
                        Text("Couldn't load feelings")
                            .pebblesFont(.callout)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        Button("Retry") { Task { await palettes.load() } }
                            .pebblesFont(.bodyEmphasized)
                            .foregroundStyle(Color.pebbles.primary)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 32)
                } else {
                    ProgressView().frame(maxWidth: .infinity).padding(.vertical, 32)
                }
            }
```
`EmotionPaletteService.didFinishLoading` and `load()` exist (`Pebbles/Services/EmotionPaletteService.swift`).

- [ ] French: `Couldn't load feelings` → `Impossible de charger les émotions`. Commit:
```bash
git commit -am "feat(ios): retry when the feelings do not load

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 6.7: Lint, test, push, PR, Arkaik

- [ ] `swiftlint`; `CompleteFlowModelTests`, `ValenceCopyTests`, `ValenceFanLayoutTests` → green.
- [ ] PR `feat(ios): valence without a preselection, with labels and help`, `Part of #1514`, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`. Arkaik: the valence and emotion step nodes → `development`.

---

# Part 7 — Life screen + summary pill (branch `feat/1514-life-screen-ios`)

### Task 7.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1514-valence-ios && git checkout -b feat/1514-life-screen-ios
```

### Task 7.2: Steps and draft: three steps, domains as a capped set (D1, D10)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteStep.swift`, `CompleteStep+Copy.swift`, `CompleteStepContent.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteDraft.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowModel.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/CompletePebblePayload.swift`
- Modify: `apps/ios/Pebbles/Services/CompleteDraftStore.swift` (snapshot `domainIds` round-trips the whole array)
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` (`Picker("Domain", selection:)` binds to the first id)
- Delete: `apps/ios/Pebbles/Features/Complete/Steps/CompleteSoulsStep.swift`, `CompleteCollectionStep.swift`, `CompletePrivacyStep.swift`, `CompleteDomainStep.swift`
- Tests: `PebblesTests/Features/Complete/CompleteStepTests.swift`, `CompleteDraftTests.swift`, `CompleteFlowModelTests.swift`, `CompletePebblePayloadFromDraftTests.swift`, `PebblesTests/Moments/CompleteDraftFromPebbleTests.swift`, `CompleteDraftStoreTests.swift`

- [ ] **Step 1: Tests.** `CompleteStepTests`:
```swift
    @Test("valence, emotion, life, then success; three counted")
    func order() {
        #expect(CompleteStep.allCases == [.valence, .emotion, .life, .success])
        #expect(CompleteStep.counted == [.valence, .emotion, .life])
        #expect(CompleteStep.success.dotIndex == nil)
        #expect(CompleteStep.life.next == .success)
    }
```
`CompleteDraftTests` (replace domain-id expectations):
```swift
    @Test("complete needs a valence, an emotion and at least one domain; three domains at most")
    func complete() {
        var draft = CompleteDraft()
        draft.valence = .highlightSmall
        draft.emotionId = UUID()
        #expect(!draft.isComplete)
        draft.domainIds = [UUID()]
        #expect(draft.isComplete)
    }
```
`CompleteFlowModelTests` add:
```swift
    @Test("domains toggle up to three, in tap order; the fourth is refused with a warning")
    func toggleDomain() {
        let (model, recorder) = model()
        let ids = (0..<4).map { _ in UUID() }
        ids.forEach { model.toggleDomain($0) }
        #expect(model.draft.domainIds == Array(ids.prefix(3)))
        #expect(recorder.taps.last == .warning)
        model.toggleDomain(ids[0])
        #expect(model.draft.domainIds == [ids[1], ids[2]])
    }

    @Test("life is answered with a domain; souls are optional")
    func lifeAnswered() {
        let (model, _) = model()
        model.go(to: .life)
        #expect(!model.isAnswered)
        model.toggleDomain(UUID())
        #expect(model.isAnswered)
    }
```
`CompletePebblePayloadFromDraftTests`: the draft sets `domainIds = [a, b]` and expects `payload.domainIds == [a, b]`; the prefill test sets `prefill = Prefill(domainIds: [a], collectionId: nil)` with `domainIds = [a]` and expects `payload.domainIds == nil`. `CompleteDraftFromPebbleTests`: `draft.domainIds == pebble.domains.map(\.id)` (all of them, cap 3 by `prefix(3)`). `CompleteDraftStoreTests`: `draft.domainIds = [UUID(), UUID()]` round-trips both.

- [ ] **Step 2: Implement.**

`CompleteStep`:
```swift
enum CompleteStep: Int, CaseIterable, Identifiable, Hashable {
    case valence, emotion, life, success
    var id: Int { rawValue }
    static var counted: [CompleteStep] { allCases.filter { $0 != .success } }
    var dotIndex: Int? { self == .success ? nil : rawValue }
    var next: CompleteStep? { CompleteStep(rawValue: rawValue + 1) }
    var previous: CompleteStep? { rawValue == 0 ? nil : CompleteStep(rawValue: rawValue - 1) }
}
```
(`isOptional` goes: nothing is optional any more.) `+Copy`: titles `"How did it land?"`, `"What did you feel?"`, `"What part of life?"`, `"Make it richer"`; subtitles all `nil` (the pill replaces them; the "Moment saved · 3 questions…" header is Task 7.5).

`CompleteDraft`: `var domainIds: [UUID] = []`, `Prefill.domainIds: [UUID]`, `isComplete` uses `!domainIds.isEmpty`, `isEmpty` uses `domainIds.isEmpty`, `init(from pebble:)` sets `domainIds = Array(pebble.domains.map(\.id).prefix(3))` and the prefill. `static let domainCap = 3`.

`CompleteFlowModel`: `hasAnswer` becomes
```swift
        switch step {
        case .success: return true
        case .valence: return draft.valence != nil
        case .emotion: return draft.emotionId != nil
        case .life:    return !draft.domainIds.isEmpty
        }
```
`isAnswered` is `hasAnswer(for: step)`; `optionalButtonIsSkip` goes; `select(domainId:)` and `select(collectionId:)` are replaced by:
```swift
    func toggleDomain(_ id: UUID) {
        if let index = draft.domainIds.firstIndex(of: id) {
            haptic(.selection)
            draft.domainIds.remove(at: index)
        } else if draft.domainIds.count < CompleteDraft.domainCap {
            haptic(.selection)
            draft.domainIds.append(id)
        } else {
            haptic(.warning)
        }
    }

    func select(collectionId: UUID?) {
        haptic(.selection)
        draft.collectionId = collectionId
    }
```
`firstGap()` returns `.life` as the last fallback. `commitAndAdvance` stays for the emotion.

`CompletePebblePayload.init?(momentId:draft:)`: `let domainIds = draft.domainIds; guard !domainIds.isEmpty …`; prefill compare `prefill.domainIds == domainIds` → `self.domainIds = nil` else `domainIds`.

`CompleteDraftSnapshot`: `domainIds = draft.domainIds.isEmpty ? nil : draft.domainIds`; `draft.domainIds = domainIds ?? []`.

`PebbleFormView` line ~65: `Picker("Domain", selection: Binding(get: { draft.domainIds.first }, set: { draft.domainIds = $0.map { [$0] } ?? [] }))`. `EditPebbleSheet` has no direct `domainId` use.

`CompleteStepContent`:
```swift
        switch step {
        case .valence: CompleteValenceStep(model: model, isFirstPebble: isFirstPebble)
        case .emotion: CompleteEmotionStep(model: model)
        case .life:    CompleteLifeStep(model: model)
        case .success: EmptyView()
        }
```
`git rm` the four step files. `CompleteFlowView.action(for:)` becomes:
```swift
        switch step {
        case .emotion, .success: return nil
        case .valence: return .primary("Continue", enabled: model.isAnswered, loading: false) { model.advance() }
        case .life:
            let count = model.draft.domainIds.count
            return .primary(count > 1 ? "Make it a pebble (\(count))" : "Make it a pebble",
                            enabled: model.draft.isComplete, loading: model.isCompleting) { Task { await complete() } }
        }
```
(`CompleteLifeStep` is Task 7.3; stub it as `Text("")` to compile tests first.)

- [ ] **Step 3: Run** the six suites → green. Commit:
```bash
git add -A apps/ios/Pebbles apps/ios/PebblesTests
git commit -m "feat(ios): three pebble steps, domains as a capped set

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 7.3: `RecentDomains` and `CompleteLifeStep` (D10)

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/RecentDomains.swift`
- Create: `apps/ios/Pebbles/Features/Complete/Steps/CompleteLifeStep.swift`
- Create: `apps/ios/Pebbles/Features/Souls/UsualSoulsStrip.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/RecentDomainsTests.swift`

The spec's RECENT row is "the user's last domains". `path_moments` carries no domains, so recency is device-local: the last five domain ids this account picked here, newest first. Say so in a doc comment.

- [ ] **Step 1: Test**
```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("RecentDomains")
struct RecentDomainsTests {
    @Test("newest first, deduplicated, five at most, per owner")
    func record() {
        let defaults = UserDefaults(suiteName: "RecentDomainsTests-\(UUID().uuidString)")!
        let owner = UUID(), other = UUID()
        let ids = (0..<7).map { _ in UUID() }
        let recent = RecentDomains(defaults: defaults)
        recent.record(Array(ids[0..<3]), owner: owner)
        recent.record([ids[2], ids[3], ids[4], ids[5], ids[6]], owner: owner)
        #expect(recent.ids(owner: owner) == [ids[2], ids[3], ids[4], ids[5], ids[6]])
        #expect(recent.ids(owner: other).isEmpty)
    }
}
```

- [ ] **Step 2: Implement**
```swift
import Foundation

/// The RECENT row of the Life screen (record-flow D10): the last domains this
/// account picked here, newest first, five at most. Device-local: the Path's
/// summaries carry no domains, and recency from the server would need an RPC
/// the spec defers.
struct RecentDomains {
    static let cap = 5
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) { self.defaults = defaults }

    private func key(_ owner: UUID) -> String { "recentDomains.\(owner.uuidString.lowercased())" }

    func ids(owner: UUID) -> [UUID] {
        (defaults.stringArray(forKey: key(owner)) ?? []).compactMap(UUID.init(uuidString:))
    }

    func record(_ picked: [UUID], owner: UUID) {
        var ordered: [UUID] = []
        for id in picked + ids(owner: owner) where !ordered.contains(id) { ordered.append(id) }
        defaults.set(Array(ordered.prefix(Self.cap)).map(\.uuidString), forKey: key(owner))
    }
}
```

- [ ] **Step 3: `UsualSoulsStrip`**
```swift
import SwiftUI

/// "Who was there?" on the Life screen (record-flow D10, souls spec S5): the
/// four most-tagged people as tiles that toggle in place, and a More tile that
/// opens the picker sheet. With nobody at all, one "Add people" tile.
struct UsualSoulsStrip: View {
    let souls: [SoulWithGlyph]
    let selection: [UUID]
    let onToggle: (UUID) -> Void
    let onMore: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs + 2) {
            Text("Who was there?")
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            HStack(spacing: 4) {
                if souls.isEmpty {
                    moreTile(label: "Add people")
                } else {
                    ForEach(SoulChooserModel.usual(from: souls).prefix(4)) { soul in
                        SoulTile(soul: soul, isSelected: selection.contains(soul.id)) { onToggle(soul.id) }
                    }
                    moreTile(label: "More")
                }
            }
        }
    }

    private func moreTile(label: LocalizedStringResource) -> some View {
        Button(action: onMore) {
            VStack(spacing: Spacing.xs + 2) {
                PbblsIconImage(.plusCircle, size: 44, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 88)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous)
                .strokeBorder(Color.pebbles.outline, style: StrokeStyle(lineWidth: 1.5, dash: [6, 6])))
            .contentShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        }
        .buttonStyle(.plain)
    }
}
```
The usual set includes people already chosen for this pebble who have zero pebbles (a soul created inline): make the strip's source `selection`-aware: after `usual(from:)`, append any selected soul missing from the four (replace the last tile) so a freshly added person is visible. Implement as:
```swift
    private var shown: [SoulWithGlyph] {
        var tiles = Array(SoulChooserModel.usual(from: souls).prefix(4))
        for id in selection where !tiles.contains(where: { $0.id == id }) {
            if let soul = souls.first(where: { $0.id == id }) {
                if tiles.count == 4 { tiles.removeLast() }
                tiles.append(soul)
            }
        }
        return tiles
    }
```
and iterate `shown`.

- [ ] **Step 4: `CompleteLifeStep`** (the scaffold scrolls its content; the strip must stay above the button, so the step renders its own sticky bottom via `safeAreaInset` on the scaffold's `ScrollView`; the scaffold has no hook for that, so this step uses `contentFillsHeight: true` with its own `ScrollView` and the strip beneath it, the button floating. Add `contentFillsHeight: model.step == .life` in `CompleteFlowView`.)
```swift
import SwiftUI

/// What part of life (record-flow D10, Margin treatment): RECENT, then all
/// 18 as a two-column list of glyph + name, the last tap's description in a
/// caption bar, three at most. Beneath the list, sticky, "Who was there?"
/// with the four most-tagged people and More.
struct CompleteLifeStep: View {
    let model: CompleteFlowModel

    @Environment(ReferenceDataService.self) private var refs
    @Environment(SupabaseService.self) private var supabase
    @State private var lastTapped: Domain?
    @State private var showsPicker = false

    private var recent: [Domain] {
        guard let owner = supabase.session?.user.id else { return [] }
        let ids = RecentDomains().ids(owner: owner)
        return ids.compactMap { id in refs.domains.first { $0.id == id } }
    }

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.md) {
                    if !recent.isEmpty {
                        sectionLabel("RECENT")
                        grid(recent)
                    }
                    sectionLabel("ALL")
                    grid(refs.domains)
                }
                .padding(.bottom, Spacing.lg)
            }
            .scrollBounceBehavior(.basedOnSize)
            captionBar
            UsualSoulsStrip(souls: refs.souls, selection: model.draft.soulIds,
                            onToggle: { model.toggleSoul($0) }, onMore: { showsPicker = true })
                .padding(.vertical, Spacing.sm)
                .background(Color.pebbles.surface)
            if let error = model.completeError {
                Text(verbatim: error)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.error)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, Spacing.sm)
            }
        }
        .task { await refs.refreshSouls() }
        .sheet(isPresented: $showsPicker) {
            SoulChooserSheet(title: "Who was there?", currentSelection: model.draft.soulIds,
                             allowsSkip: true, detents: [.large], onConfirm: { model.setSouls($0) })
        }
    }

    private func sectionLabel(_ text: LocalizedStringResource) -> some View {
        Text(text).pebblesFont(.meta).foregroundStyle(Color.pebbles.onSurfaceVariant)
    }

    private func grid(_ domains: [Domain]) -> some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: Spacing.sm), GridItem(.flexible(), spacing: Spacing.sm)],
                  spacing: Spacing.sm) {
            ForEach(domains) { domain in
                let isSelected = model.draft.domainIds.contains(domain.id)
                Button {
                    lastTapped = domain
                    model.toggleDomain(domain.id)
                } label: {
                    HStack(spacing: Spacing.sm) {
                        GlyphView(case: isSelected ? .selected : .default, strokes: domain.strokes, side: 40)
                            .accessibilityHidden(true)
                        Text(verbatim: domain.localizedName)
                            .pebblesFont(isSelected ? .bodyEmphasized : .body)
                            .foregroundStyle(isSelected ? Color.pebbles.onPrimaryContainer : Color.pebbles.onSurface)
                            .lineLimit(1)
                        Spacer(minLength: 0)
                    }
                    .padding(.horizontal, Spacing.sm)
                    .frame(minHeight: 52)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(isSelected ? Color.pebbles.primaryContainer : Color.pebbles.surfaceContainerLow))
                    .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(verbatim: domain.localizedName))
                .accessibilityHint(Text(verbatim: domain.localizedLabel))
                .accessibilityAddTraits(isSelected ? [.isSelected] : [])
            }
        }
    }

    /// The one-line description of the last tap; empty keeps the height.
    private var captionBar: some View {
        Text(verbatim: lastTapped?.localizedLabel ?? " ")
            .pebblesFont(.subhead)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, Spacing.xs)
    }
}
```
`CompleteFlowView.complete()` records the recent domains after a success: `if let owner = supabase.session?.user.id { RecentDomains().record(model.draft.domainIds, owner: owner) }`.

- [ ] **Step 5: Partial failure copy (D6).** In `complete()`'s `catch`, the message becomes `String(localized: "Moment saved, pebble not made yet · Try again")` for any error other than the ones `userMessageForMomentWriteError` names specifically (keep the specific ones: `pebble_needs_domain` etc.).

- [ ] **Step 6: French:** `RECENT` → `RÉCENTS`, `ALL` → `TOUS`, `Who was there?` → `Qui était là ?`, `More` → `Plus`, `Add people` → `Ajouter des gens`, `Make it a pebble (%lld)` → `En faire un galet (%lld)`, `Moment saved, pebble not made yet · Try again` → `Moment enregistré, pas encore de galet · Réessayer`.

- [ ] Run `RecentDomainsTests`, simulator: Life shows RECENT (empty the first time), ALL in two columns, cap at three with a warning haptic on the fourth, the strip sticky, More opens the picker at full height, Done returns with pills reflected as selected tiles. Commit:
```bash
git add -A apps/ios/Pebbles apps/ios/PebblesTests apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): the life screen with domains and the usual people

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 7.4: The summary pill (D11)

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/RecordSummaryPill.swift`
- Modify: `apps/ios/Pebbles/Features/Record/RecordStepScaffold.swift` (`var pill: AnyView? = nil`, drawn in place of the subtitle when non-nil)
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/RecordSummaryPillTests.swift`

- [ ] Test:
```swift
import Testing
@testable import Pebbles

@Suite("RecordSummaryPill")
struct RecordSummaryPillTests {
    @Test("the pill joins the answers so far with middle dots")
    func text() {
        #expect(RecordSummaryPill.text(valence: .highlightMedium, emotionName: nil) == "Highlight · week")
        #expect(RecordSummaryPill.text(valence: .lowlightLarge, emotionName: "Joyful") == "Lowlight · month · Joyful")
        #expect(RecordSummaryPill.text(valence: nil, emotionName: nil) == nil)
    }
}
```

- [ ] Implement:
```swift
import SwiftUI

/// The answers so far under the headline (record-flow D11): "Highlight ·
/// week", then "Highlight · week · Joyful", with Edit back to that step.
struct RecordSummaryPill: View {
    let valence: Valence?
    let emotionName: String?
    let onEdit: () -> Void

    static func text(valence: Valence?, emotionName: String?) -> String? {
        guard let valence else { return nil }
        var parts = [String(localized: valence.shortLabel), String(localized: valence.spanWord)]
        if let emotionName { parts.append(emotionName) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        if let text = Self.text(valence: valence, emotionName: emotionName) {
            Button(action: onEdit) {
                HStack(spacing: Spacing.xs + 2) {
                    Text(verbatim: text).pebblesFont(.subheadEmphasized).lineLimit(1)
                    Text("Edit").pebblesFont(.subhead).underline()
                }
                .foregroundStyle(Color.pebbles.onSecondaryContainer)
                .padding(.horizontal, Spacing.md)
                .frame(height: 32)
                .background(Capsule().fill(Color.pebbles.secondaryContainer))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(verbatim: text))
            .accessibilityHint(Text("Edit"))
        }
    }
}
```
In `CompleteFlowView.stepBody`, pass `pill:` for `.emotion` and `.life`:
```swift
            pill: model.step == .valence ? nil : AnyView(RecordSummaryPill(
                valence: model.draft.valence,
                emotionName: model.draft.emotionId.flatMap { palettes.byEmotionId[$0]?.localizedName },
                onEdit: { model.go(to: model.step == .life && model.draft.emotionId != nil ? .emotion : .valence) }
            ))
```
with `@Environment(EmotionPaletteService.self) private var palettes`. On the emotion step the pill shows the valence only, Edit goes to valence; on Life, Edit goes to emotion (the spec's "back to that step" for the last answer).

- [ ] French: `Edit` exists (`Modifier`). Run the test, commit:
```bash
git add -A apps/ios/Pebbles apps/ios/PebblesTests
git commit -m "feat(ios): the summary pill under the pebble stage's headline

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 7.5: "Moment saved · 3 questions to make it a pebble" (D1)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift`, `apps/ios/Pebbles/Features/Capture/CaptureFlowView.swift`

- [ ] `CompleteFlowView` gains `var afterSave = false`; the valence step's `subtitle` becomes `afterSave ? "Moment saved · 3 questions to make it a pebble" : nil`. `CaptureFlowView` passes `afterSave: true` when it swaps to `CompleteFlowView`. French: `Moment enregistré · 3 questions pour en faire un galet`.
- [ ] Commit: `feat(ios): the pebble stage says the moment is saved`.

### Task 7.6: Lint, test, push, PR, Arkaik

- [ ] `swiftlint`; all Complete suites + `RecentDomainsTests` + `RecordSummaryPillTests` → green; the full `PebblesTests` minus References once (`-skip-testing:PebblesTests/References`).
- [ ] PR `feat(ios): the life screen and the summary pill`, `Part of #1514`, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`. Arkaik: domain + souls step nodes → one "Life" view node (`create_node` if absent, `update_node` status `development`); collection and privacy step nodes → deprecated per the `arkaik` skill.

---

# Part 8 — Make it richer, entry points, bare page (branch `feat/1514-make-it-richer-ios`)

### Task 8.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1514-life-screen-ios && git checkout -b feat/1514-make-it-richer-ios
```

### Task 8.2: `CompleteRicherStep` (D12)

**Files:**
- Create: `apps/ios/Pebbles/Features/Complete/Steps/CompleteRicherStep.swift`
- Delete: `apps/ios/Pebbles/Features/Complete/Steps/CompleteSuccessStep.swift`
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift`
- Test: `apps/ios/PebblesTests/Features/Complete/RicherWeekRowTests.swift`

- [ ] **Step 1: Test the week row's maths**
```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("RicherWeekRow")
struct RicherWeekRowTests {
    @Test("seven days from Monday with the pebble's day marked")
    func days() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.firstWeekday = 2
        calendar.timeZone = TimeZone(identifier: "Europe/Paris")!
        // Wednesday 5 November 2026, 10:00 Paris.
        let wednesday = Date(timeIntervalSince1970: 1_793_955_600)
        let days = RicherWeekRow.days(around: wednesday, calendar: calendar)
        #expect(days.count == 7)
        #expect(days.map(\.isMarked) == [false, false, true, false, false, false, false])
        #expect(calendar.component(.weekday, from: days[0].date) == 2)
    }
}
```

- [ ] **Step 2: Implement the step**
```swift
import SwiftUI
import os

/// Make it richer (record-flow D12, Riverbed treatment): the lit stone with
/// its derived glyph, "+N karma", the name, the week with the new stone on
/// its day, then three rows that each edit the finished pebble: collection
/// and visibility through `complete_pebble` (keyed on moment and caller, so
/// a second call is an edit), words through `save_moment` (the edit sheet).
/// Done returns to the origin; Record another starts a fresh capture.
struct CompleteRicherStep: View {
    let momentId: UUID
    let name: String?
    let happenedAt: Date?
    let result: CompletionResult
    let draft: CompleteDraft
    let onExit: () -> Void
    /// Nil when the host cannot start another capture (the held card).
    let onRecordAnother: (() -> Void)?
    let onEdited: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(SupabaseService.self) private var supabase

    @State private var collectionId: UUID?
    @State private var visibility: Visibility
    @State private var hasWords: Bool
    @State private var words: MomentDetail?
    @State private var editError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "complete-richer")

    init(momentId: UUID, name: String?, happenedAt: Date?, result: CompletionResult, draft: CompleteDraft,
         hasWords: Bool, onExit: @escaping () -> Void, onRecordAnother: (() -> Void)?, onEdited: @escaping () -> Void) {
        self.momentId = momentId
        self.name = name
        self.happenedAt = happenedAt
        self.result = result
        self.draft = draft
        self.onExit = onExit
        self.onRecordAnother = onRecordAnother
        self.onEdited = onEdited
        _collectionId = State(initialValue: draft.collectionId)
        _visibility = State(initialValue: draft.visibility)
        _hasWords = State(initialValue: hasWords)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.xl) {
                PebbleStoneView(
                    valence: draft.valence ?? .neutralMedium,
                    glyph: result.glyph,
                    palette: draft.emotionId.flatMap { palettes.palette(for: $0) },
                    followsTilt: true
                )
                .frame(maxWidth: .infinity)
                .frame(height: 240)
                .padding(.top, Spacing.lg)

                VStack(spacing: Spacing.sm) {
                    MomentNameText(name: name, untitledFont: .ysabeauSemiboldItalic(28))
                        .pebblesFont(.title)
                        .multilineTextAlignment(.center)
                    if let karma = result.karmaDelta, karma > 0 {
                        HStack(spacing: 6) {
                            PbblsIconImage(.karmaDot, size: 18, relativeTo: .headline, style: AnyShapeStyle(Color.pebbles.primary))
                            Text("+\(karma) karma").pebblesFont(.headline).foregroundStyle(Color.pebbles.onSurface)
                        }
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel(Text("Earned \(karma) karma"))
                    }
                    if let happenedAt {
                        RicherWeekRow(date: happenedAt)
                            .padding(.top, Spacing.sm)
                    }
                }

                VStack(alignment: .leading, spacing: Spacing.sm) {
                    Text("MAKE IT RICHER")
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    if !refs.collections.isEmpty { collectionRow }
                    visibilityRow
                    if !hasWords { wordsRow }
                    if let editError {
                        Text(verbatim: editError).pebblesFont(.subhead).foregroundStyle(Color.pebbles.error)
                    }
                }

                VStack(spacing: Spacing.xs) {
                    Button("Done", action: onExit).buttonStyle(PebblesPrimaryButtonStyle())
                    if let onRecordAnother {
                        Button("Record another", action: onRecordAnother)
                            .pebblesFont(.bodyEmphasized)
                            .foregroundStyle(Color.pebbles.primary)
                            .frame(maxWidth: .infinity, minHeight: 44)
                    }
                }
            }
            .padding(.horizontal, Spacing.lg)
            .padding(.bottom, Spacing.lg)
        }
        .sheet(item: $words) { detail in
            EditMomentSheet(detail: detail, onSaved: {
                hasWords = true
                onEdited()
            })
        }
    }

    // MARK: Rows

    private var collectionRow: some View {
        Menu {
            ForEach(refs.collections) { collection in
                Button {
                    Task { await edit(collectionId: collection.id) }
                } label: {
                    Label { Text(verbatim: collection.name) } icon: {
                        if collection.id == collectionId { Image(systemName: "checkmark") }
                    }
                }
            }
            if collectionId != nil {
                Button("Remove from collection") { Task { await edit(collectionId: nil) } }
            }
        } label: {
            row(icon: .collection, title: "Add to a collection",
                value: collectionId.flatMap { id in refs.collections.first { $0.id == id }?.name })
        }
    }

    private var visibilityRow: some View {
        Menu {
            ForEach(Visibility.allCases) { grade in
                Button {
                    Task { await edit(visibility: grade) }
                } label: {
                    Label { Text(grade.label) } icon: { if grade == visibility { Image(systemName: "checkmark") } }
                }
            }
        } label: {
            row(icon: .abstractKey, title: "Who can see it", value: String(localized: visibility.label))
        }
    }

    private var wordsRow: some View {
        Button {
            Task { await openWords() }
        } label: {
            row(icon: .abstractPen, title: "Add a few words", value: String(localized: "+1 karma"))
        }
        .buttonStyle(.plain)
    }

    private func row(icon: PbblsIcon, title: LocalizedStringResource, value: String?) -> some View {
        HStack(spacing: Spacing.md) {
            PbblsIconImage(icon, size: 24, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
            Text(title).pebblesFont(.body).foregroundStyle(Color.pebbles.onSurface)
            Spacer(minLength: Spacing.sm)
            if let value {
                Text(verbatim: value).pebblesFont(.subhead).foregroundStyle(Color.pebbles.onSurfaceVariant).lineLimit(1)
            }
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(Color.pebbles.outline)
        }
        .padding(.horizontal, Spacing.md)
        .frame(minHeight: 52)
        .frame(maxWidth: .infinity)
        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color.pebbles.surfaceContainerLow))
        .contentShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    // MARK: Edits (one write per tap, D6)

    private func edit(collectionId newId: UUID?) async {
        var payload = CompletePebblePayload(momentId: momentId)
        payload.collectionIds = newId.map { [$0] } ?? []
        await send(payload) { collectionId = newId }
    }

    private func edit(visibility grade: Visibility) async {
        var payload = CompletePebblePayload(momentId: momentId)
        payload.visibility = grade
        await send(payload) { visibility = grade }
    }

    private func send(_ payload: CompletePebblePayload, then apply: () -> Void) async {
        editError = nil
        do {
            _ = try await MomentsService(client: supabase.client).completePebble(payload)
            TapHaptics.play(.selection)
            apply()
            onEdited()
        } catch {
            logger.error("richer edit failed: \(error.localizedDescription, privacy: .private)")
            editError = userMessageForMomentWriteError(error)
        }
    }

    private func openWords() async {
        editError = nil
        do {
            words = try await MomentsService(client: supabase.client).moment(id: momentId)
        } catch {
            logger.error("moment(id:) for words failed: \(error.localizedDescription, privacy: .private)")
            editError = String(localized: "Something went wrong. Please try again.")
        }
    }
}

/// The week around the pebble's day, Monday first, the new stone on its day.
struct RicherWeekRow: View {
    struct Day: Identifiable {
        let date: Date
        let isMarked: Bool
        var id: Date { date }
    }

    let date: Date

    private var locale: Locale { SpineStrings.appLocale }

    static func days(around date: Date, calendar: Calendar) -> [Day] {
        let weekday = calendar.component(.weekday, from: date)
        let offsetToMonday = (weekday - calendar.firstWeekday + 7) % 7
        let start = calendar.startOfDay(for: calendar.date(byAdding: .day, value: -offsetToMonday, to: date) ?? date)
        return (0..<7).compactMap { offset in
            guard let day = calendar.date(byAdding: .day, value: offset, to: start) else { return nil }
            return Day(date: day, isMarked: calendar.isDate(day, inSameDayAs: date))
        }
    }

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ForEach(Self.days(around: date, calendar: SpineCalendar.current)) { day in
                VStack(spacing: Spacing.xs) {
                    Text(verbatim: day.date.formatted(.dateTime.weekday(.narrow).locale(locale)))
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    Circle()
                        .fill(day.isMarked ? Color.pebbles.primary : Color.pebbles.outlineVariant)
                        .frame(width: day.isMarked ? 10 : 6, height: day.isMarked ? 10 : 6)
                        .frame(height: 10)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: date.formatted(.dateTime.weekday(.wide).day().month().locale(locale))))
    }
}
```
`SpineCalendar.current` must have `firstWeekday = 2`; check `grep -n "firstWeekday" apps/ios/Pebbles/Features/Path/Spine/**/*.swift`. If not, the test's calendar shows the intended behaviour and the view must set Monday explicitly.

- [ ] **Step 3: Wire it.** `CompleteFlowView` gains `var momentHappenedAt: Date? = nil`, `var momentHasWords = false`, `var onRecordAnother: (() -> Void)? = nil`; the success branch builds `CompleteRicherStep(momentId:name:happenedAt:result:draft:hasWords:onExit: { dismiss() }, onRecordAnother:onEdited: { onCompleted(momentId) })`. `CaptureFlowView` passes `momentHappenedAt: model.draft.happenedAt`, `momentHasWords: !model.draft.description.trimmingCharacters(in: .whitespaces).isEmpty` and `onRecordAnother: { startAnother() }` where
```swift
    private func startAnother() {
        completing = nil
        model = CaptureFlowModel()
        pickedPhoto = nil
        snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
        hasRestored = true
    }
```
`HeldCardPage` and `HomeView` pass `momentHappenedAt: detail.happenedAt`/`bare.happenedAt` and `momentHasWords` from the detail's description where they have it, `onRecordAnother: nil`.

- [ ] **Step 4: French:** `MAKE IT RICHER` → `ENRICHIS-LE`, `Add to a collection` → `Ajouter à une collection`, `Remove from collection` → `Retirer de la collection`, `Who can see it` → `Qui peut le voir`, `Add a few words` → `Ajouter quelques mots`, `+1 karma` → `+1 karma`, `Record another` → `En enregistrer un autre`, `Done` exists.

- [ ] Run `RicherWeekRowTests`; simulator: finish a pebble → the lit stone, "+N karma", the week row, three rows (collection only when the account has one, words only when the moment has none); each row writes and the row updates; Done returns to the Path week; Record another opens a fresh grid. Commit:
```bash
git add -A apps/ios/Pebbles apps/ios/PebblesTests apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ios): make it richer replaces the success screen

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 8.2b: Split `CaptureFlowView` (it sits at the 400-line file_length limit)

**Files:** `apps/ios/Pebbles/Features/Capture/CaptureFlowView.swift`, create `apps/ios/Pebbles/Features/Record/Steps/RecordPhotoControls.swift`, create `apps/ios/Pebbles/Features/Capture/CaptureDraftAutosaver.swift`

`private` is file-scoped, so an extension file cannot reach the view's `@State`; extract types, not extensions:
- [ ] `RecordPhotoControls` view: "Just write" (tonal) + the camera circle (shown when `CameraPicker.isAvailable`), with closures `onJustWrite`, `onCamera`, and a `#Preview`.
- [ ] `CaptureDraftAutosaver` (`@MainActor final class`): the 800 ms debounce, `flush()`, `clear()`, `enabled` flag, and `savedSnap(from: FormSnap?, userId:)`; the view keeps only the `onChange` hooks. A Swift Testing suite covers: schedule then flush writes once; a disabled autosaver never writes; `savedSnap` for `.existing`, `.pending(.uploaded)`, `.pending(.uploading)` (nil).
- [ ] Delete the unreachable `.existing` branch of `RecordPhotoStep` (`attachedWithoutThumb`, `onRemove`) and its closure in the flow view: a restored photo is seeded as `.pending`.
- [ ] Commit `quality(ios): split the capture flow view into its controls and its autosaver`.

### Task 8.3: Entry points (D13)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Capture/CaptureFlowModel.swift` (`init(startingAt:)`), `CaptureFlowView.swift` (`var startsAtMomentPage = false`)
- Modify: `apps/ios/Pebbles/Features/Home/HomeView.swift`, `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`
- Delete: `apps/ios/Pebbles/Features/Home/Shortcuts/QuickMomentSheet.swift` (and `QuickMomentExit`) if nothing else references them
- Modify: `apps/ios/Pebbles/Features/Home/Shortcuts/MomentToast.swift` copy key `home.toast.name` value → "Edit" / "Modifier" (the toast already opens `EditMomentSheet`)

- [ ] `CaptureFlowModel.init(haptic:startingAt: CaptureStep = .photo)` sets `step`. `CaptureFlowView` builds `CaptureFlowModel(startingAt: startsAtMomentPage ? .moment : .photo)`; a restore still wins. The back chevron on the moment page is hidden when `startsAtMomentPage` (`canGoBack: model.step.previous != nil && !startsAtMomentPage`), so the quick path never shows the grid.
- [ ] `HomeView`: `showsQuick` presents `.fullScreenCover { CaptureFlowView(onMomentChanged: …, startsAtMomentPage: true) }`; the `pendingComplete` dance goes (the flow chains itself). The checklist `.moment` task uses the same. `SoulsListView`'s prompt `onRecord` presents the same cover. Then `grep -rn "QuickMomentSheet\|QuickMomentExit" apps/ios` → delete the file if empty.
- [ ] Path "+" and Home "New pebble" both already open `CaptureFlowView`; make sure both read the same word: the Home tile's label becomes "New pebble" if it is not already (`grep -n "home.pebble\|New pebble" apps/ios/Pebbles/Features/Home/*.swift`), and the Path FAB's `accessibilityLabel` stays "New pebble".
- [ ] Build, simulator: Quick moment → page without a grid, no back chevron; the camera toast's button reads Edit and opens the edit sheet. Commit:
```bash
git add -A apps/ios
git commit -m "feat(ios): one record entry model; quick moment opens the page

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 8.4: The bare moment page (D14)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Card/BareMomentPage.swift`, `HeldCardPage.swift`, `CardStrings.swift`

- [ ] `BareMomentPage` gains `var hasDraft = false` and `var onAddWords: (() -> Void)? = nil`. `BarePlace` takes both: when `hasDraft` the kicker reads `CardStrings.placeStartedKicker` ("You started this") and the button "Continue"; after the button, when `onAddWords != nil` (the host passes it only when `detail.description` is empty):
```swift
                if let onAddWords {
                    Button(action: onAddWords) {
                        Label { Text("Add a few words") } icon: { PbblsIconImage(.abstractPen, size: 20, relativeTo: .subheadline) }
                    }
                    .pebblesFont(.subheadEmphasized)
                    .foregroundStyle(Color.pebbles.primary)
                    .frame(minHeight: 44)
                    .padding(.top, Spacing.xs)
                }
```
- [ ] `HeldCardPage` (where it builds `BareMomentPage` ~line 189): `hasDraft: hasCompleteDraft`, `onAddWords: detail.description?.isEmpty == false ? nil : { editingWords = detail }` with `@Environment(CompleteDraftStore.self) private var drafts`, `@Environment(SupabaseService.self)`, `private var hasCompleteDraft: Bool { supabase.session.flatMap { drafts.load(momentId: detail.id, ownerId: $0.user.id) } != nil }` and `.sheet(item: $editingWords) { EditMomentSheet(detail: $0, onSaved: { model.reload() }) }` (use whatever reload the page already calls after `EditMomentSheet`; grep `onSaved` in the file).
- [ ] `CardStrings`: add `placeStartedKicker` EN "You started this" FR "Tu avais commencé", `continue` EN "Continue" FR "Continuer" (if `Continue` is already in the catalog, reuse it).
- [ ] Record the existing `HeldCardBareReferences` again only if the default render changed (it should not: `hasDraft` false, `onAddWords` nil in the fixture). Commit:
```bash
git add -A apps/ios
git commit -m "feat(ios): the bare page offers words and resumes a started pebble

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

### Task 8.5: Lint, test, push, PR, Arkaik

- [ ] `swiftlint`; `RicherWeekRowTests`, the Capture and Complete suites → green.
- [ ] PR `feat(ios): make it richer, one entry model, the bare page`, `Part of #1514`, labels `feat ui ios no-lab-note`, milestone `M70 · Record flow`. Arkaik: success node renamed "Make it richer", Quick moment node's edge re-pointed at the moment page, all `development`.

---

# Part 9 — Copy pass and reference renders (branch `feat/1514-copy-renders-ios`)

### Task 9.1: Branch

```bash
cd /Users/alexis/code/pbbls && git checkout feat/1514-make-it-richer-ios && git checkout -b feat/1514-copy-renders-ios
```

### Task 9.2: The catalog (D15, S9)

- [ ] Open `Pebbles/Resources/Localizable.xcstrings` in Xcode (or run `python3 -I -c` over it read-only) and list every key whose `en` or `fr` is missing or in state `new`/`stale`:
```bash
python3 -I - <<'PY'
import json
d=json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings'))['strings']
for k,v in d.items():
    loc=v.get('localizations',{})
    for lang in ('en','fr'):
        u=loc.get(lang,{}).get('stringUnit')
        if not u or u.get('state')!='translated': print(repr(k),lang,u and u.get('state'))
PY
```
Fix each by inserting the missing `fr` (tu, no exclamation marks, the house tone of the existing entries) as text at its anchor. Expected: the script prints nothing.
- [ ] Remove keys no longer referenced by any Swift file (the old step titles "When did it happen?" only if the chip's sheet no longer uses it, "Anyone in this one?", "Add it to a collection?", "Who can see it?", "Your pebble", "Back to my path", "Skip" if unused, "Make it a pebble now", "Save moment", "Or skip it and write from memory.", "A name, a photo, or both.", "What do you call it?", "Taken from your photo"): for each, `grep -rn "<key>" apps/ios/Pebbles --include=*.swift` returns nothing before deleting.
- [ ] The 18 domain labels: list `domain.<slug>.name` and `domain.<slug>.label` EN/FR in the PR body as a table with a "proposed" column left empty, for the maintainer's copy pass; change nothing in this task unless a label exceeds 40 characters (then shorten and say so). The spec names Events, Weather and Tasks next to Identity and Spirituality as the ones to look at.
- [ ] Run `-only-testing:PebblesTests/LocalizationTests` → green. Commit `chore(ios): record flow and souls copy in english and french`.

### Task 9.2b: Accessibility reflow of the chooser (S8)

**Files:** `apps/ios/Pebbles/Features/Souls/SoulChooser.swift`, `SoulPillTray.swift`, `PebblesTests/References/SoulChooserReferences.swift`

- [ ] Read `@Environment(\.dynamicTypeSize)` in `SoulChooser`; when `typeSize.isAccessibilitySize` the Usual grid uses three columns instead of four. In `SoulPillTray`, at accessibility sizes lay the pills row above the button (`VStack`) instead of beside it. Add one render `pickerTwoChosenAx2` with `ReferenceConfig(scheme: .paper, dynamicType: .accessibility2)` and record it (EN pass only).
- [ ] Commit `feat(ios): the souls chooser reflows at accessibility sizes`.

Note for the Android mirror (#1519): the inline-create error line sits *above* the lens/plus bar, not under the field, because the bar lives in the bottom safe-area inset where "under" would be hidden by the keyboard.

### Task 9.3: Flow reference renders

**Files:**
- Create: `apps/ios/PebblesTests/References/RecordFlowReferences.swift`

- [ ] Five renders, both schemes, Paper in EN + FR, at `ReferenceConfig.phoneWidth` and once at `dynamicType: .accessibility2` for the Life screen (the device-pass gate):
  - `momentPage`: `RecordMomentStep(model:snap:blockedMessage:…)` with a model whose draft has a name and words, no snap, inside `RecordStepScaffold(title: "Your moment", action: .primary("Save & make it a pebble", enabled: true, loading: false) {}, secondaryAction: RecordStepSecondaryAction(label: "Just save", enabled: true) {})`.
  - `valenceEmpty`: `ValencePickerContent(selected: nil, onSelect: { _ in })` in the scaffold titled "How did it land?" with a disabled Continue.
  - `valenceHelp`: `ValenceHelpSheet()` at 393 × 560.
  - `life`: `CompleteLifeStep(model:)` with a `CompleteFlowModel` whose draft holds two domain ids from a fixture of six `Domain`s (copy the two in `DomainPickerContent`'s preview and add four), environment `ReferenceDataService` replaced with `refs.replace(domains:souls:collections:)` using `SoulFixtures.eleven`; the pill above from `RecordSummaryPill(valence: .highlightMedium, emotionName: "Joyful", onEdit: {})`.
  - `richer`: `CompleteRicherStep(...)` with `CompletionResult(pebbleId:karmaDelta: 3, glyph: nil)`, a draft with `.highlightMedium`, `happenedAt` a fixed Wednesday, `hasWords: false`, two collections in `refs`.
  Follow `SoulChooserReferences` for the environment wiring. Render with `TEST_RUNNER_PBBLS_RECORD_REFERENCES=1`, inspect each PNG, commit `test(ios): record flow reference renders`.

### Task 9.4: The Lab Note, the PR, Arkaik, the journal

- [ ] `swiftlint`; the whole `PebblesTests` minus References → green; `-only-testing:PebblesTests/References/SoulChooserReferences` and `RecordFlowReferences` record cleanly.
- [ ] Invoke the repo-local `lab-note` skill and write one `## Lab Note (EN/FR)` for the whole flow (a moment in 3 taps, a pebble in 8, the people chooser) in the PR body. PR `feat(ios): record flow copy and reference renders`, body starts `Resolves #1514`, labels `feat ui ios`, milestone `M70 · Record flow`.
- [ ] Arkaik: every touched acceptance and view at `development` (the GitHub App moves them to `releasing` at merge; never set `live`).
- [ ] Append a "Lessons learned" section to this plan file once the stack is merged (what the device pass found at XXL in French on the SE-size device, what the subagents tripped on).
- [ ] Drop the build-journal pebble for the stack: `node scripts/journal/drop-pebble.mjs --size medium --valence highlight --collection iOS --title "A pebble in eight taps" --journal "…"` (see the script header; no PR numbers, file names or finding ids).

---

## Self-review against the specs

| Spec item | Task |
|---|---|
| S1 ranking/grouping, S2 selection, S3 search/Add, S4 inline create | 1.2–1.6 |
| S5 picker sheet / half sheet / People tab / Life strip | 3.2, 3.3, 2.2, 7.3 |
| S6 glass chrome, S7 load/error, S8 large text | 1.5, 1.6, 2.2, 9.3 (ax2 render) |
| S9 copy EN/FR | each part + 9.2 |
| D1 flow and dots, D2 unsaved preview + draft store | 4.2, 4.4, 4.6 |
| D3 photo step, D4 when chip, D5 moment page, D6 save paths and partial failure | 5.3, 4.5, 4.6, 7.3 step 5 |
| D7 no seed, labels, ghost, Neutral; D8 help sheet + (i); D9 Retry | 6.2–6.6 |
| D10 Life screen, D11 pill, D12 Make it richer, D13 entry points, D14 bare page | 7.3, 7.4, 8.2, 8.3, 8.4 |
| D15 copy pass, D16 measurement (no client work: events already logged) | 9.2 |
| Tests: flow models, draft round-trip, pill strings, references | 4.2, 4.4, 6.2, 7.2, 7.4, 8.2, 1.7, 2.3, 9.3 |

Known deviations, stated: RECENT domains are device-local (7.3); the souls step hosts the chooser inline with a duplicated button for parts 3–6 (3.4); the upload ring on the moment page is static (4.5); the Home "New pebble" keeps hiding "Just save" (`chainsComplete`, 4.6), as D5 allows.
