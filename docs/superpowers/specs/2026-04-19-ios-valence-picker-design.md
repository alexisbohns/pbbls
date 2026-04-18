# iOS Valence Picker — Design

**Issue:** #284 — `[Feat] Valence picker on iOS`
**Date:** 2026-04-19
**Scope:** iOS (`apps/ios`), V1 / TestFlight (Milestone M23)
**Labels:** `feat`, `ios`, `ui`

## Problem

The valence input on the iOS pebble form is a plain `Picker` with nine
text-only options ("Lowlight — small", "Neutral — medium", …). Users do
not understand what valence means, what the size axis represents, or how
their choice will affect the resulting pebble.

## Goal

Replace the inline picker with a sheet-based picker that:

1. Groups the nine options by **size** (small / medium / large), with each
   group framed as the kind of life event it represents (Day / Week /
   Month event) and described in user-facing language.
2. Shows each option as a **static pebble shape** drawn from the same
   art the web uses, so the user sees what they are picking before they
   pick it.
3. Closes itself the moment a valence is selected, writing back through
   the existing `PebbleDraft.valence` binding.

The data model and persistence path do not change — only how a `Valence`
is chosen.

## Non-goals

- **No data-layer changes.** `Valence.positiveness` and
  `Valence.intensity` mappings, `PebbleDraft`, `PebbleCreatePayload`,
  `PebbleUpdatePayload`, and the `compose-pebble` edge function are
  untouched.
- **No web-side parity work.** Web keeps its current valence picker.
- **No reuse of the per-option button elsewhere.** Stays inline in
  `ValencePickerSheet`. Extract later if and when a second consumer
  appears (filter UI, summary chip, etc.).
- **No live preview render in the picker.** The issue is explicit that
  shapes are static assets, not `PebbleRenderView` snapshots.
- **No haptic feedback or option-tap animations.** Out of scope for V1;
  follow up after TestFlight if the feel suffers.
- **No new tests beyond `ValenceMetadataTests`.** UI tests remain
  deferred per `apps/ios/CLAUDE.md`.
- **No Arkaik bundle update.** This change re-presents an existing field
  on existing screens (`CreatePebbleSheet`, `EditPebbleSheet`); it does
  not add or remove screens, routes, models, or endpoints.
- **No edits to `EditPebbleSheet` or `CreatePebbleSheet` directly.**
  Both consume `PebbleFormView`, which is the single integration point.

## Architecture

The picker is a single self-contained sheet, opened from a row in
`PebbleFormView`. The sheet is the only stateful piece — it reads the
current `Valence?` from the form's draft, shows the nine options grouped
into three sections, and on tap writes the new value through a closure
and dismisses itself. The form row is a presentational button that
mirrors the active selection.

```
PebbleFormView (existing)
  └─ "Valence" row (Button)         ← replaces inline Picker (lines 65-70)
       └─ presents
            └─ ValencePickerSheet   ← new
                 └─ for each ValenceSizeGroup:
                      ├─ section header (name + description)
                      └─ HStack of 3 option buttons (lowlight / neutral / highlight)
```

There is no Supabase call, no async work, and no service layer — the
data is the static `Valence` enum and nine PDF assets. This keeps the
sheet lighter than `GlyphPickerSheet`, which carries a service and a
loading state because it lists user-owned data.

## Files

### New

- `apps/ios/Pebbles/Features/Path/ValencePickerSheet.swift`
  The sheet view. Owns sheet chrome (`NavigationStack`, title, close
  button), iterates the three size groups, and lays out the three
  options per group inline.
- `apps/ios/Pebbles/Resources/Assets.xcassets/Valence/`
  Image-set folder with a parent `Contents.json`
  (`provides-namespace: true`) and nine PDF asset sets — one per
  `Valence` case.
- `apps/ios/PebblesTests/ValenceMetadataTests.swift`
  Unit tests covering `sizeGroup`, `polarity`, and `assetName` mappings,
  plus a sanity check that all nine cases have a non-empty asset name
  string.

### Modified

- `apps/ios/Pebbles/Features/Path/Models/Valence.swift`
  Add `sizeGroup`, `polarity`, `assetName`, and `shortLabel` computed
  properties. Add `ValenceSizeGroup` and `ValencePolarity` helper enums.
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`
  Replace the inline `Picker("Valence", …)` (lines 65–70) with a
  `Button` row that shows the active option's shape + label + chevron
  and presents `ValencePickerSheet`. Add a local
  `@State private var showValencePicker = false`. The empty-state
  placeholder mirrors the Glyph row's dashed-border style so the two
  rows feel symmetric.

### Not touched

- `PebbleDraft`, `PebbleCreatePayload`, `PebbleUpdatePayload`,
  `compose-pebble` — value semantics of `Valence` are unchanged.
- `EditPebbleSheet`, `CreatePebbleSheet` — they pass through
  `PebbleFormView` and inherit the new behaviour.
- `project.yml` — new files land in `Features/Path/` which is already
  mapped by an existing path-based group; no `xcodegen generate` run
  expected. (Verify during implementation by inspecting the relevant
  group's `path:` mapping.)

## Data model additions

```swift
enum ValenceSizeGroup: String, CaseIterable, Identifiable {
    case small, medium, large
    var id: String { rawValue }

