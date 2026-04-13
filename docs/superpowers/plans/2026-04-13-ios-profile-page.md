# iOS Profile Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the stub `ProfileView` into a functional Profile tab that shows Karma/Bounce stats, lets the user drill into read-only lists of Collections/Souls/Glyphs, and opens Terms/Privacy in an in-app Safari sheet — all using native SwiftUI with zero custom design.

**Architecture:** Each screen owns its own fetch via `@State` + `.task { }`, calling `supabase.client` directly (mirroring the existing `PathView` pattern). No ViewModel, no Repository layer. Sheets are driven by an optional enum + `.sheet(item:)`. The three list sub-screens are pushed via `NavigationLink` from the main Profile list.

**Tech Stack:** SwiftUI, iOS 17+, `@Observable` environment (`SupabaseService`), Swift Package `supabase-swift` (already wired), `os.Logger` for error logs, XcodeGen (`project.yml` → `xcodegen generate`) as the project source of truth.

**Spec:** `docs/superpowers/specs/2026-04-13-ios-profile-page-design.md`
**Issue:** #250
**Branch:** `feat/250-ios-profile-page` (already created)

---

## Primer for a junior iOS dev (read once before Task 1)

You're going to see a lot of new things. Here's the short version so the steps make sense:

- **Each `.swift` file is picked up automatically.** You never "add file to project" in Xcode here. The project is generated from `apps/ios/project.yml` by running `xcodegen generate`. After creating a new file, running `npm run generate --workspace=@pbbls/ios` regenerates `Pebbles.xcodeproj` so the new file is compiled.
- **`@State`** is SwiftUI's way of saying "this value belongs to this view, and when it changes the view re-renders." If the view disappears and comes back, the state resets.
- **`.task { ... }`** is a modifier you attach to a view. It runs an async closure when the view appears and cancels it automatically when the view goes away. It's what you want for "fetch data on appear" — never use `Task { }` inside `.onAppear` unless you have a reason.
- **`NavigationStack` + `NavigationLink`** gives you push navigation (slide-in-from-right with a back button). It's what Settings.app uses.
- **`.sheet(item: $binding) { item in ... }`** presents a modal sheet whenever `binding` becomes non-nil, passing the unwrapped value into the closure. The `item` must be `Identifiable`.
- **`@Environment(SupabaseService.self)`** reads a shared service from the SwiftUI environment. It was put there by `PebblesApp.swift`. You just read it — never construct one.
- **`os.Logger`** is Apple's structured logging. You never use `print()`. Errors go through `logger.error(...)`.
- **`Form` and `List` with `insetGrouped` style** give you the native iOS Settings look for free — rounded sections, gray background, system fonts.
- **`supabase.client.from(...).select(...).execute().value`** is how supabase-swift does a query. `.value` decodes the response into whatever Swift type you annotated on the `let`. RLS on the backend automatically restricts rows to the current user, so we don't pass `user_id`.
- **`CodingKeys`** is how a `Decodable` struct maps snake_case JSON fields (what Supabase returns) to camelCase Swift properties.

Commits use **conventional commits** (`feat(ios): ...`, `chore(ios): ...`) — lowercase, no period. Commit after each task. That way if a step goes wrong, the previous green state is one `git reset` away.

**Build check:** at the end of every task that touches Swift code, you will run `npm run build --workspace=@pbbls/ios` from the repo root. This runs `xcodegen generate` then `xcodebuild ... build`. A clean build is the spec for "task done" because we don't have unit tests for this feature.

---

## File map

New files (all under `apps/ios/Pebbles/Features/Profile/`):

| File | Responsibility | Task |
|---|---|---|
| `Models/KarmaSummary.swift` | Decodable struct for `v_karma_summary` | 1 |
| `Models/BounceSummary.swift` | Decodable struct for `v_bounce` | 1 |
| `Models/Glyph.swift` | Decodable struct for `glyphs` rows | 1 |
| `Components/ProfileStatRow.swift` | Reusable tappable stat row (label + value + icon) | 2 |
| `Components/ProfileNavRow.swift` | Reusable tappable row with chevron, for sheet triggers | 3 |
| `Sheets/KarmaExplainerSheet.swift` | Static explainer sheet for Karma | 4 |
| `Sheets/BounceExplainerSheet.swift` | Static explainer sheet for Bounce | 4 |
| `Lists/CollectionsListView.swift` | Read-only list of user's collections | 5 |
| `Lists/SoulsListView.swift` | Read-only list of user's souls | 6 |
| `Lists/GlyphsListView.swift` | Read-only list of user's glyphs | 7 |

