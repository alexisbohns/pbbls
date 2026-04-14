# iOS — Pebble Detail Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native SwiftUI sheet on iOS that opens when the user taps a pebble in the path, showing the full pebble with its emotion, domain(s), soul(s), and collection(s).

**Architecture:** A new `PebbleDetailSheet` view takes a `pebbleId: UUID`, fetches a rich `PebbleDetail` struct via a single embedded PostgREST query (joining `emotions`, `pebble_domains`, `pebble_souls`, `collection_pebbles`), and renders it as a read-only `Form` mirroring `CreatePebbleSheet`'s structure. `PathView` wraps each row in a `Button` that sets `selectedPebbleId`, presenting the sheet via `.sheet(item:)`.

**Tech Stack:** SwiftUI (iOS 17+), `@Environment(SupabaseService.self)`, supabase-swift 2.x, Swift Testing, xcodegen.

**Spec:** `docs/superpowers/specs/2026-04-14-ios-pebble-detail-sheet-design.md`. Read it before starting.

## Important context for the executor

- **Issue:** #253. Milestone: M19 · iOS ShameVP. Labels to apply to the PR: `feat`, `core`, `ios`.
- **Branch:** Work on `feat/253-pebble-detail-sheet`. Create it at the start (see Task 0). Never rely on an auto-generated branch name.
- **xcodegen handles file inclusion.** `apps/ios/project.yml` declares `sources: [path: Pebbles]`, which globs the whole folder. After adding any new `.swift` file under `Pebbles/`, run `npm run generate --workspace=@pbbls/ios` from the repo root to refresh the `.xcodeproj`. Never hand-edit the `.pbxproj`.
- **Build command:** `npm run build --workspace=@pbbls/ios` from the repo root. Runs `xcodegen generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build`.
- **Lint command:** `npm run lint --workspace=@pbbls/ios` (swiftlint).
- **Test command:** `npm run test --workspace=@pbbls/ios` (runs Swift Testing suites). One unit test for `PebbleDetail` decoding is included in this plan — the rest is build + manual smoke.
- **Trust RLS.** The `pebbles_select` policy already scopes reads to the authenticated user. Do not pass `user_id` in client filters.
- **Logging discipline.** Every `catch` block must log via `os.Logger` with `privacy: .private` on user-derived strings. Mirror the existing pattern in `apps/ios/Pebbles/Features/Path/PathView.swift`. No empty catches.
- **Supabase PostgREST embedding:** the detail fetch uses relation names directly in `.select(...)`. The supabase-swift client passes this through to PostgREST. Format:
  ```
  select("
      id, name, description, happened_at, intensity, positiveness, visibility,
      emotion:emotions(id, name, color),
      pebble_domains(domain:domains(id, name)),
      pebble_souls(soul:souls(id, name)),
      collection_pebbles(collection:collections(id, name))
  ")
  ```
  Junction tables (`pebble_domains`, `pebble_souls`, `collection_pebbles`) return as arrays of objects each holding the embedded target row. We flatten these in Swift via a custom `init(from:)`.

## File structure

All work is scoped to the existing Path feature, with one new utilities file under Services.

```
apps/ios/Pebbles/
├── Services/
│   └── UUID+Identifiable.swift      (NEW — one-line extension so UUID works with .sheet(item:))
└── Features/Path/
    ├── Models/
    │   ├── PebbleDetail.swift        (NEW — rich read model + *Ref types + custom decoding)
    │   └── (existing files unchanged)
    ├── PebbleDetailSheet.swift       (NEW — the sheet view)
    └── PathView.swift                (MODIFY — tap row → present sheet)

apps/ios/PebblesTests/
└── PebbleDetailDecodingTests.swift   (NEW — decoding + valence derivation)
```

Each file has one clear responsibility. `PebbleDetail` is the only file that knows about the PostgREST junction-row shape; the view only sees clean flat arrays.

---

### Task 0: Create the feature branch

**Files:** none

- [ ] **Step 1: From repo root, confirm clean working tree and current branch**

Run:
```bash
git status
git branch --show-current
```
Expected: clean tree, current branch is `main` (or whichever branch you brainstormed from — the spec commit should already be on it).

- [ ] **Step 2: Create and check out the feature branch**

Run:
```bash
git checkout -b feat/253-pebble-detail-sheet
```
Expected: `Switched to a new branch 'feat/253-pebble-detail-sheet'`

---

### Task 1: Add `UUID+Identifiable` extension