    var name: String {
        switch self {
        case .small:  "Day event"
        case .medium: "Week event"
        case .large:  "Month event"
        }
    }

    var description: String {
        switch self {
        case .small:  "This moment impacted my day and will be wrapped in my weekly Cairn"
        case .medium: "This moment impacted my whole week and will be wrapped in my monthly Cairn"
        case .large:  "This moment impacted my whole month and will be wrapped in my yearly Cairn"
        }
    }
}

enum ValencePolarity: String, CaseIterable {
    case lowlight, neutral, highlight
    // Drives left-to-right ordering of options in each section.
}

extension Valence {
    var sizeGroup: ValenceSizeGroup { /* 9-case switch */ }
    var polarity: ValencePolarity   { /* 9-case switch */ }
    var assetName: String { "valence-\(rawValue)" }
    var shortLabel: String {
        switch polarity {
        case .lowlight:  "Lowlight"
        case .neutral:   "Neutral"
        case .highlight: "Highlight"
        }
    }
}
```

Notes:
- `shortLabel` is the under-shape label inside an option ("Lowlight" /
  "Neutral" / "Highlight"). The full `label` ("Lowlight — small") stays
  for the form row's collapsed display where the size also matters.
- `ValenceSizeGroup` and `ValencePolarity` are `CaseIterable` so the
  sheet body becomes a single nested `ForEach` over groups and
  polarities; the matching `Valence` case is found by predicate. No
  hand-maintained ordering.
- Nothing in this section changes any existing API surface of `Valence`.

## UI behaviour and visual states

### Form row (collapsed view in `PebbleFormView`)

A single `Button` row inside the existing `Section("Mood")`:

```
[ shape ]  Valence                            [ label ]  ›
```

- **Empty state** (`draft.valence == nil`): dashed-border 32×32
  placeholder in `Color.pebblesMutedForeground`; label reads "Choose…"
  in the same colour. Mirrors the Glyph row's empty state at
  `apps/ios/Pebbles/Features/Path/PebbleFormView.swift:81-86`.
- **Filled state**: small (~32pt) shape thumbnail using
  `Image(valence.assetName)` rendered with `.renderingMode(.template)`
  and tinted via `.foregroundStyle(Color.pebblesMutedForeground)`;
  label reads `valence.label` ("Lowlight — small") in
  `Color.pebblesForeground`.
- Tapping the row sets `showValencePicker = true`.

### Sheet — `ValencePickerSheet`

```
NavigationStack
 └─ ScrollView
     └─ VStack (spacing 24)
         └─ ForEach ValenceSizeGroup
             └─ VStack (alignment: .leading, spacing 12)
                 ├─ name        .font(.headline)    .foregroundStyle(Color.pebblesMutedForeground)
                 ├─ description .font(.subheadline) .foregroundStyle(Color.pebblesMutedForeground)
                 └─ HStack (spacing 12)
                     └─ ForEach ValencePolarity
                         └─ ValenceOptionButton (active = current selection)
