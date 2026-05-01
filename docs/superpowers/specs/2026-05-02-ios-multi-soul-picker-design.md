# iOS multi-soul picker on pebble create/edit

**Status:** approved
**Date:** 2026-05-02
**Scope:** iOS only. One issue, one PR. No DB or web changes.

## Problem

iOS users want to link a pebble with several souls. The pebble form currently
exposes souls as a single-value `Picker` row in `PebbleFormView`'s "Optional"
section, so only one soul can be tagged per pebble.

The web app and database already model this as many-to-many: `pebble_souls`
join table, `create_pebble`/`update_pebble` RPCs accept `soul_ids[]` plus an
optional `new_souls[]` for inline creation. The iOS read path also already
decodes and renders multiple souls (`PebbleDetail.souls: [SoulWithGlyph]`,
`PebbleReadView.soulsRow`). The gap is purely the iOS write UI.

## Goals

1. Let iOS users tag a pebble with zero, one, or many souls on create and edit.
2. Allow inline soul creation from inside the picker without leaving the
   pebble form.
3. Match the visual language of the existing pebble read view (glyph + name
   chip flow) for the entry-point row.
4. Stay tightly scoped: no DB migration, no web changes, no speculative
   refactors of the Profile souls grid.

## Non-goals

- No DB migration. RPCs already accept `soul_ids[]`.
- No web-app changes.
- No refactor of `SoulsListView` / `SoulGridCell`.
- No order semantics on the soul list — `pebble_souls` has no `sort_order`
  column; selection is a `Set`.
- No long-press remove or swipe-to-delete on chips. Removal happens by
  re-opening the picker and toggling the soul off.
- No deferred soul creation via the RPC's `new_souls` payload key. iOS
  uses the existing eager `CreateSoulSheet` flow.

## UX

### Entry-point in `PebbleFormView`

A new `Section("Souls")` between the "Glyph" section and the "Optional"
section. Inside the section, a chip flow renders:

- Zero or more **soul chips** — rounded-square glyph thumbnail (~44pt) on a
  muted accent background, with the soul's name to the right of the tile.
- One trailing **"Add" chip** — same shape, dashed border, `person.badge.plus`
  icon, label "Add".

The flow wraps to multiple lines using the existing `PebblePillFlow` layout.
The section escapes Form row chrome via `.listRowInsets(EdgeInsets())` and
`.listRowBackground(Color.clear)`, the same approach used for the artwork row
at the top of the form.

**Tap behavior:** tapping any chip (selected or "Add") opens
`SoulPickerSheet`. There is no inline tap-to-remove or long-press remove in
this version.

### `SoulPickerSheet`

Modal sheet, structurally mirroring `ValencePickerSheet`:

- `NavigationStack` with title "Choose souls" (inline).
- `presentationDetents([.medium, .large])`, drag indicator visible,
  `.pebblesScreen()` modifier.
- Toolbar: `Cancel` (left, dismisses without applying), `Done` (right,
  applies the current selection and dismisses). `Done` is enabled
  unconditionally — zero souls is a valid result.