Modified files:

| File | What changes | Task |
|---|---|---|
| `apps/ios/Pebbles/Features/Profile/ProfileView.swift` | Full rewrite — stats, lists, legal, log out | 8 |
| `docs/arkaik/bundle.json` | Bump statuses for list nodes now shipping on iOS | 9 |

Existing files **imported but not modified**:

- `apps/ios/Pebbles/Features/Path/Models/Soul.swift` — already `Identifiable, Decodable, Hashable` with `id`, `name`.
- `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift` — same shape.
- `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift` — already provides the `LegalDoc` enum and the Safari wrapper.

---

## Task 1: Add Karma / Bounce / Glyph model structs

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Models/KarmaSummary.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Models/BounceSummary.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Models/Glyph.swift`

Pure data structs. No UI. These compile independently so we lock them in first.

- [ ] **Step 1: Create `KarmaSummary.swift`**

Write this file verbatim:

```swift
import Foundation

/// Mirrors the `v_karma_summary` view (one row per user, filtered by RLS).
/// `total_karma` is the sum of `karma_events.delta` for the current user;
/// `pebbles_count` is the user's total pebble count.
struct KarmaSummary: Decodable {
    let totalKarma: Int
    let pebblesCount: Int

    enum CodingKeys: String, CodingKey {
        case totalKarma = "total_karma"
        case pebblesCount = "pebbles_count"
    }
}
```

- [ ] **Step 2: Create `BounceSummary.swift`**

```swift
import Foundation

/// Mirrors the `v_bounce` view. `bounce_level` is a 0–7 integer computed
/// from distinct active days over the last 28 days; `active_days` is the
/// raw count used to derive that level.
struct BounceSummary: Decodable {
    let bounceLevel: Int
    let activeDays: Int

    enum CodingKeys: String, CodingKey {
        case bounceLevel = "bounce_level"
        case activeDays = "active_days"
    }
}
```

- [ ] **Step 3: Create `Glyph.swift`**

```swift
import Foundation

/// Minimal read shape for a `glyphs` row. `name` is nullable in the schema,
/// so we model it as optional. Full glyph editing lives in a future feature.
struct Glyph: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String?
}
```

- [ ] **Step 4: Regenerate Xcode project and build**

Run from the repo root:

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `xcodegen` regenerates `Pebbles.xcodeproj`, then `xcodebuild` prints `** BUILD SUCCEEDED **`. Any syntax error in the structs will surface here.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Models/
git commit -m "feat(ios): add profile stat and glyph models"
```

---

## Task 2: Add `ProfileStatRow` component

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileStatRow.swift`

A single reusable row used for both Karma and Bounce. It shows an SF Symbol icon, a title, and a value on the trailing edge. Tappable as a plain `Button` so it fits inside a `List` row and inherits row-tap behavior.

- [ ] **Step 1: Create the file**

```swift
import SwiftUI

/// One row in the Profile screen's Stats section. Displays a label and an
/// optional integer value; taps trigger the provided action (used to open an
/// explainer sheet). Shows an em-dash when `value` is nil so the row keeps
/// its layout while stats are loading or have failed to load.
struct ProfileStatRow: View {
    let title: String
    let systemImage: String
    let value: Int?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Label(title, systemImage: systemImage)
                    .foregroundStyle(.primary)
                Spacer()
                Text(valueText)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var valueText: String {
        if let value { return String(value) }
        return "—"
    }
}

