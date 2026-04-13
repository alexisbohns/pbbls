# iOS — Create a Pebble Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native SwiftUI form on iOS that lets an authenticated user record a new pebble and see it appear in the path immediately.

**Architecture:** A `CreatePebbleSheet` is presented as a sheet from a button on `PathView`. The sheet loads four reference lists (emotions, domains, souls, collections) in parallel via `async let`, holds form state in a `PebbleDraft` value type, inserts the pebble via `.insert(...).select().single()`, then inserts join-table rows in parallel before dismissing and prepending the freshly returned `Pebble` to the path list.

**Tech Stack:** SwiftUI (iOS 17+), `@Observable`/`@Environment`, supabase-swift 2.x, xcodegen.

**Spec:** `docs/superpowers/specs/2026-04-13-ios-create-pebble-design.md`. Read it before starting.

## Important context for the executor

- **No automated tests in this plan.** The iOS app has no test target wired up beyond a stub, and the `apps/ios/CLAUDE.md` says: *"No UI tests for now. Add a `PebblesUITests` target in a dedicated PR when smoke tests are actually needed."* Verification in this plan is **build success + manual smoke test in the simulator**. Do not invent test files.
- **xcodegen handles file inclusion.** `apps/ios/project.yml` declares `sources: [path: Pebbles]`, which globs the entire folder. After adding any new `.swift` file under `Pebbles/`, run `npm run generate --workspace=@pbbls/ios` to refresh the `.xcodeproj`. Never hand-edit the `.pbxproj`.
- **Build command:** `npm run build --workspace=@pbbls/ios` from the repo root. This runs `xcodegen generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build`.
- **Lint command:** `npm run lint --workspace=@pbbls/ios` (swiftlint).
- **Branch:** Work on `feat/212-ios-create-pebble`. Already created and checked out.
- **Trust RLS.** Never pass `user_id` in client payloads or filters. The database scopes everything via `auth.uid()`.
- **Logging discipline.** Every `catch` block must log via `os.Logger` with `privacy: .private` on user-derived strings. Mirror the pattern in `apps/ios/Pebbles/Features/Path/PathView.swift:49`. No empty catches.
- **The supabase-swift insert API** — at the time of writing, the pattern is:
  ```swift
  let inserted: Pebble = try await supabase.client
      .from("pebbles")
      .insert(payload)
      .select()
      .single()
      .execute()
      .value
  ```
  `payload` can be a `Codable` struct or a `[String: AnyJSON]`. Prefer a `Codable` struct (call it `PebbleInsert`) for type safety. If the SDK API has shifted, adjust — the contract is "insert one row, get the inserted row back as a `Pebble`".

## File structure

All new files live under `apps/ios/Pebbles/Features/Path/`.

```
Features/Path/
├── Models/
│   ├── Pebble.swift              (existing — unchanged)
│   ├── PebbleDraft.swift         (NEW)
│   ├── PebbleInsert.swift        (NEW — the Codable insert payload)
│   ├── Valence.swift             (NEW)
│   ├── Visibility.swift          (NEW)
│   ├── Emotion.swift             (NEW)
│   ├── Domain.swift              (NEW)
│   ├── Soul.swift                (NEW)
│   └── PebbleCollection.swift    (NEW — named to avoid Swift.Collection collision)
├── PathView.swift                (modify)
└── CreatePebbleSheet.swift       (NEW)
```

`PebbleCollection` rather than `Collection` because `Collection` is a Swift standard-library protocol. Avoiding the collision once now is cheaper than disambiguating at every use site.

---

### Task 1: Add reference data models

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/Emotion.swift`
- Create: `apps/ios/Pebbles/Features/Path/Models/Domain.swift`
- Create: `apps/ios/Pebbles/Features/Path/Models/Soul.swift`
- Create: `apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift`

- [ ] **Step 1: Create `Emotion.swift`**

```swift
import Foundation

struct Emotion: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    let color: String
}
```

- [ ] **Step 2: Create `Domain.swift`**

```swift
import Foundation

struct Domain: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
    let label: String
}
```

- [ ] **Step 3: Create `Soul.swift`**

```swift
import Foundation

struct Soul: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
}
```

- [ ] **Step 4: Create `PebbleCollection.swift`**

```swift
import Foundation