- Body: `ScrollView` containing a `LazyVGrid` with adaptive 96pt columns
  and 16pt spacing (identical to `SoulsListView`'s grid).
- Cells: a `SoulSelectableCell` per soul, then a final `+ New` dashed tile.
- Tapping a soul cell toggles its inclusion in the internal `Set<UUID>`
  selection. Tapping `+ New` presents `CreateSoulSheet`.

**Empty state:** if the user has zero souls, the grid shows only the `+ New`
tile centered, with a one-line caption ("Add the first soul to tag this
pebble with"). No error chrome.

**Selection visual** on `SoulSelectableCell`: when selected, the glyph tile
gets a 2pt `Color.pebblesAccent` ring and a small `checkmark.circle.fill`
badge in the top-right corner. The name label below stays unchanged.
Accessibility: `.isButton` trait always; `.isSelected` trait when selected.

### Inline soul creation

The `+ New` tile presents the existing `CreateSoulSheet`. On save,
`CreateSoulSheet` writes the new soul to Supabase eagerly (current behavior)
and invokes its `onCreated` callback. The picker:

1. Appends the returned `SoulWithGlyph` to its local in-memory souls list.
2. Inserts the new id into its `selection` set so the soul appears
   pre-selected when the user lands back in the grid.

No reload of the souls list is needed — the picker holds the new soul in
memory until the next time the sheet is opened.

If the user then cancels the pebble form, the orphan soul stays in their
library. This matches the existing Profile flow where a user can create a
soul and never tag a pebble with it. Souls are reusable entities; orphans
are not pollution.

## Data model changes

### `PebbleDraft.swift`

```swift
// before
var soulId: UUID?

// after
var soulIds: [UUID] = []
```

`init(from detail: PebbleDetail)`:

```swift
// before
self.soulId = detail.souls.first?.id

// after
self.soulIds = detail.souls.map(\.id)
```

`isValid` is unchanged — souls are not required.

### `PebbleCreatePayload.swift`

`init(from draft: PebbleDraft, userId: UUID)`:

```swift
// before
self.soulIds = draft.soulId.map { [$0] } ?? []

// after
self.soulIds = draft.soulIds
```

The `soul_ids` JSON key, the encoding logic, and the RPC contract are all
unchanged.

### `PebbleUpdatePayload.swift`

`init(from draft: PebbleDraft, detail: PebbleDetail)`:

- Compare `Set(draft.soulIds)` against `Set(detail.souls.map(\.id))`.
- Send `soul_ids` only when the sets differ. Send the full `draft.soulIds`
  array when sending — the RPC replaces the join rows wholesale (verified
  in migration `20260411000005_security_hardening.sql` lines 298–305).

## New files

### `Features/Path/SoulPickerSheet.swift`

```swift
struct SoulPickerSheet: View {
    let currentSelection: [UUID]
    let onConfirm: ([UUID]) -> Void
    // ...
}
```

Internal state:

- `@State private var souls: [SoulWithGlyph] = []`
- `@State private var selection: Set<UUID>` (seeded from `currentSelection`)
- `@State private var isLoading = true`
- `@State private var loadError: String?`
- `@State private var isPresentingCreate = false`
- `@Environment(SupabaseService.self) private var supabase`
- `@Environment(\.dismiss) private var dismiss`

Loads souls via the same query as `SoulsListView` (lines 117–122), including
the `glyphs(...)` join.

### `Features/Profile/Lists/SoulSelectableCell.swift`

A button-wrapped variant of `SoulGridCell` with a selected/unselected visual
state. Sits next to `SoulGridCell` rather than replacing it; Profile's grid
is navigation, not selection, so the two stay separate for now. Factorization
is a future cleanup if the parallel becomes obvious.

### `Features/Path/SelectedSoulsRow.swift`

The inline chip flow used inside `PebbleFormView`'s new "Souls" section.
Owns the `showPicker: Bool` state and presents `SoulPickerSheet` on tap.
Co-locates a small `SoulChip` view at the bottom of the file (rounded-square
glyph tile + name label, plus the dashed "Add" variant).

## Modified files

- **`PebbleDraft.swift`** — `soulId: UUID?` → `soulIds: [UUID]`; `init(from:)`
  reads `souls.map(\.id)`.
- **`PebbleCreatePayload.swift`** — pass `draft.soulIds` straight through.
- **`PebbleUpdatePayload.swift`** — diff `Set(draft.soulIds)` vs
  `Set(detail.souls.map(\.id))`.
- **`PebbleFormView.swift`** — `souls: [Soul]` prop becomes
  `souls: [SoulWithGlyph]`. Remove the `Picker("Soul")` row from the
  "Optional" section. Add the new `Section("Souls")` between "Glyph" and
  "Optional", containing `SelectedSoulsRow`.
- **`CreatePebbleSheet.swift`** — widen the souls fetch select string to
  include `glyphs(id, name, strokes, view_box)` so it returns
  `[SoulWithGlyph]` instead of `[Soul]`. Pass the new type into the form.
- **`EditPebbleSheet.swift`** — same souls-fetch widening as
  `CreatePebbleSheet.swift`.
- **`CreateSoulSheet.swift`** — `onCreated: () -> Void` becomes
  `onCreated: (SoulWithGlyph) -> Void`. The sheet already has the new
  soul in scope after the insert; constructing a `SoulWithGlyph` from the
  inserted row + the picked glyph is straightforward (or the sheet can
  re-select the row with the glyph join, mirroring `SoulsListView`'s
  query).
- **`SoulsListView.swift`** — update the `CreateSoulSheet` callsite at
  line 33 to ignore the argument: `{ _ in Task { await load() } }`.

## Localization

New keys to add to `Pebbles/Resources/Localizable.xcstrings`, with both
`en` and `fr` values filled before the PR per `apps/ios/CLAUDE.md`:

- Section title: `Souls`
- Add chip label: `Add`
- Sheet navigation title: `Choose souls`
- New tile label: `+ New soul`
- Empty-state caption: `Add the first soul to tag this pebble with`
- Accessibility selection label: `Selected` / `Not selected`

## Edge cases

- **Zero souls.** `draft.soulIds == []` is valid on both create and edit.
  The chip flow renders only the dashed "Add" chip. The update payload
  omits `soul_ids` if the set didn't change, sends `[]` if it did.
- **Editing a pebble that had a soul, removing it all.** Picker opens with
  the soul pre-selected, user taps it off, hits Done. Update payload
  detects the change (`Set([id]) != Set()`) and sends `soul_ids: []`. The
  RPC replaces the join rows wholesale.
- **Inline create then Cancel pebble form.** The orphan soul persists in
  the user's library. Same outcome as creating a soul from Profile and
  never tagging it.
- **Inline create then Cancel picker.** The new soul is already in the DB
  (eager creation). Cancel only discards the picker's selection state, so
  the soul exists but isn't tagged on this pebble. User can add it later.
- **Long names.** The chip's name label uses `lineLimit(1)` and
  `truncationMode(.tail)`, matching `SoulGridCell` line 16.
- **Sheet dismissed via swipe-down (drag indicator).** Treated as Cancel —
  selection is not applied.
- **Network failure on souls load inside the picker.** Same pattern as
  `SoulsListView` lines 75–80: `ContentUnavailableView` with retry copy.

## Estimated size

- 3 new files, 8 modified files.
- ~250–350 net new lines of Swift.
- Medium task per `CLAUDE.md` triage. Workspace-scoped lint and build
  (no shared types or `packages/*` touched).

## Issue and PR

One issue, one PR.

- Issue title: `[Feat] Multi-select souls picker on pebble create/edit (iOS)`
- PR title: `feat(ios): multi-select souls on pebble create and edit`
- Labels: `feat`, `ios`, `ui`, `core`. No `db` label (no migration).
- Milestone: to be confirmed with the user when opening the issue.

## Verification

- Manually verify on simulator:
  - Create flow with zero, one, and three souls (including a `+ New` insert).
  - Edit flow that adds a soul, removes a soul, replaces all souls, and
    leaves the set unchanged (no `soul_ids` should be sent).
  - Empty-state picker on a fresh account.
  - Truncation with a long soul name.
  - Localization: `en` and `fr` both render correctly; no `New`/`Stale`
    rows in `Localizable.xcstrings`.
- Read view continues to render the multi-soul row unchanged.