#Preview {
    List {
        Section("Stats") {
            ProfileStatRow(title: "Karma", systemImage: "sparkles", value: 128) {}
            ProfileStatRow(title: "Bounce", systemImage: "arrow.up.right", value: nil) {}
        }
    }
}
```

**Why `.buttonStyle(.plain)`**: without it, every label inside a `Button` turns blue (the default tinted button style). `.plain` lets us keep the native row look.

**Why `.contentShape(Rectangle())`**: expands the tap target to the full row width, so the whole row is tappable — not just the text.

- [ ] **Step 2: Regenerate and build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`. SwiftUI previews don't break the build even if the simulator isn't running.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileStatRow.swift
git commit -m "feat(ios): add ProfileStatRow component"
```

---

## Task 3: Add `ProfileNavRow` component

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Components/ProfileNavRow.swift`

A tappable row with a label and a trailing chevron (the little `>` indicator you see in Settings.app on rows that open a sheet but aren't a `NavigationLink`). We use this for **Terms** and **Privacy**, which open a Safari sheet rather than push a new screen.

We don't use this for Collections / Souls / Glyphs — those are `NavigationLink`s, which draw their own chevron automatically.

- [ ] **Step 1: Create the file**

```swift
import SwiftUI

/// A labeled row with a trailing chevron, for triggers that open a sheet
/// rather than push a screen. Collections / Souls / Glyphs don't use this —
/// they use `NavigationLink`, which provides its own chevron.
struct ProfileNavRow: View {
    let title: String
    let systemImage: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Label(title, systemImage: systemImage)
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    List {
        Section("Legal") {
            ProfileNavRow(title: "Terms", systemImage: "doc.text") {}
            ProfileNavRow(title: "Privacy", systemImage: "lock.shield") {}
        }
    }
}
```

- [ ] **Step 2: Regenerate and build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Components/ProfileNavRow.swift
git commit -m "feat(ios): add ProfileNavRow component"
```

---

## Task 4: Add Karma and Bounce explainer sheets

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift`
- Create: `apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift`

Pure static content sheets. Each wraps a `Form` in a `NavigationStack` so it gets a native title bar and a Done button. Dismissal uses `@Environment(\.dismiss)` — the standard iOS way to close a sheet from inside the presented view.

- [ ] **Step 1: Create `KarmaExplainerSheet.swift`**

```swift
import SwiftUI

struct KarmaExplainerSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("""
                    Karma reflects the energy you put into your path. \
                    Every pebble you record, every soul you tend, every glyph \
                    you draw — they all contribute.

                    Your Karma grows as you show up for yourself.
                    """)
                }
            }
            .navigationTitle("Karma")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    KarmaExplainerSheet()
}
```

- [ ] **Step 2: Create `BounceExplainerSheet.swift`**

```swift
import SwiftUI

struct BounceExplainerSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("""
                    Bounce is a measure of your momentum over the last 28 \
                    days. It goes from level 0 (quiet) to level 7 (unstoppable).

                    The more days you record a pebble, the higher your Bounce. \
                    Miss a stretch and it eases back down — that's fine. It's \
                    a rhythm, not a score.
                    """)
                }
            }
            .navigationTitle("Bounce")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#Preview {
    BounceExplainerSheet()
}
```

- [ ] **Step 3: Regenerate and build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Sheets/
git commit -m "feat(ios): add karma and bounce explainer sheets"
```

---

## Task 5: Add `CollectionsListView`

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`

A self-contained screen: owns its fetch, renders one of three states (loading / error / data-or-empty). Fetches `id, name` from the `collections` table. RLS restricts rows to the current user, so no `user_id` filter.

The empty state uses `ContentUnavailableView`, which is new in iOS 17 and gives you the native "Nothing here yet" look (icon + headline + optional description) that Settings.app and Mail use. Zero custom design.

- [ ] **Step 1: Create the file**

```swift
import SwiftUI
import os

struct CollectionsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [PebbleCollection] = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    var body: some View {
        content
            .navigationTitle("Collections")
            .navigationBarTitleDisplayMode(.inline)
            .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load collections",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No collections yet",
                systemImage: "tray",
                description: Text("Your collections will appear here.")
            )
        } else {
            List(items) { collection in
                Text(collection.name)
            }
        }
    }

    private func load() async {
        do {
            let result: [PebbleCollection] = try await supabase.client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("collections fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        CollectionsListView()
            .environment(SupabaseService())
    }
}
```

**Note** that `PebbleCollection` is the existing struct in `Features/Path/Models/PebbleCollection.swift`. Swift's `import` at the top of the file is for *modules* (like `SwiftUI`), not other files in the same target — everything inside the `Pebbles` target is automatically in scope. So we don't need any new import.

- [ ] **Step 2: Regenerate and build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift
git commit -m "feat(ios): add read-only collections list view"
```

---

## Task 6: Add `SoulsListView`

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

Same shape as `CollectionsListView`, but targets the `souls` table and uses the existing `Soul` type from `Features/Path/Models/Soul.swift`.

- [ ] **Step 1: Create the file**

```swift
import SwiftUI
import os

struct SoulsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [Soul] = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    var body: some View {
        content
            .navigationTitle("Souls")
            .navigationBarTitleDisplayMode(.inline)
            .task { await load() }
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
        } else if items.isEmpty {
            ContentUnavailableView(
                "No souls yet",
                systemImage: "person.2",
                description: Text("People and beings you tag on your pebbles will appear here.")
            )
        } else {
            List(items) { soul in
                Text(soul.name)
            }
        }
    }

    private func load() async {
        do {
            let result: [Soul] = try await supabase.client
                .from("souls")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        SoulsListView()
            .environment(SupabaseService())
    }
}
```

- [ ] **Step 2: Regenerate and build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift
git commit -m "feat(ios): add read-only souls list view"
```

