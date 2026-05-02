# iOS Multi-Soul Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let iOS users tag a pebble with zero, one, or many souls on create
and edit, including inline soul creation from the picker.

**Architecture:** The DB and the iOS read path already model souls as
many-to-many. This plan only changes the iOS write UI. New `SoulPickerSheet`
(multi-select grid + inline `+ New` tile) replaces the single-soul `Picker`
in `PebbleFormView`. Entry-point becomes a chip flow (glyph tile + name +
dashed "Add" tile) inside its own form section. `PebbleDraft.soulId: UUID?`
becomes `soulIds: [UUID]`; both payloads pass the array straight through.

**Tech Stack:** Swift 5, SwiftUI (iOS 17+), Swift Testing (`@Suite`/`@Test`),
Supabase Swift SDK.

**Spec:** `docs/superpowers/specs/2026-05-02-ios-multi-soul-picker-design.md`

**Task ordering rationale:** Tasks 1–5 are non-breaking additions
(everything new compiles alongside the old single-soul `Picker`). Task 6
flips the data model and removes the old `Picker` in one atomic commit.
Task 7 is the final localization sweep.

---

## Task 1: Widen souls fetch from `Soul` to `SoulWithGlyph`

The chip flow needs glyph strokes to render the rounded-square thumbnail.
`SoulsListView.swift` already runs the joined query; mirror its select string
in the two pebble sheets and propagate the type up to `PebbleFormView`. This
task does not yet change UI — the existing `Picker` continues to work because
`SoulWithGlyph` carries the same `id` and `name` properties as `Soul`.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` (souls state + fetch)
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` (souls state + fetch)
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` (prop type)

- [ ] **Step 1: Change the souls state and fetch in `CreatePebbleSheet`**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`:

```swift
// line 14 — before
@State private var souls: [Soul] = []
// line 14 — after
@State private var souls: [SoulWithGlyph] = []
```

```swift
// lines 231-236 — before
async let soulsQuery: [Soul] = supabase.client
    .from("souls")
    .select("id, name, glyph_id")
    .order("name")
    .execute()
    .value
// lines 231-236 — after
async let soulsQuery: [SoulWithGlyph] = supabase.client
    .from("souls")
    .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
    .order("name")
    .execute()
    .value
```

- [ ] **Step 2: Change the souls state and fetch in `EditPebbleSheet`**

In `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`:

```swift
// line 25 — before
@State private var souls: [Soul] = []
// line 25 — after
@State private var souls: [SoulWithGlyph] = []
```

```swift
// lines 173-178 — before
async let soulsQuery: [Soul] = supabase.client
    .from("souls")
    .select("id, name, glyph_id")
    .order("name")
    .execute()
    .value
// lines 173-178 — after
async let soulsQuery: [SoulWithGlyph] = supabase.client
    .from("souls")
    .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
    .order("name")
    .execute()
    .value
```

- [ ] **Step 3: Change the prop type on `PebbleFormView`**

In `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`:

```swift
// line 15 — before
let souls: [Soul]
// line 15 — after
let souls: [SoulWithGlyph]
```

```swift
// line 48 — before
souls: [Soul],
// line 48 — after
souls: [SoulWithGlyph],
```

The `Picker("Soul")` body at lines 220–226 already does
`Text(soul.name).tag(UUID?.some(soul.id))` — both fields are present on
`SoulWithGlyph`, so the picker keeps compiling.

- [ ] **Step 4: Build the iOS workspace to confirm no regressions**

Run:

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

Expected: `BUILD SUCCEEDED`. The existing single-soul flow still works.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/PebbleFormView.swift
git commit -m "$(cat <<'EOF'
chore(ios): widen souls fetch to SoulWithGlyph in pebble form

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `CreateSoulSheet.onCreated` returns the new `SoulWithGlyph`

