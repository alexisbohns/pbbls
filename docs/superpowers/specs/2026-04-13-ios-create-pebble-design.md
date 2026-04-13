# iOS — Create a Pebble (issue #212)

## Context

The iOS app currently has a read-only `PathView` that lists pebbles fetched from Supabase. Issue #212 asks for the inverse: a native, zero-design form that lets an authenticated user record a new pebble and see it appear in the path immediately.

This is the first write-path feature on iOS, so the patterns we choose here will be reused by every subsequent create/edit flow.

## Goals

- Authenticated user can record a pebble with all mandatory fields and see it appear in the path list without leaving the screen.
- Use only native SwiftUI components (`Form`, `Picker`, `DatePicker`, `TextField`, `.sheet`). No custom design.
- Keep the data layer consistent with the existing `PathView` pattern: views call `SupabaseService` directly via `@Environment`. No new repository or store abstraction yet (YAGNI per `apps/ios/CLAUDE.md`).

## Non-goals

- Multi-select for domain, soul, or collection. The DB join tables support many-to-many, but V1 ships single-select.
- Per-field inline validation messages. The Save button is disabled until all mandatory fields are filled — that's the only validation feedback.
- Atomic insert via a Postgres function. The pebble row and its join rows are inserted in two steps and may be partially applied on failure (see "Known limitations").
- Caching the reference data (emotions, domains) across sheet opens. Each sheet open re-fetches.
- Extending the `Pebble` model with new fields. The current 3-field struct is sufficient because `PathView` only displays `name` and `happenedAt`.

## User flow

1. User is on `PathView` and sees a "Record a pebble" button at the top of the list.
2. User taps the button. A sheet slides up containing `CreatePebbleSheet`.
3. The sheet's `.task` loads four reference lists in parallel: `emotions`, `domains`, `souls`, `collections`. A `ProgressView` shows while loading. On error, an inline message with a Retry button is shown.
4. The form renders with sensible defaults: "When" defaults to now, "Privacy" defaults to Private. All other mandatory fields are empty.
5. User fills the fields. The Save button in the navigation bar is disabled until all mandatory fields are valid.
6. User taps Save. The button shows a spinner. The new pebble is inserted into `pebbles`, then any selected join rows (domain — always; soul, collection — optional) are inserted in parallel.
7. On success: the inserted `Pebble` is passed back to `PathView` via an `onCreated` closure. `PathView` prepends the pebble to its `pebbles` array and re-sorts by `happenedAt` descending. The sheet dismisses.
8. On failure: the sheet does not dismiss. The user keeps their typed values. An inline error message is shown. The error is logged via `os.Logger`.

## Acceptance criteria (from issue #212)

- As an authenticated user, when I'm on the path, then I can record a pebble.
- As an authenticated user, when I submit a new pebble, then I see it appear in the path.

## Key design decisions

### 1. Valence is presented as a single 9-option picker

The issue specs a "select for the valence" with 9 options:
`lowlight small / medium / large`, `neutral small / medium / large`, `highlight small / medium / large`.

The `pebbles` table does not have a `valence` column. It has two columns:

- `positiveness smallint check (positiveness between -1 and 1)`
- `intensity smallint check (intensity between 1 and 3)`

The 9 UI options map exactly to `positiveness × intensity`. We expose this as a single `Valence` Swift enum with 9 cases. Each case knows its `positiveness` and `intensity`, and the insert payload splits the enum into both columns.

A single picker (rather than two side-by-side pickers for tone and size) was chosen to match the issue text literally.

### 2. The form lives in a sheet, not inline above the list

The issue says "Put a record section card above the path listing the existing pebbles". Taken literally that means an inline form above the list. With 9 fields that would push the path list off-screen, defeating the acceptance criterion of "I can see the new pebble appear".

We honor the spirit by adding a "Record a pebble" button card at the top of the list (always visible) and presenting the form in a `.sheet`. Sheets are the iOS-standard pattern for create flows (Reminders, Calendar, Notes).

### 3. Date and time merged into a single `DatePicker`

The issue lists separate date and time pickers. iOS's `DatePicker` natively supports `displayedComponents: [.date, .hourAndMinute]` to show both in one control — this is what Calendar and Reminders use. Using one picker:

- removes the need to merge two `Date` values when saving,
- matches native iOS conventions,
- presents one tap target instead of two.

This is a conscious divergence from the issue text. If the issue author objects, splitting back into two pickers is a small change.

### 4. Single-select for domain, soul, and collection

The schema supports many-to-many for all three (`pebble_domains`, `pebble_souls`, `collection_pebbles` are join tables). The issue uses the word "select" loosely.

V1 ships single-select because:

- the acceptance criteria only require recording a pebble and seeing it appear,
- single-select uses native `Picker` rows; multi-select requires a custom `List` of checkmark rows behind a `NavigationLink`,
- the schema does not need to change when we upgrade later.

### 5. Insert returns the inserted row (`.insert(...).select().single()`)

Three options were considered for refreshing the path after a save:

- A — refetch the full pebble list after dismiss (extra round-trip)
- B — optimistic insert into the local array, send insert in background (rollback complexity)
- C — `.insert(...).select().single()` returns the freshly inserted row, append it to the local array

Option C was chosen. It avoids the extra round-trip, avoids rollback logic, and future-proofs us for server-rendered fields (timestamps, derived columns) since we always work with the real row.

### 6. Reference data is loaded each time the sheet opens

Three options were considered for loading emotions / domains / souls / collections:

- A — load when the sheet opens (`~300ms` spinner each time)
- B — load once in `PathView` and pass into the sheet
- C — shared `@Observable` reference store injected via `@Environment`

Option A was chosen. The iOS app has exactly one screen that needs these lists. A shared store is premature abstraction (matching the YAGNI guidance in `apps/ios/CLAUDE.md`). 300ms behind a `ProgressView` is acceptable for a form opened occasionally. Always-fresh data avoids stale-cache bugs.

### 7. No new service or repository — call Supabase directly

`PathView` already calls `supabase.client.from("pebbles")...` directly via `@Environment(SupabaseService.self)`. `CreatePebbleSheet` follows the same pattern. The `apps/ios/CLAUDE.md` says: *"When a test needs to fake Supabase, extract a `SupabaseServicing` protocol at that moment — not before."*

### 8. Trust Row Level Security — do not filter by user_id in the client

The `souls` and `collections` table policies (`packages/supabase/supabase/migrations/20260411000001_core_tables.sql:175-184`) already enforce `user_id = auth.uid()` on select. The client does not pass a user_id; the database scopes results automatically. The same applies to insert: we do not pass `user_id` in the payload (the policy requires `user_id = auth.uid()` on insert, which the auth trigger handles).

This is the canonical Supabase pattern: trust RLS, do not duplicate it in client code.

## File layout

All new files live under `apps/ios/Pebbles/Features/Path/`.

```
Features/Path/
├── Models/
│   ├── Pebble.swift               (existing — unchanged)
│   ├── PebbleDraft.swift          (NEW — the in-progress form state)
│   ├── Emotion.swift              (NEW — reference table row)
│   ├── Domain.swift               (NEW — reference table row)
│   ├── Soul.swift                 (NEW — user-owned)
│   └── Collection.swift           (NEW — user-owned)
├── PathView.swift                 (extend: add button + sheet)
└── CreatePebbleSheet.swift        (NEW — the form sheet)
```

`Pebble` (the domain model) and `PebbleDraft` (the form state) are intentionally separate types. `Pebble` is what comes back from the server with required fields like `id`. `PebbleDraft` is what the user is currently typing — fields can be empty or invalid. Mixing the two leads to optional-soup. This is a pattern that will be reused across every future create/edit flow.

`xcodegen` source is `apps/ios/project.yml`. New files under `Features/Path/` are picked up automatically by xcodegen's source globbing — no manual project edits needed. Run `xcodegen generate` (or `npm run generate --workspace=@pbbls/ios`) after adding files to refresh the `.xcodeproj`.

## Component contracts

### `PebbleDraft`

A value-type struct that holds the form state. Lives in `@State` on `CreatePebbleSheet`.

```swift
struct PebbleDraft {
    var happenedAt: Date = Date()         // mandatory, "now" by default
    var name: String = ""                 // mandatory
    var description: String = ""          // optional
    var emotionId: UUID? = nil            // mandatory
    var domainId: UUID? = nil             // mandatory
    var valence: Valence? = nil           // mandatory
    var soulId: UUID? = nil               // optional
    var collectionId: UUID? = nil         // optional
    var visibility: Visibility = .private // mandatory

    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && emotionId != nil
        && domainId != nil
        && valence != nil
    }
}

enum Valence: String, CaseIterable, Identifiable {
    case lowlightSmall, lowlightMedium, lowlightLarge
    case neutralSmall, neutralMedium, neutralLarge
    case highlightSmall, highlightMedium, highlightLarge

    var id: String { rawValue }
    var label: String { ... }     // "Lowlight — small", etc.
    var positiveness: Int { ... } // -1, 0, +1
    var intensity: Int { ... }    // 1, 2, 3
}

enum Visibility: String, CaseIterable, Identifiable {
    case `private`, `public`
    var id: String { rawValue }
    var label: String { self == .private ? "Private" : "Public" }
}
```

### `Emotion`, `Domain`, `Soul`, `Collection`