struct PebbleCollection: Identifiable, Decodable, Hashable {
    let id: UUID
    let name: String
}
```

- [ ] **Step 5: Regenerate Xcode project and build**

Run from repo root:
```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`. The four new files are now part of the Pebbles target.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Emotion.swift \
        apps/ios/Pebbles/Features/Path/Models/Domain.swift \
        apps/ios/Pebbles/Features/Path/Models/Soul.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleCollection.swift
git commit -m "feat(ios): add reference and user-owned models for pebble creation"
```

---

### Task 2: Add `Valence` and `Visibility` enums

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/Valence.swift`
- Create: `apps/ios/Pebbles/Features/Path/Models/Visibility.swift`

- [ ] **Step 1: Create `Valence.swift`**

```swift
import Foundation

/// The 9-option valence picker shown in the create-pebble form.
/// Maps to the `pebbles.positiveness` and `pebbles.intensity` columns on save.
enum Valence: String, CaseIterable, Identifiable, Hashable {
    case lowlightSmall, lowlightMedium, lowlightLarge
    case neutralSmall, neutralMedium, neutralLarge
    case highlightSmall, highlightMedium, highlightLarge

    var id: String { rawValue }

    var label: String {
        switch self {
        case .lowlightSmall:   return "Lowlight — small"
        case .lowlightMedium:  return "Lowlight — medium"
        case .lowlightLarge:   return "Lowlight — large"
        case .neutralSmall:    return "Neutral — small"
        case .neutralMedium:   return "Neutral — medium"
        case .neutralLarge:    return "Neutral — large"
        case .highlightSmall:  return "Highlight — small"
        case .highlightMedium: return "Highlight — medium"
        case .highlightLarge:  return "Highlight — large"
        }
    }

    /// Maps to `pebbles.positiveness` (-1, 0, +1).
    var positiveness: Int {
        switch self {
        case .lowlightSmall, .lowlightMedium, .lowlightLarge:    return -1
        case .neutralSmall, .neutralMedium, .neutralLarge:       return 0
        case .highlightSmall, .highlightMedium, .highlightLarge: return 1
        }
    }

    /// Maps to `pebbles.intensity` (1, 2, 3).
    var intensity: Int {
        switch self {
        case .lowlightSmall, .neutralSmall, .highlightSmall:    return 1
        case .lowlightMedium, .neutralMedium, .highlightMedium: return 2
        case .lowlightLarge, .neutralLarge, .highlightLarge:    return 3
        }
    }
}
```

- [ ] **Step 2: Create `Visibility.swift`**

```swift
import Foundation