```

- Title: **"Choose a valence"**, inline display mode.
- Toolbar: a single `Cancel` button in `.cancellationAction` placement
  (matches `GlyphPickerSheet`).
- `.presentationDetents([.medium, .large])` with
  `.presentationDragIndicator(.visible)`.
- `.pebblesScreen()` modifier applied for visual consistency with the
  rest of the app (same as `GlyphPickerSheet.swift:33`).

### Per-option button (rendered inline in the sheet)

A vertical `VStack` containing the shape (~64pt) and the option's
`shortLabel` underneath, wrapped in a tappable rounded-rect background.
The colour treatment maps directly to the issue's wording: muted
foreground when inactive, background-on-accent when active.

- **Inactive**: container background `Color.pebblesSurfaceAlt`; shape
  rendered as `.template` and tinted
  `Color.pebblesMutedForeground`; label
  `.foregroundStyle(Color.pebblesMutedForeground)`.
- **Active** (`option == draft.valence`): container background
  `Color.pebblesAccent`; shape tinted `Color.pebblesBackground`; label
  `.foregroundStyle(Color.pebblesBackground)`.
- All three options inside a section share equal width via
  `.frame(maxWidth: .infinity)`.
- Tapping calls a `(Valence) -> Void` closure provided by the parent;
  the parent updates `draft.valence` and calls `dismiss()`.

The "ValenceOptionButton" referenced in the layout sketch is a private
`@ViewBuilder` (or private `struct` in the same file) — not a separate
file. Per Approach A, extraction into its own view is deferred until a
second consumer exists.

### Accessibility

- Each option button: `.accessibilityLabel("\(sizeGroup.name), \(shortLabel)")`
  — e.g. "Day event, Lowlight". `.accessibilityAddTraits(.isSelected)`
  when active. Color is paired with the active/inactive label and trait
  so it isn't the sole indicator (matches the project guideline in
  `CLAUDE.md`).
- Form row: `.accessibilityLabel("Valence")` plus
  `.accessibilityValue(currentLabel)`; the chevron is
  `.accessibilityHidden(true)` (matches the Glyph row).
- Sheet header reads "Choose a valence" via the navigation title.

## Asset pipeline

Source files exist on web at
`apps/web/public/pebbles/{low,medium,high}-{negative,neutral,positive}.svg`.
iOS needs them as **PDFs** in the asset catalog. Web's `low/medium/high`
naming refers to size; iOS keeps `small/medium/large` to match the
existing `Valence` raw values.

### Web → iOS naming map

| Web SVG               | iOS asset name                | Valence case        |
| --------------------- | ----------------------------- | ------------------- |
| `low-negative.svg`    | `valence-lowlightSmall.pdf`   | `.lowlightSmall`    |
| `low-neutral.svg`     | `valence-neutralSmall.pdf`    | `.neutralSmall`     |
| `low-positive.svg`    | `valence-highlightSmall.pdf`  | `.highlightSmall`   |
| `medium-negative.svg` | `valence-lowlightMedium.pdf`  | `.lowlightMedium`   |
| `medium-neutral.svg`  | `valence-neutralMedium.pdf`   | `.neutralMedium`    |
| `medium-positive.svg` | `valence-highlightMedium.pdf` | `.highlightMedium`  |
| `high-negative.svg`   | `valence-lowlightLarge.pdf`   | `.lowlightLarge`    |
| `high-neutral.svg`    | `valence-neutralLarge.pdf`    | `.neutralLarge`     |
| `high-positive.svg`   | `valence-highlightLarge.pdf`  | `.highlightLarge`   |

### Conversion

SVG → PDF locally via `rsvg-convert -f pdf` (or Inkscape if `librsvg`
isn't installed). Output a 256×256pt logical canvas, single-color
(black) so template rendering can recolor on demand. Run as a one-off
during implementation; the resulting PDFs are checked into the repo. No
build-time conversion — the assets are static and low-churn.

### Asset catalog layout

```
Resources/Assets.xcassets/
  Valence/
    Contents.json                            ← provides-namespace: true
    valence-lowlightSmall.imageset/
      Contents.json                          ← single-scale + preserves-vector-representation
      valence-lowlightSmall.pdf
    … (8 more)
```

Each `imageset/Contents.json` is **Universal**, **Single Scale**, with
`template-rendering-intent: "template"` and
`preserves-vector-representation: true`. Together this enables
`.foregroundStyle(...)` recoloring and crisp scaling at any size.

### Implementation ordering

The conversion + catalog setup happen as a single early step in the
implementation plan, before the picker view is wired, so the view code
can reference real asset names from the start.

## Acceptance criteria

From issue #284, restated against this design:

1. **As a user on a pebble view, when I tap on the valence select, then
   it opens a sheet to pick the valence.** — Met by the new `Button`
   row in `PebbleFormView` presenting `ValencePickerSheet`.
2. **As a user on the valence sheet, I see three sections per size with
   description and three options per section for lowlight, neutral and
   highlight.** — Met by the sheet's
   `ForEach(ValenceSizeGroup.allCases)` × `ForEach(ValencePolarity.allCases)`
   structure, with the section copy from `ValenceSizeGroup.name` and
   `.description`.
3. **As a user on a valence sheet, when I pick a valence option, then
   it closes the sheet.** — Met by the option button's selection
   closure setting `draft.valence` and calling `dismiss()`.

## Risks and mitigations

- **Asset coverage gap.** If any of the nine PDFs is missing, the
  corresponding option renders an empty `Image` and the picker breaks
  silently. Mitigation: `ValenceMetadataTests` includes a guard that
  `assetName` is non-empty for every case; the implementation step adds
  all nine PDFs together as a single change so missing-asset bugs are
  caught at first run.
- **PDF template recoloring.** PDFs that were not exported as
  single-color black don't recolor cleanly with
  `.foregroundStyle(...)`. Mitigation: the conversion step explicitly
  flattens fills to black before export; the asset catalog config
  (`Single Scale` + `Preserves Vector Representation` +
  `template-rendering-intent: template`) is verified visually in the
  preview as the first step after wiring the option button, so any
  regression is caught before the rest of the picker is built on top.
- **Sheet height with the medium detent.** Three sections with
  descriptions plus the option grid could overflow `.medium`. The
  sheet is wrapped in a `ScrollView`, and detents include `.large` so
  the user can drag up if content is clipped at medium.

## Open questions

None at design time. Anything that arises during implementation
(unexpected detent crowding, accent contrast on the active state, etc.)
should be raised in the implementation plan rather than silently
adjusted here.

## Implementation hand-off

Next step: invoke the `superpowers:writing-plans` skill to convert this
design into a step-by-step implementation plan with TDD framing for the
new `ValenceMetadataTests` and a verification checklist for the picker
end-to-end flow.