**Why:** SwiftUI's `.sheet(item:)` requires an `Identifiable` binding. `UUID` is not `Identifiable` out of the box. A one-line extension unblocks the pattern cleanly and is a common, harmless addition.

**Files:**
- Create: `apps/ios/Pebbles/Services/UUID+Identifiable.swift`

- [ ] **Step 1: Create the extension file**

Write `apps/ios/Pebbles/Services/UUID+Identifiable.swift`:

```swift
import Foundation

extension UUID: @retroactive Identifiable {
    public var id: UUID { self }
}
```

Note: `@retroactive` silences the Swift 6 warning about conforming a type from another module to a protocol from another module. Required on Xcode 15.3+.

- [ ] **Step 2: Regenerate the Xcode project**

Run from repo root:
```bash
npm run generate --workspace=@pbbls/ios
```
Expected: `Created project at ...Pebbles.xcodeproj`, no errors.

- [ ] **Step 3: Build to confirm the extension compiles**

Run from repo root:
```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Services/UUID+Identifiable.swift apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): add UUID Identifiable extension for sheet(item:)"
```

---

### Task 2: Add the `PebbleDetail` model

**Why:** A dedicated read model for the detail sheet keeps the list's lightweight `Pebble` unchanged and isolates PostgREST junction-row decoding in one place. The view will only ever see clean arrays (`domains: [DomainRef]`).

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`

- [ ] **Step 1: Write the file**

Write `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`:

```swift
import Foundation

// MARK: - Ref types (detail-view-local; intentionally not reusing the picker models)

struct EmotionRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
    let color: String
}

struct DomainRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
}

struct SoulRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
}

struct CollectionRef: Decodable, Hashable, Identifiable {
    let id: UUID
    let name: String
}

// MARK: - PebbleDetail

/// Read model for the detail sheet. Decodes a single pebble row with embedded
/// relations via PostgREST. Junction-table wrappers are flattened during
/// decoding so the view sees clean `domains`/`souls`/`collections` arrays.
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

    /// Derived from `intensity` + `positiveness`. DB remains source of truth.
    var valence: Valence {
        switch (positiveness, intensity) {
        case (-1, 1): return .lowlightSmall
        case (-1, 2): return .lowlightMedium
        case (-1, 3): return .lowlightLarge
        case (0, 1):  return .neutralSmall
        case (0, 2):  return .neutralMedium
        case (0, 3):  return .neutralLarge
        case (1, 1):  return .highlightSmall
        case (1, 2):  return .highlightMedium
        case (1, 3):  return .highlightLarge
        default:      return .neutralMedium // DB has CHECK constraints; this is defensive only
        }
    }

    // MARK: Decoding

    private enum CodingKeys: String, CodingKey {
        case id
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotion
        case pebbleDomains = "pebble_domains"
        case pebbleSouls = "pebble_souls"
        case collectionPebbles = "collection_pebbles"
    }

    private struct DomainWrapper: Decodable { let domain: DomainRef }
    private struct SoulWrapper: Decodable { let soul: SoulRef }
    private struct CollectionWrapper: Decodable { let collection: CollectionRef }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try c.decode(UUID.self, forKey: .id)
        self.name = try c.decode(String.self, forKey: .name)
        self.description = try c.decodeIfPresent(String.self, forKey: .description)
        self.happenedAt = try c.decode(Date.self, forKey: .happenedAt)
        self.intensity = try c.decode(Int.self, forKey: .intensity)
        self.positiveness = try c.decode(Int.self, forKey: .positiveness)
        self.visibility = try c.decode(Visibility.self, forKey: .visibility)
        self.emotion = try c.decode(EmotionRef.self, forKey: .emotion)

        let domainWrappers = try c.decodeIfPresent([DomainWrapper].self, forKey: .pebbleDomains) ?? []
        self.domains = domainWrappers.map(\.domain)

        let soulWrappers = try c.decodeIfPresent([SoulWrapper].self, forKey: .pebbleSouls) ?? []
        self.souls = soulWrappers.map(\.soul)

        let collectionWrappers = try c.decodeIfPresent([CollectionWrapper].self, forKey: .collectionPebbles) ?? []
        self.collections = collectionWrappers.map(\.collection)
    }
}
```

Note: `Visibility` is `Decodable` automatically because it's a `String`-backed `RawRepresentable` enum.

- [ ] **Step 2: Regenerate and build**

Run from repo root:
```bash
npm run generate --workspace=@pbbls/ios
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): add PebbleDetail read model with junction-row decoding"
```

---

### Task 3: Unit test `PebbleDetail` decoding and valence derivation

**Why:** `PebbleDetail.init(from:)` flattens junction rows and `valence` is a pure mapping. Both are easy to test and the only logic in this feature worth testing without UI infrastructure.

**Files:**
- Create: `apps/ios/PebblesTests/PebbleDetailDecodingTests.swift`

- [ ] **Step 1: Write the test file**

Write `apps/ios/PebblesTests/PebbleDetailDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDetail decoding")
struct PebbleDetailDecodingTests {

