# iOS Profile Page — Design

**Issue:** [#250 — [iOS] Profile page](https://github.com/pbbls/pbbls/issues/250)
**Milestone:** M19 · iOS ShameVP
**Date:** 2026-04-13

## Context

The Profile tab is currently a stub containing only a "Log out" button. Issue #250 requires turning it into a functional screen that exposes the user's gamification stats (Karma, Bounce), access to their custom lists (Collections, Souls, Glyphs), and links to legal documents (Terms, Privacy).

The issue is explicit: **zero custom design — native UI components only.** The goal is functional wiring, not visual polish.

## Goals

1. Display Karma and Bounce stats, each tappable to open an explainer sheet.
2. Provide navigation into read-only lists of Collections, Souls, and Glyphs.
3. Provide in-app Safari sheets for Terms and Privacy, reusing the existing `LegalDocumentSheet`.
4. Preserve the existing Log out button.
5. Fail gracefully: if the stats fetch errors, the screen still works.

## Non-goals

- No CRUD on Collections / Souls / Glyphs. The list screens are read-only.
- No custom design, colors, typography, or spacing beyond what SwiftUI `List` + `Section` provide natively.
- No refactor of existing models (`Soul.swift`, `PebbleCollection.swift` stay in `Features/Path/Models/`).
- No new architecture layer (no ViewModel, no Repository). The feature follows the existing `PathView` pattern: view owns `@State`, calls `supabase.client` directly, logs errors via `os.Logger`.
- No unit tests. The feature is view wiring and Decodable structs; there is no business logic worth isolating.

## Backend surface used

Already present in `packages/supabase/supabase/migrations/`:

- **`v_karma_summary`** — per-user view exposing `total_karma` (sum of `karma_events.delta`) and `pebbles_count`.
- **`v_bounce`** — per-user view exposing `active_days` and `bounce_level` (0–7, computed from distinct active days over the last 28 days).
- **`collections`** — user-owned list, RLS-filtered to the current user. Fields used: `id`, `name`.
- **`souls`** — user-owned list, RLS-filtered. Fields used: `id`, `name`.
- **`glyphs`** — user-owned list, RLS-filtered. Fields used: `id`, `name`.

No backend changes are required.

## File layout

All new files live under `apps/ios/Pebbles/Features/Profile/`. Each file is one Swift source unit; `xcodegen generate` (driven by `project.yml`) picks them up automatically — no manual Xcode project edits.

```
Features/Profile/
  ProfileView.swift              (rewrite — currently a stub)
  Models/
    KarmaSummary.swift           (new — Decodable, maps v_karma_summary)
    BounceSummary.swift          (new — Decodable, maps v_bounce)
    Glyph.swift                  (new — Decodable, maps glyphs rows)
  Components/
    ProfileStatRow.swift         (new — reusable tappable stat row)
    ProfileNavRow.swift          (new — reusable row for nav/sheet triggers)
  Sheets/
    KarmaExplainerSheet.swift    (new — static copy in a native Form)
    BounceExplainerSheet.swift   (new — static copy in a native Form)
  Lists/
    CollectionsListView.swift    (new — read-only list, owns its fetch)
    SoulsListView.swift          (new — read-only list, owns its fetch)
    GlyphsListView.swift         (new — read-only list, owns its fetch)
```

`Soul` and `PebbleCollection` structs already exist under `Features/Path/Models/` with exactly the fields we need (`id: UUID`, `name: String`, `Identifiable, Decodable, Hashable`). The new list views import and reuse them. Only `Glyph.swift` is a new model.

## UI tree

```
ProfileView (NavigationStack root)
│  @State karma: KarmaSummary?
│  @State bounce: BounceSummary?
│  @State isLoading, loadError
│  @State presentedSheet: ProfileSheet?   (enum: .karma, .bounce)
│  @State presentedLegalDoc: LegalDoc?    (existing enum)
│
├─ List (insetGrouped style)
│  │
│  ├─ Section "Stats"
│  │  ├─ ProfileStatRow("Karma",  value: karma?.totalKarma,  icon: "sparkles")
│  │  │    → tap sets presentedSheet = .karma
│  │  └─ ProfileStatRow("Bounce", value: bounce?.bounceLevel, icon: "arrow.up.right")
│  │       → tap sets presentedSheet = .bounce
│  │
│  ├─ Section "Lists"
│  │  ├─ NavigationLink → CollectionsListView()
│  │  ├─ NavigationLink → SoulsListView()
│  │  └─ NavigationLink → GlyphsListView()
│  │
│  ├─ Section "Legal"
│  │  ├─ ProfileNavRow "Terms"   → presentedLegalDoc = .terms
│  │  └─ ProfileNavRow "Privacy" → presentedLegalDoc = .privacy
│  │
│  └─ Section (destructive)
│     └─ Log out button (preserved from existing ProfileView)
│
├─ .sheet(item: $presentedSheet)
│     .karma  → KarmaExplainerSheet
│     .bounce → BounceExplainerSheet
│
└─ .sheet(item: $presentedLegalDoc)
     → LegalDocumentSheet(url: $0.url)    (reuses existing wrapper)
```

### Why a single `presentedSheet` enum instead of two booleans

SwiftUI presents at most one sheet per view at a time. Two adjacent `.sheet(isPresented:)` modifiers cannot both be active; only the last one wins. The idiomatic solution is a single optional enum driving `.sheet(item:)`. `LegalDoc` already uses this pattern in `AuthView`, so we follow it.

Note: the legal sheet is a *separate* `.sheet(item:)` with its own state because `LegalDoc` is defined in the Auth feature and we want to reuse it without reshaping it. Two `.sheet(item:)` modifiers on the same view are safe as long as the identifiers don't overlap.

## Data flow

Three independent fetch scopes, each screen owns its own data (mirroring how `PathView` fetches pebbles).

### `ProfileView.load()`

Runs in `.task { }` on appear. Fetches both stats in parallel:

```swift
private func load() async {
    do {
        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary")
            .select("total_karma, pebbles_count")
            .single()
            .execute()
            .value

        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce")
            .select("bounce_level, active_days")
            .single()
            .execute()
            .value

        self.karma = try await karmaResult
        self.bounce = try await bounceResult
    } catch {
        logger.error("profile stats fetch failed: \(error.localizedDescription, privacy: .private)")
        self.loadError = error
    }
    self.isLoading = false
}
```

RLS filters the views to the authenticated user automatically, so no `eq("user_id", ...)` filter is needed in the Swift call.

### `CollectionsListView` / `SoulsListView` / `GlyphsListView`

Each holds its own `@State var items: [T]`, `isLoading`, `loadError`. Each calls `supabase.client.from("<table>").select("id, name").order("name").execute().value` in its own `.task { }`. Pattern is identical to `PathView.load()`. No shared state, no parent coordination.

### Explainer sheets

No fetch. Pure static SwiftUI `Form` with a headline and body `Text`.

## Loading and error behavior

**Top-level Profile screen:** graceful degradation. While `isLoading`, stat rows show `—` for the value. On fetch error, stat rows keep showing `—` and an `os.Logger.error` line is emitted. The nav rows (Collections, Souls, Glyphs, Terms, Privacy) do not depend on the stats fetch and are tappable immediately. A full-screen spinner would block interaction on parts of the screen that don't need the data.

**List screens (Collections / Souls / Glyphs):** three states.

1. **Loading:** native `ProgressView()` centered.
2. **Error:** `Text(loadError).foregroundStyle(.secondary)` + an `os.Logger.error` line.
3. **Empty (fetch succeeded, zero rows):** native `ContentUnavailableView("No collections yet", systemImage: "tray")` (and analogous copy for souls and glyphs). This is the iOS 17 native empty-state component — no custom design needed.

Every async catch path logs via `os.Logger`. Silent failures are bugs — this matches the discipline documented in both the web and iOS `CLAUDE.md`.

## Models

```swift
// KarmaSummary.swift
struct KarmaSummary: Decodable {
    let totalKarma: Int
    let pebblesCount: Int

    enum CodingKeys: String, CodingKey {
        case totalKarma = "total_karma"
        case pebblesCount = "pebbles_count"
    }
}

// BounceSummary.swift
struct BounceSummary: Decodable {
    let bounceLevel: Int
    let activeDays: Int

    enum CodingKeys: String, CodingKey {
        case bounceLevel = "bounce_level"
        case activeDays = "active_days"
    }
}

// Glyph.swift
struct Glyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String?   // glyphs.name is nullable in the schema
}
```

Snake-case ↔ camel-case conversion is explicit via `CodingKeys` (rather than a global `JSONDecoder.keyDecodingStrategy`), matching the convention used by existing models like `Pebble`.

## Explainer sheet copy (placeholder)

Hardcoded in Swift. Subject to rewrite — these exist to satisfy the acceptance criteria and give the user something informative on tap.

**Karma:**
> Karma reflects the energy you put into your path. Every pebble you record, every soul you tend, every glyph you draw — they all contribute.
>
> Your Karma grows as you show up for yourself.

**Bounce:**
> Bounce is a measure of your momentum over the last 28 days. It goes from level 0 (quiet) to level 7 (unstoppable).
>
> The more days you record a pebble, the higher your Bounce. Miss a stretch and it eases back down — that's fine. It's a rhythm, not a score.

Each sheet is a native `NavigationStack { Form { Section { Text(...) } } }` with a nav bar title and a "Done" button that dismisses via `@Environment(\.dismiss)`. Standard iOS swipe-down also dismisses.

## Verification plan

1. **Build:** `xcodegen generate` then build in Xcode (or via CI script). Swift compiler surfaces type errors and warnings.
2. **Manual smoke test in simulator**, walking every acceptance criterion from the issue:
   - Profile tab renders all seven items (Karma, Bounce, Collections, Souls, Glyphs, Terms, Privacy).
   - Tap Karma → explainer sheet; Done dismisses.
   - Tap Bounce → explainer sheet; Done dismisses.
   - Tap Collections / Souls / Glyphs → pushes a list screen; back button returns.
   - Tap Terms → Safari sheet to `https://www.pbbls.app/docs/terms`.
   - Tap Privacy → Safari sheet to `https://www.pbbls.app/docs/privacy`.
   - Log out still works.
3. **Empty state:** verify against a fresh account with no collections/souls/glyphs — `ContentUnavailableView` should render, not a broken-looking empty list.
4. **Error logging:** temporarily break a view name to confirm the `os.Logger.error` line fires and the screen degrades gracefully without crashing.

## Arkaik map update

The iOS Profile screen and its three list sub-screens will be added (or updated if already present) in `docs/arkaik/bundle.json` as part of this change, per the `arkaik` skill instructions. This is a surgical update — only the nodes and edges affected.

## Out of scope (explicit)

- Managing (create/edit/delete) collections, souls, or glyphs.
- Custom visual design, icons beyond SF Symbols, or any bespoke styling.
- Moving `Soul.swift` or `PebbleCollection.swift` out of `Features/Path/Models/`.
- Any ViewModel, Repository, or new abstraction layer.
- Unit or UI tests.
- Changes to `SupabaseService`, `AppEnvironment`, or app-level configuration.
