# iOS Valence Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the inline `Picker` for valence in the iOS pebble form with a sheet-based picker that groups the nine options by size (Day / Week / Month event) and shows each option as a static pebble shape, so users actually understand what they're choosing.

**Architecture:** A single self-contained sheet view (`ValencePickerSheet`) is opened from a `Button` row inside `PebbleFormView`. The sheet reads the current `Valence?` from the form's draft, lays out three sections (one per size group) with three options each (lowlight / neutral / highlight), and on tap writes back through a closure and dismisses. Nine static PDF assets — converted from the existing web SVGs — are drawn with `.template` rendering so they recolor cleanly between inactive (muted) and active (background-on-accent) states.

**Tech Stack:** Swift 5.9, SwiftUI, iOS 17+, Swift Testing (`@Suite` / `@Test` / `#expect`), `xcodegen`, SwiftLint. The conversion step uses `rsvg-convert` from `librsvg`.

**Spec:** `docs/superpowers/specs/2026-04-19-ios-valence-picker-design.md`

**Branch:** `feat/284-ios-valence-picker` (already created off `main`, with the spec as the only commit).

**Issue:** [#284](https://github.com/alexisbohns/pbbls/issues/284) — `[Feat] Valence picker on iOS`. Labels: `feat`, `ios`, `ui`. Milestone: `M23 · TestFlight V1`.

---

## Task 1: Verify branch and clean baseline

**Goal:** Confirm we're on the right branch, that `project.yml` will auto-pick new files, and that the baseline build + tests are green before we change anything. If anything fails here, stop and investigate — every later task assumes a clean baseline.

**Files:**
- Read-only: `apps/ios/project.yml`

- [ ] **Step 1: Confirm branch and clean working tree**

Run from repo root:

```bash
git branch --show-current && git status --short
```

Expected output:
```
feat/284-ios-valence-picker
?? .claude/scheduled_tasks.lock
```

(The `.claude/scheduled_tasks.lock` line is fine — it's a local-only file that's intentionally gitignored. If you see anything else, stop and reconcile before continuing.)

- [ ] **Step 2: Confirm `project.yml` auto-picks new files in `Pebbles/Features/Path/` and `PebblesTests/`**

Run:

```bash
grep -n "path: Pebbles\b\|path: PebblesTests\b" apps/ios/project.yml
```

Expected output:
```
38:      - path: Pebbles
58:      - path: PebblesTests
```

(Line numbers may shift slightly — what matters is that both targets use a directory-level `path:` mapping. That means xcodegen will discover anything under those folders without manual updates to `project.yml`.)

- [ ] **Step 3: Run baseline build to confirm a clean starting state**

Run from repo root:

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`. The build script runs `xcodegen generate` first, so this also refreshes `Pebbles.xcodeproj`.

If the build fails, stop. Report the failure — do not start the implementation on top of a broken baseline.

- [ ] **Step 4: Run baseline tests to confirm green starting state**

Run from repo root:

```bash
npm run test --workspace=@pbbls/ios
```

Expected: `Test Suite 'All tests' passed`. Note the pass count for comparison after we add `ValenceMetadataTests`.

- [ ] **Step 5: Run baseline lint to confirm clean starting state**

Run from `apps/ios`:

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: `Done linting!` with `0 violations`.

No commit for this task — it's a verification gate, not a change.

---

## Task 2: Asset pipeline — convert web SVGs to PDFs and add to the asset catalog

**Goal:** Bring the nine pebble shape SVGs from the web codebase into the iOS asset catalog as PDFs, configured for template rendering so they recolor cleanly between inactive and active states. All nine land in one commit so a missing-asset bug surfaces on first run rather than weeks later.

**Files:**
- Read-only sources: `apps/web/public/pebbles/{low,medium,high}-{negative,neutral,positive}.svg` (9 files)
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Valence/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Valence/valence-<case>.imageset/Contents.json` (9 files)
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Valence/valence-<case>.imageset/valence-<case>.pdf` (9 files)

Where `<case>` is each of: `lowlightSmall`, `neutralSmall`, `highlightSmall`, `lowlightMedium`, `neutralMedium`, `highlightMedium`, `lowlightLarge`, `neutralLarge`, `highlightLarge`.

- [ ] **Step 1: Install `rsvg-convert` if not already present**

```bash
which rsvg-convert || brew install librsvg
```

Expected: a path to `rsvg-convert`, or a successful brew install ending with `librsvg ...: ... files installed`.

- [ ] **Step 2: Create the namespaced parent folder for the Valence asset group**

Run from repo root:

```bash
mkdir -p apps/ios/Pebbles/Resources/Assets.xcassets/Valence
cat > apps/ios/Pebbles/Resources/Assets.xcassets/Valence/Contents.json <<'EOF'
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
EOF
```

The folder is purely for keeping the Xcode asset browser tidy — we deliberately omit `provides-namespace` so asset names stay flat. That means `Image("valence-lowlightSmall")` resolves directly without a namespace prefix, matching `Valence.assetName`.

- [ ] **Step 3: Convert one SVG and create one imageset to prove the pipeline works**

This is the smoke test. If template recoloring works for one asset, it works for all nine.

Run from repo root:

```bash
mkdir -p apps/ios/Pebbles/Resources/Assets.xcassets/Valence/valence-neutralMedium.imageset
rsvg-convert -f pdf \
  apps/web/public/pebbles/medium-neutral.svg \
  -o apps/ios/Pebbles/Resources/Assets.xcassets/Valence/valence-neutralMedium.imageset/valence-neutralMedium.pdf
cat > apps/ios/Pebbles/Resources/Assets.xcassets/Valence/valence-neutralMedium.imageset/Contents.json <<'EOF'
{
  "images" : [
    {
      "filename" : "valence-neutralMedium.pdf",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "preserves-vector-representation" : true,
    "template-rendering-intent" : "template"
  }
}
EOF
```

- [ ] **Step 4: Regenerate the Xcode project and run a build to confirm the asset compiles**

Run from `apps/ios`:

```bash
npm run build
```

Expected: `BUILD SUCCEEDED`. If you see `error: ambiguous reference` for the asset name, stop — usually means the `Contents.json` is malformed.

- [ ] **Step 5: Visually verify template recoloring in an Xcode preview**

Open `apps/ios/Pebbles.xcodeproj` in Xcode. In `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`, temporarily add this preview at the bottom of the file (you'll remove it after the check):

```swift
#Preview("valence template smoke test") {
    VStack(spacing: 24) {
        Image("valence-neutralMedium")
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: 96, height: 96)
            .foregroundStyle(.red)
        Text("If the pebble above is solid RED (not black), template rendering works.")
            .font(.caption)
    }
    .padding()
}
```

Run the preview. Expected: the pebble shape renders solid red.

If it renders **black**, template rendering isn't wired up correctly. Re-check that `template-rendering-intent: "template"` is in the imageset's `Contents.json`, and re-build. Don't proceed to step 6 until red is confirmed.

- [ ] **Step 6: Remove the smoke-test preview**

Delete the `#Preview("valence template smoke test")` block you just added. Confirm with:

