# iOS — Pebble Detail Sheet

**Issue:** [#253](https://github.com/Bohns/pbbls/issues/253) — [Feat] Pebbles details view
**Milestone:** M19 · iOS ShameVP
**Date:** 2026-04-14

## Context

Pebbles can be created (C of CRUD) via `CreatePebbleSheet`, and listed in a
lightweight timeline on `PathView`. They cannot yet be read in full. This spec
covers R of CRUD: tapping a pebble in the timeline opens a sheet showing the
complete pebble — all stored fields plus its related emotion, domain, soul,
and collection rows.

Update and delete are explicitly out of scope (future issues).

## Requirements

From the issue:

- As a user with a pebble history, when I tap a pebble item, then it opens a
  sheet with that pebble's details.
- Use only iOS native primitive SwiftUI components.
- Read-only. No edit, no delete, no swipe actions.

## Non-goals

- Editing pebble fields.
- Deleting pebbles.
- Sharing, exporting, or deep-linking.
- Rich/editorial layout — an Apple-style `Form` is sufficient for V1.
- Pre-fetching all detail data in the list query.

## Design decisions

### What the sheet shows (option B — rich)

The sheet shows everything the create sheet collects:

- `name`, `description`, `happened_at`, `visibility`
- `intensity` + `positiveness` displayed as a single `Valence` label
- `emotion` (name + colored dot)
- `domain(s)` (from `pebble_domains` join)
- `soul(s)` (from `pebble_souls` join, optional section)
- `collection(s)` (from `collection_pebbles` join, optional section)

A "minimal" variant (only direct `pebbles` columns) was rejected because the
create sheet already persists all of the above, so hiding them on read would
surprise the user.

### Fetch on tap, not upfront

`PathView` keeps its current lightweight list query (`id, name, happened_at`).
`PebbleDetailSheet` runs its own query by id when it appears.

Rationale:
- Separation of concerns — list owns the timeline query, detail owns the
  full-pebble query. They evolve independently.
- Cheaper list as history grows; no 4-join payload per row.
- Fresh data on every open — when edit lands later, no stale-state bugs.
- Simpler `Pebble` struct — stays minimal for the list.

Tradeoff: a brief loading spinner when opening the sheet. Acceptable and
consistent with native iOS patterns (Messages, Mail).

### Single embedded PostgREST query

The detail fetch is one round-trip using PostgREST relation embedding:

```swift
.from("pebbles")
.select("""
    id, name, description, happened_at, intensity, positiveness, visibility,
    emotion:emotions(id, name, color),
    pebble_domains(domain:domains(id, name)),
    pebble_souls(soul:souls(id, name)),
    collection_pebbles(collection:collections(id, name))
""")
.eq("id", value: pebbleId)
.single()
```

Rationale: one error path, one loading state, atomic freshness. PostgREST is
built for this — we should use it rather than parallelizing four queries on
the client.

### `Form` layout, not editorial `ScrollView`

The sheet uses `Form` + `Section` + `LabeledContent`, matching
`CreatePebbleSheet`'s structure. Rationale:
- Mirrors create → read for free cognitive mapping.
- Teaches core SwiftUI primitives the project will reuse everywhere.
- No design system yet — editorial layouts invite bikeshedding.

Editorial layout can be revisited when a visual design direction exists.

## Architecture

### File layout

```
apps/ios/Pebbles/Features/Path/
  Models/
    PebbleDetail.swift          (NEW)  rich read model, single-pebble shape
  PebbleDetailSheet.swift        (NEW)  the sheet view
  PathView.swift                 (EDIT) tap row → present sheet
```

`Pebble` (the lightweight list model) is untouched. A new `PebbleDetail` type
keeps the list/detail boundary explicit: lists decode `Pebble`, details decode
`PebbleDetail`.

### `PebbleDetail` model

A `Decodable` struct containing every field the sheet renders, plus small
ref types for the joined rows:

```swift
struct PebbleDetail: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let description: String?
    let happenedAt: Date
    let intensity: Int
    let positiveness: Int
    let visibility: Visibility
    let emotion: EmotionRef
    let domains: [DomainRef]
    let souls: [SoulRef]
    let collections: [CollectionRef]

    var valence: Valence { /* derived from intensity + positiveness */ }
}

struct EmotionRef: Decodable, Hashable { let id: UUID; let name: String; let color: String }
struct DomainRef:  Decodable, Hashable { let id: UUID; let name: String }
struct SoulRef:    Decodable, Hashable { let id: UUID; let name: String }
struct CollectionRef: Decodable, Hashable { let id: UUID; let name: String }
```

- `Visibility` is the existing enum — reused.
- `valence` is a **computed** property derived from `intensity`+`positiveness`
  via a switch. The DB remains source of truth; no drift risk.
- `domains`/`souls`/`collections` are flattened during decoding via a custom
  `init(from:)` that reads the PostgREST shape
  (`pebble_domains: [{ domain: ... }]`) and exposes clean arrays. The
  junction-row wrappers never leak into the view layer.
- The `*Ref` types are detail-view-local and intentionally do **not** reuse
  `Emotion`/`Domain`/`Soul`/`PebbleCollection` — those were shaped for the
  create-sheet pickers; decoupling avoids coupling the picker contracts to the
  detail view.

### `PebbleDetailSheet` view

Takes `pebbleId: UUID` as its only parameter. Small API surface means the
sheet can be reused from anywhere later (notification taps, deep links,
search results) without signature churn.

Three view states, mirroring `PathView` and `CreatePebbleSheet`:

1. `isLoading` → `ProgressView()`
2. `loadError` → error text + Retry button
3. `detail` present → a `Form` with:
   - **Section (unlabeled):** `When`, and `Description` (rendered only when
     non-empty).
   - **Section "Mood":** `Emotion` (name + colored dot), `Domain` (comma
     joined if multiple), `Valence` (the `Valence.label` for the derived
     case).
   - **Section "Optional":** `Soul`, `Collection`. Entire section omitted if
     both arrays are empty.
   - **Section "Privacy":** `Privacy` via `LabeledContent`.

Wrapped in a `NavigationStack` with `navigationTitle(detail?.name ?? "Pebble")`,
inline title display, and a `Done` button in the confirmation toolbar slot.

Loading is driven by `.task { await load() }`. A new sheet instance is created
each time the user reopens it, so `load()` naturally refreshes on reopen.

Errors are surfaced in-view **and** logged via `os.Logger` with category
`pebble-detail`, matching the project's "silent failures are bugs" rule.

### `PathView` edits

Three small changes:

1. New `@State private var selectedPebbleId: UUID?`.
2. New `.sheet(item: $selectedPebbleId) { id in PebbleDetailSheet(pebbleId: id) }`
   alongside the existing create sheet.
3. The existing row `VStack` is wrapped in a `Button { selectedPebbleId = pebble.id }`
   with `.buttonStyle(.plain)` to keep the row visual identical while adding
   tap behavior.

`UUID` is not `Identifiable` out of the box; we add a one-line extension
`extension UUID: Identifiable { public var id: UUID { self } }` in a small
utilities file (e.g. `Services/UUID+Identifiable.swift`). This is a common and
harmless extension that unblocks `.sheet(item:)` cleanly.

A sheet (not a `NavigationLink`) is the right affordance: the issue asks for
a sheet, and the detail is a side-view of the row — not a navigation
destination that warrants a back button or stack entry.

## Data flow

```
user taps row in PathView
  → PathView sets selectedPebbleId = pebble.id
  → SwiftUI observes the non-nil binding and presents PebbleDetailSheet(pebbleId:)
  → sheet's .task runs load()
  → load() calls supabase.client.from("pebbles").select(<embedded>).eq(id).single()
  → PostgREST returns the row with embedded emotion / pebble_domains / pebble_souls / collection_pebbles
  → PebbleDetail.init(from:) flattens junction rows into clean arrays
  → view transitions from ProgressView to Form
user taps Done (or swipes down)
  → dismiss() → selectedPebbleId returns to nil → sheet tears down
```

## Error handling

Every failure path logs via `os.Logger(category: "pebble-detail")` and sets
`loadError` to a user-facing message. The error state offers a Retry button
that re-runs `load()`. No silent catches. No timeouts in V1 — Supabase-swift
does not hang indefinitely on standard requests, and the project has not yet
introduced a `withTimeout` helper on the iOS side.

## Testing

Per the iOS CLAUDE.md, V1 ships without UI tests. Where we can add value
without XCTest:

- `PebbleDetail` decoding from a canned PostgREST-shaped JSON fixture, via
  Swift Testing (`@Suite`, `@Test`, `#expect`). Verifies the flattening of
  `pebble_domains`/`pebble_souls`/`collection_pebbles` and the `valence`
  derivation from `intensity` + `positiveness`.

No fake `SupabaseService` is introduced — per the iOS CLAUDE.md "extract a
protocol at that moment, not before" — since the only unit under test is
pure decoding.

## Out of scope

- Edit/delete (future issue covering U and D).
- Navigation from detail sheet to related entities (soul page, domain filter).
- Pagination or infinite scroll on the list.
- Offline caching of the detail payload.

## Open questions

None at authoring time. The design reuses the existing `SupabaseService`
environment injection, the existing `Visibility`/`Valence` enums, and
PostgREST features already relied upon elsewhere.