The picker (Task 4) needs the new soul's id so it can auto-select it after
inline creation. Today `CreateSoulSheet` inserts the row and calls
`onCreated()` with no payload. Switch the insert to `.insert(...).select(...).single().execute()`
with the same join the picker uses, and widen the callback signature.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` (callsite)

- [ ] **Step 1: Widen the callback type and the insert**

In `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`:

```swift
// line 8 — before
let onCreated: () -> Void
// line 8 — after
let onCreated: (SoulWithGlyph) -> Void
```

In `save()` (around lines 110–125), replace:

```swift
// before
try await supabase.client
    .from("souls")
    .insert(payload)
    .execute()
onCreated()
dismiss()
```

with:

```swift
// after
let inserted: SoulWithGlyph = try await supabase.client
    .from("souls")
    .insert(payload)
    .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
    .single()
    .execute()
    .value
onCreated(inserted)
dismiss()
```

The inserted glyph rows return joined under the same `glyphs(...)` shape
`SoulsListView` already uses, so `SoulWithGlyph` decoding works without a
new model.

- [ ] **Step 2: Update the existing `#Preview` callsite in the same file**

```swift
// last line of CreateSoulSheet.swift — before
CreateSoulSheet(onCreated: {})
// after
CreateSoulSheet(onCreated: { _ in })
```

- [ ] **Step 3: Update the `SoulsListView` callsite**

In `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`:

```swift
// line 33 — before
CreateSoulSheet(onCreated: {
    Task { await load() }
})
// line 33 — after
CreateSoulSheet(onCreated: { _ in
    Task { await load() }
})
```

The Profile grid still reloads from the server; it doesn't need the
returned `SoulWithGlyph`. Future work could optimize this by appending
the inserted row directly, but that's out of scope.

- [ ] **Step 4: Build to confirm**

Run:

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift \
        apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift
git commit -m "$(cat <<'EOF'
refactor(ios): CreateSoulSheet onCreated returns the inserted soul

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `SoulSelectableCell`

A button-wrapped variant of `SoulGridCell` with selected/unselected state.
Sits next to `SoulGridCell` rather than replacing it — Profile's grid is
navigation, not selection, so the two stay separate.

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift`

- [ ] **Step 1: Create the cell**

Write `apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift`:

```swift
import SwiftUI

/// Selection variant of `SoulGridCell`. Same 96pt glyph thumbnail + name
/// label, plus a 2pt accent ring and a checkmark badge in the top-right
/// when selected. Tap toggles via `onToggle`.
struct SoulSelectableCell: View {
    let soul: SoulWithGlyph
    let isSelected: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            VStack(spacing: 8) {
                ZStack(alignment: .topTrailing) {
                    GlyphThumbnail(strokes: soul.glyph.strokes, side: 96)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(
                                    isSelected ? Color.pebblesAccent : Color.clear,
                                    lineWidth: 2
                                )
                        )

                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.title3)
                            .foregroundStyle(Color.pebblesAccent, Color.pebblesBackground)
                            .padding(6)
                            .accessibilityHidden(true)
                    }
                }
                Text(soul.name)
                    .font(.callout)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity)
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(soul.name)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : [.isButton])
    }
}

#Preview("not selected") {
    SoulSelectableCell(
        soul: SoulWithGlyph(
            id: UUID(),
            name: "Héloïse",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
                viewBox: "0 0 200 200",
                userId: nil
            )
        ),
        isSelected: false,
        onToggle: {}
    )
    .padding()
}