```bash
git diff apps/ios/Pebbles/Features/Path/PebbleFormView.swift
```

Expected: no diff (you've put the file back to its original state).

- [ ] **Step 7: Convert and add the remaining eight assets**

Run from repo root:

```bash
declare -A MAP=(
  [lowlightSmall]=low-negative
  [neutralSmall]=low-neutral
  [highlightSmall]=low-positive
  [lowlightMedium]=medium-negative
  [highlightMedium]=medium-positive
  [lowlightLarge]=high-negative
  [neutralLarge]=high-neutral
  [highlightLarge]=high-positive
)
for ios in "${!MAP[@]}"; do
  web="${MAP[$ios]}"
  dir="apps/ios/Pebbles/Resources/Assets.xcassets/Valence/valence-$ios.imageset"
  mkdir -p "$dir"
  rsvg-convert -f pdf "apps/web/public/pebbles/$web.svg" -o "$dir/valence-$ios.pdf"
  cat > "$dir/Contents.json" <<EOF
{
  "images" : [
    {
      "filename" : "valence-$ios.pdf",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "preserves-vector-representation" : true,
    "template-rendering-intent" : "template"
  }
}
EOF
done
```

(Note: `neutralMedium` was added in step 3 and is intentionally omitted from the loop above.)

- [ ] **Step 8: Verify all nine assets are in place**

Run from repo root:

```bash
ls apps/ios/Pebbles/Resources/Assets.xcassets/Valence/ | sort
```

Expected output (10 lines — one parent `Contents.json`, nine imagesets):
```
Contents.json
valence-highlightLarge.imageset
valence-highlightMedium.imageset
valence-highlightSmall.imageset
valence-lowlightLarge.imageset
valence-lowlightMedium.imageset
valence-lowlightSmall.imageset
valence-neutralLarge.imageset
valence-neutralMedium.imageset
valence-neutralSmall.imageset
```

If any are missing, re-run step 7 — `rsvg-convert` may have failed silently if a source SVG was missing.

- [ ] **Step 9: Build and confirm all assets compile**

Run from `apps/ios`:

```bash
npm run build
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 10: Commit**

Run from repo root:

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/Valence
git commit -m "$(cat <<'EOF'
feat(ios): add static valence pebble shape assets

Converts the nine pebble shape SVGs from apps/web/public/pebbles/ to PDFs
and registers them in Assets.xcassets/Valence/, configured for template
rendering so consumers can recolor with .foregroundStyle(...).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add `ValenceSizeGroup` and `ValencePolarity` enums (TDD)

**Goal:** Introduce the two helper enums the picker needs to lay itself out — `ValenceSizeGroup` for the three section headers, `ValencePolarity` for the left-to-right ordering of options inside a section. Tests drive each piece of behaviour the picker view will rely on.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Valence.swift`
- Create: `apps/ios/PebblesTests/ValenceMetadataTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/ValenceMetadataTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("ValenceSizeGroup")
struct ValenceSizeGroupTests {

    @Test("allCases is small, medium, large in that order")
    func allCasesOrder() {
        #expect(ValenceSizeGroup.allCases == [.small, .medium, .large])
    }

    @Test("name copy matches the spec")
    func nameCopy() {
        #expect(ValenceSizeGroup.small.name == "Day event")
        #expect(ValenceSizeGroup.medium.name == "Week event")
        #expect(ValenceSizeGroup.large.name == "Month event")
    }

    @Test("description copy matches the spec")
    func descriptionCopy() {
        #expect(ValenceSizeGroup.small.description ==
            "This moment impacted my day and will be wrapped in my weekly Cairn")
        #expect(ValenceSizeGroup.medium.description ==
            "This moment impacted my whole week and will be wrapped in my monthly Cairn")
        #expect(ValenceSizeGroup.large.description ==
            "This moment impacted my whole month and will be wrapped in my yearly Cairn")
    }

    @Test("id matches rawValue")
    func idMatchesRawValue() {
        for group in ValenceSizeGroup.allCases {
            #expect(group.id == group.rawValue)
        }
    }
}

@Suite("ValencePolarity")
struct ValencePolarityTests {

    @Test("allCases is lowlight, neutral, highlight in that order")
    func allCasesOrder() {
        #expect(ValencePolarity.allCases == [.lowlight, .neutral, .highlight])
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run from repo root:

```bash
npm run test --workspace=@pbbls/ios 2>&1 | grep -E "ValenceSizeGroup|ValencePolarity|error:"
```

Expected: compilation errors mentioning `cannot find type 'ValenceSizeGroup'` and `cannot find type 'ValencePolarity'`. The tests don't run at all yet — that's the failure we want.

- [ ] **Step 3: Add the two enums to `Valence.swift`**

Open `apps/ios/Pebbles/Features/Path/Models/Valence.swift` and append at the bottom of the file (after the existing `Valence` enum, before any closing braces):

```swift
/// Groups the nine `Valence` cases by size for the picker sheet.
/// Drives the three section headers ("Day event" / "Week event" / "Month event").
enum ValenceSizeGroup: String, CaseIterable, Identifiable {
    case small, medium, large

    var id: String { rawValue }

    var name: String {
        switch self {
        case .small:  return "Day event"
        case .medium: return "Week event"
        case .large:  return "Month event"
        }
    }

    var description: String {
        switch self {
        case .small:
            return "This moment impacted my day and will be wrapped in my weekly Cairn"
        case .medium:
            return "This moment impacted my whole week and will be wrapped in my monthly Cairn"
        case .large:
            return "This moment impacted my whole month and will be wrapped in my yearly Cairn"
        }
    }
}

/// Drives the left-to-right ordering of options inside each picker section.
enum ValencePolarity: String, CaseIterable {
    case lowlight, neutral, highlight
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run from repo root:

```bash
npm run test --workspace=@pbbls/ios
```

Expected: tests pass; new ones are reported alongside the existing baseline. Compare the pass count against the baseline from Task 1 — it should be exactly five higher (the five `@Test` functions you just added: 4 in `ValenceSizeGroupTests`, 1 in `ValencePolarityTests`).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Valence.swift apps/ios/PebblesTests/ValenceMetadataTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): add ValenceSizeGroup and ValencePolarity helpers

Two helper enums needed by the upcoming valence picker sheet — size
groups carry the section header copy from issue #284, polarity drives
left-to-right option ordering. Covered by ValenceMetadataTests.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Add `Valence` extensions for `sizeGroup`, `polarity`, `assetName`, `shortLabel` (TDD)

**Goal:** Bridge each `Valence` case to its size group, polarity, asset name, and short label, so the picker view can drive layout and rendering off the enum alone with no hand-maintained dictionaries.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Valence.swift`
- Modify: `apps/ios/PebblesTests/ValenceMetadataTests.swift`

- [ ] **Step 1: Add the failing tests**

Append to `apps/ios/PebblesTests/ValenceMetadataTests.swift` (above the file's final brace if any — the file currently ends after `ValencePolarityTests`, so append after that closing brace):

```swift
@Suite("Valence helpers")
struct ValenceHelpersTests {

    @Test("sizeGroup mapping covers all nine cases")
    func sizeGroupMapping() {
        #expect(Valence.lowlightSmall.sizeGroup   == .small)
        #expect(Valence.neutralSmall.sizeGroup    == .small)
        #expect(Valence.highlightSmall.sizeGroup  == .small)
        #expect(Valence.lowlightMedium.sizeGroup  == .medium)
        #expect(Valence.neutralMedium.sizeGroup   == .medium)
        #expect(Valence.highlightMedium.sizeGroup == .medium)
        #expect(Valence.lowlightLarge.sizeGroup   == .large)
        #expect(Valence.neutralLarge.sizeGroup    == .large)
        #expect(Valence.highlightLarge.sizeGroup  == .large)
    }

    @Test("polarity mapping covers all nine cases")
    func polarityMapping() {
        #expect(Valence.lowlightSmall.polarity    == .lowlight)
        #expect(Valence.lowlightMedium.polarity   == .lowlight)
        #expect(Valence.lowlightLarge.polarity    == .lowlight)
        #expect(Valence.neutralSmall.polarity     == .neutral)
        #expect(Valence.neutralMedium.polarity    == .neutral)
        #expect(Valence.neutralLarge.polarity     == .neutral)
        #expect(Valence.highlightSmall.polarity   == .highlight)
        #expect(Valence.highlightMedium.polarity  == .highlight)
        #expect(Valence.highlightLarge.polarity   == .highlight)
    }

    @Test("assetName matches the imagesets in Assets.xcassets/Valence")
    func assetNameMatchesAssets() {
        for valence in Valence.allCases {
            let name = valence.assetName
            #expect(name == "valence-\(valence.rawValue)")
            #expect(!name.isEmpty)
        }
    }

    @Test("shortLabel reflects polarity")
    func shortLabel() {
        #expect(Valence.lowlightSmall.shortLabel  == "Lowlight")
        #expect(Valence.neutralMedium.shortLabel  == "Neutral")
        #expect(Valence.highlightLarge.shortLabel == "Highlight")
    }

    @Test("Lookup by (sizeGroup, polarity) is unique for every cell")
    func lookupIsUnique() {
        for group in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let matches = Valence.allCases.filter {
                    $0.sizeGroup == group && $0.polarity == polarity
                }
                #expect(matches.count == 1, "(\(group), \(polarity)) should map to exactly one Valence")
            }
        }
    }
}
```

The last test is the integrity check the picker view depends on — for every `(sizeGroup, polarity)` cell, exactly one `Valence` exists. If the mapping ever drifts (e.g., two cases claim the same cell, or a cell goes empty), this test catches it before the picker renders an empty option.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
npm run test --workspace=@pbbls/ios 2>&1 | grep -E "Valence helpers|error:"
```