enum Visibility: String, CaseIterable, Identifiable, Hashable {
    case `private` = "private"
    case `public` = "public"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .private: return "Private"
        case .public:  return "Public"
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Valence.swift \
        apps/ios/Pebbles/Features/Path/Models/Visibility.swift
git commit -m "feat(ios): add valence and visibility enums for pebble creation"
```

---

### Task 3: Add `PebbleDraft` and `PebbleInsert`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`
- Create: `apps/ios/Pebbles/Features/Path/Models/PebbleInsert.swift`

- [ ] **Step 1: Create `PebbleDraft.swift`**

```swift
import Foundation

/// In-progress form state for the create-pebble sheet.
/// A value type held in `@State` on `CreatePebbleSheet`.
/// Optional fields use `nil` to mean "not yet picked"; non-optionals carry sensible defaults.
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

    /// True when every mandatory field is set. Drives the Save button's disabled state.
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespaces).isEmpty
        && emotionId != nil
        && domainId != nil
        && valence != nil
    }
}
```

- [ ] **Step 2: Create `PebbleInsert.swift`**

```swift
import Foundation

/// The Codable payload sent to `pebbles.insert(...)`.
/// Built from a validated `PebbleDraft` — non-optionals here mean
/// `PebbleDraft.isValid` was true at the call site.
/// `user_id` is intentionally absent: RLS scopes the row to `auth.uid()`.
struct PebbleInsert: Encodable {
    let name: String
    let description: String?
    let happenedAt: Date
    let intensity: Int
    let positiveness: Int
    let visibility: String
    let emotionId: UUID

    enum CodingKeys: String, CodingKey {
        case name
        case description
        case happenedAt = "happened_at"
        case intensity
        case positiveness
        case visibility
        case emotionId = "emotion_id"
    }
}

extension PebbleInsert {
    /// Build an insert payload from a validated draft.
    /// Precondition: `draft.isValid == true`.
    init(from draft: PebbleDraft) {
        precondition(draft.isValid, "PebbleInsert(from:) called with invalid draft")
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
    }
}
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleInsert.swift
git commit -m "feat(ios): add PebbleDraft form state and PebbleInsert payload"
```

---

### Task 4: Scaffold `CreatePebbleSheet` (loading + error states only)

This step creates the sheet shell with the four reference lists loading via `.task`. No form fields yet — those land in Task 5. Splitting it makes the loading behavior reviewable on its own.

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Create the sheet skeleton**

```swift
import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: (Pebble) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var emotions: [Emotion] = []
    @State private var domains: [Domain] = []
    @State private var souls: [Soul] = []
    @State private var collections: [PebbleCollection] = []

    @State private var isLoadingReferences = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("New pebble")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        // Save button lands in Task 6.
                        EmptyView()
                    }
                }
        }
        .task { await loadReferences() }
    }

    @ViewBuilder
    private var content: some View {
        if isLoadingReferences {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") {
                    Task { await loadReferences() }
                }
            }
        } else {
            // Form fields land in Task 5.
            Text("References loaded: \(emotions.count) emotions, \(domains.count) domains, \(souls.count) souls, \(collections.count) collections")
        }
    }

    private func loadReferences() async {
        isLoadingReferences = true
        loadError = nil
        do {
            async let emotionsQuery: [Emotion] = supabase.client
                .from("emotions")
                .select()
                .order("name")
                .execute()
                .value
            async let domainsQuery: [Domain] = supabase.client
                .from("domains")
                .select()
                .order("name")
                .execute()
                .value
            async let soulsQuery: [Soul] = supabase.client
                .from("souls")
                .select("id, name")
                .order("name")
                .execute()
                .value
            async let collectionsQuery: [PebbleCollection] = supabase.client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value

            let (loadedEmotions, loadedDomains, loadedSouls, loadedCollections) =
                try await (emotionsQuery, domainsQuery, soulsQuery, collectionsQuery)

            self.emotions = loadedEmotions
            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.isLoadingReferences = false
        } catch {
            logger.error("reference load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load the form data."
            self.isLoadingReferences = false
        }
    }
}

#Preview {
    CreatePebbleSheet { _ in }
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "feat(ios): scaffold CreatePebbleSheet with reference data loading"
```

---

### Task 5: Render the form fields

Replace the placeholder `Text` in `CreatePebbleSheet.content` with a real `Form`. No save logic yet — that's Task 6.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Replace the loaded-state branch with a `Form`**

Replace the entire `else` branch in `content` (the `Text("References loaded: ...")` line) with:

```swift
} else {
    Form {
        Section {
            DatePicker(
                "When",
                selection: $draft.happenedAt,
                displayedComponents: [.date, .hourAndMinute]
            )

            TextField("Name", text: $draft.name)

            TextField("Description (optional)", text: $draft.description, axis: .vertical)
                .lineLimit(1...5)
        }

        Section("Mood") {
            Picker("Emotion", selection: $draft.emotionId) {
                Text("Choose…").tag(UUID?.none)
                ForEach(emotions) { emotion in
                    Text(emotion.name).tag(UUID?.some(emotion.id))
                }
            }

            Picker("Domain", selection: $draft.domainId) {
                Text("Choose…").tag(UUID?.none)
                ForEach(domains) { domain in
                    Text(domain.name).tag(UUID?.some(domain.id))
                }
            }

            Picker("Valence", selection: $draft.valence) {
                Text("Choose…").tag(Valence?.none)
                ForEach(Valence.allCases) { valence in
                    Text(valence.label).tag(Valence?.some(valence))
                }
            }
        }

        Section("Optional") {
            Picker("Soul", selection: $draft.soulId) {
                Text("None").tag(UUID?.none)
                ForEach(souls) { soul in
                    Text(soul.name).tag(UUID?.some(soul.id))
                }
            }

            Picker("Collection", selection: $draft.collectionId) {
                Text("None").tag(UUID?.none)
                ForEach(collections) { collection in
                    Text(collection.name).tag(UUID?.some(collection.id))
                }
            }
        }

        Section("Privacy") {
            Picker("Privacy", selection: $draft.visibility) {
                ForEach(Visibility.allCases) { visibility in
                    Text(visibility.label).tag(visibility)
                }
            }
            .pickerStyle(.segmented)
        }

        if let saveError {
            Section {
                Text(saveError)
                    .foregroundStyle(.red)
                    .font(.callout)
            }
        }
    }
}
```

**Why the `.tag(UUID?.some(...))` and `.tag(UUID?.none)` shapes?**
SwiftUI matches a `Picker`'s `selection` to a row by its `tag`. The selection type here is `UUID?` (optional), so every tag must also be `UUID?`. `.tag(UUID?.none)` is the "Choose…" row; `.tag(UUID?.some(emotion.id))` wraps the real id in the same optional type. If you write `.tag(emotion.id)` you'll get `UUID` (non-optional), the types won't match, and the picker silently fails to highlight the selected row. This is one of the most common SwiftUI footguns.

- [ ] **Step 2: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "feat(ios): render create-pebble form fields"
```

---

### Task 6: Implement save logic

Add the Save button, the insert call, and the join-row inserts. Replace the `EmptyView()` toolbar item from Task 4.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Replace the `confirmationAction` toolbar item**

Replace:
```swift
ToolbarItem(placement: .confirmationAction) {
    // Save button lands in Task 6.
    EmptyView()
}
```

with:

```swift
ToolbarItem(placement: .confirmationAction) {
    if isSaving {
        ProgressView()
    } else {
        Button("Save") {
            Task { await save() }
        }
        .disabled(!draft.isValid)
    }
}
```

- [ ] **Step 2: Add the `save()` method to `CreatePebbleSheet`**

Add this method after `loadReferences()`:

```swift
private func save() async {
    guard draft.isValid else { return }
    isSaving = true
    saveError = nil

    do {
        let payload = PebbleInsert(from: draft)

        let inserted: Pebble = try await supabase.client
            .from("pebbles")
            .insert(payload)
            .select()
            .single()
            .execute()
            .value

        try await insertJoinRows(for: inserted.id)

        onCreated(inserted)
        dismiss()
    } catch {
        logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
        self.saveError = "Couldn't save your pebble. Please try again."
        self.isSaving = false
    }
}

private func insertJoinRows(for pebbleId: UUID) async throws {
    // Domain is mandatory — always one row.
    async let domainInsert: Void = insertPebbleDomain(pebbleId: pebbleId, domainId: draft.domainId!)

    // Soul is optional.
    async let soulInsert: Void = {
        if let soulId = draft.soulId {
            try await insertPebbleSoul(pebbleId: pebbleId, soulId: soulId)
        }
    }()

    // Collection is optional.
    async let collectionInsert: Void = {
        if let collectionId = draft.collectionId {
            try await insertCollectionPebble(collectionId: collectionId, pebbleId: pebbleId)
        }
    }()

    _ = try await (domainInsert, soulInsert, collectionInsert)
}

private func insertPebbleDomain(pebbleId: UUID, domainId: UUID) async throws {
    struct Row: Encodable {
        let pebble_id: UUID
        let domain_id: UUID
    }
    _ = try await supabase.client
        .from("pebble_domains")
        .insert(Row(pebble_id: pebbleId, domain_id: domainId))
        .execute()
}

private func insertPebbleSoul(pebbleId: UUID, soulId: UUID) async throws {
    struct Row: Encodable {
        let pebble_id: UUID
        let soul_id: UUID
    }
    _ = try await supabase.client
        .from("pebble_souls")
        .insert(Row(pebble_id: pebbleId, soul_id: soulId))
        .execute()
}

private func insertCollectionPebble(collectionId: UUID, pebbleId: UUID) async throws {
    struct Row: Encodable {
        let collection_id: UUID
        let pebble_id: UUID
    }
    _ = try await supabase.client
        .from("collection_pebbles")
        .insert(Row(collection_id: collectionId, pebble_id: pebbleId))
        .execute()
}
```

**Why nested anonymous closures for the optional join inserts?**
`async let` requires the right-hand side to be an expression that produces a value of the declared type. Wrapping the conditional in an immediately-invoked async closure (`{ ... }()`) makes the optional-handling fit into the `async let` shape. Without the wrapper you'd need a manual `TaskGroup`, which is more code for the same effect.

**Why local `Row` structs instead of a top-level model?**
Each join table only has two columns and is only inserted from one place in the codebase. Inlining the `Encodable` shape keeps the model folder uncluttered. If a second call site appears, promote them.

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "feat(ios): wire CreatePebbleSheet save flow with join-row inserts"
```

---

### Task 7: Wire `PathView` to present the sheet

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Read the current file**

```bash
cat apps/ios/Pebbles/Features/Path/PathView.swift
```

The current file is the one shown in the spec context — a `NavigationStack` with a `List(pebbles)` rendering `name` and `happenedAt`.

- [ ] **Step 2: Add the sheet state and a "Record a pebble" row**

Replace the entire file with:

```swift
import SwiftUI
import os

struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false

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
                        VStack(alignment: .leading, spacing: 4) {
                            Text(pebble.name).font(.body)
                            Text(pebble.happenedAt, style: .date)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
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

**What changed:**
- `@State private var isPresentingCreate = false` — drives the sheet.
- `.sheet(isPresented:)` modifier on `NavigationStack` — presents `CreatePebbleSheet`.
- The `List(pebbles)` shorthand became `List { Section { ... }; Section("Path") { ForEach(pebbles) { ... } } }` so we can put a "Record a pebble" button row above the pebble rows.
- New `handleCreated` method — appends the new pebble and re-sorts (the user may have backdated it, so simple `insert(at: 0)` isn't enough).

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```
Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```
Expected: zero violations. Fix any that appear (line length, trailing whitespace, etc.) before committing.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ios): present CreatePebbleSheet from PathView"
```

---

### Task 8: Manual smoke test in the simulator

No automated tests; verify by running the app.

- [ ] **Step 1: Boot the simulator and run the app**

```bash
cd apps/ios && xcodegen generate
open Pebbles.xcodeproj
```

Then in Xcode: pick an iPhone simulator, press ⌘R. Sign in with a real account (the one you've been using during dev).

- [ ] **Step 2: Verify the path screen**

Expected:
- Title: "Path"
- A "Record a pebble" button row above the existing pebble list
- Existing pebbles below the button

If the button doesn't appear: check that Task 7 step 2 was applied correctly.

- [ ] **Step 3: Open the sheet**

Tap "Record a pebble".

Expected:
- A sheet slides up from the bottom
- A `ProgressView` is briefly visible
- Then a `Form` appears with: When (date+time), Name, Description, Emotion, Domain, Valence, Soul, Collection, Privacy
- Cancel button in the top-left
- Save button in the top-right, **disabled** (greyed out)

- [ ] **Step 4: Verify validation**

- Type a name → Save still disabled (emotion/domain/valence not set)
- Pick an emotion → Save still disabled
- Pick a domain → Save still disabled
- Pick a valence → **Save now enabled**
- Clear the name → Save disabled again
- Type the name back → Save enabled again

- [ ] **Step 5: Save a pebble**

Fill all mandatory fields, leave Soul and Collection as "None", privacy as Private. Tap Save.

Expected:
- The Save button shows a `ProgressView`
- The sheet dismisses
- The new pebble appears at the top of the path list (or in the correct sorted position if you backdated it)

- [ ] **Step 6: Verify in the database**

Use the Supabase dashboard or `psql` against the linked project to verify:
```sql
select id, name, happened_at, intensity, positiveness, visibility, emotion_id
  from pebbles
  order by created_at desc
  limit 1;

select pebble_id, domain_id from pebble_domains where pebble_id = '<id from above>';
```

Expected: one pebble row with the values you typed; one `pebble_domains` row linking it to the chosen domain.

- [ ] **Step 7: Test failure paths**

- Open the sheet, kill the network (Settings → Airplane Mode in the simulator), tap Save → expect inline red error, sheet stays open, typed values preserved.
- Re-enable network, tap Save again → expect success.
- Kill the network *before* opening the sheet → expect the loading state to fail with "Couldn't load the form data." and a Retry button.

- [ ] **Step 8: Test optional fields**

Create a soul via your existing data (or skip if you have none yet). Open the sheet, pick the soul, save. Verify a row in `pebble_souls`:
```sql
select * from pebble_souls order by pebble_id desc limit 1;
```

Skip the collection test if you have no collections. The optional path is exercised by the existing "leave as None" save in Step 5.

- [ ] **Step 9: Commit anything you changed during smoke testing**

If you fixed any bugs during smoke testing, commit each fix as a separate `fix(ios): …` commit. If everything worked, no commit needed.

---

### Task 9: Open the pull request

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/212-ios-create-pebble
```

- [ ] **Step 2: Create the PR**

```bash
gh pr create --title "feat(ios): create a pebble (#212)" --body "$(cat <<'EOF'
Resolves #212.

## Summary

Adds a native SwiftUI form on iOS that lets an authenticated user record a new pebble and see it appear in the path immediately.

- A "Record a pebble" button card sits above the existing path list
- Tapping it opens a `.sheet` containing `CreatePebbleSheet`
- The sheet loads emotions, domains, souls, and collections in parallel via `async let`
- All form state lives in a `PebbleDraft` value type with an `isValid` computed property gating the Save button
- On save: insert into `pebbles` via `.insert(...).select().single()`, then insert join rows in parallel, prepend the returned pebble to the path list, dismiss

## Conscious divergences from the issue text

- **Single combined date+time picker** instead of two separate pickers (matches Calendar/Reminders, removes merge logic)
- **Sheet** instead of an inline form card above the list (10 fields would push the path off-screen)
- **Single 9-option valence picker** that splits into `positiveness` + `intensity` columns on save (the DB has no `valence` column)
- **Single-select** for domain, soul, and collection (the DB join tables support many-to-many; we keep the schema, ship single-select for V1)

## Key files

- `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/PebbleInsert.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/Valence.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/Visibility.swift` (new)
- `apps/ios/Pebbles/Features/Path/Models/{Emotion,Domain,Soul,PebbleCollection}.swift` (new)
- `apps/ios/Pebbles/Features/Path/PathView.swift` (modified)

## Known limitations

- Pebble + join row inserts are not atomic. Acceptable for V1; revisit with an RPC if real failures appear.
- No offline support, no image attachments, no glyphs, no pebble cards — all out of scope for this PR.

## Test plan

- [ ] Build succeeds: `npm run build --workspace=@pbbls/ios`
- [ ] Lint clean: `npm run lint --workspace=@pbbls/ios`
- [ ] Path screen shows a "Record a pebble" button above the list
- [ ] Tapping the button opens a sheet that loads reference data
- [ ] Save button stays disabled until name + emotion + domain + valence are all set
- [ ] Saving inserts a row in `pebbles` and a row in `pebble_domains`
- [ ] Saving with a soul also inserts into `pebble_souls`
- [ ] Saving with a collection also inserts into `collection_pebbles`
- [ ] The new pebble appears in the path list after dismiss
- [ ] Failed save preserves form state and shows an inline error

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Apply labels and milestone**

The issue is labelled `feat`, `api`, `ios` and is in milestone `M19 · iOS ShameVP`. **Ask the user to confirm** inheriting these labels and milestone before applying:

> "Issue #212 has labels `feat`, `api`, `ios` and milestone `M19 · iOS ShameVP`. Apply the same to the PR?"

After confirmation:
```bash
gh pr edit --add-label feat --add-label api --add-label ios --milestone "M19 · iOS ShameVP"
```

- [ ] **Step 4: Return the PR URL**

Print the URL `gh pr create` outputs so the user can open it.

---

## Self-review notes

- **Spec coverage:** every section of the spec has at least one task. Models → Tasks 1-3. Sheet scaffolding → Task 4. Form fields → Task 5. Save flow → Task 6. PathView wiring → Task 7. Smoke test → Task 8. PR workflow → Task 9.
- **No placeholders:** every code block contains real Swift. The two `// land in Task N` comments in Task 4 are intentional scaffolding markers, replaced in Task 6.
- **Type consistency:** `PebbleDraft.valence` is `Valence?` everywhere; the picker uses `Valence?.none` / `Valence?.some(...)` tags matching the binding type. `PebbleInsert.emotionId` is `UUID` (non-optional) because it's only constructed from a validated draft. Join-row local structs use snake_case property names so they encode directly to the column names without needing `CodingKeys`.
- **Known footguns called out inline:** the `UUID?` tag matching gotcha (Task 5), the `async let` + optional wrapper pattern (Task 6).