---

## Task 7: Add `GlyphsListView`

**Files:**
- Create: `apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift`

Same shape again. Uses the new `Glyph` struct from Task 1. Because `Glyph.name` is optional (nullable in the DB), we show a fallback label for unnamed glyphs rather than an empty row.

- [ ] **Step 1: Create the file**

```swift
import SwiftUI
import os

struct GlyphsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var items: [Glyph] = []
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.glyphs")

    var body: some View {
        content
            .navigationTitle("Glyphs")
            .navigationBarTitleDisplayMode(.inline)
            .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load glyphs",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else if items.isEmpty {
            ContentUnavailableView(
                "No glyphs yet",
                systemImage: "scribble",
                description: Text("The glyphs you carve will appear here.")
            )
        } else {
            List(items) { glyph in
                Text(glyph.name ?? "Untitled glyph")
                    .foregroundStyle(glyph.name == nil ? .secondary : .primary)
            }
        }
    }

    private func load() async {
        do {
            let result: [Glyph] = try await supabase.client
                .from("glyphs")
                .select("id, name")
                .order("name")
                .execute()
                .value
            self.items = result
        } catch {
            logger.error("glyphs fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}

#Preview {
    NavigationStack {
        GlyphsListView()
            .environment(SupabaseService())
    }
}
```

- [ ] **Step 2: Regenerate and build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift
git commit -m "feat(ios): add read-only glyphs list view"
```

---

## Task 8: Rewrite `ProfileView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift` (full rewrite)

This is the keystone task — everything from Tasks 1–7 gets wired together here. The view:

1. Fetches Karma and Bounce in parallel on appear.
2. Renders four sections: Stats, Lists, Legal, and the sign-out button.
3. Drives two `.sheet(item:)` modifiers — one for `ProfileSheet` (karma/bounce explainers) and one for `LegalDoc` (terms/privacy).
4. Preserves the existing Log out button behavior.

**A short note on the two-sheet pattern**: SwiftUI is fine with multiple `.sheet(item:)` modifiers attached to the same view, as long as each is driven by a different optional state property. Only one sheet can be visible at a time — if one is already open, setting the other's state has no effect until the first is dismissed. That's acceptable here: nothing in the UI can trigger both at once.

- [ ] **Step 1: Rewrite the file**

Replace the full contents of `apps/ios/Pebbles/Features/Profile/ProfileView.swift` with:

```swift
import SwiftUI
import os