Expected: compilation errors mentioning `value of type 'Valence' has no member 'sizeGroup'` (and similar for `polarity`, `assetName`, `shortLabel`).

- [ ] **Step 3: Add the extension**

Append to `apps/ios/Pebbles/Features/Path/Models/Valence.swift`, after the two helper enums you added in Task 3:

```swift
extension Valence {
    var sizeGroup: ValenceSizeGroup {
        switch self {
        case .lowlightSmall, .neutralSmall, .highlightSmall:    return .small
        case .lowlightMedium, .neutralMedium, .highlightMedium: return .medium
        case .lowlightLarge, .neutralLarge, .highlightLarge:    return .large
        }
    }

    var polarity: ValencePolarity {
        switch self {
        case .lowlightSmall, .lowlightMedium, .lowlightLarge:    return .lowlight
        case .neutralSmall, .neutralMedium, .neutralLarge:       return .neutral
        case .highlightSmall, .highlightMedium, .highlightLarge: return .highlight
        }
    }

    /// Asset name in `Assets.xcassets/Valence/`. Always non-empty.
    var assetName: String { "valence-\(rawValue)" }

    /// Polarity-only label used inside an option button ("Lowlight" / "Neutral" / "Highlight").
    /// Use `label` when the size axis also matters (e.g. the collapsed form row).
    var shortLabel: String {
        switch polarity {
        case .lowlight:  return "Lowlight"
        case .neutral:   return "Neutral"
        case .highlight: return "Highlight"
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass, with the new `Valence helpers` suite contributing five additional tests beyond Task 3's count.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Valence.swift apps/ios/PebblesTests/ValenceMetadataTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): bridge Valence to size group, polarity, and asset name

Adds Valence.sizeGroup, .polarity, .assetName, and .shortLabel so the
upcoming valence picker can lay itself out by iterating size groups and
polarities. The lookup-uniqueness test ensures every (sizeGroup,
polarity) cell maps to exactly one Valence case.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Build `ValencePickerSheet`

**Goal:** Build the new sheet end-to-end — chrome, three sections, nine option buttons (with both inactive and active visual states), selection callback, and accessibility. The sheet is fully self-contained — no Supabase, no async, no service. It compiles as soon as the file lands and renders correctly in the Xcode preview.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift`