#Preview("selected") {
    SoulSelectableCell(
        soul: SoulWithGlyph(
            id: UUID(),
            name: "Ingrid",
            glyphId: SystemGlyph.default,
            glyph: Glyph(
                id: SystemGlyph.default,
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 6)],
                viewBox: "0 0 200 200",
                userId: nil
            )
        ),
        isSelected: true,
        onToggle: {}
    )
    .padding()
}
```

- [ ] **Step 2: Regenerate the Xcode project**

The new file must be picked up by `project.yml` (XcodeGen finds it by
folder pattern, but rerun to be sure):

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `Generated project successfully`.

- [ ] **Step 3: Build to confirm**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): SoulSelectableCell with checkmark and accent ring

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `SoulPickerSheet`

Multi-select sheet mirroring `ValencePickerSheet`'s structure. Loads souls
itself, holds a `Set<UUID>` selection, presents `CreateSoulSheet` from a
trailing `+ New` tile, applies the selection on `Done` and discards on
`Cancel`.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`

- [ ] **Step 1: Create the sheet**

Write `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`:

```swift
import SwiftUI
import os

/// Multi-select sheet for tagging a pebble with souls. Shown from
/// `SelectedSoulsRow` inside `PebbleFormView`. Loads its own souls via
/// `SupabaseService` so the form doesn't need to refetch when an inline
/// `+ New` insert happens.
///
/// Tap a cell to toggle. Done applies the selection. Cancel (or
/// swipe-down) discards. The `+ New` tile presents `CreateSoulSheet`;
/// the inserted soul is appended to the local list and pre-selected.
struct SoulPickerSheet: View {
    let currentSelection: [UUID]
    let onConfirm: ([UUID]) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var souls: [SoulWithGlyph] = []
    @State private var selection: Set<UUID> = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-form.souls")

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: 16)]

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Choose souls")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
                            onConfirm(Array(selection))
                            dismiss()
                        }
                    }
                }
                .pebblesScreen()
                .task { await load() }
                .sheet(isPresented: $isPresentingCreate) {
                    CreateSoulSheet { inserted in
                        souls.append(inserted)
                        selection.insert(inserted.id)
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load souls",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else {
            ScrollView {
                LazyVGrid(columns: columns, spacing: 16) {
                    ForEach(souls) { soul in
                        SoulSelectableCell(
                            soul: soul,
                            isSelected: selection.contains(soul.id),
                            onToggle: { toggle(soul.id) }
                        )
                    }
                    NewSoulTile { isPresentingCreate = true }
                }
                .padding()

                if souls.isEmpty {
                    Text("Add the first soul to tag this pebble with")
                        .font(.callout)
                        .foregroundStyle(Color.pebblesMutedForeground)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                        .padding(.top, 8)
                }
            }
        }
    }

    private func toggle(_ id: UUID) {
        if selection.contains(id) {
            selection.remove(id)
        } else {
            selection.insert(id)
        }
    }

    private func load() async {
        selection = Set(currentSelection)
        isLoading = true
        loadError = nil
        do {
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name", ascending: true)
                .execute()
                .value
            self.souls = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

/// Trailing tile in the picker grid that opens `CreateSoulSheet`.
/// Visually matches a soul cell: same 96pt square frame, dashed border,
/// `person.badge.plus` icon, and "+ New soul" label below.
private struct NewSoulTile: View {
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(
                        Color.pebblesMutedForeground,
                        style: StrokeStyle(lineWidth: 1.5, dash: [4])
                    )
                    .frame(width: 96, height: 96)
                    .overlay {
                        Image(systemName: "person.badge.plus")
                            .font(.title2)
                            .foregroundStyle(Color.pebblesMutedForeground)
                    }
                Text("+ New soul")
                    .font(.callout)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Create a new soul")
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `Generated project successfully`.

- [ ] **Step 3: Build to confirm**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

Expected: `BUILD SUCCEEDED`. The sheet exists but isn't presented anywhere
yet — that's fine, Task 5/6 wire it up.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): SoulPickerSheet with multi-select grid and inline create

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `SelectedSoulsRow` + `SoulChip`

The inline chip flow rendered inside `PebbleFormView`'s new "Souls"
section. Owns the picker sheet's presented state. Tapping any chip
(selected or "Add") opens the picker.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/SelectedSoulsRow.swift`

- [ ] **Step 1: Create the row**