Decodable structs matching the columns of their respective tables. Only the fields the form actually needs:

```swift
struct Emotion: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    let color: String
}

struct Domain: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    let label: String
}

struct Soul: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
}

struct Collection: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
}
```

Note: `Collection` may collide with Swift's `Collection` protocol in some contexts. If it does, alias on import or rename to `PebbleCollection`. Decide at implementation time, not now.

### `CreatePebbleSheet`

```swift
struct CreatePebbleSheet: View {
    let onCreated: (Pebble) -> Void
    // ... internal @State for draft, reference data, loading, error
}
```

The only input is the `onCreated` closure. `SupabaseService` is pulled from `@Environment`. Dismissal happens internally via `@Environment(\.dismiss)`.

### `PathView` extensions

- A new `@State private var isPresentingCreate = false`.
- A "Record a pebble" button rendered above the list (e.g., as the first row of the `List` or as a section header).
- A `.sheet(isPresented: $isPresentingCreate) { CreatePebbleSheet { newPebble in ... } }`.
- The `onCreated` closure inserts `newPebble` into `pebbles` and re-sorts by `happenedAt` descending.

## Data flow

1. Tap "Record a pebble" → `isPresentingCreate = true`.
2. Sheet mounts. `.task` runs:
   ```swift
   async let emotions: [Emotion] = supabase.client.from("emotions").select().order("name").execute().value
   async let domains: [Domain] = supabase.client.from("domains").select().order("name").execute().value
   async let souls: [Soul] = supabase.client.from("souls").select("id, name").order("name").execute().value
   async let collections: [Collection] = supabase.client.from("collections").select("id, name").order("name").execute().value
   ```
3. User fills `draft`. Each field bound via `$draft.field`. Save button disabled when `!draft.isValid`.
4. Save tapped. `isSaving = true`.
5. Insert pebble:
   ```swift
   let payload = [
       "name": draft.name,
       "description": draft.description.isEmpty ? nil : draft.description,
       "happened_at": ISO8601DateFormatter().string(from: draft.happenedAt),
       "intensity": draft.valence!.intensity,
       "positiveness": draft.valence!.positiveness,
       "visibility": draft.visibility.rawValue,
       "emotion_id": draft.emotionId!.uuidString,
   ]
   let inserted: Pebble = try await supabase.client
       .from("pebbles")
       .insert(payload)
       .select()
       .single()
       .execute()
       .value
   ```
   (Exact payload typing — `[String: AnyJSON]` or a Codable struct — to be decided at implementation time based on the supabase-swift SDK API. Either approach satisfies the contract.)
6. Insert join rows in parallel:
   ```swift
   async let pd: Void = insertPebbleDomain(pebbleId: inserted.id, domainId: draft.domainId!)
   async let ps: Void = draft.soulId.map { insertPebbleSoul(pebbleId: inserted.id, soulId: $0) } ?? ()
   async let cp: Void = draft.collectionId.map { insertCollectionPebble(collectionId: $0, pebbleId: inserted.id) } ?? ()
   _ = try await (pd, ps, cp)
   ```
7. Call `onCreated(inserted)` → dismiss the sheet.
8. `PathView`'s closure prepends the pebble and re-sorts.

## Error handling

- Every async call is wrapped in `try/catch`.
- Catches log via `os.Logger` with `privacy: .private` on user-facing strings, mirroring `PathView.swift:49`.
- Reference data load failure: replaces the form with an error message and a Retry button.
- Insert failure: sets `saveError`, keeps the sheet open, keeps the typed values, lets the user retry.
- The Save button shows a `ProgressView` while `isSaving` is true and is `.disabled(!draft.isValid || isSaving)`.

## Known limitations

- **Pebble + join row inserts are not atomic.** If the pebble insert succeeds but a join insert fails, the pebble exists with incomplete relations. Acceptable for V1: failures are rare, the user can edit later, and the alternative (a Postgres `rpc` function) is meaningful complexity. To be revisited if real failures appear.
- **No offline support.** The form requires network. Acceptable for V1.
- **No image attachments.** Snaps will be added in a separate issue.
- **No glyph picker.** Glyphs will be added in a separate issue.

## Out of scope (for explicit clarity)

- Editing existing pebbles
- Deleting pebbles
- Snaps (image attachments)
- Glyphs
- Pebble cards (`pebble_cards` rows)
- Multi-select on domain / soul / collection
- Atomic transactional insert via RPC
- Caching reference data across sheet opens
- Extending the `Pebble` model with additional fields
- Tests (no test infrastructure in iOS yet — to be added in a dedicated PR per `apps/ios/CLAUDE.md`)

## Open questions

None at design time. Implementation-time decisions noted inline (insert payload typing, `Collection` naming collision).