    private func makeDecoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            let string = try container.decode(String.self)
            if let date = formatter.date(from: string) { return date }
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: string) { return date }
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "Invalid ISO8601: \(string)"
            )
        }
        return decoder
    }

    private let fullJSON = """
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "name": "Shipped the thing",
      "description": "Finally.",
      "happened_at": "2026-04-14T15:42:00Z",
      "intensity": 3,
      "positiveness": 1,
      "visibility": "private",
      "emotion": {
        "id": "22222222-2222-2222-2222-222222222222",
        "name": "Joy",
        "color": "#FFD166"
      },
      "pebble_domains": [
        { "domain": { "id": "33333333-3333-3333-3333-333333333333", "name": "Work" } }
      ],
      "pebble_souls": [
        { "soul": { "id": "44444444-4444-4444-4444-444444444444", "name": "Alex" } }
      ],
      "collection_pebbles": [
        { "collection": { "id": "55555555-5555-5555-5555-555555555555", "name": "Wins" } }
      ]
    }
    """.data(using: .utf8)!

    @Test("decodes a full row with one of each relation")
    func decodesFullRow() throws {
        let detail = try makeDecoder().decode(PebbleDetail.self, from: fullJSON)

        #expect(detail.name == "Shipped the thing")
        #expect(detail.description == "Finally.")
        #expect(detail.intensity == 3)
        #expect(detail.positiveness == 1)
        #expect(detail.visibility == .private)
        #expect(detail.emotion.name == "Joy")
        #expect(detail.emotion.color == "#FFD166")
        #expect(detail.domains.map(\.name) == ["Work"])
        #expect(detail.souls.map(\.name) == ["Alex"])
        #expect(detail.collections.map(\.name) == ["Wins"])
    }

    @Test("valence is derived from intensity + positiveness")
    func derivesValence() throws {
        let detail = try makeDecoder().decode(PebbleDetail.self, from: fullJSON)
        #expect(detail.valence == .highlightLarge)
    }

    @Test("empty join arrays decode as empty")
    func decodesEmptyRelations() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Quiet moment",
          "description": null,
          "happened_at": "2026-04-14T08:00:00Z",
          "intensity": 1,
          "positiveness": 0,
          "visibility": "private",
          "emotion": {
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Calm",
            "color": "#88CCEE"
          },
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": []
        }
        """.data(using: .utf8)!

        let detail = try makeDecoder().decode(PebbleDetail.self, from: json)

        #expect(detail.description == nil)
        #expect(detail.domains.isEmpty)
        #expect(detail.souls.isEmpty)
        #expect(detail.collections.isEmpty)
        #expect(detail.valence == .neutralSmall)
    }

    @Test("multiple domains flatten cleanly")
    func decodesMultipleDomains() throws {
        let json = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "name": "Cross-domain moment",
          "description": null,
          "happened_at": "2026-04-14T08:00:00Z",
          "intensity": 2,
          "positiveness": -1,
          "visibility": "public",
          "emotion": {
            "id": "22222222-2222-2222-2222-222222222222",
            "name": "Sad",
            "color": "#6699CC"
          },
          "pebble_domains": [
            { "domain": { "id": "33333333-3333-3333-3333-333333333333", "name": "Work" } },
            { "domain": { "id": "44444444-4444-4444-4444-444444444444", "name": "Health" } }
          ],
          "pebble_souls": [],
          "collection_pebbles": []
        }
        """.data(using: .utf8)!

        let detail = try makeDecoder().decode(PebbleDetail.self, from: json)

        #expect(detail.domains.map(\.name) == ["Work", "Health"])
        #expect(detail.visibility == .public)
        #expect(detail.valence == .lowlightMedium)
    }
}
```

- [ ] **Step 2: Regenerate and run the tests**

Run from repo root:
```bash
npm run generate --workspace=@pbbls/ios
npm run test --workspace=@pbbls/ios
```
Expected: all four `PebbleDetail decoding` tests pass, plus the existing smoke test.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/PebblesTests/PebbleDetailDecodingTests.swift apps/ios/Pebbles.xcodeproj
git commit -m "test(ios): cover PebbleDetail decoding and valence derivation"
```