- [ ] **Step 1: Create the sheet file with full implementation**

Create `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift`:

```swift
import SwiftUI

/// Sheet for picking a `Valence`, presented from `PebbleFormView`'s
/// "Valence" row. Three sections — one per `ValenceSizeGroup` — each
/// containing three options (lowlight / neutral / highlight). Tapping
/// an option writes back via `onSelected` and dismisses the sheet.
///
/// Pure UI: no Supabase calls, no async work. The nine options are
/// derived from `Valence.allCases` filtered by `(sizeGroup, polarity)`.
struct ValencePickerSheet: View {
    let currentValence: Valence?
    let onSelected: (Valence) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    ForEach(ValenceSizeGroup.allCases) { group in
                        section(for: group)
                    }
                }
                .padding()
            }
            .navigationTitle("Choose a valence")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .pebblesScreen()
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private func section(for group: ValenceSizeGroup) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(group.name)
                .font(.headline)
                .foregroundStyle(Color.pebblesMutedForeground)

            Text(group.description)
                .font(.subheadline)
                .foregroundStyle(Color.pebblesMutedForeground)

            HStack(spacing: 12) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    if let option = valence(in: group, polarity: polarity) {
                        optionButton(for: option, in: group)
                    }
                }
            }
        }
    }

    /// The single `Valence` case at a given (size, polarity) cell.
    /// Lookup uniqueness is guaranteed by `ValenceHelpersTests.lookupIsUnique`.
    private func valence(in group: ValenceSizeGroup, polarity: ValencePolarity) -> Valence? {
        Valence.allCases.first { $0.sizeGroup == group && $0.polarity == polarity }
    }

    @ViewBuilder
    private func optionButton(for option: Valence, in group: ValenceSizeGroup) -> some View {
        let isActive = (option == currentValence)

        Button {
            onSelected(option)
            dismiss()
        } label: {
            VStack(spacing: 8) {
                Image(option.assetName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                    .foregroundStyle(isActive ? Color.pebblesBackground : Color.pebblesMutedForeground)

                Text(option.shortLabel)
                    .font(.footnote)
                    .foregroundStyle(isActive ? Color.pebblesBackground : Color.pebblesMutedForeground)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(isActive ? Color.pebblesAccent : Color.pebblesSurfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(group.name), \(option.shortLabel)")
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
    }
}

#Preview("nothing selected") {
    Color.clear.sheet(isPresented: .constant(true)) {
        ValencePickerSheet(currentValence: nil, onSelected: { _ in })
    }
}

#Preview("highlightMedium selected") {
    Color.clear.sheet(isPresented: .constant(true)) {
        ValencePickerSheet(currentValence: .highlightMedium, onSelected: { _ in })
    }
}
```

