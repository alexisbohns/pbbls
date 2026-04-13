# iOS Path view — fetch & render pebbles (design)

**Issue:** [#211 [iOS] Path view](https://github.com/anthropics/pbbls/issues/211)
**Milestone:** M19 · Timeline & browse
**Date:** 2026-04-13
**Status:** Approved

## Goal

Replace the placeholder `PathView` (currently `Text("Path")`) with a working
reverse-chronological list of the signed-in user's pebbles, fetched from
Supabase. This is the **first iOS view that reads data from Supabase** and
will set the pattern that subsequent data-fetching views copy.

## Scope

In scope:
- Define a minimal `Pebble` Swift model (id, name, happenedAt).
- Fetch the current user's pebbles from Supabase, ordered by `happened_at`
  descending, on view appearance.
- Render the result as a SwiftUI `List` showing each pebble's name and date.
- Surface loading and error states.
- Log fetch failures via `os.Logger`.

Explicitly out of scope (each becomes a follow-up issue):
- Pull-to-refresh
- Cursor pagination / infinite scroll
- Styled empty state
- Tap → `PebbleDetailView`
- Card visual design (emotion color, intensity, glyph, etc.)
- A `PebbleRepository` / `SupabaseServicing` abstraction (YAGNI per
  `apps/ios/CLAUDE.md` — promote when a second consumer exists)
- Tests (no test target exists yet on iOS; tracked separately)

## Architectural rationale

The web app enforces "components never call the provider directly" because
its data layer has multiple `DataProvider` implementations. iOS today has
exactly one path to Supabase (`SupabaseService`), and `apps/ios/CLAUDE.md`
explicitly instructs: *"When a test needs to fake Supabase, extract a
`SupabaseServicing` protocol at that moment — not before. YAGNI."*

So this PR:
- Extracts the `Pebble` model into its own file (cheap, will be reused by
  Detail view, Profile, etc.).
- Calls `supabase.client` directly from `PathView`'s `.task` block.
- Does **not** introduce a repository layer. That happens the moment a
  second view needs the same fetch.

## Files

```
apps/ios/Pebbles/
  Features/Path/
    Models/
      Pebble.swift          ← NEW
    PathView.swift          ← REWRITE
```

`Models/` lives under `Features/Path/` for now; promote to
`Pebbles/Models/` when a second feature consumes `Pebble`.

## `Pebble.swift`

```swift
import Foundation

struct Pebble: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
    let happenedAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case happenedAt = "happened_at"
    }
}
```

**Field selection:** only the columns rendered in scope A. Adding
emotion / intensity / glyph fields now would be dead code; each is a
one-line addition when a future PR needs them.

**Types:** `UUID` and `Date` are decoded by the Supabase Swift SDK's
configured `JSONDecoder` from the database's `uuid` and `timestamptz`
columns.

## `PathView.swift`

```swift
import SwiftUI
import os

struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Path")
        }
        .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            Text(loadError).foregroundStyle(.secondary)
        } else {
            List(pebbles) { pebble in
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name).font(.body)
                    Text(pebble.happenedAt, style: .date)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func load() async {
        do {
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at")
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
            self.isLoading = false
        } catch {
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load your pebbles."
            self.isLoading = false
        }
    }
}
```

### State machine

Three mutually exclusive UI states:

1. **Loading** — `isLoading == true`. Shown until `load()` completes.
   Renders `ProgressView`.
2. **Error** — `loadError != nil`. Renders a single secondary-colored line
   of text. Generic user-facing string; technical detail goes to
   `os.Logger`.
3. **List** — neither of the above. Renders `List(pebbles)`. An empty
   pebble array currently renders as an empty `List`; a styled empty state
   is a follow-up.

### Why these choices

- **`.task { }` not `.onAppear`** — auto-cancels when the view disappears,
  per `apps/ios/CLAUDE.md`.
- **`List` not `ScrollView + LazyVStack`** — free row separators,
  accessibility, and pull-to-refresh hookpoint when we need it later. Cheap
  to swap for `ScrollView` when the real card design lands.
- **`@Environment(SupabaseService.self)`** — matches the iOS service
  injection pattern; views never construct `SupabaseClient` themselves.
- **Logging on every error path** — mirrors the web-side rule that silent
  failures are bugs.
- **`privacy: .private`** on the logged error — iOS-equivalent of redacting
  PII in production logs.

## Data access

### Query

```
from("pebbles")
  .select("id, name, happened_at")
  .order("happened_at", ascending: false)
  .execute()
```

### Why no `.eq("user_id", ...)` filter

`packages/supabase/supabase/migrations/20260411000001_core_tables.sql:164-172`
enables RLS on `public.pebbles` with `pebbles_select`, `pebbles_insert`,
`pebbles_update`, and `pebbles_delete` policies. Selects are already
restricted to the current user. Adding a client-side filter would be
redundant and invites the bug where the server policy and the client
filter drift apart.

### Index

`pebbles_happened_at_idx` exists
(`20260411000001_core_tables.sql:298`), so the ordered scan is cheap even
as user history grows.

## Error handling

- Network failures, decode errors, and auth errors all flow through the
  single `catch` block.
- The user sees a generic "Couldn't load your pebbles." line.
- The technical error is logged via `os.Logger` with
  `privacy: .private`, viewable in Console.app while debugging.
- The `isLoading` flag is reset to `false` in both success and failure
  paths so the UI never gets stuck on the spinner.

## Known limitations (deliberate)

- **No retry button.** First failure shows the error and stays there until
  the view is dismissed and re-shown. Acceptable for a first-cut data
  view; retry comes with the pull-to-refresh follow-up.
- **No empty-state copy.** A new user with zero pebbles sees an empty
  `List`. Visually plain but not broken.
- **Whole-list refetch every time the view appears.** No caching, no
  incremental update. Acceptable: the query is indexed and bounded by RLS
  to the current user's rows.

## Verification

Manual:
- Sign in as a user with existing pebbles → list renders newest-first.
- Sign in as a user with zero pebbles → empty `List`, no error.
- Toggle airplane mode and re-open Path → error line appears, error logged
  to Console.
- Build: `xcodegen generate` then build the iOS scheme in Xcode.

No automated tests in this PR — the iOS app has no test target yet.

## Follow-ups

Each becomes its own issue once this lands:

1. Pull-to-refresh (`.refreshable { }`)
2. Cursor pagination / infinite scroll
3. Styled empty state
4. Tap → `PebbleDetailView`
5. Rich pebble card design (emotion, intensity, glyph)
6. Promote `Pebble` model out of `Features/Path/Models/` when a second
   feature consumes it
7. Extract a `PebbleRepository` once a second view fetches pebbles