---

### Task 4: Build `PebbleDetailSheet`

**Why:** The actual read UI. Loads the pebble by id via a single embedded PostgREST query, renders three view states (loading / error / loaded), and uses `LabeledContent` inside a `Form` for the settings-style layout.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`

- [ ] **Step 1: Write the file**

Write `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`:

```swift
import SwiftUI
import os

struct PebbleDetailSheet: View {
    let pebbleId: UUID

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var detail: PebbleDetail?
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-detail")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(detail?.name ?? "Pebble")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
        }
        .task { await load() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") {
                    Task { await load() }
                }
            }
            .padding()
        } else if let detail {
            loadedForm(detail)
        }
    }

    @ViewBuilder
    private func loadedForm(_ detail: PebbleDetail) -> some View {
        Form {
            Section {
                LabeledContent("When", value: detail.happenedAt.formatted(date: .abbreviated, time: .shortened))
                if let description = detail.description, !description.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Description")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(description)
                    }
                }
            }

            Section("Mood") {
                LabeledContent("Emotion") {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(Color(hex: detail.emotion.color) ?? .secondary)
                            .frame(width: 10, height: 10)
                        Text(detail.emotion.name)
                    }
                }
                if !detail.domains.isEmpty {
                    LabeledContent("Domain", value: detail.domains.map(\.name).joined(separator: ", "))
                }
                LabeledContent("Valence", value: detail.valence.label)
            }

            if !detail.souls.isEmpty || !detail.collections.isEmpty {
                Section("Optional") {
                    if !detail.souls.isEmpty {
                        LabeledContent("Soul", value: detail.souls.map(\.name).joined(separator: ", "))
                    }
                    if !detail.collections.isEmpty {
                        LabeledContent("Collection", value: detail.collections.map(\.name).joined(separator: ", "))
                    }
                }
            }

            Section("Privacy") {
                LabeledContent("Privacy", value: detail.visibility.label)
            }
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let fetched: PebbleDetail = try await supabase.client
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
                .execute()
                .value
            self.detail = fetched
            self.isLoading = false
        } catch {
            logger.error("pebble detail fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }
}

// Small helper so we can render the emotion color string ("#RRGGBB") as a SwiftUI Color.
// Returns nil on malformed input; the view falls back to `.secondary`.
private extension Color {
    init?(hex: String) {
        var s = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.hasPrefix("#") { s.removeFirst() }
        guard s.count == 6, let value = UInt32(s, radix: 16) else { return nil }
        let r = Double((value >> 16) & 0xFF) / 255.0
        let g = Double((value >> 8) & 0xFF) / 255.0
        let b = Double(value & 0xFF) / 255.0
        self = Color(red: r, green: g, blue: b)
    }
}

#Preview {
    PebbleDetailSheet(pebbleId: UUID())
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Regenerate and build**

Run from repo root:
```bash
npm run generate --workspace=@pbbls/ios
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): add PebbleDetailSheet with embedded PostgREST fetch"
```

---

### Task 5: Wire `PathView` to present the sheet on row tap

**Why:** The connective tissue. A tap sets `selectedPebbleId`, SwiftUI's `.sheet(item:)` observes the optional binding and presents the detail sheet, and `.buttonStyle(.plain)` preserves the existing row appearance.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Replace the file contents**

Rewrite `apps/ios/Pebbles/Features/Path/PathView.swift` to:

```swift
import SwiftUI
import os

struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Path")
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet { newPebble in
                handleCreated(newPebble)
            }
        }
        .sheet(item: $selectedPebbleId) { id in
            PebbleDetailSheet(pebbleId: id)
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            Text(loadError).foregroundStyle(.secondary)
        } else {
            List {
                Section {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Label("Record a pebble", systemImage: "plus.circle.fill")
                            .font(.headline)
                    }
                }

                Section("Path") {
                    ForEach(pebbles) { pebble in
                        Button {
                            selectedPebbleId = pebble.id
                        } label: {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(pebble.name).font(.body)
                                Text(pebble.happenedAt, style: .date)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func handleCreated(_ pebble: Pebble) {
        pebbles.append(pebble)
        pebbles.sort { $0.happenedAt > $1.happenedAt }
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

#Preview {
    PathView()
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Build and lint**

Run from repo root:
```bash
npm run build --workspace=@pbbls/ios
npm run lint --workspace=@pbbls/ios
```
Expected: both succeed.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ios): present PebbleDetailSheet on path row tap (#253)"
```

---

### Task 6: Manual smoke test in the simulator

**Why:** The unit tests cover decoding, not user flow. Confirm the whole loop end-to-end before opening the PR.

**Files:** none

- [ ] **Step 1: Run the app on a simulator**

Open `apps/ios/Pebbles.xcodeproj` in Xcode, pick any iPhone simulator, press ⌘R.

- [ ] **Step 2: Verify the happy path**

- Sign in with your dev account.
- On the Path screen, tap an existing pebble row.
- Expected: a sheet slides up showing a `ProgressView` briefly, then a `Form` with the pebble's name in the navbar, the "When" row, description (if any), emotion (name + colored dot), domain(s), valence label, optional soul/collection rows, and privacy.
- Tap "Done". Expected: sheet dismisses, list state is unchanged.

- [ ] **Step 3: Verify a pebble with no description and no optional relations**

- Create a new pebble via the existing create flow with only the required fields filled (name, emotion, domain, valence) — no description, no soul, no collection.
- Tap the new row.
- Expected: Description row is absent. "Optional" section is absent entirely. No crashes, no empty placeholder rows.

- [ ] **Step 4: Verify the error path**

- In Airplane Mode (Settings → Airplane Mode on in the simulator, or Features → Network Link Conditioner → 100% Loss), tap a pebble row.
- Expected: sheet shows the error text "Couldn't load this pebble." with a Retry button. Turn network back on and tap Retry — it loads.
- Check the Xcode console: a log line from category `pebble-detail` should appear for the failed fetch. No silent catches.

- [ ] **Step 5: Verify no regression in Create**

- Turn networking back on. Tap "Record a pebble", fill the form, save.
- Expected: the sheet closes and the new pebble appears at the top of the path, same as before.

---

### Task 7: Open the pull request

**Why:** Ship it.

**Files:** none

- [ ] **Step 1: Push the branch**

Run:
```bash
git push -u origin feat/253-pebble-detail-sheet
```

- [ ] **Step 2: Create the PR**

Run:
```bash
gh pr create --title "feat(ios): pebble detail sheet (#253)" --body "$(cat <<'EOF'
Resolves #253.

## Summary
- Adds `PebbleDetailSheet` — a read-only sheet that opens when a user taps a pebble in the path, showing all stored fields plus the embedded emotion, domain(s), soul(s), and collection(s).
- Adds `PebbleDetail` read model with custom decoding that flattens PostgREST junction rows into clean arrays.
- Wires `PathView` rows as `Button`s that set `selectedPebbleId`, presenting the sheet via `.sheet(item:)`.
- Adds a `UUID: Identifiable` extension so `.sheet(item:)` accepts a `UUID?` directly.
- Adds unit tests for decoding and valence derivation via Swift Testing.

## Key files
- \`apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift\` (new)
- \`apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift\` (new)
- \`apps/ios/Pebbles/Services/UUID+Identifiable.swift\` (new)
- \`apps/ios/Pebbles/Features/Path/PathView.swift\` (modified)
- \`apps/ios/PebblesTests/PebbleDetailDecodingTests.swift\` (new)

## Implementation notes
- Fetch-on-tap pattern: list query stays lightweight; the sheet runs its own single embedded PostgREST query by id.
- `valence` is a computed property derived from `intensity` + `positiveness` — DB remains source of truth.
- Junction-row decoding is isolated in \`PebbleDetail.init(from:)\`; the view layer only sees flat \`domains\`/\`souls\`/\`collections\` arrays.

## Test plan
- [x] \`npm run test --workspace=@pbbls/ios\` (unit tests pass)
- [x] \`npm run build --workspace=@pbbls/ios\` (build succeeds)
- [x] \`npm run lint --workspace=@pbbls/ios\` (lint clean)
- [x] Simulator: tap row → sheet opens with full details
- [x] Simulator: pebble with no description / no optionals renders correctly (section omitted)
- [x] Simulator: offline → error + Retry works, log line appears
- [x] Simulator: create flow unaffected
EOF
)"
```

- [ ] **Step 3: Apply labels and milestone**

The issue has labels `feat`, `core`, `ios` and milestone `M19 · iOS ShameVP`. Per the project's PR workflow, the PR inherits the same labels and milestone. Confirm with the user before applying if you're uncertain; otherwise:

```bash
gh pr edit --add-label feat --add-label core --add-label ios --milestone "M19 · iOS ShameVP"
```

- [ ] **Step 4: Report the PR URL back to the user.**
