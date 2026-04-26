# iOS — Name and rename glyphs

**Issue:** [#300 — \[Feat\] Set a name to a glyph](https://github.com/alexisbohns/pbbls/issues/300)
**Milestone:** M25 · Improved core UX
**Scope:** iOS only.

## Context

`Glyph.name` already exists as a nullable `text` column in `public.glyphs`, the
`Glyph` Swift model already decodes it, and `GlyphService.create(strokes:name:)`
already accepts a name parameter — but no UI ever supplied one. Likewise, no
screen lets a user edit a glyph's name after creation.

This spec wires both ends to the existing column: a name field in the carve
sheet, and a tap-to-rename alert on Profile > Glyphs.

## Acceptance criteria (from #300)

- As I'm collecting a pebble, when I carve a new glyph, then I can set its name.
- As I'm setting the glyph's name, when I tap outside the keyboard, then the keyboard closes.
- As I'm focused on the glyph name with keyboard open, when I tap the close-keyboard button, then it closes the keyboard.
- As I'm on Profile > Glyphs, when I tap a glyph, then I can change its name.

## Scope

**In scope:**

1. Optional name field in `GlyphCarveSheet`, displayed **above** the canvas.
2. Carve sheet keyboard dismissal: tap-outside on surrounding background, plus
   a keyboard-toolbar **Done** button. Both wired through `@FocusState`.
3. Tap-to-rename in `GlyphsListView` via a native iOS `.alert` containing a
   `TextField` and Cancel/Save buttons.
4. New `GlyphService.updateName(id:name:)` (single-table update, no RPC).
5. Localize all new strings in `Localizable.xcstrings` (en + fr).

**Out of scope:**

- Editing strokes (no canvas re-edit).
- Deleting glyphs from Profile > Glyphs.
- Naming from `GlyphPickerSheet` directly — naming happens inside the carve
  sheet, which the picker already presents for new glyphs.
- Backfilling names for existing rows.
- Server-side name validation, length cap, or uniqueness.

## UI

### `GlyphCarveSheet`

New layout inside the existing `content` `VStack` (top → bottom):

```
[ TextField "Name (optional)" ]   ← new
[ GlyphCanvasView ]               ← unchanged
[ saveError ]                     ← unchanged
[ Undo / Clear ]                  ← unchanged
```

State and behavior:

- `@State private var name: String = ""`
- `@FocusState private var nameFieldFocused: Bool`
- `TextField` placeholder: `LocalizedStringKey("Name (optional)")`
- `.textInputAutocapitalization(.words)`
- `.submitLabel(.done)` with `.onSubmit { nameFieldFocused = false }`
- `.accessibilityLabel("Glyph name")`
- A `ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("Done") { nameFieldFocused = false } }`
- A `.contentShape(Rectangle()).onTapGesture { nameFieldFocused = false }`
  on the surrounding `VStack` — **not** on `GlyphCanvasView`, so canvas
  strokes still register.
- Save remains enabled on `!strokes.isEmpty`. The name field never affects Save.
- On save: trim whitespace; pass `nil` if the trimmed string is empty,
  otherwise pass the trimmed string.

### `GlyphsListView`

Each grid cell becomes a `Button` (`.buttonStyle(.plain)`). Tap opens a native
rename alert:

- `@State private var renaming: Glyph?` — set on tap, drives alert presentation.
- `@State private var renameDraft: String = ""` — populated from
  `renaming?.name ?? ""` via `.onChange(of: renaming)`.
- `@State private var renameError: String?` — inline error rendered above the
  grid (mirrors the existing `loadError` pattern in the file).

```swift
.alert("Rename glyph", isPresented: renamingBinding, presenting: renaming) { glyph in
    TextField("Name (optional)", text: $renameDraft)
        .textInputAutocapitalization(.words)
    Button("Cancel", role: .cancel) {}
    Button("Save") { Task { await commitRename(glyph) } }
}
```

Optimistic update flow on Save:

1. Replace the glyph in the local `glyphs` array with a copy whose `name` is
   the trimmed draft (or `nil` if empty).
2. Call `service.updateName(id:name:)`.
3. On success, replace the local entry again with the canonical returned row.
4. On failure: revert to the original entry, log via `Logger`, set
   `renameError` to a localized string.

Each grid `Button` carries:

- `.accessibilityLabel(glyph.name ?? "Untitled glyph")`
- `.accessibilityHint("Double tap to rename")`

### Localization

New keys to add to `apps/ios/Pebbles/Resources/Localizable.xcstrings` (en + fr):

| Key | en | fr |
|---|---|---|
| `Name (optional)` | Name (optional) | Nom (facultatif) |
| `Rename glyph` | Rename glyph | Renommer le glyphe |
| `Glyph name` | Glyph name | Nom du glyphe |
| `Untitled glyph` | Untitled glyph | Glyphe sans nom |
| `Double tap to rename` | Double tap to rename | Touchez deux fois pour renommer |
| `Couldn't rename glyph. Please try again.` | Couldn't rename glyph. Please try again. | Impossible de renommer le glyphe. Veuillez réessayer. |

Reuse existing `Done`, `Save`, `Cancel` keys if already present in the catalog.

Before opening the PR: open `Localizable.xcstrings` in Xcode and confirm no
entry is in the `New` or `Stale` state, and that every row has both `en` and
`fr` filled (per `apps/ios/CLAUDE.md`).

## Data layer

### Model

`Glyph.name: String?` already exists. No change.

### Service

Add to `GlyphService`:

```swift
/// Updates a glyph's name. Pass `nil` (or an empty/whitespace-only string) to
/// clear it. Single-table write — no RPC needed (per AGENTS.md). RLS enforces
/// ownership via the existing `glyphs_update` policy.
func updateName(id: UUID, name: String?) async throws -> Glyph {
    let value = normalizedName(name)
    let updated: Glyph = try await supabase.client
        .from("glyphs")
        .update(["name": value])
        .eq("id", value: id)
        .select("id, name, strokes, view_box")
        .single()
        .execute()
        .value
    return updated
}

private func normalizedName(_ raw: String?) -> String? {
    let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines)
    return (trimmed?.isEmpty ?? true) ? nil : trimmed
}
```

Wire `normalizedName(_:)` into the existing `create(strokes:name:)` so the
trim/empty rule lives in exactly one place.

`GlyphCarveSheet.save()` switches from:

```swift
let glyph = try await service.create(strokes: strokes)
```

to:

```swift
let glyph = try await service.create(strokes: strokes, name: name)
```

(The service handles trimming.)

### Normalization rule (single source of truth)

Both create and rename apply the same rule, in the service:

1. Trim whitespace and newlines.
2. If the result is empty, store `nil`.
3. Otherwise, store the trimmed string as-is.

No length cap, no uniqueness check, no profanity filter. The DB has no
constraint to mirror.

### Migration

**None.** `glyphs.name` is already nullable. RLS already permits owner updates.
`packages/supabase/types/database.ts` does not need regeneration.

## Error handling

### Carve sheet

- Existing `saveError` String state and red `Text` block are kept as-is.
- On failure, `isSaving = false`, sheet stays open, name field preserves
  whatever the user typed.
- `Logger` (subsystem `app.pbbls.ios`, category `glyph-carve`) logs the error
  with `.private` privacy.

### Rename alert

- Optimistic local update on Save tap.
- On `service.updateName(...)` throw: revert the array entry, log via
  `Logger` (category `profile.glyphs`, already in use), set `renameError` to
  the localized retry string. Render `renameError` inline above the grid.
- `renameError` clears on the next successful action or next `.task` reload.

## Edge cases

| Case | Behavior |
|---|---|
| Save tapped with no strokes | Save disabled (existing). Name field irrelevant. |
| Name is only whitespace | Trimmed → `nil`. |
| Save with no name | Stored as `nil` (today's behavior). |
| Rename to same value | Update is still issued; harmless. |
| Rename to empty | Trimmed → `nil`; clears the name. |
| Cancel carve with strokes drawn | Existing discard alert triggers; name doesn't change cancel logic. |
| Tap-outside while save is in flight | `nameFieldFocused = false` is harmless; no race. |
| Network failure during rename | Revert local state, alert is already dismissed, error rendered above grid. User can re-tap to retry. |
| Network failure during create | Existing `saveError` flow handles it; name field retains its value. |
| Glyph deleted by another device while rename is in flight | `update().single()` returns no row → throws → caught → reverted. Acceptable for V1. |

## Accessibility

- Carve sheet `TextField`: `.accessibilityLabel("Glyph name")`.
- Keyboard toolbar **Done**: SwiftUI's default labelling is sufficient.
- Grid cells: `.accessibilityLabel(glyph.name ?? "Untitled glyph")` and
  `.accessibilityHint("Double tap to rename")`.
- Alert: SwiftUI handles announcement of title, text field, and buttons.
- Tap-to-dismiss-keyboard area excludes `GlyphCanvasView`, so VoiceOver users
  retain the canvas as its own gesture target.

## Testing

Per `apps/ios/CLAUDE.md`: Swift Testing (`@Suite`, `@Test`, `#expect`),
no UI tests.

- **New:** `apps/ios/PebblesTests/Features/Glyph/GlyphUpdateNamePayloadEncodingTests.swift`
  - Verifies that the dict body sent to Supabase encodes correctly:
    `["name": "Foo"]` → `{"name":"Foo"}`, `["name": nil]` → `{"name":null}`.
- **Existing:** `GlyphInsertPayloadEncodingTests` already covers `name` from
  the #298 work — confirm and skip if covered.

Manual QA against the four acceptance criteria before opening the PR:

1. Carve a glyph, set a name, save → name appears under the thumbnail in
   `GlyphsListView`.
2. With keyboard open in carve sheet, tap outside the field → keyboard closes,
   no extra stroke is registered on the canvas.
3. With keyboard open in carve sheet, tap the keyboard-toolbar **Done** button
   → keyboard closes.
4. On Profile > Glyphs, tap a glyph → alert appears with current name; edit
   and save → grid updates with new name.

## Files touched

**Modified:**

- `apps/ios/Pebbles/Features/Glyph/Views/GlyphCarveSheet.swift`
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`
- `apps/ios/Pebbles/Features/Glyph/Services/GlyphService.swift`
- `apps/ios/Pebbles/Resources/Localizable.xcstrings`

**Added:**

- `apps/ios/PebblesTests/Features/Glyph/GlyphUpdateNamePayloadEncodingTests.swift`

**Not touched:**

- No DB migration.
- No `database.ts` regen.
- No `project.yml` change. The new test file lives under an existing
  path-based group; run `npm run generate --workspace=@pbbls/ios` so xcodegen
  picks it up.
- `GlyphPickerSheet` — naming flows through `GlyphCarveSheet`, which the
  picker already presents.
- `docs/arkaik/bundle.json` — naming a glyph adds no screen, route, model, or
  endpoint; nothing to update.

## Implementation order (for the plan)

1. Service: add `updateName` and `normalizedName(_:)`; refactor `create(...)`
   to call the helper.
2. Encoding test for the rename payload.
3. Carve sheet: name field, focus binding, keyboard toolbar Done,
   tap-to-dismiss.
4. Rename alert on Profile > Glyphs: wrap grid cells in buttons, add alert
   and draft state, optimistic update with revert.
5. Localization pass in Xcode (`Localizable.xcstrings`): fill en + fr,
   confirm none `New`/`Stale`.
6. Run `npm run generate --workspace=@pbbls/ios`, build and run unit tests
   in Xcode.
7. Manual QA against the four acceptance criteria.

## Branch and PR

- Branch: `feat/300-glyph-name`
- PR title: `feat(ios): name and rename glyphs`
- PR body opens with `Resolves #300`, lists files changed, includes
  implementation notes and the acceptance-criteria checklist.
- Labels inherited from #300: `core`, `feat`, `ios`.
- Milestone: `M25 · Improved core UX`.
- Confirm labels and milestone with the user before opening (per `CLAUDE.md`).