Write `apps/ios/Pebbles/Features/Path/SelectedSoulsRow.swift`:

```swift
import SwiftUI

/// Inline chip flow shown inside `PebbleFormView`'s "Souls" section. Each
/// selected soul renders as a `SoulChip` (rounded-square glyph + name).
/// A trailing dashed "Add" chip opens `SoulPickerSheet`. Tapping any chip
/// also opens the picker — selection is managed exclusively there.
struct SelectedSoulsRow: View {
    @Binding var soulIds: [UUID]
    let allSouls: [SoulWithGlyph]

    @State private var isPresentingPicker = false

    var body: some View {
        PebblePillFlow(spacing: 12) {
            ForEach(selectedSouls) { soul in
                Button {
                    isPresentingPicker = true
                } label: {
                    SoulChip(soul: soul)
                }
                .buttonStyle(.plain)
            }

            Button {
                isPresentingPicker = true
            } label: {
                AddSoulChip()
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .sheet(isPresented: $isPresentingPicker) {
            SoulPickerSheet(
                currentSelection: soulIds,
                onConfirm: { soulIds = $0 }
            )
        }
    }

    /// `selected` preserves the order of `soulIds` so chip order is stable
    /// across rerenders. Souls missing from `allSouls` (e.g. the loader
    /// hasn't returned yet) are dropped silently — they'll appear once the
    /// fetch completes.
    private var selectedSouls: [SoulWithGlyph] {
        let byId = Dictionary(uniqueKeysWithValues: allSouls.map { ($0.id, $0) })
        return soulIds.compactMap { byId[$0] }
    }
}

/// Selected soul: 44pt rounded-square glyph thumbnail with a muted accent
/// background, name label to the right.
private struct SoulChip: View {
    let soul: SoulWithGlyph

    var body: some View {
        HStack(spacing: 8) {
            GlyphThumbnail(strokes: soul.glyph.strokes, side: 32)
                .padding(6)
                .background(Color.pebblesSurfaceAlt)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            Text(soul.name)
                .font(.subheadline)
                .foregroundStyle(Color.pebblesForeground)
                .lineLimit(1)
        }
        .padding(.trailing, 8)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(soul.name)
    }
}

/// Trailing dashed "Add" chip. Same height as a `SoulChip` so the flow
/// aligns vertically.
private struct AddSoulChip: View {
    var body: some View {
        HStack(spacing: 8) {
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1.5, dash: [4])
                )
                .frame(width: 44, height: 44)
                .overlay {
                    Image(systemName: "person.badge.plus")
                        .font(.callout)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
            Text("Add")
                .font(.subheadline)
                .foregroundStyle(Color.pebblesMutedForeground)
                .lineLimit(1)
        }
        .padding(.trailing, 8)
        .accessibilityLabel("Add a soul")
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `Generated project successfully`.

- [ ] **Step 3: Build to confirm**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

Expected: `BUILD SUCCEEDED`. The view exists but isn't wired in yet.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/SelectedSoulsRow.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "$(cat <<'EOF'
feat(ios): SelectedSoulsRow chip flow with Add tile

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Flip the data model and wire the new section

Atomic commit that switches `PebbleDraft.soulId` to `soulIds`, updates both
payloads, updates the existing tests, and replaces the old `Picker` in
`PebbleFormView` with the new `Section("Souls")` containing
`SelectedSoulsRow`.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`
- Modify: `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift`
- Modify: `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`
- Modify: `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`

- [ ] **Step 1: Update `PebbleDraft.swift`**

Replace `var soulId: UUID?` with `var soulIds: [UUID] = []` at line 13:

```swift
// before
var soulId: UUID?                     // optional
// after
var soulIds: [UUID] = []              // optional, empty = no souls
```

In `init(from detail: PebbleDetail)` at line 48:

```swift
// before
self.soulId = detail.souls.first?.id
// after
self.soulIds = detail.souls.map(\.id)
```