/// Discriminator for which explainer sheet is currently presented.
/// Driving sheets by an optional enum is the idiomatic SwiftUI pattern —
/// it guarantees a single sheet presentation per state transition.
private enum ProfileSheet: String, Identifiable {
    case karma
    case bounce
    var id: String { rawValue }
}

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase

    @State private var karma: KarmaSummary?
    @State private var bounce: BounceSummary?
    @State private var isLoading = true
    @State private var presentedSheet: ProfileSheet?
    @State private var presentedLegalDoc: LegalDoc?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile")

    var body: some View {
        NavigationStack {
            List {
                Section("Stats") {
                    ProfileStatRow(
                        title: "Karma",
                        systemImage: "sparkles",
                        value: karma?.totalKarma
                    ) {
                        presentedSheet = .karma
                    }
                    ProfileStatRow(
                        title: "Bounce",
                        systemImage: "arrow.up.right",
                        value: bounce?.bounceLevel
                    ) {
                        presentedSheet = .bounce
                    }
                }

                Section("Lists") {
                    NavigationLink {
                        CollectionsListView()
                    } label: {
                        Label("Collections", systemImage: "square.stack.3d.up")
                    }
                    NavigationLink {
                        SoulsListView()
                    } label: {
                        Label("Souls", systemImage: "person.2")
                    }
                    NavigationLink {
                        GlyphsListView()
                    } label: {
                        Label("Glyphs", systemImage: "scribble")
                    }
                }

                Section("Legal") {
                    ProfileNavRow(title: "Terms", systemImage: "doc.text") {
                        presentedLegalDoc = .terms
                    }
                    ProfileNavRow(title: "Privacy", systemImage: "lock.shield") {
                        presentedLegalDoc = .privacy
                    }
                }

                Section {
                    Button(role: .destructive) {
                        Task { await supabase.signOut() }
                    } label: {
                        Text("Log out")
                            .frame(maxWidth: .infinity)
                    }
                }
            }
            .navigationTitle("Profile")
            .task { await loadStats() }
            .sheet(item: $presentedSheet) { sheet in
                switch sheet {
                case .karma:  KarmaExplainerSheet()
                case .bounce: BounceExplainerSheet()
                }
            }
            .sheet(item: $presentedLegalDoc) { doc in
                LegalDocumentSheet(url: doc.url)
                    .ignoresSafeArea()
            }
        }
    }

    private func loadStats() async {
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
            // Graceful degradation: karma/bounce stay nil, rows show "—".
        }
        self.isLoading = false
    }
}

#Preview {
    ProfileView()
        .environment(SupabaseService())
}
```

**About `async let`**: this kicks off both queries immediately in parallel. Each `try await` then waits for the respective result. If we had used sequential `let karma = try await ...` then `let bounce = try await ...`, the second query would only start after the first completed — for no reason, since they're independent.

**About graceful degradation**: we deliberately do NOT set a `loadError` string. If either fetch fails, `karma` or `bounce` stays nil, which `ProfileStatRow` renders as `—`. The user can still navigate, tap Terms/Privacy, and log out. The failure is logged but doesn't block the screen.

**About `.ignoresSafeArea()` on `LegalDocumentSheet`**: `SFSafariViewController` draws its own chrome; we want it edge-to-edge.

- [ ] **Step 2: Regenerate and build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `** BUILD SUCCEEDED **`. This is the critical one — if any of the previous files had a typo in a property or init call, it surfaces here because this file uses them all.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/ProfileView.swift
git commit -m "feat(ios): wire profile page with stats, lists, and legal sheets"
```

---

## Task 9: Update the Arkaik product map

**Files:**
- Modify: `docs/arkaik/bundle.json`

The existing nodes already exist; our job is to bump their status to reflect that they now ship on iOS. Surgical edits only.

Relevant existing nodes (confirmed during planning):

| Node ID | Current status | Action |
|---|---|---|
| `V-profile` | `development` | No change — already correct. |
| `V-collections-list` | `idea` | Bump to `development`. |
| `V-souls-list` | `live` | No change — already `live` (higher than `development`). |
| `V-glyphs-list` | `idea` | Bump to `development`. |
| `V-docs-terms` | `development` | No change. |
| `V-docs-privacy` | `development` | No change. |