- [ ] **Step 2: Run xcodegen + build to confirm it compiles**

Run from `apps/ios`:

```bash
npm run build
```

Expected: `BUILD SUCCEEDED`. xcodegen automatically picks up the new file under `Pebbles/Features/Path/`.

- [ ] **Step 3: Visual verification in Xcode preview**

Open `apps/ios/Pebbles.xcodeproj` in Xcode and navigate to `ValencePickerSheet.swift`. Open the canvas and run **both** previews (`nothing selected` and `highlightMedium selected`).

Verify:

1. Three sections in order: "Day event" → "Week event" → "Month event".
2. Each section header shows both the name (bold) and description (muted, smaller).
3. Each section has three options arranged in a horizontal row.
4. In the `nothing selected` preview, all nine options use the muted colour treatment (surfaceAlt background, mutedForeground shape and label).
5. In the `highlightMedium selected` preview, the third option in the middle section is the active one (accent background, background-coloured shape and label). All others are muted.
6. The drag indicator is visible at the top of the sheet.
7. The sheet sits at the medium detent and can be dragged up to large.

If any of these fails, fix and re-run the preview before continuing.

- [ ] **Step 4: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: `0 violations`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): add ValencePickerSheet (#284)

Sheet that presents the nine valence options grouped by size into three
sections (Day / Week / Month event), each with three options (lowlight /
neutral / highlight). Inactive options use muted-foreground on
surfaceAlt; the active option uses background on accent. Selection
writes back via the onSelected closure and dismisses the sheet.

Pure UI — no Supabase, no async. Lookup uniqueness across (sizeGroup,
polarity) cells is guaranteed by ValenceHelpersTests.lookupIsUnique.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Wire `ValencePickerSheet` into `PebbleFormView`

**Goal:** Replace the inline `Picker` for valence with a `Button` row that mirrors the active selection (or shows a "Choose…" empty state) and presents the new sheet on tap. The form field's value semantics don't change — `draft.valence` is still a `Valence?`.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`

- [ ] **Step 1: Read the file and locate the spots to change**

Open `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`. You'll touch three things:

1. The `@State` block near the top (lines 20–21) — add `showValencePicker`.
2. The Mood section's valence `Picker` (lines 65–70) — replace with a `Button` row.
3. The bottom of the view (after `.task(id: draft.glyphId) { await loadSelectedGlyph() }` on line 147) — add a `.sheet` presenting `ValencePickerSheet`.

- [ ] **Step 2: Add the `showValencePicker` state variable**

In `PebbleFormView.swift`, find this block:

```swift
    @State private var showPicker = false
    @State private var selectedGlyph: Glyph?
```

Replace it with:

```swift
    @State private var showPicker = false
    @State private var showValencePicker = false
    @State private var selectedGlyph: Glyph?
```

- [ ] **Step 3: Replace the inline valence Picker with a Button row**

Find the existing Mood section's valence picker:

```swift
                Picker("Valence", selection: $draft.valence) {
                    Text("Choose…").tag(Valence?.none)
                    ForEach(Valence.allCases) { valence in
                        Text(valence.label).tag(Valence?.some(valence))
                    }
                }
```

Replace it with:

```swift
                Button {
                    showValencePicker = true
                } label: {
                    HStack(spacing: 12) {
                        if let valence = draft.valence {
                            Image(valence.assetName)
                                .renderingMode(.template)
                                .resizable()
                                .scaledToFit()
                                .frame(width: 32, height: 32)
                                .foregroundStyle(Color.pebblesMutedForeground)
                                .accessibilityHidden(true)
                        } else {
                            RoundedRectangle(cornerRadius: 6)
                                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [3]))
                                .frame(width: 32, height: 32)
                                .foregroundStyle(Color.pebblesMutedForeground)
                        }
                        Text("Valence")
                            .foregroundStyle(Color.pebblesForeground)
                        Spacer()
                        Text(draft.valence?.label ?? "Choose…")
                            .foregroundStyle(Color.pebblesMutedForeground)
                        Image(systemName: "chevron.right")
                            .font(.caption)
                            .foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Valence")
                .accessibilityValue(draft.valence?.label ?? "Choose")
```

- [ ] **Step 4: Present the sheet from the Button row**

Find this line near the bottom of `body`:

```swift
        .task(id: draft.glyphId) { await loadSelectedGlyph() }
```

Insert a `.sheet` modifier directly above it so the chain becomes:

```swift
        .sheet(isPresented: $showValencePicker) {
            ValencePickerSheet(
                currentValence: draft.valence,
                onSelected: { picked in draft.valence = picked }
            )
        }
        .task(id: draft.glyphId) { await loadSelectedGlyph() }
```

(Order matters less for `.sheet` than for `.task`, but inserting above keeps the existing `.task` modifier visually anchored.)

- [ ] **Step 5: Build and visually verify in Xcode preview**

Run from `apps/ios`:

```bash
npm run build
```

Expected: `BUILD SUCCEEDED`.

Then open `PebbleFormView.swift` in Xcode and use the existing previews (or `CreatePebbleSheet.swift`'s preview) to render the form. Verify:

1. The Mood section now has three rows: Emotion, Domain, Valence (still in that order).
2. With no valence selected, the Valence row shows a dashed-border placeholder, label "Valence", trailing text "Choose…", and a chevron.
3. Tapping the row opens `ValencePickerSheet` at the medium detent.
4. Tapping any option dismisses the sheet, and the row updates to show the picked option's small shape and full label (e.g. "Highlight — medium").
5. Tapping the row again reopens the sheet with the previously-picked option highlighted as active.
6. With VoiceOver focus on the row, it announces "Valence, Highlight — medium" (or "Valence, Choose" when empty).

If anything is off, fix and re-verify before committing.

- [ ] **Step 6: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: `0 violations`. (If a `colon_spacing` or similar warning shows up on your changes, fix it before committing — `quality(ios)` follow-ups for lint are noisy in PR review.)

- [ ] **Step 7: Run the test suite to confirm nothing regressed**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests still pass — the `Valence` helpers added in Tasks 3 and 4 plus the existing baseline.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleFormView.swift
git commit -m "$(cat <<'EOF'
feat(ios): replace inline valence Picker with sheet-based picker (#284)

Swaps the text-only Picker in the pebble form's Mood section for a
Button row that mirrors the current selection (shape + label) and opens
ValencePickerSheet on tap. Empty state mirrors the Glyph row's dashed
placeholder for visual symmetry. PebbleDraft.valence semantics
unchanged — value is still a Valence? written back from the sheet's
onSelected callback.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: End-to-end manual verification on the simulator

**Goal:** Walk through each acceptance criterion from issue #284 in the running app, on both `CreatePebbleSheet` and `EditPebbleSheet` (which both consume `PebbleFormView`), confirming the picker behaves correctly in both flows.

**Files:** None modified — verification only.

- [ ] **Step 1: Launch the app in the simulator**

Easiest path: open `apps/ios/Pebbles.xcodeproj` in Xcode, pick the `iPhone 17` simulator in the scheme bar, and press **⌘R**. The app builds and launches in the booted simulator.

If the app crashes on launch, capture the Xcode console output and stop — do not attempt verification on a crashing build.

- [ ] **Step 2: Verify acceptance criterion 1 — Create flow opens the sheet**

In the running app:

1. Sign in (or use the existing dev account).
2. Tap the floating "+" button on the Path tab to open `CreatePebbleSheet`.
3. Locate the "Valence" row in the Mood section.

Expected: the row shows a dashed-border placeholder + "Choose…" trailing text.

4. Tap the Valence row.

Expected: `ValencePickerSheet` slides up at the medium detent with a visible drag indicator at the top. Title reads "Choose a valence". A "Cancel" button is in the navigation bar.

- [ ] **Step 3: Verify acceptance criterion 2 — Three sections × three options**

In the now-open `ValencePickerSheet`:

Expected:
- Three sections, top to bottom: "Day event" → "Week event" → "Month event".
- Each section header has the name (bold) and the description (muted, smaller).
- Each section has three options in a row, ordered left-to-right: Lowlight → Neutral → Highlight.
- Each option shows a pebble shape (different per option) and a label underneath (Lowlight / Neutral / Highlight).
- All nine options use the muted (inactive) treatment.

Drag the sheet up to the large detent. Expected: more vertical space, all sections still visible, no clipping.

- [ ] **Step 4: Verify acceptance criterion 3 — Picking an option closes the sheet and updates the row**

Still in `ValencePickerSheet`:

1. Tap the centre option in the middle section ("Week event" → "Neutral").

Expected: the sheet dismisses immediately. The Valence row in the form now shows a small (~32pt) neutral-medium shape, label "Valence", and trailing text "Neutral — medium".

2. Tap the Valence row again to reopen the sheet.

Expected: the sheet opens with "Week event" → "Neutral" rendered in the active state (accent background, background-coloured shape and label). All other options are inactive.

3. Tap a different option (e.g. "Day event" → "Highlight").

Expected: sheet dismisses, row updates to "Highlight — small" with the matching shape.

4. Tap the row, then tap "Cancel" in the navigation bar instead of an option.

Expected: sheet dismisses, row keeps "Highlight — small" (no change).

- [ ] **Step 5: Verify the same flow on EditPebbleSheet**

1. Dismiss the create sheet.
2. Open an existing pebble's detail view.
3. Tap "Edit" to open `EditPebbleSheet`.
4. Confirm the Valence row shows the pebble's current valence as the filled state (small shape + full label).
5. Tap it. Confirm the sheet opens with the current valence highlighted as active.
6. Pick a different valence. Confirm the sheet dismisses and the row updates.
7. Tap "Save" on the edit sheet. Confirm save succeeds and the pebble's valence is updated when you reopen the detail.

Note: this confirms that `PebbleDraft.valence` and the downstream save path (`PebbleUpdatePayload`) still work — we didn't touch them, but this is the integration check.

- [ ] **Step 6: Verify accessibility with VoiceOver**

In the simulator, enable VoiceOver: **⌘+F5** (Hardware → Accessibility → VoiceOver). Repeat with VoiceOver on:

1. Open the create sheet, focus the Valence row.

Expected announcement: "Valence, Choose, button" (or "Valence, Highlight — medium, button" if filled).

2. Tap the row, focus an option in the picker.

Expected announcement: "Day event, Lowlight, button" (or similar for whichever option you focus). The active option additionally announces "selected".

3. Disable VoiceOver: **⌘+F5** again.

- [ ] **Step 7: No commit**

This task is verification-only; nothing changed.

If any acceptance criterion failed, stop here. Open a follow-up by fixing the failing piece in the relevant earlier task, re-committing, then re-running this task. Do not proceed to Task 8 with known failures.

---

## Task 8: Final lint, build, tests, and PR-ready summary

**Goal:** Confirm the branch is in a green, lintable, testable state, then summarize what landed for the PR description.

**Files:** None modified.

- [ ] **Step 1: Lint**

Run from `apps/ios`:

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: `0 violations`.

- [ ] **Step 2: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Tests**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass. The new `ValenceMetadataTests.swift` should contribute 10 tests across three suites: `ValenceSizeGroup` × 4 (allCasesOrder, nameCopy, descriptionCopy, idMatchesRawValue), `ValencePolarity` × 1 (allCasesOrder), and `Valence helpers` × 5 (sizeGroupMapping, polarityMapping, assetNameMatchesAssets, shortLabel, lookupIsUnique).

- [ ] **Step 4: Confirm git state**

```bash
git log --oneline main..HEAD
```

Expected: 6 commits on top of `main` (the spec commit was already on the branch from brainstorming):

```
<sha> feat(ios): replace inline valence Picker with sheet-based picker (#284)
<sha> feat(ios): add ValencePickerSheet (#284)
<sha> feat(ios): bridge Valence to size group, polarity, and asset name
<sha> feat(ios): add ValenceSizeGroup and ValencePolarity helpers
<sha> feat(ios): add static valence pebble shape assets
<sha> docs(ios): brainstorm spec for valence picker (#284)
```

(One spec commit + one asset commit + four feature commits = six total.)

- [ ] **Step 5: Stop here — PR creation is a separate user-driven step**

Do not push or open a PR autonomously. Per project guidelines (`CLAUDE.md` PR Workflow Checklist), the user confirms branch name, PR title/body format, label inheritance from issue #284 (`feat`, `ios`, `ui`), and milestone (`M23 · TestFlight V1`) before the PR is opened.

When the user is ready, the PR draft will be:

- **Title:** `feat(ios): valence picker sheet`
- **Body:** opens with `Resolves #284`, lists the new files (`ValencePickerSheet.swift`, the 9 PDF imagesets, `ValenceMetadataTests.swift`) and modified files (`Valence.swift`, `PebbleFormView.swift`), and references the design doc at `docs/superpowers/specs/2026-04-19-ios-valence-picker-design.md`.
- **Labels:** `feat`, `ios`, `ui` (inherit from issue).
- **Milestone:** `M23 · TestFlight V1` (inherit from issue).

---

## Verification checklist (against issue #284 acceptance criteria)

| # | Criterion | Verified in |
|---|-----------|-------------|
| 1 | Tap valence row → opens sheet | Task 7 Step 2 |
| 2 | Sheet shows three sections per size with description and three options per section | Task 7 Step 3 |
| 3 | Picking an option closes the sheet | Task 7 Step 4 |
| — | Active state visual (accent background + background-colour shape/label) | Task 5 Step 3, Task 7 Step 4 |
| — | Inactive state visual (muted foreground) | Task 5 Step 3, Task 7 Step 3 |
| — | Static asset (not preview render) | Task 2 |
| — | Works for both create and edit flows | Task 7 Step 2 + Step 5 |
| — | VoiceOver labels and selected trait | Task 7 Step 6 |