Update the doc comment on line 37 of the same file:

```swift
// before
/// - `soulId` / `collectionId` take the first element when present, nil otherwise.
// after
/// - `soulIds` is populated from `detail.souls.map(\.id)`; empty when no souls are linked.
/// - `collectionId` takes the first element when present, nil otherwise.
```

- [ ] **Step 2: Update `PebbleCreatePayload.swift`**

In `init(from draft: PebbleDraft, userId: UUID)` at line 91:

```swift
// before
self.soulIds = draft.soulId.map { [$0] } ?? []
// after
self.soulIds = draft.soulIds
```

- [ ] **Step 3: Update `PebbleUpdatePayload.swift`**

In `init(from draft: PebbleDraft, userId: UUID)` at line 102:

```swift
// before
self.soulIds = draft.soulId.map { [$0] } ?? []
// after
self.soulIds = draft.soulIds
```

- [ ] **Step 4: Update `PebbleFormView.swift`**

Remove the `Picker("Soul")` row from the "Optional" section (lines
220–226). The "Optional" section then contains only the Collection
picker:

```swift
// before
Section("Optional") {
    Picker("Soul", selection: $draft.soulId) {
        Text("None").tag(UUID?.none)
        ForEach(souls) { soul in
            Text(soul.name).tag(UUID?.some(soul.id))
        }
    }
    .listRowBackground(Color.pebblesListRow)

    Picker("Collection", selection: $draft.collectionId) {
        Text("None").tag(UUID?.none)
        ForEach(collections) { collection in
            Text(collection.name).tag(UUID?.some(collection.id))
        }
    }
    .listRowBackground(Color.pebblesListRow)
}
// after
Section("Optional") {
    Picker("Collection", selection: $draft.collectionId) {
        Text("None").tag(UUID?.none)
        ForEach(collections) { collection in
            Text(collection.name).tag(UUID?.some(collection.id))
        }
    }
    .listRowBackground(Color.pebblesListRow)
}
```

Add a new `Section("Souls")` between the existing "Glyph" section
(ends at line 217) and the "Optional" section. The section escapes
Form row chrome the same way the artwork row does:

```swift
Section("Souls") {
    SelectedSoulsRow(
        soulIds: $draft.soulIds,
        allSouls: souls
    )
    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
    .listRowBackground(Color.clear)
}
```

- [ ] **Step 5: Update `PebbleDraftFromDetailTests.swift`**

In `apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift`:

Replace the `fullyPopulated` assertion at line 100:

```swift
// before
#expect(draft.soulId == soulId)
// after
#expect(draft.soulIds == [soulId])
```

Rename the `noSouls` test to assert the empty-array case (lines 113–118):

```swift
// before
@Test("leaves soulId nil when no souls")
func noSouls() throws {
    let detail = try makeDetail(souls: [])
    let draft = PebbleDraft(from: detail)
    #expect(draft.soulId == nil)
}
// after
@Test("soulIds is empty array when detail has no souls")
func noSouls() throws {
    let detail = try makeDetail(souls: [])
    let draft = PebbleDraft(from: detail)
    #expect(draft.soulIds.isEmpty)
}
```

Add a new test asserting multi-soul population. Insert it directly
after the renamed `noSouls` test:

```swift
@Test("soulIds preserves all souls from the detail")
func multipleSouls() throws {
    let id1 = UUID()
    let id2 = UUID()
    let detail = try makeDetail(souls: [
        Soul(id: id1, name: "Héloïse", glyphId: UUID()),
        Soul(id: id2, name: "Ingrid", glyphId: UUID())
    ])
    let draft = PebbleDraft(from: detail)
    #expect(draft.soulIds == [id1, id2])
}
```

- [ ] **Step 6: Update `PebbleCreatePayloadEncodingTests.swift`**

In `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`:

Replace the `makeValidDraft` helper signature and body (lines 21–35) so
it accepts an array:

```swift
// before
private func makeValidDraft(
    soulId: UUID? = nil,
    collectionId: UUID? = nil
) -> PebbleDraft {
    var draft = PebbleDraft()
    draft.name = "Test"
    draft.description = "body"
    draft.emotionId = UUID()
    draft.domainId = UUID()
    draft.valence = .highlightLarge
    draft.soulId = soulId
    draft.collectionId = collectionId
    draft.visibility = .private
    return draft
}
// after
private func makeValidDraft(
    soulIds: [UUID] = [],
    collectionId: UUID? = nil
) -> PebbleDraft {
    var draft = PebbleDraft()
    draft.name = "Test"
    draft.description = "body"
    draft.emotionId = UUID()
    draft.domainId = UUID()
    draft.valence = .highlightLarge
    draft.soulIds = soulIds
    draft.collectionId = collectionId
    draft.visibility = .private
    return draft
}
```

Update the two existing soul-id tests (lines 61–78). Rename, retitle,
and switch the input shape:

```swift
// before
@Test("soul_ids is empty array when soulId is nil")
func emptySoulIds() throws {
    let draft = makeValidDraft(soulId: nil)
    let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

    let ids = json["soul_ids"] as? [String] ?? ["not-empty"]
    #expect(ids.isEmpty)
}

@Test("soul_ids is single-element array when soulId is set")
func singleSoulId() throws {
    let soulId = UUID()
    let draft = makeValidDraft(soulId: soulId)
    let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

    let ids = json["soul_ids"] as? [String] ?? []
    #expect(ids == [soulId.uuidString])
}
// after
@Test("soul_ids is empty array when draft.soulIds is empty")
func emptySoulIds() throws {
    let draft = makeValidDraft(soulIds: [])
    let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

    let ids = json["soul_ids"] as? [String] ?? ["not-empty"]
    #expect(ids.isEmpty)
}

@Test("soul_ids encodes every soul in draft.soulIds in order")
func multipleSoulIds() throws {
    let id1 = UUID()
    let id2 = UUID()
    let id3 = UUID()
    let draft = makeValidDraft(soulIds: [id1, id2, id3])
    let json = try encode(PebbleCreatePayload(from: draft, userId: UUID()))

    let ids = json["soul_ids"] as? [String] ?? []
    #expect(ids == [id1.uuidString, id2.uuidString, id3.uuidString])
}
```

- [ ] **Step 7: Update `PebbleUpdatePayloadEncodingTests.swift`**

In `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`:

Apply the same `makeValidDraft` rewrite as Step 6 (lines 21–35 of the
update tests file — the helper is identical to the create tests). Apply
the same two-test rewrite as Step 6 for the soul-id tests at lines 61–78
of the update tests file.

The other tests in this file (`scalarKeys`, `domainIds`, `collectionIds`,
`emptyDescriptionBecomesNull`, `nullGlyphId`, `setGlyphId`,
`iso8601DateWithDefaultEncoder`) are unaffected.

- [ ] **Step 8: Run the unit tests**

```bash
xcodebuild test \
  -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/PebbleDraftFromDetailTests \
  -only-testing:PebblesTests/PebbleCreatePayloadEncodingTests \
  -only-testing:PebblesTests/PebbleUpdatePayloadEncodingTests \
  -quiet
```

Expected: `TEST SUCCEEDED`. All three suites pass.

- [ ] **Step 9: Build the full app**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 10: Manual smoke test on simulator**

Launch the simulator, sign in, and walk through:

1. **Create with zero souls.** Open Create Pebble, fill the required
   fields, leave the Souls section's chip flow empty (only the "Add"
   tile). Save. The pebble appears on the Path with no soul row.
2. **Create with two souls.** Open Create Pebble, tap "Add", select
   two souls in the picker, Done. Two chips show in the form. Save.
   On the Path, the pebble's read view shows both souls.