All already include `"ios"` in their `platforms` array. No new nodes or edges are introduced — the explainer sheets are UI affordances on `V-profile`, not separate views in the product graph.

- [ ] **Step 1: Check the arkaik skill for the surgical-update pattern and validation command**

```bash
ls .claude/skills/arkaik/ 2>/dev/null || find . -type d -name arkaik
```

Read the skill's README or instructions file to find the validation script. We want to follow the skill's documented update pattern rather than hand-edit the JSON blindly.

- [ ] **Step 2: Update `V-collections-list` status**

Locate the node in `docs/arkaik/bundle.json`:

```bash
grep -n '"V-collections-list"' docs/arkaik/bundle.json
```

Change its `"status": "idea"` to `"status": "development"`. Use the Edit tool with enough surrounding context to uniquely identify the node (the `id` field is sufficient).

- [ ] **Step 3: Update `V-glyphs-list` status**

Same edit for `V-glyphs-list`: `"status": "idea"` → `"status": "development"`.

- [ ] **Step 4: Run the arkaik validation script**

Per `CLAUDE.md`: "includes a validation script to run before saving". Run whatever the arkaik skill documents (likely something like `node .claude/skills/arkaik/validate.js` or a Python equivalent). The validation must pass before committing.

Expected: script reports OK / 0 errors.

- [ ] **Step 5: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "chore(core): promote collections and glyphs list nodes to development"
```

---

## Task 10: Manual smoke test in the simulator

**Files:** none modified.

This is the "does it actually work" gate. No unit tests exist for this feature (by design — see the spec's non-goals), so manual verification in the simulator is how we confirm every acceptance criterion from issue #250.

- [ ] **Step 1: Open the project in Xcode**

```bash
open apps/ios/Pebbles.xcodeproj
```

Pick the `Pebbles` scheme and an iPhone simulator (iPhone 17 is in the test script). Press Cmd+R to build and run.

- [ ] **Step 2: Sign in (or sign up) as a test user**

The app lands on the auth screen on first launch. Use any dev credentials the repo's seed data supports.

- [ ] **Step 3: Navigate to the Profile tab and verify each acceptance criterion**

Walk through every AC from the issue, in order. Check each off mentally:

- [ ] Profile tab shows **Karma** (value renders as a number or `—`)
- [ ] Profile tab shows **Bounce** (value renders as a number or `—`)
- [ ] Profile tab shows **Collections** item
- [ ] Profile tab shows **Souls** item
- [ ] Profile tab shows **Glyphs** item
- [ ] Profile tab shows **Terms** item
- [ ] Profile tab shows **Privacy** item
- [ ] Tapping **Karma** opens the explainer sheet; "Done" dismisses
- [ ] Tapping **Bounce** opens the explainer sheet; "Done" dismisses
- [ ] Tapping **Collections** pushes `CollectionsListView`; back button returns
- [ ] Tapping **Souls** pushes `SoulsListView`; back button returns
- [ ] Tapping **Glyphs** pushes `GlyphsListView`; back button returns
- [ ] Tapping **Terms** opens `https://www.pbbls.app/docs/terms` in the in-app Safari
- [ ] Tapping **Privacy** opens `https://www.pbbls.app/docs/privacy` in the in-app Safari
- [ ] **Log out** button still works and returns to the auth screen

- [ ] **Step 4: Verify the empty-state path**

If the test account has no collections / souls / glyphs, each list screen should show a `ContentUnavailableView` — an icon plus "No X yet" headline plus description. Not a blank screen, not a broken-looking empty `List`.

If the test account *does* have data in all three, either (a) create a fresh account, or (b) temporarily change one of the fetch queries to target a non-existent column (e.g., `select("id, nope")`) to force the error path and observe the error-state UI, then revert.

- [ ] **Step 5: Verify error logging**

With Xcode's debug console open, observe that normal operation emits no `logger.error(...)` lines from category `profile`, `profile.collections`, `profile.souls`, or `profile.glyphs`. If anything unexpected logs, investigate before proceeding.