3. **Create with inline `+ New soul`.** Open Create Pebble, tap "Add",
   tap "+ New soul", create "Léa". Back in the picker grid Léa is
   pre-selected. Done. The chip appears in the form. Save.
4. **Edit replaces the set.** Open an existing pebble that has one soul,
   tap Edit, tap the soul chip, in the picker deselect the existing
   soul and select two others. Done, Save. Read view shows the new two.
5. **Edit removes all souls.** Same as 4 but deselect everything in the
   picker. Save. Read view shows no souls row.

Confirm none of the existing flows (emotion, domain, valence, glyph,
collection, photo) regressed.

- [ ] **Step 11: Lint the iOS workspace**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: clean (no new warnings or errors).

- [ ] **Step 12: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift \
        apps/ios/Pebbles/Features/Path/PebbleFormView.swift \
        apps/ios/PebblesTests/PebbleDraftFromDetailTests.swift \
        apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift \
        apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift
git commit -m "$(cat <<'EOF'
feat(ios): multi-select souls on pebble create and edit

PebbleDraft.soulId becomes soulIds: [UUID]; both payloads pass the array
straight through. The single-soul Picker is replaced with a chip flow in
its own Souls section, opening the new SoulPickerSheet.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Localization sweep

Add and translate every new user-facing string in
`Pebbles/Resources/Localizable.xcstrings` (`en` and `fr`). Confirm no
`New` or `Stale` rows remain per `apps/ios/CLAUDE.md`.

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Build with localization extraction**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

This auto-extracts new `Text`, `Button`, `.navigationTitle`, etc. literals
into `Localizable.xcstrings` via `SWIFT_EMIT_LOC_STRINGS=YES`.

- [ ] **Step 2: Open `Localizable.xcstrings` in Xcode**

```bash
open apps/ios/Pebbles.xcodeproj
```

Then in Xcode, navigate to `Pebbles/Resources/Localizable.xcstrings`.

- [ ] **Step 3: Fill `en` and `fr` for each new key**

The new keys introduced by Tasks 3–6:

| Key                                                   | en                                              | fr                                                       |
|-------------------------------------------------------|-------------------------------------------------|----------------------------------------------------------|
| `Souls`                                               | Souls                                           | Âmes                                                     |
| `Add`                                                 | Add                                             | Ajouter                                                  |
| `Choose souls`                                        | Choose souls                                    | Choisir des âmes                                         |
| `+ New soul`                                          | + New soul                                      | + Nouvelle âme                                           |
| `Add the first soul to tag this pebble with`          | Add the first soul to tag this pebble with      | Ajoutez votre première âme pour la lier à ce pebble      |
| `Couldn't load souls`                                 | Couldn't load souls                             | Impossible de charger les âmes                           |
| `Something went wrong. Please try again.`             | (already exists — confirm both rows are filled) | (already exists — confirm both rows are filled)          |
| `Add a soul`                                          | Add a soul                                      | Ajouter une âme                                          |
| `Create a new soul`                                   | Create a new soul                               | Créer une nouvelle âme                                   |

- [ ] **Step 4: Confirm no `New` or `Stale` rows remain**

In Xcode's `Localizable.xcstrings` view, the state column for every
row should be `Translated` (or `Reviewed`). No row may be in `New` or
`Stale` state.

- [ ] **Step 5: Build once more to confirm clean extraction**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet build
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "$(cat <<'EOF'
chore(ios): localize multi-soul picker strings (en, fr)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Open the issue and PR

The project requires the issue and PR to be created with the right
metadata, per `CLAUDE.md`'s PR workflow.

- [ ] **Step 1: Confirm branch name**

The branch must follow `type/issueNumber-description`. Create the issue
first, then rename the branch:

```bash
gh issue create \
  --title "[Feat] Multi-select souls picker on pebble create/edit (iOS)" \
  --label "feat,ios,ui,core" \
  --body "$(cat <<'EOF'
The pebble create/edit form lets users link only one soul today. The DB,
RPCs, and read view already support many-to-many; we want the form to
match.

Spec: docs/superpowers/specs/2026-05-02-ios-multi-soul-picker-design.md
Plan: docs/superpowers/plans/2026-05-02-ios-multi-soul-picker.md
EOF
)"
```

Capture the issue number from the output. Then ask the user which
milestone to apply, set it, and rename the local branch:

```bash
git branch -m feat/<NUMBER>-multi-soul-picker
```

(If the working branch was already named correctly when work began,
this rename is a no-op.)

- [ ] **Step 2: Push and open the PR**

```bash
git push -u origin feat/<NUMBER>-multi-soul-picker
gh pr create \
  --title "feat(ios): multi-select souls on pebble create and edit" \
  --label "feat,ios,ui,core" \
  --milestone "<MILESTONE FROM USER>" \
  --body "$(cat <<'EOF'
Resolves #<NUMBER>

## Summary
- `PebbleDraft.soulId: UUID?` → `soulIds: [UUID]`; both payloads pass the array straight through (no DB change — RPCs already accept `soul_ids[]`).
- New `SoulPickerSheet` (multi-select grid + inline `+ New` tile) replaces the single-soul `Picker` in `PebbleFormView`.
- New `SelectedSoulsRow` chip flow lives in its own "Souls" form section; tapping any chip (selected or "Add") opens the picker.
- `CreateSoulSheet.onCreated` widens to return the inserted `SoulWithGlyph` so the picker can pre-select it on inline creation.

## Test plan
- [ ] Create a pebble with zero souls
- [ ] Create a pebble with two souls
- [ ] Create a pebble using `+ New soul` from inside the picker
- [ ] Edit a pebble's soul set (replace, add, remove all)
- [ ] Verify the read view still renders multiple souls correctly
- [ ] Verify French translations render and no `New`/`Stale` rows remain in `Localizable.xcstrings`

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Self-Review

**Spec coverage:**
- Data model (`PebbleDraft.soulIds`) → Task 6 Step 1 ✓
- Create payload → Task 6 Step 2 ✓
- Update payload → Task 6 Step 3 ✓
- `SoulPickerSheet` → Task 4 ✓
- `SoulSelectableCell` → Task 3 ✓
- `SelectedSoulsRow` (chip flow) → Task 5 ✓
- Form integration (new section, picker removed) → Task 6 Step 4 ✓
- `CreateSoulSheet` callback widening → Task 2 ✓
- `SoulsListView` callsite update → Task 2 Step 3 ✓
- Souls fetch widened to `SoulWithGlyph` → Task 1 ✓
- Localization → Task 7 ✓
- Edge cases (zero souls, removing all souls during edit, inline create on
  cancel, sheet swipe-down) → covered by Task 6 Step 10 manual smoke test ✓
- Issue + PR with correct labels and milestone → Task 8 ✓

No spec requirement is unimplemented.

**Type consistency:**
- `soulIds: [UUID]` used identically in `PebbleDraft`,
  `PebbleCreatePayload`, `PebbleUpdatePayload`, and
  `SelectedSoulsRow.$soulIds`. ✓
- `SoulWithGlyph` used for the form's `souls` prop, the picker's
  `souls` state, the chip flow's `allSouls`, and the
  `CreateSoulSheet.onCreated` return type. ✓
- `SoulPickerSheet.onConfirm: ([UUID]) -> Void` matches the
  `SelectedSoulsRow` callsite which writes back into
  `Binding<[UUID]>`. ✓

**Placeholder scan:** No "TBD", "TODO", "implement later", or vague
"appropriate handling" steps. Every code-change step shows the actual
code. The PR-creation step has placeholders for `<NUMBER>` and
`<MILESTONE FROM USER>` — these are intentional and resolved at runtime
after `gh issue create` returns the number and the user names a milestone.