- [ ] **Step 6: No commit**

Nothing changed; no commit. Proceed to the PR task.

---

## Task 11: Open the pull request

**Files:** none — this is a git + gh operation.

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/250-ios-profile-page
```

- [ ] **Step 2: Confirm build and lint pass locally (PR checklist item 5)**

```bash
npm run build --workspace=@pbbls/ios
npm run lint --workspace=@pbbls/ios
```

Expected: both succeed. If lint is not configured yet for iOS (swiftlint may not be installed), note that in the PR body rather than skipping.

- [ ] **Step 3: Inherit labels and milestone from issue #250**

Per `CLAUDE.md` PR checklist: "propose inheriting the same labels and milestone from that issue and ask the user to confirm." Issue #250 has labels `api`, `core`, `feat`, `ios` and milestone `M19 · iOS ShameVP`.

**Before creating the PR, confirm with the user:** "I plan to apply labels `feat`, `api`, `core`, `ios` and milestone `M19 · iOS ShameVP` inherited from #250. OK?"

- [ ] **Step 4: Create the PR**

```bash
gh pr create \
  --title "feat(ios): profile page with stats, lists, and legal sheets" \
  --body "$(cat <<'EOF'
Resolves #250

## Summary
- Rewrites `ProfileView` to show Karma / Bounce stats, nav links to Collections / Souls / Glyphs lists, Terms / Privacy legal sheets, and the existing Log out button
- Adds read-only `CollectionsListView`, `SoulsListView`, `GlyphsListView` with native loading / error / empty states
- Adds static `KarmaExplainerSheet` and `BounceExplainerSheet` presented from the stat rows
- Introduces `KarmaSummary`, `BounceSummary`, `Glyph` Decodable models mapping `v_karma_summary`, `v_bounce`, and `glyphs` respectively
- Promotes `V-collections-list` and `V-glyphs-list` from `idea` to `development` in `docs/arkaik/bundle.json`

All new files live under `apps/ios/Pebbles/Features/Profile/`. Follows the existing `PathView` pattern: views own their `@State`, call `supabase.client` directly, log errors via `os.Logger`. No new architecture layer.

## Key files changed
- `apps/ios/Pebbles/Features/Profile/ProfileView.swift` (rewrite)
- `apps/ios/Pebbles/Features/Profile/Models/{KarmaSummary,BounceSummary,Glyph}.swift` (new)
- `apps/ios/Pebbles/Features/Profile/Components/{ProfileStatRow,ProfileNavRow}.swift` (new)
- `apps/ios/Pebbles/Features/Profile/Sheets/{KarmaExplainerSheet,BounceExplainerSheet}.swift` (new)
- `apps/ios/Pebbles/Features/Profile/Lists/{CollectionsListView,SoulsListView,GlyphsListView}.swift` (new)
- `docs/arkaik/bundle.json` (surgical status bumps)

## Test plan
- [ ] \`npm run build --workspace=@pbbls/ios\` passes
- [ ] Profile tab shows Karma, Bounce, Collections, Souls, Glyphs, Terms, Privacy
- [ ] Tapping Karma / Bounce opens explainer sheets; Done dismisses
- [ ] Tapping Collections / Souls / Glyphs pushes read-only list; back button returns
- [ ] Empty-state \`ContentUnavailableView\` renders when a list has zero rows
- [ ] Tapping Terms opens pbbls.app/docs/terms in-app Safari
- [ ] Tapping Privacy opens pbbls.app/docs/privacy in-app Safari
- [ ] Log out still works

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" \
  --label feat --label api --label core --label ios \
  --milestone "M19 · iOS ShameVP"
```

- [ ] **Step 5: Return the PR URL to the user**

Paste the URL printed by `gh pr create` so the user can open it.

---

## Done criteria

This plan is complete when:

1. All 11 tasks above are checked off.
2. `npm run build --workspace=@pbbls/ios` exits clean.
3. Every acceptance criterion from issue #250 has been verified in the simulator.
4. The PR is open against `main` with `Resolves #250`, inherited labels, and the milestone applied.
