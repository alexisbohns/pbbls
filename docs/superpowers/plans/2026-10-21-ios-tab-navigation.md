# iOS Four-Tab Navigation (Stack C) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The authed iOS root becomes a system `TabView` with four tabs (Path, People, Collections, You). Each tab owns a `NavigationStack` with `Codable` routes restored through `@SceneStorage`. Souls and Collections are promoted out of the Profile hub, and `PathBottomBar` is deleted (#903).

**Architecture:** One `@Observable` `TabNavigation` (selection, four typed route arrays, reselect scroll tokens, a tolerant `Codable` snapshot) is created as `@State` in `RootView`, injected into `PebblesTabView` and persisted under `@SceneStorage("tabs.navigation")`. Tab roots are the existing list views. Each root registers its own `navigationDestination`, so the existing `onChanged` reload closures keep their meaning, and two thin resolver views (`SoulRoute`, `CollectionRoute`) turn ids back into models. A small `MomentsRevision` signal makes Path reload when a moment changes on another tab.

**Tech Stack:** SwiftUI (iOS 17), `@Observable`, Swift Testing, XcodeGen, SwiftLint, UIKit appearance proxies, `gh stack`, `arkaik-mcp`.

**Spec (the contract):** `docs/superpowers/specs/2026-10-20-ios-tab-navigation-design.md`. It holds the tab/route table (§2), the state model (§3), back/reselect (§4), FAB + status cluster (§5), `RootView` composition and the sign-out reset contract (§6), the sheet inventory and D5 (§7), a11y (§8), testing and the 12-step device checklist (§9), the parts (§10) and the risks (§11).

---

## Dependencies

- **Stack A part A1 is required** (`Color.pebbles.*` / `UIColor.pebbles(_:)`, PR **#1163**). The bar uses `surfaceContainer`, `primary`, `onSurfaceVariant` and `outlineVariant`. The FAB uses `primaryContainer` and `onPrimaryContainer`. C's stack is created from `main` **after #1163 merges**. Task C1.0 checks this and stops if it has not.
- **Stack B (#1164 / #1166 / #1168) is not a dependency.** The only overlap is the French value of "New pebble" ("Nouveau galet"). Whichever of B1 and C1 lands first sets it; Task C1.5 is conditional on that.
- **Later stack A parts (A2/A3a/A3b)** migrate `PathView`, `NewPebbleButton`, `PebblesApp` and the `ThemeLiteralsTests` debt table. If one of them merges while C is open, rebase C (`gh stack rebase`). C2 deletes `NewPebbleButton.swift`, so an A3b hunk on that file resolves as "deleted".

## Before you start

- Work from the repo root of the checkout or worktree that will hold the stack. All paths below are relative to that root. In a worktree, first copy `apps/ios/Config/Secrets.xcconfig` from the main checkout (it is git-ignored, and `xcodegen generate` needs it).
- **Simulator.** This stack uses its own simulator, so it never fights stacks A and B for one. Every iOS command block starts with these two lines, because shell state does not persist between calls:

  ```bash
  SIM=$(xcrun simctl list devices | grep "iPhone 17 stack-c" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)
  DD="$HOME/Library/Developer/Xcode/DerivedData/Pebbles-stack-c"
  ```

  Task C1.0 creates the simulator if it is missing. Use `-destination "id=$SIM"` with **double** quotes; inside single quotes `$SIM` would not expand.
- **Named commands** (each one starts with the two lines above):
  - **Suite command** for `<Suite>`:
    `cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -only-testing:PebblesTests/<Suite> -quiet`
  - **Full suite** (a trusted run, so it clears stale objects first):
    `cd apps/ios && xcodegen generate && rm -rf "$DD/Build" && xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -quiet`
  - **Build**:
    `cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" build -quiet`
  - **Lint**: `npm run lint --workspace=@pbbls/ios` (SwiftLint; error severity fails CI).
- `PebblesTests/` and `Pebbles/` are folder sources in `apps/ios/project.yml`. New files are picked up by `xcodegen generate`, which every named command runs. This plan does not edit `project.yml`.
- **Strings** are inserted into `apps/ios/Pebbles/Resources/Localizable.xcstrings` as text, at the anchor named in the task, with the Edit tool. Never re-serialise the file (no `json.dump`, no `jq`). After each edit, `git diff --stat` on the catalog must show only the inserted lines.
- **Colours:** new and edited code names colours only as `Color.pebbles.<role>` / `UIColor.pebbles(.<role>)`. A1's `ThemeLiteralsTests` ratchet fails on system and absolute colours (`.white`, `UIColor.clear`, …) under `Features/` and `Components/`.
- **PR metadata (both parts):** labels `feat`, `ui`, `core`, `ios`, `no-lab-note`; milestone `M64 · New Path`. C1's body opens `Part of #903`, C2's opens `Closes #903`. Never write a Kritik `F-…` id in a PR body. Do not add `Co-Authored-By` lines from this plan; the executing session appends its own attribution.
- **Arkaik** is changed only through `arkaik-mcp`. Never touch `docs/arkaik/bundle.json` or `journal.jsonl`. If the tools are unavailable, say so and stop that task. Never set `live`.

## File map

| File | Part | Responsibility |
|---|---|---|
| `apps/ios/Pebbles/Features/Tabs/AppTab.swift` (new) | C1 | The four tabs; raw values are persisted |
| `apps/ios/Pebbles/Features/Tabs/TabRoutes.swift` (new) | C1 | `PathRoute` (uninhabited), `PeopleRoute`, `CollectionsRoute`, `YouRoute`, `RouteResolution` |
| `apps/ios/Pebbles/Features/Tabs/TabNavigation.swift` (new) | C1 | Selection, stacks, reselect, scroll tokens, `Snapshot` codec, `reset()` |
| `apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift` (new) | C1 | The `TabView`, four `NavigationStack`s, tab items, bar chrome |
| `apps/ios/Pebbles/Features/Tabs/SoulRoute.swift` (new) | C1 | `PeopleRoute.soul(id)` → `SoulDetailView`, pops if gone |
| `apps/ios/Pebbles/Features/Tabs/CollectionRoute.swift` (new) | C1 | `CollectionsRoute.collection(id)` → `CollectionDetailView`, pops if gone |
| `apps/ios/Pebbles/Features/Tabs/TabBarReselectShim.swift` (new, conditional) | C1 | UIKit fallback, added only if the reselect device check fails |
| `apps/ios/Pebbles/Services/MomentsRevision.swift` (new) | C1 | Cross-tab "moments changed" signal (D5) |
| `apps/ios/Pebbles/Features/Path/Components/NewPebbleFab.swift` (new) | C1 | 56pt FAB, opens Capture |
| `apps/ios/Pebbles/Features/Path/Components/PathStatusCluster.swift` (new) | C1 | Karma + `RippleBadge`, two buttons |
| `apps/ios/Pebbles/RootView.swift` | C1 | Composition, onboarding cover, `holdsLoader`, restore/persist, sign-out reset |
| `apps/ios/Pebbles/PebblesApp.swift` | C1 | `MomentsRevision` injection, `UITabBarAppearance` shim |
| `apps/ios/Pebbles/Features/Path/PathView.swift` | C1 | No stack, FAB row, reselect focus, D5 bump + reader |
| `apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift` | C1 | Scroll-to-top token |
| `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` | C1 | `origin`, D5 bumps |
| `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`, `CollectionDetailView.swift` | C1 | `origin` at the sheet call site, delete bump |
| `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` | C1 | Value links, People destinations, Circle toolbar button, scroll to top |
| `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift` | C1 | Value links, Collections destination, scroll to top |
| `apps/ios/Pebbles/Features/Profile/ProfileView.swift` | C1, C2 | You destinations, scroll to top; C2 drops the collections card |
| `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift`, `ProfileShortcutTile.swift` | C1, C2 | C1 value link + tab jumps; C2 Glyphs only |
| `apps/ios/Pebbles/Features/Profile/Components/ProfileAchievementsCard.swift`, `ProfileLabCard.swift` | C1 | Value links |
| `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift`, `ProfileCollectionCard.swift` | C1, C2 | C1 tab jumps; C2 deleted |
| `apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift`, `NewPebbleButton.swift` | C2 | Deleted |
| `apps/ios/Pebbles/Features/Karma/KarmaEarnedCapsule.swift` | C2 | Doc comment only |
| `apps/ios/Pebbles/Resources/Localizable.xcstrings` | C1 | "People", "You", "Opens You"; conditional "Nouveau galet" |
| `apps/ios/Scripts/verify-color-contrast.mjs` | C1 (conditional) | Bar contrast pairs |
| `apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift` (new) | C1 | State model |
| `apps/ios/PebblesTests/Features/Tabs/TabRouteResolutionTests.swift` (new) | C1 | Resolvers |
| `apps/ios/PebblesTests/Features/Tabs/MomentsRevisionTests.swift` (new) | C1 | D5 signal |
| `apps/ios/PebblesTests/Features/Tabs/TabStringsTests.swift` (new) | C1 | EN/FR of the tab strings |
| `docs/decisions/log.md` | C2 | Appended entry |
| `apps/ios/CLAUDE.md` | C2 | One line replaced |

---

## Part C1 — `feat/903-ios-four-tabs`

PR title: `feat(ios): four tabs with their own stacks`. Body starts `Part of #903`.

### Task C1.0: Preflight, stack init, Arkaik pickup

**Files:** none in the repo.

- [ ] **Step 1: Check that A1 is on main**

```bash
git fetch origin
git checkout main
git pull --ff-only
git log --oneline -1 main
git log --oneline main | grep -m1 "#1163"
test -f apps/ios/Shared/Theme/PebblesColors.swift && echo "A1 present"
grep -cE "case (surfaceContainer|primary|onSurfaceVariant|outlineVariant|primaryContainer|onPrimaryContainer|onSurface)$" apps/ios/Shared/Theme/PebblesColors.swift
```

Expected: the `grep -m1` prints the A1 merge commit (`… (#1163)`), "A1 present" prints, and the role count is `7`. **If any check fails, stop and report "waiting on #1163".** Do not start the stack.

- [ ] **Step 2: Create the simulator if it is missing**

```bash
xcrun simctl list devices | grep -q "iPhone 17 stack-c" || xcrun simctl create "iPhone 17 stack-c" "iPhone 17"
SIM=$(xcrun simctl list devices | grep "iPhone 17 stack-c" | grep -oE '[0-9A-F]{8}(-[0-9A-F]{4}){3}-[0-9A-F]{12}' | head -1)
echo "$SIM"
xcrun simctl boot "$SIM" 2>/dev/null || true
```

Expected: one UDID prints.

- [ ] **Step 3: Init the stack**

```bash
git config rerere.enabled true
git config remote.pushDefault origin
gh stack init feat/903-ios-four-tabs
git branch --show-current
```

Expected: `feat/903-ios-four-tabs`.

- [ ] **Step 4: Baseline**

Run the **Full suite** and **Lint**. Expected: green. If main is red, stop and report; do not fix main inside this stack.

- [ ] **Step 5: Move the touched views to `ios: development` in Arkaik**

Load the tools: ToolSearch `select:mcp__arkaik-mcp__get_node,mcp__arkaik-mcp__update_node,mcp__arkaik-mcp__add_edge,mcp__arkaik-mcp__remove_edge`. If they do not load, say so and skip this step. Note it for the PR body and never fall back to the bundle file.

For each of `V-timeline`, `V-profile`, `V-souls-list`, `V-collections-list`, `V-connections-list`, `V-soul-detail`, `V-collection-detail`:

1. `get_node(node_id)`. Keep the returned `status`, `platforms` and any `metadata`.
2. `update_node` with `patch.metadata` set to the **whole** returned metadata object, with `platform_statuses.ios = "development"` set inside it. `update_node` replaces metadata wholesale, so every other key (gherkin, values, other platforms) is sent back unchanged. If the node returned no `metadata` or no `platform_statuses`, send `{"platform_statuses": {<each platform in platforms>: <the node's status>, "ios": "development"}}`.
3. Do **not** patch the top-level `status`. It reflects web and Android, which are not changing.
4. `get_node` again and confirm iOS reads `development` and nothing else moved.

If the validator refuses a patch, stop and report the refusal verbatim. Do not retry with a top-level status change.

---

### Task C1.1: `AppTab`, the routes and `TabNavigation` (selection, reselect, pop)

**Files:**
- Create: `apps/ios/Pebbles/Features/Tabs/AppTab.swift`
- Create: `apps/ios/Pebbles/Features/Tabs/TabRoutes.swift`
- Create: `apps/ios/Pebbles/Features/Tabs/TabNavigation.swift`
- Test: `apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// #903 §4: back is per stack; a reselect pops first, then scrolls.
@Suite("TabNavigation — selection")
@MainActor
struct TabNavigationTests {
    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let collectionId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!

    @Test("starts on Path with every stack empty and no scroll requests")
    func defaults() {
        let nav = TabNavigation()
        #expect(nav.selected == .path)
        #expect(nav.path.isEmpty)
        #expect(nav.people.isEmpty)
        #expect(nav.collections.isEmpty)
        #expect(nav.you.isEmpty)
        #expect(nav.scrollToTop.isEmpty)
    }

    @Test("selecting another tab switches without touching any stack")
    func selectSwitches() {
        let nav = TabNavigation()
        nav.people = [.soul(soulId)]
        nav.select(.you)
        #expect(nav.selected == .you)
        #expect(nav.people == [.soul(soulId)])
        #expect(nav.scrollToTop.isEmpty)
    }

    @Test("reselecting a pushed tab pops it to root and does not scroll")
    func reselectPops() {
        let nav = TabNavigation()
        nav.select(.people)
        nav.people = [.soul(soulId), .connections]
        nav.select(.people)
        #expect(nav.selected == .people)
        #expect(nav.people.isEmpty)
        #expect(nav.scrollToTop[.people] == nil)
    }

    @Test("reselecting a tab at its root bumps only that tab's scroll token")
    func reselectAtRootScrolls() {
        let nav = TabNavigation()
        nav.select(.collections)
        nav.select(.collections)
        nav.select(.collections)
        #expect(nav.scrollToTop[.collections] == 2)
        #expect(nav.scrollToTop[.people] == nil)
        #expect(nav.scrollToTop[.path] == nil)
    }

    @Test("pop then scroll takes two taps (D3)")
    func popThenScroll() {
        let nav = TabNavigation()
        nav.select(.you)
        nav.you = [.lab]
        nav.select(.you)
        #expect(nav.you.isEmpty)
        #expect(nav.scrollToTop[.you] == nil)
        nav.select(.you)
        #expect(nav.scrollToTop[.you] == 1)
    }

    @Test("reselecting Path at root asks Path to scroll, since Path never pushes")
    func pathReselect() {
        let nav = TabNavigation()
        nav.select(.path)
        #expect(nav.scrollToTop[.path] == 1)
    }

    @Test("popToRoot leaves the other stacks intact")
    func popToRootIsLocal() {
        let nav = TabNavigation()
        nav.people = [.soul(soulId)]
        nav.collections = [.collection(collectionId)]
        nav.you = [.glyphs]
        nav.popToRoot(.collections)
        #expect(nav.collections.isEmpty)
        #expect(nav.people == [.soul(soulId)])
        #expect(nav.you == [.glyphs])
    }

    @Test("depth reports each stack's own count")
    func depth() {
        let nav = TabNavigation()
        nav.people = [.soul(soulId), .connections]
        #expect(nav.depth(of: .people) == 2)
        #expect(nav.depth(of: .you) == 0)
        #expect(nav.depth(of: .path) == 0)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `TabNavigationTests`.
Expected: build FAIL with "cannot find 'TabNavigation' in scope".

- [ ] **Step 3: Write `AppTab.swift`**

```swift
import Foundation

/// The four authed tabs (#903 §2), in bar order. Raw values are persisted by
/// `@SceneStorage("tabs.navigation")`: never rename one, add a case instead.
enum AppTab: String, Codable, CaseIterable, Hashable {
    case path, people, collections, you
}
```

- [ ] **Step 4: Write `TabRoutes.swift`**

```swift
import Foundation

/// Path pushes nothing yet. The stack exists for reselect and for future
/// pushes. The enum is uninhabited, so its conformances are written by hand:
/// no value of it can exist, and decoding one always fails (a snapshot that
/// somehow holds one falls back to an empty Path stack).
enum PathRoute {}

extension PathRoute: Hashable {
    static func == (lhs: PathRoute, rhs: PathRoute) -> Bool {
        switch lhs {}
    }

    func hash(into hasher: inout Hasher) {
        switch self {}
    }
}

extension PathRoute: Codable {
    init(from decoder: Decoder) throws {
        throw DecodingError.dataCorrupted(
            .init(codingPath: decoder.codingPath, debugDescription: "PathRoute has no cases")
        )
    }

    func encode(to encoder: Encoder) throws {
        switch self {}
    }
}

/// Routes carry ids, never models (#903 §3): `SoulWithGlyph` and `Collection`
/// are Decodable-only and change under edits. `SoulRoute` / `CollectionRoute`
/// resolve the id when the route is shown.
enum PeopleRoute: Codable, Hashable {
    case soul(UUID)
    case connections
}

enum CollectionsRoute: Codable, Hashable {
    case collection(UUID)
}

enum YouRoute: Codable, Hashable {
    case glyphs
    case achievements
    case lab
}

/// What a resolver view found for its id.
enum RouteResolution<Value> {
    case loading
    case found(Value)
    case missing
}

extension RouteResolution: Equatable where Value: Equatable {}
```

- [ ] **Step 5: Write `TabNavigation.swift` (selection part)**

```swift
import Foundation
import Observation

/// The authed navigation state (#903 §3, D2): which tab is showing and each
/// tab's own stack. Created as `@State` in `RootView`'s authed branch and
/// injected with `.environment(_:)`, so any descendant can switch tabs and
/// `RootView` can read `selected` and reset everything on sign-out.
@Observable @MainActor
final class TabNavigation {
    var selected: AppTab = .path
    var path: [PathRoute] = []
    var people: [PeopleRoute] = []
    var collections: [CollectionsRoute] = []
    var you: [YouRoute] = []
    /// Bumped when a tab is reselected at its root; roots observe their own
    /// entry and scroll to `scrollTopAnchor`.
    private(set) var scrollToTop: [AppTab: Int] = [:]

    /// The `.id` a tab root puts on its top-most content.
    static let scrollTopAnchor = "tabs.scroll-top"

    /// §4: not current → switch. Current with a stack → pop to root.
    /// Current at root → ask the root to scroll to top.
    func select(_ tab: AppTab) {
        guard tab == selected else {
            selected = tab
            return
        }
        if depth(of: tab) > 0 {
            popToRoot(tab)
        } else {
            scrollToTop[tab, default: 0] += 1
        }
    }

    func popToRoot(_ tab: AppTab) {
        switch tab {
        case .path: path.removeAll()
        case .people: people.removeAll()
        case .collections: collections.removeAll()
        case .you: you.removeAll()
        }
    }

    func depth(of tab: AppTab) -> Int {
        switch tab {
        case .path: path.count
        case .people: people.count
        case .collections: collections.count
        case .you: you.count
        }
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run the **Suite command** with `TabNavigationTests`.
Expected: PASS (8 tests). If the compiler rejects the empty `switch` in `PathRoute`, check that the `extension PathRoute` blocks are exactly as above. An uninhabited enum's empty switch is exhaustive.

- [ ] **Step 7: Lint and commit**

Run **Lint**. Expected: no errors.

```bash
git add apps/ios/Pebbles/Features/Tabs apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift
git commit -m "feat(ios): add the tab navigation state with reselect semantics"
```

---

### Task C1.2: Snapshot, tolerant restore and `reset()`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Tabs/TabNavigation.swift`
- Test: `apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift`

- [ ] **Step 1: Write the failing tests**

Append to `TabNavigationTests.swift`:

```swift
/// #903 §3 / §11: restoration survives a system kill, and a snapshot from an
/// older build never crashes the launch.
@Suite("TabNavigation — snapshot")
@MainActor
struct TabNavigationSnapshotTests {
    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let collectionId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!

    @Test("snapshot and restore round-trip every stack and the selection")
    func roundTrip() throws {
        let nav = TabNavigation()
        nav.select(.collections)
        nav.people = [.soul(soulId), .connections]
        nav.collections = [.collection(collectionId)]
        nav.you = [.glyphs, .achievements, .lab]

        let data = try #require(nav.snapshot.encoded)
        let restored = TabNavigation()
        restored.restore(try #require(TabNavigation.Snapshot.decoded(from: data)))

        #expect(restored.snapshot == nav.snapshot)
        #expect(restored.selected == .collections)
        #expect(restored.people == [.soul(soulId), .connections])
        #expect(restored.collections == [.collection(collectionId)])
        #expect(restored.you == [.glyphs, .achievements, .lab])
    }

    @Test("an unknown tab raw value decodes to Path and keeps the stacks")
    func unknownTab() throws {
        let json = #"{"selected":"settings","path":[],"people":[{"connections":{}}],"collections":[],"you":[]}"#
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data(json.utf8)))
        #expect(snapshot.selected == .path)
        #expect(snapshot.people == [.connections])
    }

    @Test("a removed route case drops that stack to root, not the whole snapshot")
    func removedRouteCase() throws {
        let json = #"{"selected":"you","path":[],"people":[{"connections":{}}],"collections":[],"you":[{"wallet":{}}]}"#
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data(json.utf8)))
        #expect(snapshot.selected == .you)
        #expect(snapshot.you.isEmpty)
        #expect(snapshot.people == [.connections])
    }

    @Test("missing keys decode to defaults")
    func missingKeys() throws {
        let snapshot = try #require(TabNavigation.Snapshot.decoded(from: Data("{}".utf8)))
        #expect(snapshot == TabNavigation.Snapshot())
    }

    @Test("bytes that are not a snapshot decode to nil")
    func garbage() {
        #expect(TabNavigation.Snapshot.decoded(from: Data("not json".utf8)) == nil)
    }

    @Test("reset empties every stack, the selection and the scroll tokens")
    func reset() {
        let nav = TabNavigation()
        nav.select(.people)
        nav.people = [.soul(soulId)]
        nav.you = [.lab]
        nav.select(.path)
        nav.select(.path)
        nav.reset()
        #expect(nav.snapshot == TabNavigation.Snapshot())
        #expect(nav.scrollToTop.isEmpty)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `TabNavigationSnapshotTests`.
Expected: build FAIL with "value of type 'TabNavigation' has no member 'snapshot'".

- [ ] **Step 3: Implement**

In `TabNavigation.swift`, before the final closing brace of the class, add:

```swift
    // MARK: - Restoration

    /// The `Codable` mirror of the five navigation fields, written to
    /// `@SceneStorage("tabs.navigation")` by `RootView`. Decoding is tolerant
    /// per field: an unknown tab or a removed route case falls back to that
    /// field's default rather than failing the whole snapshot.
    struct Snapshot: Codable, Equatable {
        var selected: AppTab
        var path: [PathRoute]
        var people: [PeopleRoute]
        var collections: [CollectionsRoute]
        var you: [YouRoute]

        enum CodingKeys: String, CodingKey {
            case selected, path, people, collections, you
        }

        init(
            selected: AppTab = .path,
            path: [PathRoute] = [],
            people: [PeopleRoute] = [],
            collections: [CollectionsRoute] = [],
            you: [YouRoute] = []
        ) {
            self.selected = selected
            self.path = path
            self.people = people
            self.collections = collections
            self.you = you
        }

        init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.init(
                selected: (try? container.decode(AppTab.self, forKey: .selected)) ?? .path,
                path: (try? container.decode([PathRoute].self, forKey: .path)) ?? [],
                people: (try? container.decode([PeopleRoute].self, forKey: .people)) ?? [],
                collections: (try? container.decode([CollectionsRoute].self, forKey: .collections)) ?? [],
                you: (try? container.decode([YouRoute].self, forKey: .you)) ?? []
            )
        }

        /// nil when the bytes are not a JSON object at all.
        static func decoded(from data: Data) -> Snapshot? {
            try? JSONDecoder().decode(Snapshot.self, from: data)
        }

        var encoded: Data? {
            try? JSONEncoder().encode(self)
        }
    }

    var snapshot: Snapshot {
        Snapshot(selected: selected, path: path, people: people, collections: collections, you: you)
    }

    func restore(_ snapshot: Snapshot) {
        selected = snapshot.selected
        path = snapshot.path
        people = snapshot.people
        collections = snapshot.collections
        you = snapshot.you
    }

    /// Sign-out (#903 §6): the next account inherits nothing.
    func reset() {
        restore(Snapshot())
        scrollToTop = [:]
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run the **Suite command** with `TabNavigationSnapshotTests`, then with `TabNavigationTests`.
Expected: PASS (6 and 8).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Tabs/TabNavigation.swift apps/ios/PebblesTests/Features/Tabs/TabNavigationTests.swift
git commit -m "feat(ios): restore the tab stacks from a tolerant snapshot"
```

---

### Task C1.3: `MomentsRevision` and its injection

**Files:**
- Create: `apps/ios/Pebbles/Services/MomentsRevision.swift`
- Modify: `apps/ios/Pebbles/PebblesApp.swift`, `apps/ios/Pebbles/RootView.swift` (preview only)
- Test: `apps/ios/PebblesTests/Features/Tabs/MomentsRevisionTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

/// #903 D5: a moment changed on one tab tells the others.
@Suite("MomentsRevision")
@MainActor
struct MomentsRevisionTests {
    @Test("starts at zero with no origin, so nothing reads as foreign")
    func initial() {
        let revision = MomentsRevision()
        #expect(revision.value == 0)
        #expect(revision.lastOrigin == nil)
        #expect(!revision.changedOutside(.path))
    }

    @Test("a bump counts up and records where it came from")
    func bump() {
        let revision = MomentsRevision()
        revision.bump(from: .people)
        revision.bump(from: .collections)
        #expect(revision.value == 2)
        #expect(revision.lastOrigin == .collections)
    }

    @Test("Path ignores its own bumps and reacts to another tab's")
    func foreign() {
        let revision = MomentsRevision()
        revision.bump(from: .path)
        #expect(!revision.changedOutside(.path))
        revision.bump(from: .people)
        #expect(revision.changedOutside(.path))
    }
}
```

Save as `apps/ios/PebblesTests/Features/Tabs/MomentsRevisionTests.swift`.

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `MomentsRevisionTests`.
Expected: build FAIL with "cannot find 'MomentsRevision' in scope".

- [ ] **Step 3: Implement**

`apps/ios/Pebbles/Services/MomentsRevision.swift`:

```swift
import Observation

/// Cross-tab "a moment changed" signal (#903 D5). New pattern, named in the
/// C1 PR body.
///
/// Bumpers: `PebbleDetailSheet` (save, delete, completion), the
/// `.momentDeleteDialog` presenters, and Capture's `onMomentChanged`.
/// Reader: `PathView`, which reloads when another tab bumped. People and
/// Collections detail views already reload through their own callbacks.
/// When stack D lands, `PathModel` becomes the reader. When E5 deletes
/// `PebbleDetailSheet`, `HeldCardModel` takes over its bumps.
@Observable @MainActor
final class MomentsRevision {
    private(set) var value = 0
    private(set) var lastOrigin: AppTab?

    func bump(from origin: AppTab) {
        lastOrigin = origin
        value += 1
    }

    /// True when the latest change was made somewhere other than `tab`.
    func changedOutside(_ tab: AppTab) -> Bool {
        guard let lastOrigin else { return false }
        return lastOrigin != tab
    }
}
```

In `apps/ios/Pebbles/PebblesApp.swift`:
- After `@State private var karmaOverlay = KarmaOverlayWindowController()`, add `@State private var momentsRevision = MomentsRevision()`.
- After `.environment(connections)` in `body`, add `.environment(momentsRevision)`.

In `apps/ios/Pebbles/RootView.swift`'s `#Preview`, after `.environment(ConnectionsService(supabase: supabase))`, add `.environment(MomentsRevision())`.

- [ ] **Step 4: Run the test to verify it passes**

Run the **Suite command** with `MomentsRevisionTests`.
Expected: PASS (3).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Services/MomentsRevision.swift apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/RootView.swift apps/ios/PebblesTests/Features/Tabs/MomentsRevisionTests.swift
git commit -m "feat(ios): add the cross-tab moments revision signal"
```

---

### Task C1.4: Tab strings

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`
- Test: `apps/ios/PebblesTests/Features/Tabs/TabStringsTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Foundation
import Testing

/// #903 §2 / §8: every string the bar and the status cluster show has an FR
/// value matching Android's `tab_*` strings.
@Suite("Tab strings")
struct TabStringsTests {
    private static let keys = ["Path", "People", "Collections", "You", "Circle", "Opens You", "New pebble"]
    private static let french = ["Chemin", "Proches", "Collections", "Toi", "Cercle", "Ouvre Toi", "Nouveau galet"]

    @Test("each tab string resolves in French", arguments: zip(keys, french))
    func frenchValue(key: String, expected: String) {
        #expect(Self.resolve(key, language: "fr") == expected)
    }

    @Test("each tab string resolves in English to its key", arguments: keys)
    func englishValue(key: String) {
        #expect(Self.resolve(key, language: "en") == key)
    }

    /// Same lookup as LocalizationTests' `resolveKey`: the locale's `.lproj`
    /// inside the app bundle (the test host).
    private static func resolve(_ key: String, language: String) -> String {
        guard
            let path = Bundle.main.path(forResource: language, ofType: "lproj"),
            let bundle = Bundle(path: path)
        else { return "__NO_BUNDLE__" }
        return bundle.localizedString(forKey: key, value: "__MISSING__", table: nil)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `TabStringsTests`.
Expected: FAIL for "People" (`__MISSING__`), "You", "Opens You", and "New pebble" (`Nouveau pebble`) unless B1 has already landed. "Path", "Collections" and "Circle" pass.

- [ ] **Step 3: Insert the three new keys as text**

With the Edit tool on `apps/ios/Pebbles/Resources/Localizable.xcstrings`:

1. Find the anchor line `    "Opens soul creation form" : {` and insert **before** it:

```
    "Opens You" : {
      "extractionState" : "manual",
      "localizations" : {
        "en" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Opens You"
          }
        },
        "fr" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Ouvre Toi"
          }
        }
      }
    },
```

2. Find `    "People and beings you tag on your pebbles will appear here." : {` and insert **before** it:

```
    "People" : {
      "extractionState" : "manual",
      "localizations" : {
        "en" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "People"
          }
        },
        "fr" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Proches"
          }
        }
      }
    },
```

3. Find `    "You already own this glyph" : {` and insert **before** it:

```
    "You" : {
      "extractionState" : "manual",
      "localizations" : {
        "en" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "You"
          }
        },
        "fr" : {
          "stringUnit" : {
            "state" : "translated",
            "value" : "Toi"
          }
        }
      }
    },
```

Check the diff:

```bash
git diff --stat apps/ios/Pebbles/Resources/Localizable.xcstrings
python3 -c "import json,sys; json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings'))" && echo valid
```

Expected: `48 insertions(+)`, no deletions, and `valid`.

- [ ] **Step 4: Run the test**

Run the **Suite command** with `TabStringsTests`.
Expected: everything passes except `New pebble` in French, unless B1 has landed. Task C1.5 handles that.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/PebblesTests/Features/Tabs/TabStringsTests.swift
git commit -m "feat(ios): add the people, you and opens-you strings"
```

---

### Task C1.5 (conditional): "Nouveau galet" if B1 has not landed

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Check**

```bash
grep -c '"value" : "Nouveau galet"' apps/ios/Pebbles/Resources/Localizable.xcstrings
```

If it prints `1`, B1's #1101 rider is already on this branch. Skip to Step 4 and write "galet: from B1" in the PR notes.

- [ ] **Step 2: Edit as text**

With the Edit tool, replace `"value" : "Nouveau pebble"` (unique in the file) with `"value" : "Nouveau galet"`. Check that `git diff --stat` on the catalog shows `1 insertion(+), 1 deletion(-)`. Leave "Caillou créé" / "Caillou enrichi" alone; they belong to B1.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "fix(ios): say nouveau galet on the new-pebble button"
```

Note for the PR body: "Sets the `New pebble` FR value from #1101; B1 makes the same one-line change, so whichever lands second is a no-op."

- [ ] **Step 4: Verify**

Run the **Suite command** with `TabStringsTests`. Expected: PASS (14).

---

### Task C1.6: `PebblesTabView`, `RootView` composition, restore/persist, sign-out reset; strip `PathView`'s stack

This is the first task that changes what the app shows. Path, People, Collections and You render inside their own stacks. The hub's links still work, because they are closure-based until C1.10–C1.12.

**Files:**
- Create: `apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Write `PebblesTabView.swift`**

```swift
import SwiftUI

/// The authed root (#903): a system `TabView` with one `NavigationStack` per
/// tab, driven by `TabNavigation`. Rendered only with a session (`RootView`
/// gates on `canShowAuthedTabs`). Each root registers its own
/// `navigationDestination`, so destinations can call back into the list that
/// pushed them.
///
/// Cross-tab presentation goes through `RootView` (or, later, the card-stack
/// host), never by setting another tab's state (§11).
struct PebblesTabView: View {
    var onPathFirstLoad: () -> Void = {}

    @Environment(TabNavigation.self) private var nav

    var body: some View {
        @Bindable var nav = nav
        TabView(selection: Binding(get: { nav.selected }, set: { nav.select($0) })) {
            NavigationStack(path: $nav.path) {
                PathView(onFirstLoad: onPathFirstLoad)
            }
            .modifier(TabChrome())
            .tabItem { Label("Path", systemImage: "square.stack") }
            .tag(AppTab.path)

            NavigationStack(path: $nav.people) {
                SoulsListView()
            }
            .modifier(TabChrome())
            .tabItem { Label("People", systemImage: "person.2") }
            .tag(AppTab.people)

            NavigationStack(path: $nav.collections) {
                CollectionsListView()
            }
            .modifier(TabChrome())
            .tabItem { Label("Collections", systemImage: "circle.hexagongrid") }
            .tag(AppTab.collections)

            NavigationStack(path: $nav.you) {
                ProfileView()
            }
            .modifier(TabChrome())
            .tabItem { Label("You", systemImage: "person.crop.circle") }
            .tag(AppTab.you)
        }
        .tint(Color.pebbles.primary)
    }
}

/// Bar background (#903 §6). `toolbarBackground(_:for: .tabBar)` only takes
/// effect from inside a tab's content, so it is applied per stack. `.visible`
/// keeps the scrolling week list from showing through in light mode. The
/// unselected colour and the hairline live in `PebblesApp`'s
/// `UITabBarAppearance` shim, because iOS 17 SwiftUI has no modifier for them.
private struct TabChrome: ViewModifier {
    func body(content: Content) -> some View {
        content
            .toolbarBackground(Color.pebbles.surfaceContainer, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
    }
}

#Preview {
    let supabase = SupabaseService()
    return PebblesTabView()
        .environment(TabNavigation())
        .environment(MomentsRevision())
        .environment(supabase)
        .environment(EmotionPaletteService(client: supabase.client))
        .environment(ReferenceDataService(client: supabase.client))
        .environment(PathStatsService(supabase: supabase))
        .environment(CompleteDraftStore())
        .environment(ConnectionsService(supabase: supabase))
        .environment(AchievementsService(client: supabase.client))
}
```

- [ ] **Step 2: Strip `PathView`'s `NavigationStack` and route**

In `apps/ios/Pebbles/Features/Path/PathView.swift`:

1. Replace

```swift
import SwiftUI
import os

private enum PathRoute: Hashable {
    case profile
}

struct PathView: View {
```

with

```swift
import SwiftUI
import os

/// The Path tab's root (#903). Its `NavigationStack` lives in
/// `PebblesTabView`; Path pushes nothing yet.
struct PathView: View {
```

2. After `@Environment(CompleteDraftStore.self) private var completeDrafts`, add `@Environment(TabNavigation.self) private var nav`.
3. Delete the line `@State private var navPath = NavigationPath()`.
4. Replace

```swift
        NavigationStack(path: $navPath) {
            content
                .navigationDestination(for: PathRoute.self) { route in
                    switch route {
                    case .profile: ProfileView()
                    }
                }
                .toolbar(.hidden, for: .navigationBar)
                .pebblesScreen()
        }
        .task {
```

with

```swift
        content
            .toolbar(.hidden, for: .navigationBar)
            .pebblesScreen()
        .task {
```

5. Replace `onProfile: { navPath.append(PathRoute.profile) }` with `onProfile: { nav.select(.you) }`. This is temporary: Task C1.13 replaces the bar.
6. Replace the preview with:

```swift
#Preview {
    NavigationStack {
        PathView()
    }
    .environment(SupabaseService())
    .environment(CompleteDraftStore())
    .environment(TabNavigation())
    .environment(MomentsRevision())
}
```

- [ ] **Step 3: Compose the tabs in `RootView`**

In `apps/ios/Pebbles/RootView.swift`:

1. Replace `import SwiftUI` (line 1) with:

```swift
import SwiftUI
import os
```

2. In the doc comment, replace `///   - if the user is authenticated, the stack swaps to `PathView`, with the` with `///   - if the user is authenticated, the stack swaps to `PebblesTabView`, with the`. Then replace `///     settles — so `PathView`'s own spinner never flashes.` with `///     settles — so `PathView`'s own spinner never flashes. A launch restored`, followed by a new line `///     onto another tab holds no loader (`holdsLoader`).`
3. After `@State private var acceptingInvite: InviteToken?`, add:

```swift
    /// Authed navigation (#903 §3). Lives here, not in a child, so this view
    /// can read `selected` for `holdsLoader` and reset it on sign-out.
    @State private var nav = TabNavigation()
    /// Sign-out reset contract (#903 §6): every authed `@SceneStorage` key is
    /// declared here and cleared in `resetAuthedSceneState()`. A later stack
    /// that adds one (stack E's `heldCard`) registers it in both places, in
    /// the PR that adds it.
    @SceneStorage("tabs.navigation") private var storedTabs: Data?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "root")
```

4. After the `isResolvedSignedOut` computed property, add:

```swift
    /// A restored scene on People or You would otherwise boil forever: Path's
    /// `.task` (and so `onFirstLoad`) only runs once Path is shown.
    private var holdsLoader: Bool {
        !pathFeedLoaded && nav.selected == .path
    }
```

5. Replace the authed branch, from `ZStack {` / `PathView(onFirstLoad: { pathFeedLoaded = true })` through `.animation(.easeInOut(duration: 0.35), value: pathFeedLoaded)`, which is

```swift
                ZStack {
                    PathView(onFirstLoad: { pathFeedLoaded = true })
                        .fullScreenCover(isPresented: $isPresentingOnboarding) {
                            OnboardingView(steps: OnboardingSteps.all) {
                                hasSeenOnboarding = true
                                isPresentingOnboarding = false
                            }
                        }

                    if !pathFeedLoaded {
```

and continues to

```swift
                }
                .animation(.easeInOut(duration: 0.35), value: pathFeedLoaded)
```

with

```swift
                ZStack {
                    PebblesTabView(onPathFirstLoad: { pathFeedLoaded = true })
                        .environment(nav)
                        .fullScreenCover(isPresented: $isPresentingOnboarding) {
                            OnboardingView(steps: OnboardingSteps.all) {
                                hasSeenOnboarding = true
                                isPresentingOnboarding = false
                            }
                        }

                    if holdsLoader {
                        // Hold the loader over the home feed load so its own
                        // spinner never flashes. `startSettled` skips a second
                        // draw-on — the logo just boils, then fades out.
                        HandcraftedLogoView(shouldSettle: false, startSettled: true)
                            .containerRelativeFrame(.horizontal) { width, _ in width * 0.33 }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.system.background.ignoresSafeArea())
                            .transition(.opacity)
                    }

                    // The card-stack overlay host (#903 §7, later stack) goes
                    // here, above the tabs, so one overlay serves every tab.
                }
                .animation(.easeInOut(duration: 0.35), value: holdsLoader)
```

If stack A3 has already migrated `Color.system.background` on main, keep main's colour expression in the loader line. Only the `if` condition and the surrounding lines change.

6. After `.task { await palettes.load() }`, add:

```swift
        // Restore once, before the authed branch first renders: the loader
        // keeps that branch hidden for well over one runloop.
        .task { restoreTabs() }
        .onChange(of: nav.snapshot) { _, snapshot in
            // Signed out, the state is being reset, not navigated.
            guard supabase.session != nil else { return }
            storedTabs = snapshot.encoded
        }
```

7. Replace the `isResolvedSignedOut` handler

```swift
        .onChange(of: isResolvedSignedOut) { _, signedOut in
            guard signedOut else { return }
            SignedOutPurge(
```

with

```swift
        .onChange(of: isResolvedSignedOut) { _, signedOut in
            guard signedOut else { return }
            resetAuthedSceneState()
            SignedOutPurge(
```

This handler fires on the same sign-out as the `pathFeedLoaded = false` re-arm above it. It also fires on a cold launch that resolves signed out, which is the case where a restored snapshot belongs to an expired session. Say this in the PR body, because spec §6 names the re-arm handler.

8. Before the closing brace of `struct RootView` (after `body`), add:

```swift
    private func restoreTabs() {
        guard let storedTabs else { return }
        guard let snapshot = TabNavigation.Snapshot.decoded(from: storedTabs) else {
            logger.error("tabs.navigation scene storage did not decode; keeping defaults")
            return
        }
        nav.restore(snapshot)
    }

    /// The one place authed scene state is cleared (#903 §6).
    private func resetAuthedSceneState() {
        nav.reset()
        storedTabs = nil
    }
```

- [ ] **Step 4: Build**

Run **Build**. Expected: `** BUILD SUCCEEDED **`. If `PathView` reports "cannot find 'ProfileView'", step 2 left the old destination behind.

- [ ] **Step 5: Run the full suite and lint**

Run the **Full suite** and **Lint**. Expected: green.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift apps/ios/Pebbles/RootView.swift apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(ios): root the authed app in four tabs with their own stacks"
```

---

### Task C1.7: Device check: reselect detection (first device task)

**Files:** none.

Spec §11: the `Binding` setter firing for the already-selected tab is long-standing behaviour, not a documented contract. This is checked before anything else is built on it.

- [ ] **Step 1: Install on the iPhone 15**

Build and install the branch on the maintainer's iPhone 15 (the memory "iOS device deploy recipe" has the ids and commands). The orchestrator runs this with the maintainer. Run the same smoke on the `iPhone 17 stack-c` simulator first: `xcrun simctl install "$SIM" "$DD/Build/Products/Debug-iphonesimulator/Pebbles.app" && xcrun simctl launch "$SIM" app.pbbls.ios`.

- [ ] **Step 2: Check**

1. Sign in. Tap **You**, then Achievements (still a closure link, so it is not in `nav.you`). Tap **You** again: nothing pops yet. That is expected, because closure links are not in the stack.
2. Tap **People**, tap a soul (closure push). Tap **People** again: nothing pops. Also expected.
3. Temporarily push a value route to test the setter. In `SoulsListView`, add `@Environment(TabNavigation.self) private var nav` after the `refs` environment line. In the toolbar `+` action, replace `isPresentingCreate = true` with `nav.people.append(.connections)`. Do **not** commit this; Task C1.11 adds the real button. Rebuild and install. Tap it, then tap **People**.
   - **Pass:** the stack pops to the souls list. Tap **People** again: no crash, nothing visible (scroll arrives in C1.10).
   - **Fail:** nothing pops. The setter is not called on reselect on this iOS version.
4. Revert the temporary line: `git checkout -- apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`.

Record the iOS version and the result for the PR body ("reselect via `TabView` selection setter: works on iOS 17.x / 18.x / 26.x").

If it passes, skip Task C1.8.

---

### Task C1.8 (conditional): UIKit reselect fallback

Do this task **only** if Task C1.7 failed. It is UIKit introspection (spec §11), so it is named as a new pattern in the PR body.

**Files:**
- Create: `apps/ios/Pebbles/Features/Tabs/TabBarReselectShim.swift`
- Modify: `apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift`

- [ ] **Step 1: Write the shim**

```swift
import SwiftUI
import UIKit

/// Fallback for #903 §11 "Reselect detection": on this iOS version SwiftUI's
/// `TabView(selection:)` setter is not called when the current tab is tapped
/// again. This finds the hosting `UITabBarController` and wraps its delegate,
/// forwarding everything to SwiftUI's own delegate. The index is the bar
/// order, which `PebblesTabView` keeps equal to `AppTab.allCases`.
struct TabBarReselectShim: UIViewControllerRepresentable {
    let onReselect: (Int) -> Void

    func makeCoordinator() -> Proxy { Proxy() }

    func makeUIViewController(context: Context) -> Probe {
        Probe(proxy: context.coordinator)
    }

    func updateUIViewController(_ controller: Probe, context: Context) {
        context.coordinator.onReselect = onReselect
    }

    final class Probe: UIViewController {
        private let proxy: Proxy

        init(proxy: Proxy) {
            self.proxy = proxy
            super.init(nibName: nil, bundle: nil)
        }

        required init?(coder: NSCoder) { nil }

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard let tabs = tabBarController, tabs.delegate !== proxy else { return }
            proxy.original = tabs.delegate
            tabs.delegate = proxy
        }
    }

    final class Proxy: NSObject, UITabBarControllerDelegate {
        weak var original: UITabBarControllerDelegate?
        var onReselect: (Int) -> Void = { _ in }

        func tabBarController(
            _ tabBarController: UITabBarController,
            shouldSelect viewController: UIViewController
        ) -> Bool {
            if viewController === tabBarController.selectedViewController,
               let index = tabBarController.viewControllers?.firstIndex(of: viewController) {
                onReselect(index)
            }
            return original?.tabBarController?(tabBarController, shouldSelect: viewController) ?? true
        }

        override func responds(to aSelector: Selector!) -> Bool {
            super.responds(to: aSelector) || (original?.responds(to: aSelector) ?? false)
        }

        override func forwardingTarget(for aSelector: Selector!) -> Any? {
            if original?.responds(to: aSelector) == true { return original }
            return super.forwardingTarget(for: aSelector)
        }
    }
}
```

- [ ] **Step 2: Install it once, on the Path root**

In `PebblesTabView.swift`, replace

```swift
                PathView(onFirstLoad: onPathFirstLoad)
```

with

```swift
                PathView(onFirstLoad: onPathFirstLoad)
                    .background(
                        TabBarReselectShim { index in
                            guard AppTab.allCases.indices.contains(index) else { return }
                            nav.select(AppTab.allCases[index])
                        }
                        .frame(width: 0, height: 0)
                    )
```

- [ ] **Step 3: Re-run the Task C1.7 check**

Repeat C1.7 Step 2, items 3–4. Expected: the stack pops once per tap, not twice. If it pops and then also scrolls on a single tap, the setter does fire after all and both paths are running: remove the shim and report.

- [ ] **Step 4: Build, lint, commit**

Run **Build** and **Lint**.

```bash
git add apps/ios/Pebbles/Features/Tabs/TabBarReselectShim.swift apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift
git commit -m "fix(ios): detect tab reselect through the tab bar delegate"
```

---

### Task C1.9: Tab bar appearance and its contrast pairs

**Files:**
- Modify: `apps/ios/Pebbles/PebblesApp.swift`
- Modify (conditional): `apps/ios/Scripts/verify-color-contrast.mjs`

- [ ] **Step 1: Add the `UITabBarAppearance` shim**

In `PebblesApp.init()`, after `Self.configureNavigationBarAppearance()`, add `Self.configureTabBarAppearance()`. Before the closing brace of `struct PebblesApp`, add:

```swift
    /// The two bar colours iOS 17 SwiftUI cannot express (#903 §6, a new
    /// pattern named in the C1 PR): the unselected item (`onSurfaceVariant`)
    /// and the hairline (`outlineVariant`). The background repeats
    /// `PebblesTabView`'s `toolbarBackground` so the bar is opaque before
    /// SwiftUI applies it. The selected item follows `.tint(primary)`.
    private static func configureTabBarAppearance() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor.pebbles(.surfaceContainer)
        appearance.shadowColor = UIColor.pebbles(.outlineVariant)

        let unselected = UIColor.pebbles(.onSurfaceVariant)
        for item in [
            appearance.stackedLayoutAppearance,
            appearance.inlineLayoutAppearance,
            appearance.compactInlineLayoutAppearance
        ] {
            item.normal.iconColor = unselected
            item.normal.titleTextAttributes = [.foregroundColor: unselected]
        }

        let proxy = UITabBar.appearance()
        proxy.standardAppearance = appearance
        proxy.scrollEdgeAppearance = appearance
    }
```

- [ ] **Step 2: Check the contrast pairs**

```bash
grep -n "surfaceContainer" apps/ios/Scripts/verify-color-contrast.mjs || echo "pairs missing"
```

If it prints "pairs missing", append this to the end of `apps/ios/Scripts/verify-color-contrast.mjs`:

```js
// Tab bar pairs (#903 §8): the unselected label and the selected item on the
// bar ground, in every appearance the role colorsets declare.
const m3 = (role) =>
  JSON.parse(
    readFileSync(
      join(root, `Pebbles/Resources/Assets.xcassets/M3/${role}.colorset/Contents.json`),
      "utf8",
    ),
  ).colors;
const rgb = ({ color: { components: c } }) => ({
  r: Number.parseInt(c.red, 16) / 255,
  g: Number.parseInt(c.green, 16) / 255,
  b: Number.parseInt(c.blue, 16) / 255,
});
const look = (entry) => JSON.stringify(entry.appearances ?? []);

for (const [ink, ground] of [
  ["onSurfaceVariant", "surfaceContainer"],
  ["primary", "surfaceContainer"],
]) {
  const grounds = new Map(m3(ground).map((entry) => [look(entry), entry]));
  for (const entry of m3(ink)) {
    const bg = grounds.get(look(entry));
    if (!bg) fail(`${ink} declares appearance ${look(entry)} that ${ground} lacks.`);
    const measured = ratio(rgb(entry), rgb(bg));
    if (measured < 4.5) {
      fail(`${ink} on ${ground} ${look(entry)} is ${measured.toFixed(3)}:1, below WCAG AA (4.5:1).`);
    }
  }
  console.log(`verify-color-contrast: ${ink} on ${ground} is AA in every appearance`);
}
```

Run:

```bash
npm run verify:contrast --workspace=@pbbls/ios
```

Expected: two "is AA in every appearance" lines. **If a pair fails, stop and report the ratio.** Stack A owns the roles, so do not change a colour here.

- [ ] **Step 3: Build and look**

Run **Build**, install on the simulator, and check in both appearances (`xcrun simctl ui "$SIM" appearance dark` / `light`): the bar is opaque `surfaceContainer`, the selected item is `primary`, the others are `onSurfaceVariant`, and there is a hairline on top.

- [ ] **Step 4: Lint and commit**

Run **Lint**.

```bash
git add apps/ios/Pebbles/PebblesApp.swift apps/ios/Scripts/verify-color-contrast.mjs
git commit -m "feat(ios): style the tab bar from the m3 roles"
```

---

### Task C1.10: Resolver views `SoulRoute` and `CollectionRoute`

**Files:**
- Create: `apps/ios/Pebbles/Features/Tabs/SoulRoute.swift`
- Create: `apps/ios/Pebbles/Features/Tabs/CollectionRoute.swift`
- Test: `apps/ios/PebblesTests/Features/Tabs/TabRouteResolutionTests.swift`

- [ ] **Step 1: Write the failing tests**

```swift
import Foundation
import Testing
@testable import Pebbles

/// #903 §3: a restored id that no longer resolves pops its route instead of
/// showing a broken screen.
@Suite("Tab route resolution")
@MainActor
struct TabRouteResolutionTests {
    private let soulId = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!

    private func soul(_ id: UUID, name: String = "Ada") -> SoulWithGlyph {
        SoulWithGlyph(
            id: id,
            name: name,
            glyphId: SystemGlyph.default,
            glyph: Glyph(id: SystemGlyph.default, name: nil, strokes: [], viewBox: "0 0 200 200", userId: nil),
            pebblesCount: 0
        )
    }

    private func refs(loadedWith souls: [SoulWithGlyph]?) -> ReferenceDataService {
        let service = ReferenceDataService(client: SupabaseService().client)
        if let souls { service.replace(domains: [], souls: souls, collections: []) }
        return service
    }

    @Test("a soul missing from loaded reference data resolves to missing (pops)")
    func soulMissing() {
        let result = SoulRoute.resolve(soulId, known: [], refs: refs(loadedWith: [soul(UUID())]))
        #expect(result == .missing)
    }

    @Test("before reference data loads, an unknown soul is still loading")
    func soulLoading() {
        let result = SoulRoute.resolve(soulId, known: [], refs: refs(loadedWith: nil))
        #expect(result == .loading)
    }

    @Test("the list's own rows win over reference data")
    func soulFromKnown() {
        let fresh = soul(soulId, name: "Fresh")
        let result = SoulRoute.resolve(soulId, known: [fresh], refs: refs(loadedWith: [soul(soulId, name: "Stale")]))
        #expect(result == .found(fresh))
    }

    @Test("a restored soul resolves from reference data")
    func soulFromRefs() {
        let stored = soul(soulId)
        let result = SoulRoute.resolve(soulId, known: [], refs: refs(loadedWith: [stored]))
        #expect(result == .found(stored))
    }

    @Test("no collection row means missing (pops)")
    func collectionMissing() {
        #expect(CollectionRoute.resolution(fetched: []) == .missing)
    }

    @Test("a fetched collection row resolves")
    func collectionFound() throws {
        let json = """
        {"id":"44444444-4444-4444-4444-444444444444","name":"Trips","mode":null,\
        "glyph_id":null,"glyph":null,"pebble_count":[{"count":3}]}
        """
        let row = try JSONDecoder().decode(Collection.self, from: Data(json.utf8))
        #expect(CollectionRoute.resolution(fetched: [row]) == .found(row))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run the **Suite command** with `TabRouteResolutionTests`.
Expected: build FAIL with "cannot find 'SoulRoute' in scope".

- [ ] **Step 3: Write `SoulRoute.swift`**

```swift
import SwiftUI
import os

/// Shows `PeopleRoute.soul(id)` (#903 §3). The souls list's own rows are the
/// freshest source (a soul created a moment ago is not in reference data until
/// `refreshSouls()` lands); reference data covers a restored scene whose list
/// has not loaded yet. An id found in neither, once reference data has
/// loaded, was deleted elsewhere: log and pop.
struct SoulRoute: View {
    let id: UUID
    let known: [SoulWithGlyph]
    let onChanged: () -> Void

    @Environment(ReferenceDataService.self) private var refs
    @Environment(\.dismiss) private var dismiss

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "tabs.soul-route")

    static func resolve(
        _ id: UUID,
        known: [SoulWithGlyph],
        refs: ReferenceDataService
    ) -> RouteResolution<SoulWithGlyph> {
        if let soul = known.first(where: { $0.id == id }) ?? refs.souls.first(where: { $0.id == id }) {
            return .found(soul)
        }
        return refs.hasLoaded ? .missing : .loading
    }

    var body: some View {
        switch Self.resolve(id, known: known, refs: refs) {
        case .found(let soul):
            SoulDetailView(initial: soul, onChanged: onChanged)
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .pebblesScreen()
        case .missing:
            Color.clear.task {
                logger.notice("soul \(id.uuidString, privacy: .private) no longer exists; popping its route")
                dismiss()
            }
        }
    }
}
```

- [ ] **Step 4: Write `CollectionRoute.swift`**

```swift
import SwiftUI
import os

/// Shows `CollectionsRoute.collection(id)` (#903 §3). Uses the list's row when
/// it has one. Otherwise (a restored scene, or a jump from the You hub) it
/// fetches the one `collections` row by id. That is a single-table,
/// single-row select, so no RPC (AGENTS.md). No row, or a failed fetch, logs
/// and pops.
struct CollectionRoute: View {
    let id: UUID
    let known: Collection?
    let onChanged: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var resolution: RouteResolution<Collection>

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "tabs.collection-route")

    /// The column list `CollectionDetailView.reloadCollection()` reads.
    static let columns = "id, name, mode, glyph_id, glyph:glyphs(id, name, strokes, view_box, user_id), pebble_count:collection_pebbles(count)"

    init(id: UUID, known: Collection?, onChanged: @escaping () -> Void) {
        self.id = id
        self.known = known
        self.onChanged = onChanged
        self._resolution = State(initialValue: known.map { .found($0) } ?? .loading)
    }

    static func resolution(fetched rows: [Collection]) -> RouteResolution<Collection> {
        rows.first.map { .found($0) } ?? .missing
    }

    var body: some View {
        Group {
            switch resolution {
            case .found(let collection):
                CollectionDetailView(collection: collection, onChanged: onChanged)
            case .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .pebblesScreen()
            case .missing:
                Color.clear
            }
        }
        .task(id: id) { await resolve() }
    }

    private func resolve() async {
        if case .found = resolution { return }
        do {
            let rows: [Collection] = try await supabase.client
                .from("collections")
                .select(Self.columns)
                .eq("id", value: id)
                .limit(1)
                .execute()
                .value
            resolution = Self.resolution(fetched: rows)
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("collection route fetch failed: \(error.localizedDescription, privacy: .private)")
            resolution = .missing
        }
        if resolution == .missing {
            logger.notice("collection \(id.uuidString, privacy: .private) did not resolve; popping its route")
            dismiss()
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run the **Suite command** with `TabRouteResolutionTests`.
Expected: PASS (6). If `SupabaseService()` in a test needs config, it already works that way in `SignedOutPurgeTests`.

- [ ] **Step 6: Lint and commit**

```bash
git add apps/ios/Pebbles/Features/Tabs/SoulRoute.swift apps/ios/Pebbles/Features/Tabs/CollectionRoute.swift apps/ios/PebblesTests/Features/Tabs/TabRouteResolutionTests.swift
git commit -m "feat(ios): resolve soul and collection routes by id"
```

---

### Task C1.11: People tab: value links, destinations, Circle button, scroll to top

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`

- [ ] **Step 1: Inject the navigator**

After `@Environment(ReferenceDataService.self) private var refs`, add `@Environment(TabNavigation.self) private var nav`.

- [ ] **Step 2: Toolbar Circle button and destinations**

Replace

```swift
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    PebbleToolbarButton(action: { isPresentingCreate = true }) {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add soul")
                }
            }
            .task { await load() }
```

with

```swift
            .toolbar {
                // People reaches Connections from here (#903 §2): the hub
                // tile's icon and copy.
                ToolbarItem(placement: .topBarLeading) {
                    PebbleToolbarButton(action: { nav.people.append(.connections) }) {
                        Image(systemName: "person.2.badge.plus")
                    }
                    .accessibilityLabel("Circle")
                }
                ToolbarItem(placement: .primaryAction) {
                    PebbleToolbarButton(action: { isPresentingCreate = true }) {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Add soul")
                }
            }
            .navigationDestination(for: PeopleRoute.self) { route in
                switch route {
                case .soul(let id):
                    SoulRoute(id: id, known: items, onChanged: {
                        Task {
                            await load()
                            await refs.refreshSouls()
                        }
                    })
                case .connections:
                    ConnectionsListView()
                }
            }
            .task { await load() }
```

- [ ] **Step 3: Value link and scroll to top**

Replace

```swift
            ScrollView {
                LazyVGrid(columns: columns, spacing: Spacing.lg) {
                    ForEach(items) { item in
                        NavigationLink {
                            SoulDetailView(initial: item, onChanged: {
                                Task {
                                    await load()
                                    await refs.refreshSouls()
                                }
                            })
                        } label: {
                            SoulItem(case: .default, soul: item, count: item.pebblesCount)
                        }
```

with

```swift
            ScrollViewReader { proxy in
            ScrollView {
                LazyVGrid(columns: columns, spacing: Spacing.lg) {
                    ForEach(items) { item in
                        NavigationLink(value: PeopleRoute.soul(item.id)) {
                            SoulItem(case: .default, soul: item, count: item.pebblesCount)
                        }
```

Then replace the end of that branch

```swift
                .padding(Spacing.lg)
            }
        }
    }
```

with

```swift
                .padding(Spacing.lg)
                .id(TabNavigation.scrollTopAnchor)
            }
            .onChange(of: nav.scrollToTop[.people, default: 0]) {
                withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
            }
            }
        }
    }
```

Reindent the `ScrollViewReader` body with Xcode (Ctrl-I) or by hand so SwiftLint's `closing_brace`/indentation warnings stay at zero.

- [ ] **Step 4: Preview**

Replace the preview body's `.environment(ReferenceDataService(client: supabase.client))` with

```swift
            .environment(ReferenceDataService(client: supabase.client))
            .environment(TabNavigation())
            .environment(CompleteDraftStore())
            .environment(MomentsRevision())
```

- [ ] **Step 5: Build, lint, smoke**

Run **Build** and **Lint**. Install on the simulator: People › a soul pushes the detail. People › Circle pushes Connections, and Invite still opens its sheet there. Tapping **People** twice from a pushed soul pops, then scrolls the grid to top.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift
git commit -m "feat(ios): make souls the people tab root with value routes"
```

---

### Task C1.12: Collections tab: value links, destination, scroll to top

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`

- [ ] **Step 1: Inject the navigator**

After `@Environment(ReferenceDataService.self) private var refs`, add `@Environment(TabNavigation.self) private var nav`.

- [ ] **Step 2: Destination**

Replace

```swift
            .task { await load() }
            .sheet(isPresented: $isPresentingCreate) {
                CreateCollectionSheet(onCreated: {
```

with

```swift
            .navigationDestination(for: CollectionsRoute.self) { route in
                switch route {
                case .collection(let id):
                    CollectionRoute(id: id, known: items.first(where: { $0.id == id }), onChanged: {
                        Task {
                            await load()
                            await refs.refreshCollections()
                        }
                    })
                }
            }
            .task { await load() }
            .sheet(isPresented: $isPresentingCreate) {
                CreateCollectionSheet(onCreated: {
```

- [ ] **Step 3: Value link and scroll to top**

Replace

```swift
            List {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, collection in
                    NavigationLink {
                        CollectionDetailView(collection: collection, onChanged: {
                            Task {
                                await load()
                                await refs.refreshCollections()
                            }
                        })
                    } label: {
                        CollectionRow(collection: collection)
                    }
```

with

```swift
            ScrollViewReader { proxy in
            List {
                ForEach(Array(items.enumerated()), id: \.element.id) { index, collection in
                    NavigationLink(value: CollectionsRoute.collection(collection.id)) {
                        CollectionRow(collection: collection)
                    }
```

Then replace

```swift
            .pebblesList()
        }
    }

    private func load() async {
```

with

```swift
            .pebblesList()
            .onChange(of: nav.scrollToTop[.collections, default: 0]) {
                guard let first = items.first else { return }
                withAnimation { proxy.scrollTo(first.id, anchor: .top) }
            }
            }
        }
    }

    private func load() async {
```

Reindent the `ScrollViewReader` body.

- [ ] **Step 4: Preview**

Add `.environment(TabNavigation())`, `.environment(CompleteDraftStore())` and `.environment(MomentsRevision())` after the preview's `ReferenceDataService` environment line.

- [ ] **Step 5: Build, lint, smoke, commit**

Run **Build** and **Lint**. Simulator: Collections › a collection pushes the detail, Edit still opens its sheet, and a swipe-delete on the root still works.

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift
git commit -m "feat(ios): make collections the collections tab root with value routes"
```

---

### Task C1.13: You tab: destinations, value links, hub jumps, scroll to top

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileAchievementsCard.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileLabCard.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift`

The hub's Souls and Circle tiles, and the collections card, cannot push into the You stack: its path is `[YouRoute]`, and their destinations now push `PeopleRoute` / `CollectionsRoute` values. Until C2 deletes them, they **switch tabs** instead. That is navigation state on the tab being opened, not a presentation, so §11's rule is respected.

- [ ] **Step 1: `ProfileShortcutTile` takes a route or an action**

Replace the whole file with:

```swift
import SwiftUI

struct ProfileShortcutTile: View {
    /// A You-stack push, or (C1 only, for tiles whose screen moved to another
    /// tab) a tab switch.
    enum Target {
        case route(YouRoute)
        case action(() -> Void)
    }

    let title: LocalizedStringResource
    let systemImage: String
    let target: Target

    var body: some View {
        switch target {
        case .route(let route):
            NavigationLink(value: route) { tile }
                .buttonStyle(.plain)
        case .action(let action):
            Button(action: action) { tile }
                .buttonStyle(.plain)
        }
    }

    private var tile: some View {
        SurfaceTile(systemImage: systemImage) {
            Text(title)
        }
    }
}
```

- [ ] **Step 2: `ProfileShortcutsRow`**

Replace the whole file with:

```swift
import SwiftUI

/// Souls / Glyphs / Circle. Souls and Circle now live under the People tab
/// (#903), so their tiles switch to it; C2 removes them.
struct ProfileShortcutsRow: View {
    @Environment(TabNavigation.self) private var nav

    var body: some View {
        HStack(spacing: Spacing.sm) {
            ProfileShortcutTile(title: "Souls", systemImage: "person.2", target: .action {
                nav.popToRoot(.people)
                nav.select(.people)
            })
            ProfileShortcutTile(title: "Glyphs", systemImage: "scribble", target: .route(.glyphs))
            ProfileShortcutTile(title: "Circle", systemImage: "person.2.badge.plus", target: .action {
                nav.people = [.connections]
                nav.select(.people)
            })
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
    .environment(TabNavigation())
}
```

- [ ] **Step 3: Achievements and Lab cards become value links**

In `ProfileAchievementsCard.swift`, replace

```swift
        NavigationLink {
            AchievementsView()
        } label: {
```

with

```swift
        NavigationLink(value: YouRoute.achievements) {
```

In `ProfileLabCard.swift`, replace

```swift
        NavigationLink {
            LabView()
        } label: {
```

with

```swift
        NavigationLink(value: YouRoute.lab) {
```

`LabView`'s own links to `AnnouncementDetailView` / `LogListView` stay closure-based (§2), so restoration lands on Lab.

- [ ] **Step 4: Collections card switches tabs**

In `ProfileCollectionsCard.swift`:

1. After `@Environment(SupabaseService.self) private var supabase`, add `@Environment(TabNavigation.self) private var nav`.
2. Replace

```swift
                            NavigationLink {
                                CollectionDetailView(collection: collection, onChanged: {
                                    Task {
                                        hasLoaded = false
                                        await load()
                                    }
                                })
                            } label: {
```

with

```swift
                            Button {
                                nav.collections = [.collection(collection.id)]
                                nav.select(.collections)
                            } label: {
```

3. Replace

```swift
        NavigationLink {
            CollectionsListView()
        } label: {
```

with

```swift
        Button {
            nav.popToRoot(.collections)
            nav.select(.collections)
        } label: {
```

- [ ] **Step 5: `ProfileView` destinations and scroll to top**

In `ProfileView.swift`:

1. After `@Environment(PathStatsService.self) private var stats`, add `@Environment(TabNavigation.self) private var nav`.
2. Replace

```swift
    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.xl) {
```

with

```swift
    var body: some View {
        ScrollViewReader { proxy in
        ScrollView {
            VStack(spacing: Spacing.xl) {
```

3. Replace

```swift
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .pebblesToolbarTitle("Profile")
```

with

```swift
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
            .id(TabNavigation.scrollTopAnchor)
        }
        .onChange(of: nav.scrollToTop[.you, default: 0]) {
            withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
        }
        }
        .navigationDestination(for: YouRoute.self) { route in
            switch route {
            case .glyphs: GlyphsListView()
            case .achievements: AchievementsView()
            case .lab: LabView()
            }
        }
        .pebblesToolbarTitle("Profile")
```

Reindent the `ScrollViewReader` body.

4. In the preview, after `.environment(PathStatsService(supabase: supabase))`, add `.environment(TabNavigation())`.

- [ ] **Step 6: Build, lint, smoke**

Run **Build** and **Lint**. Simulator, You tab:
- Glyphs, Achievements and Lab push. Reselecting **You** pops them; a second tap scrolls to top.
- The Souls tile selects People at its root. The Circle tile selects People with Connections pushed.
- The collections header selects Collections at root. A collection tile selects Collections with that detail pushed.
- The gear still opens Settings.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile
git commit -m "feat(ios): route the you hub through its own stack and the new tabs"
```

---

### Task C1.14: Path chrome: `NewPebbleFab` + `PathStatusCluster`, reselect focuses this week

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Components/NewPebbleFab.swift`
- Create: `apps/ios/Pebbles/Features/Path/Components/PathStatusCluster.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Components/WeekPathView.swift`

- [ ] **Step 1: `NewPebbleFab.swift`**

```swift
import SwiftUI

/// Path's floating "New pebble" button (#903 §5), trailing on the FAB row.
/// A tap opens Capture. There is no long-press: none existed on iOS, and
/// Android's went with the all-at-once form (§1).
struct NewPebbleFab: View {
    let onTap: () -> Void

    private static let side: CGFloat = 56
    private static let cornerRadius: CGFloat = 16

    var body: some View {
        Button(action: onTap) {
            Image(systemName: "plus")
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.pebbles.onPrimaryContainer)
                .frame(width: Self.side, height: Self.side)
                .background(
                    RoundedRectangle(cornerRadius: Self.cornerRadius, style: .continuous)
                        .fill(Color.pebbles.primaryContainer)
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New pebble")
    }
}

#Preview {
    NewPebbleFab(onTap: {}).padding()
}
```

- [ ] **Step 2: `PathStatusCluster.swift`**

```swift
import SwiftUI

/// Karma + Ripples, leading on Path's FAB row (#903 §5, D4). Two independent
/// buttons (#442's split): #446 swaps `onRipple` for the Ripples explainer
/// sheet, and #444 restyles the badge rendered here.
struct PathStatusCluster: View {
    let karma: Int?
    let ripple: RippleSummary?
    let onKarma: () -> Void
    let onRipple: () -> Void

    var body: some View {
        HStack(spacing: 16) {
            Button(action: onKarma) {
                karmaStat
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Karma \(karma.map(String.init) ?? "—")")
            .accessibilityHint("Opens You")

            Button(action: onRipple) {
                RippleBadge(level: ripple?.rippleLevel ?? 0,
                            activeToday: ripple?.activeToday ?? false)
            }
            .buttonStyle(.plain)
        }
    }

    /// The caption sits under the number, so it stays legible at
    /// accessibility sizes (as `PathBottomBar` did).
    private var karmaStat: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkle")
                .foregroundStyle(Color.pebbles.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text(karma.map { "\($0)" } ?? "—")
                    .font(.ysabeauSemibold(17))
                    .foregroundStyle(Color.pebbles.onSurface)
                Text("karma")
                    .font(.caption)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
    }
}

#Preview {
    PathStatusCluster(
        karma: 42,
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 10, activeToday: true),
        onKarma: {},
        onRipple: {}
    )
    .padding()
}
```

- [ ] **Step 3: Replace Path's bottom inset**

In `PathView.swift`, replace

```swift
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 12) {
                    NewPebbleButton(onTap: { isPresentingCapture = true })
                    PathBottomBar(
                        karma: stats.karma,
                        ripple: rippleWithLocalActiveToday,
                        onProfile: { nav.select(.you) }
                    )
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
```

with

```swift
            // One row, clear background, 8pt above the tab bar (#903 §5). The
            // week list scrolls under it; `WeekPathView`'s bottom padding lets
            // the last pebble clear it.
            .safeAreaInset(edge: .bottom) {
                HStack(alignment: .center, spacing: 16) {
                    PathStatusCluster(
                        karma: stats.karma,
                        ripple: rippleWithLocalActiveToday,
                        onKarma: { nav.select(.you) },
                        onRipple: { nav.select(.you) }
                    )
                    Spacer(minLength: 0)
                    NewPebbleFab(onTap: { isPresentingCapture = true })
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
```

- [ ] **Step 4: Reselect at root focuses the current week**

In `PathView.swift`:

1. In the `WeekPathView(` call, after `onCreate: { isPresentingCapture = true }`, add a comma and a new line `scrollToTopToken: nav.scrollToTop[.path, default: 0]`.
2. After the `.task { await stats.load() }` line, add:

```swift
        .onChange(of: nav.scrollToTop[.path, default: 0]) { focusCurrentWeek() }
```

3. After `focusWeek(containing:)`, add:

```swift
    /// Path reselected at its root (#903 §4): "top of Path" means now. The
    /// same current-week lookup `load()` uses.
    private func focusCurrentWeek() {
        let current = WeekRollBuilder.weekStart(for: today, calendar: isoCalendar)
        guard entries.contains(where: { $0.weekStart == current }) else { return }
        withAnimation { focusedWeekStart = current }
    }
```

- [ ] **Step 5: `WeekPathView` scrolls to top on the token**

In `WeekPathView.swift`:

1. After `let onCreate: () -> Void`, add:

```swift
    /// Bumped by `TabNavigation` when Path is reselected at its root.
    var scrollToTopToken: Int = 0
```

2. Replace the non-empty branch, from `                ScrollView {` through the end of its `.mask(…)`:

```swift
                ScrollView {
                    LazyVStack(spacing: 0) {
```

…

```swift
                        startPoint: .top, endPoint: .bottom
                    )
                )
```

with

```swift
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(entry.moments.enumerated()), id: \.element.id) { index, moment in
                                if index < revealedCount {
                                    PathPebbleRow(
                                        moment: moment,
                                        positionIndex: index,
                                        onTap: { onTap(moment) },
                                        onDelete: { onDelete(moment) }
                                    )
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.vertical, 8)
                                    .padding(.horizontal, 24)
                                    .transition(.opacity.combined(with: .move(edge: .top)))
                                }
                            }
                        }
                        .padding(.bottom, 80)
                        .id(TabNavigation.scrollTopAnchor)
                    }
                    .scrollClipDisabled()
                    .mask(
                        LinearGradient(
                            stops: [
                                .init(color: .black, location: 0.0),
                                .init(color: .black, location: 0.85),
                                .init(color: .clear, location: 1.0),
                            ],
                            startPoint: .top, endPoint: .bottom
                        )
                    )
                    .onChange(of: scrollToTopToken) {
                        withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
                    }
                }
```

If stack A has already rewritten the mask stops on main, keep main's stops. Only the `ScrollViewReader` wrapper, the `.id` and the `.onChange` are this task's. The two `.black` stops are `ThemeLiteralsTests`' allowlisted mask, so their line count must not change.

3. Update the type's doc comment: replace `/// The bottom of the list fades behind the New button via a vertical` with `/// The bottom of the list fades behind the FAB row via a vertical`.

- [ ] **Step 6: Build, tests, lint, smoke**

Run **Build**, the **Suite command** with `ThemeLiteralsTests` (if that suite exists on main), and **Lint**. Simulator, Path:
- One row: karma + badge leading, a 56pt square `+` trailing, 8pt above the bar.
- `+` opens Capture. Karma selects You. The badge selects You.
- Swipe to an older week and tap **Path**: the current week comes back.
- At a large accessibility text size the karma caption stays under the number and the row does not clip.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path
git commit -m "feat(ios): put new pebble, karma and ripples on one row above the tabs"
```

---

### Task C1.15: D5: moments changed on another tab refresh Path

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`

The signal itself is tested in `MomentsRevisionTests` (C1.3). This task wires the bumpers and the reader; device check 7 proves it end to end.

- [ ] **Step 1: `PebbleDetailSheet` knows its tab and bumps**

1. Replace

```swift
    let momentId: UUID
    var onPebbleUpdated: (() -> Void)?
```

with

```swift
    let momentId: UUID
    /// The tab that presented this page, so the cross-tab signal can tell
    /// the presenter's own change from another tab's (#903 D5).
    let origin: AppTab
    var onPebbleUpdated: (() -> Void)?
```

2. After `@Environment(CompleteDraftStore.self) private var completeDrafts`, add `@Environment(MomentsRevision.self) private var revision`.
3. Replace every `onPebbleUpdated?()` in the file with `notifyChanged()` (Edit with `replace_all`). Before that, `grep -c "onPebbleUpdated?()" apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` must print `4` (edit pebble, edit moment, completion, delete).
4. Before `private func delete(`, add:

```swift
    /// Every change made from this page: the presenter's own reload, then the
    /// cross-tab signal so the other tabs catch up (#903 D5).
    private func notifyChanged() {
        onPebbleUpdated?()
        revision.bump(from: origin)
    }
```

5. In the preview, replace `PebbleDetailSheet(momentId: UUID())` with `PebbleDetailSheet(momentId: UUID(), origin: .path)` and add `.environment(MomentsRevision())` to its environment chain.

- [ ] **Step 2: Call sites pass their tab**

- `PathView.swift`: `PebbleDetailSheet(momentId: id, onPebbleUpdated: {` → `PebbleDetailSheet(momentId: id, origin: .path, onPebbleUpdated: {`
- `SoulDetailView.swift`: the same line → `PebbleDetailSheet(momentId: id, origin: .people, onPebbleUpdated: {`
- `CollectionDetailView.swift`: the same line → `PebbleDetailSheet(momentId: id, origin: .collections, onPebbleUpdated: {`

- [ ] **Step 3: The delete-dialog presenters bump**

In each of the three files below, add `@Environment(MomentsRevision.self) private var revision` after `@Environment(CompleteDraftStore.self) private var completeDrafts`. Then replace

```swift
            try await MomentDeletion.perform(
                option, on: target, client: supabase.client, drafts: completeDrafts
            )
```

(unique in each file) with the same block followed by one line:
- `PathView.swift`: `            revision.bump(from: .path)`
- `SoulDetailView.swift`: `            revision.bump(from: .people)`
- `CollectionDetailView.swift`: `            revision.bump(from: .collections)`

Add `.environment(MomentsRevision())` to the two detail views' previews.

- [ ] **Step 4: Capture bumps, Path reads**

In `PathView.swift`:

1. Replace

```swift
            CaptureFlowView(onMomentChanged: { momentId in
```

with

```swift
            CaptureFlowView(onMomentChanged: { momentId in
                revision.bump(from: .path)
```

2. After the `.onChange(of: nav.scrollToTop[.path, default: 0]) { focusCurrentWeek() }` line, add:

```swift
        // #903 D5: a moment changed from People or Collections. Path's own
        // changes already reloaded through their callbacks.
        .onChange(of: revision.value) {
            guard revision.changedOutside(.path) else { return }
            Task { await stats.refresh() }
            reloadTask?.cancel()
            reloadTask = Task { await load() }
        }
```

- [ ] **Step 5: Build, full suite, lint**

Run **Build**, the **Full suite** and **Lint**. Expected: green. A compile error "missing argument for parameter 'origin'" points at a call site Step 2 missed.

- [ ] **Step 6: Smoke**

Simulator: Collections › a collection › a pebble › delete the moment. Switch to **Path**: it is gone without a pull. Repeat from People › soul › pebble › edit: Path shows the edit.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path apps/ios/Pebbles/Features/Profile/Views
git commit -m "feat(ios): refresh path when a moment changes on another tab"
```

---

### Task C1.16: Offscreen work under `TabView` (§11)

**Files (conditional):** `apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift`

- [ ] **Step 1: Read what already stops**

```bash
grep -n "onAppear\|onDisappear" apps/ios/Pebbles/Features/Path/Stone/PebbleStoneView.swift apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift
```

Expected: `PebbleStoneView` starts `StoneMotionSource` in `.onAppear` and stops it in `.onDisappear` (lines 71–78 on 2026-10-21). `TabView` fires `.onDisappear` on a tab switch, so tilt already stops. Nothing to add for CoreMotion.

- [ ] **Step 2: Profile on the device (with the maintainer)**

Instruments › Allocations + Time Profiler on the iPhone 15, Release build. Record a cold launch on Path, then a full tour (People › soul, Collections › collection, You › Glyphs, back to Path). Then do the same on `main`.
- **Blocker:** heap growth beyond one Path-sized heap (compare Path's own footprint on main).
- **Rive check:** with You showing, Time Profiler must show no steady `RiveRuntime` frames.

- [ ] **Step 3 (only if Rive keeps rendering offscreen): pause the cairns**

In `WeekRollCairnCell.swift`, replace `        .onAppear { configureAutoBind() }` with:

```swift
        .onAppear {
            configureAutoBind()
            rvm.play()
        }
        // Under the tab bar the Path tab stays mounted (#903 §11).
        .onDisappear { rvm.pause() }
```

Re-profile and expect no `RiveRuntime` frames on another tab. Then commit:

```bash
git add apps/ios/Pebbles/Features/Path/Components/WeekRollCairnCell.swift
git commit -m "fix(ios): pause the cairn animations while path is offscreen"
```

Record both profiles' numbers for the PR body.

---

### Task C1.17: C1 verification

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Expected: zero SwiftLint errors, all tests pass (including `TabNavigationTests`, `TabNavigationSnapshotTests`, `TabRouteResolutionTests`, `MomentsRevisionTests`, `TabStringsTests`, `LocalizationCatalogFileTests` and `ThemeLiteralsTests` when present), and the build succeeds.

```bash
grep -rn "PathBottomBar(" apps/ios/Pebbles || echo "PathBottomBar not rendered"
```

Expected: `PathBottomBar not rendered`.

- [ ] **Step 2: Device checks 1–10 and 12 (iPhone 15, light and dark; the orchestrator runs these with the maintainer)**

1. Cold launch lands on Path, and the loader hands off without a spinner flash.
2. Push People › Soul, switch to You and back: the soul is still pushed.
3. Reselect People: it pops to root. A second tap scrolls to top.
4. Path reselect at root returns to the current week.
5. Background the app, kill it from Xcode (not the switcher), relaunch: the tab and the stacks are restored.
6. Do the same with People as the restored tab: no stuck loader.
7. Delete a moment from Collections › detail, then switch to Path: it is gone.
8. A karma tap selects You.
9. An invite universal link (`xcrun simctl openurl "$SIM" https://www.pbbls.app/invite/<token>` on the simulator; a Notes link on the device), opened while on any tab, presents the accept sheet.
10. First-run onboarding still covers the tab bar (reset with Settings › Developer, or delete and reinstall).
12. VoiceOver walk of the bar: "Path, tab, 1 of 4" … "You, tab, 4 of 4". The FAB reads "New pebble". Karma reads "Karma N", hint "Opens You".

Record pass/fail per item for the PR body. Any failure is fixed on this branch before the PR is opened.

---

### Task C1.18: Open the C1 PR

**Files:** none.

- [ ] **Step 1: Push and submit**

```bash
git fetch origin && git log --oneline -1 origin/main
gh stack push
gh stack submit --auto
gh stack view --json
```

- [ ] **Step 2: Title, body, labels, milestone**

Write the body to a scratch file outside the repo. It contains:
- `Part of #903`.
- Key files: the file map's C1 rows.
- **New patterns for review:** `MomentsRevision` (D5) and the `UITabBarAppearance` shim (§6). Add the `TabBarReselectShim` if C1.8 ran.
- Deviations: destinations are registered on each tab root, not in `PebblesTabView`, so `onChanged` reloads keep their meaning. The scene-state reset runs in the `isResolvedSignedOut` handler, which also covers a cold launch that resolves signed out. The hub's Souls/Circle/Collections entries switch tabs until C2.
- The reselect result (C1.7), the contrast output (C1.9), the profiles (C1.16), the device-check table (C1.17), and the galet note (C1.5).
- The Arkaik outcome from C1.0.
- No `## Lab Note` section (Arkaik coverage replaces it). No `F-…` ids.

```bash
gh pr edit <C1-number> --title "feat(ios): four tabs with their own stacks" --body-file <scratch-body-file> \
  --add-label feat,ui,core,ios,no-lab-note --milestone "M64 · New Path"
```

---

## Part C2 — `feat/903-ios-hub-handoff`

PR title: `feat(ios): the hub gives souls and collections to their tabs`. Body starts `Closes #903`.

### Task C2.0: Add the branch

- [ ] **Step 1**

```bash
gh stack add feat/903-ios-hub-handoff
git branch --show-current
```

Expected: `feat/903-ios-hub-handoff`.

---

### Task C2.1: Delete `PathBottomBar` and `NewPebbleButton`

**Files:**
- Delete: `apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift`
- Delete: `apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift`
- Modify: `apps/ios/Pebbles/Features/Karma/KarmaEarnedCapsule.swift` (doc comment)
- Modify (conditional): `apps/ios/PebblesTests/ThemeLiteralsTests.swift`

- [ ] **Step 1: Delete**

```bash
git rm apps/ios/Pebbles/Features/Path/Components/PathBottomBar.swift apps/ios/Pebbles/Features/Path/Components/NewPebbleButton.swift
```

- [ ] **Step 2: Fix the remaining mention**

```bash
grep -rn "PathBottomBar\|NewPebbleButton" apps/ios/Pebbles apps/ios/PebblesTests apps/ios/PebblesWidget
```

Expected: only `KarmaEarnedCapsule.swift:7` (or wherever its doc comment now sits). In that line, replace `in \`PathBottomBar\`` with `in \`PathStatusCluster\``. Re-run the grep: expected no output.

- [ ] **Step 3: Ratchet entries for deleted files**

```bash
test -f apps/ios/PebblesTests/ThemeLiteralsTests.swift && grep -n "PathBottomBar\|NewPebbleButton" apps/ios/PebblesTests/ThemeLiteralsTests.swift
```

If it prints an entry, delete that `debt` / `allowlist` line. A deleted file counts 0 hits and would fail "lower the debt".

- [ ] **Step 4: Build, full suite, lint, commit**

Run **Build**, the **Full suite** and **Lint**.

```bash
git add -A apps/ios/Pebbles/Features apps/ios/PebblesTests
git commit -m "feat(ios): delete the path bottom bar and the full-width new button"
```

---

### Task C2.2: The hub gives up Souls, Circle and Collections

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutsRow.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileShortcutTile.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionCard.swift`

- [ ] **Step 1: The tile is a You route only**

Replace the whole of `ProfileShortcutTile.swift` with:

```swift
import SwiftUI

struct ProfileShortcutTile: View {
    let title: LocalizedStringResource
    let systemImage: String
    let route: YouRoute

    var body: some View {
        NavigationLink(value: route) {
            SurfaceTile(systemImage: systemImage) {
                Text(title)
            }
        }
        .buttonStyle(.plain)
    }
}
```

- [ ] **Step 2: The row keeps Glyphs**

Replace the whole of `ProfileShortcutsRow.swift` with:

```swift
import SwiftUI

/// Glyphs. Souls and Circle live under the People tab, Collections under its
/// own tab (#903), so they have no tile here.
struct ProfileShortcutsRow: View {
    var body: some View {
        HStack(spacing: Spacing.sm) {
            ProfileShortcutTile(title: "Glyphs", systemImage: "scribble", route: .glyphs)
        }
    }
}

#Preview {
    NavigationStack {
        ProfileShortcutsRow().padding()
    }
}
```

- [ ] **Step 3: Remove the collections card**

In `ProfileView.swift`, delete the line `                ProfileCollectionsCard()` and the blank line after it.

```bash
git rm apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionsCard.swift apps/ios/Pebbles/Features/Profile/Components/ProfileCollectionCard.swift
grep -rn "ProfileCollectionsCard\|ProfileCollectionCard" apps/ios/Pebbles apps/ios/PebblesTests || echo "gone"
grep -rn "CollectionModeBadge(" apps/ios/Pebbles | grep -v "Components/CollectionModeBadge.swift"
```

Expected: `gone`. The second grep still lists `CollectionsListView.swift` and `CollectionDetailView.swift`, so `CollectionModeBadge` stays.

Check the ratchet the same way as C2.1 Step 3, with `ProfileCollectionsCard\|ProfileCollectionCard`.

- [ ] **Step 4: Any other component left unreferenced?**

```bash
for f in apps/ios/Pebbles/Features/Profile/Components/*.swift; do
  name=$(basename "$f" .swift)
  n=$(grep -rlw "$name" apps/ios/Pebbles | grep -v "$f" | wc -l | tr -d ' ')
  [ "$n" = "0" ] && echo "unreferenced: $name"
done
```

Expected: no output. If a component prints, it lost its last caller in this task: `git rm` it, and name it in the PR body.

- [ ] **Step 5: Build, full suite, lint, smoke, commit**

Run **Build**, the **Full suite** and **Lint**. Simulator, You tab: banner, a single Glyphs tile, stats, achievements, Lab and logout. No souls, circle or collections entry remains. Glyphs pushes.

```bash
git add -A apps/ios/Pebbles/Features/Profile apps/ios/PebblesTests
git commit -m "feat(ios): drop the souls, circle and collections entries from the hub"
```

---

### Task C2.3: Decision-log entry

**Files:**
- Modify: `docs/decisions/log.md` (append only)

- [ ] **Step 1: Append**

Append this at the end of `docs/decisions/log.md`, after one blank line. Replace `<YYYY-MM-DD>` with the day C2 is opened. Edit no earlier entry.

```markdown
## <YYYY-MM-DD> — iOS converges on the four-tab IA (#903)

- **Status:** taken
- **Scope:** ios, ui
- **Context:** The 2026-09-21 entry gave Android a four-tab bar (Path, People, Collections, You) and recorded that "iOS and Android now differ in IA on purpose", leaving the iOS question to #903. iOS kept `PathView` as the authed root with one `NavigationStack`, a Profile hub reached from `PathBottomBar`, and Souls, Collections and Circle as closure links inside that hub. Every new surface had two answers to "where does it live", and #446 / #444 were blocked on a bar Android had already deleted. The maintainer decided #903 as **converge**.
- **Decision:**
  - **D1** The authed root is a system `TabView` with Path, People, Collections and You. The tab roots are the existing views (`PathView`, `SoulsListView`, `CollectionsListView`, `ProfileView`), not new hub screens. People reaches Connections from a toolbar button. The hub's duplicate entries are removed.
  - **D2** One `@Observable` `TabNavigation` (`Features/Tabs/`), created in `RootView`'s authed branch and injected, holds the selection and four typed `Codable` route arrays (ids, never models). It is persisted under `@SceneStorage("tabs.navigation")` with per-field tolerant decoding. `SoulRoute` / `CollectionRoute` resolve ids and pop when they no longer resolve.
  - **D3** Back is per stack. A reselect pops to root; a second reselect scrolls to top. Path's "top" is the current week.
  - **D4** "New pebble" is a Path-only FAB (`NewPebbleFab`), and karma + Ripples stay on Path as `PathStatusCluster` on the same row. `PathBottomBar` and `NewPebbleButton` are deleted. **There is no long-press:** none existed on iOS, and Android's went with the all-at-once form, so "tap opens Capture" is the whole gesture.
  - **D5** `MomentsRevision` (one counter plus its origin tab) tells Path that a moment changed on another tab. Bumpers: `PebbleDetailSheet`, the delete-dialog presenters, Capture.
  - Settings stays a sheet from the You tab (Android pushes it). That is a presentation difference, not an IA one.
- **Why:** The 1:1 rule is about behaviour and contract; the 2026-09-21 break was accepted only as an IA divergence awaiting #903, and converging removes it. Typed arrays rather than `NavigationPath` make restoration assertable and need no opaque blob. One navigator makes reselect, the karma jump to You and the sign-out reset single calls. The two-tap pop-then-scroll is the `UITabBarController` behaviour iOS users already expect. Keeping karma and Ripples beside the FAB costs no height (the row exists for the FAB) and gives #446 / #444 their hook; Android's attempt to move them above the roll was reverted on device.
- **Consequences:**
  - **Cross-tab presentation goes through `RootView` (or the future card-stack host above the tabs), never by setting another tab's state.** A `.sheet` on a non-selected tab is dropped by UIKit.
  - **Every authed `@SceneStorage` key is declared in `RootView` and cleared in `resetAuthedSceneState()`**, in the PR that adds it (stack E's `heldCard` is next). The next account must inherit nothing.
  - A tab root registers its own `navigationDestination`, so a destination can call back into the list that pushed it. Links below a tab root's first push may stay closure-based; they are not restored, and restoration lands on their parent.
  - New patterns accepted: `MomentsRevision`, and a `UITabBarAppearance` shim in `PebblesApp` for the unselected item and the hairline (iOS 17 SwiftUI cannot express them).
  - All four tabs stay mounted once visited. Offscreen work stops on `.onDisappear` (stone tilt already did). C1's Instruments numbers are in its PR.
  - When stack D lands, `PathModel` reads `MomentsRevision`. When E5 deletes `PebbleDetailSheet`, `HeldCardModel` takes over its bumps.
- **Supersedes / Superseded-by:** Supersedes nothing. It answers the "iOS and Android now differ in IA on purpose" consequence of **2026-09-21 — Android gets a four-tab bottom bar, a deliberate break from the 1:1 iOS mirror (#852)**: the two clients share the IA again.
- **Refs:** #903, #446, #444, #852, `docs/superpowers/specs/2026-10-20-ios-tab-navigation-design.md`, `docs/superpowers/plans/2026-10-21-ios-tab-navigation.md`, `apps/ios/Pebbles/Features/Tabs/TabNavigation.swift`, `apps/ios/Pebbles/Features/Tabs/PebblesTabView.swift`, `apps/ios/Pebbles/RootView.swift`.
```

If C1.8 ran, add a fifth bullet under Consequences: "Reselect is detected through a `UITabBarControllerDelegate` proxy (`TabBarReselectShim`), because the `TabView` selection setter did not fire on reselect on iOS <version>." Otherwise add: "Reselect relies on the `TabView(selection:)` setter firing for the current tab, verified on device; if a point release breaks it, the fallback is a delegate proxy found through the hosting controller."

- [ ] **Step 2: Commit**

```bash
git add docs/decisions/log.md
git commit -m "docs(ios): record the four-tab convergence decision"
```

---

### Task C2.4: `apps/ios/CLAUDE.md`

**Files:**
- Modify: `apps/ios/CLAUDE.md`

- [ ] **Step 1: Replace the line**

Replace

```
The authed root is `PathView` with a custom bottom bar (not a system `TabView`); sheets and full-screen covers do most secondary navigation.
```

with

```
The authed root is a system `TabView` (Path / People / Collections / You), each tab owning a `NavigationStack` driven by `TabNavigation` (`Features/Tabs/`); sheets and full-screen covers still do secondary navigation.
```

The preceding sentence ("Features map roughly … Profile (access to Collections, Glyphs, Souls).") is outside the spec's replacement. Leave it, and mention it in the PR body as a follow-up for the next audit pass.

- [ ] **Step 2: Commit**

```bash
git add apps/ios/CLAUDE.md
git commit -m "docs(ios): describe the tab view root in the ios agent context"
```

---

### Task C2.5: Arkaik edges and descriptions

**Files:** none in the repo. `arkaik-mcp` only.

- [ ] **Step 1: Load the tools**

ToolSearch `select:mcp__arkaik-mcp__get_node,mcp__arkaik-mcp__update_node,mcp__arkaik-mcp__add_edge,mcp__arkaik-mcp__remove_edge`. If they do not load, say so, stop this task and note it in the PR body.

- [ ] **Step 2: Check web before removing a Profile edge**

Edges carry no platform. On 2026-10-21 the web profile still linked souls, connections and collections (`apps/web/components/profile/ShortcutsRow.tsx`, `apps/web/components/profile/CollectionsCard.tsx`).

```bash
grep -n "/souls\|/connections" apps/web/components/profile/ShortcutsRow.tsx
grep -n "/collections" apps/web/components/profile/CollectionsCard.tsx
```

- If either grep prints, **keep** that Profile → X edge. Removing it would tell the map that web lost an entry it still has. Record "kept for web" in the PR body as a deviation from spec §10.
- If neither prints, web has converged too, and the edges are removed in Step 3.

- [ ] **Step 3: Edges**

1. `get_node("V-profile")` and note the ids of its outgoing `composes` edges to `V-souls-list`, `V-collections-list` and `V-connections-list` (for example `e-V-profile-V-souls-list`). Remove each one Step 2 cleared with `remove_edge(edge_id)`.
2. Tab-root edges: `get_node("V-home")` and confirm `composes` edges to `V-timeline`, `V-souls-list`, `V-collections-list` and `V-profile` exist (on 2026-10-21 all four did, from Android #852). Add any missing one with `add_edge(source_id: "V-home", target_id: <root>, edge_type: "composes")`.
3. People reaches Connections: `add_edge(source_id: "V-souls-list", target_id: "V-connections-list", edge_type: "composes")`. A duplicate refusal means it already exists. That is fine; note it.
4. Parents of the details: `get_node("V-souls-list")` must compose `V-soul-detail`, and `get_node("V-collections-list")` must compose `V-collection-detail`. Add whichever is missing.

- [ ] **Step 4: Descriptions**

For each node, `get_node`, then `update_node` with `patch.description` = the current description with the sentence below appended. Send only `description`. If metadata must be sent at all, send back the whole object as returned.

- `V-timeline`: "iOS (#903): the Path tab root; a 56pt FAB opens Capture and a status cluster (karma + Ripples) shares its row; the profile entry is gone (the You tab replaces it)."
- `V-profile`: "iOS (#903): the You tab root; Glyphs, Achievements and Lab push in its stack, Settings stays a sheet; the souls, circle and collections entries moved to their tabs."
- `V-souls-list`: "iOS (#903): the People tab root; a Circle toolbar button pushes Connections."
- `V-collections-list`: "iOS (#903): the Collections tab root."
- `V-connections-list`: "iOS (#903): pushed under the People tab, from the souls list's Circle button."
- `V-soul-detail`: "iOS (#903): pushed in the People tab's stack by id, restored across launches."
- `V-collection-detail`: "iOS (#903): pushed in the Collections tab's stack by id, restored across launches."

Optionally, also update `V-home`'s description sentence about the iOS custom bottom bar so it says iOS now uses the same four-tab bar. Send its full current text with that sentence rewritten.

- [ ] **Step 5: No status change**

Do not change any status in C2. The Arkaik GitHub App marks `releasing` at merge, and nothing here sets `live`. Re-`get_node` one node to confirm `ios` still reads `development`.

---

### Task C2.6: C2 verification and PR

**Files:** none.

- [ ] **Step 1: Gates**

Run **Lint**, the **Full suite** and **Build**. Then:

```bash
grep -rn "PathBottomBar" apps/ios/Pebbles apps/ios/PebblesTests apps/ios/PebblesWidget || echo "PathBottomBar gone"
grep -rn "ProfileCollectionsCard\|SoulsListView()\|ConnectionsListView()" apps/ios/Pebbles/Features/Profile || echo "no duplicate hub routes"
```

Expected: `PathBottomBar gone` and `no duplicate hub routes`.

- [ ] **Step 2: Device check 11**

Sign out from You, then sign in as a different account (a test account, never the maintainer's real one without asking): every tab is at its root, Path is selected, and no soul or collection id from the first account is pushed. Kill from Xcode and relaunch: still nothing from the first account.

- [ ] **Step 3: Push and edit the PR**

```bash
gh stack push
gh stack submit --auto
gh stack view --json
```

Body (scratch file): `Closes #903`. Then the key files (the C2 rows of the file map), the deleted components, the decision-log entry and the CLAUDE.md line, the Arkaik outcome (edges removed/kept for web, edges added, descriptions), device check 11, and "#446 and #444 are unblocked: their hook is `PathStatusCluster`". No Lab Note section and no `F-…` ids.

```bash
gh pr edit <C2-number> --title "feat(ios): the hub gives souls and collections to their tabs" --body-file <scratch-body-file> \
  --add-label feat,ui,core,ios,no-lab-note --milestone "M64 · New Path"
```

---

### Task C2.7: The full device checklist (final; the orchestrator runs it with the maintainer)

**Files:** none.

Install the top of the stack (C2) on the iPhone 15. Run every step in **light** and in **dark** appearance. Record pass/fail per step in the C2 PR as a comment. Any failure blocks `gh stack merge`.

- [ ] 1. Cold launch lands on Path, and the loader hands off without a spinner flash.
- [ ] 2. Push People › Soul, switch to You and back: the soul is still pushed.
- [ ] 3. Reselect People: it pops to root. A second tap scrolls to top.
- [ ] 4. Path reselect at root returns to the current week.
- [ ] 5. Background the app, kill it from Xcode (not the switcher), relaunch: the tab and the stacks are restored.
- [ ] 6. Do the same with People as the restored tab: no stuck loader.
- [ ] 7. Delete a moment from Collections › detail, then switch to Path: it is gone.
- [ ] 8. A karma tap selects You.
- [ ] 9. An invite universal link opened while on any tab presents the accept sheet.
- [ ] 10. First-run onboarding still covers the tab bar.
- [ ] 11. Sign out and back in as another account: the tabs reset.
- [ ] 12. VoiceOver walk of the bar ("Path, tab, 1 of 4" … "You, tab, 4 of 4"; FAB "New pebble"; karma "Karma N", hint "Opens You").

When all twelve pass in both appearances, merge the whole stack bottom to top with `gh stack merge --yes` (after a final `gh stack rebase` and green CI, per the gh-stack memory notes).

---

## Self-review (done while writing)

- **Spec coverage:**
  - §2 tab table and items: C1.4, C1.6, C1.11–C1.13.
  - §3 state model, restoration and resolvers: C1.1, C1.2, C1.6, C1.10.
  - §4 back and reselect: C1.1, C1.7/C1.8, C1.11–C1.14.
  - §5 FAB and cluster: C1.14.
  - §6 composition, `holdsLoader`, reset contract and bar styling: C1.6, C1.9.
  - §7 inventory (onboarding cover moved; invite sheet above the tabs; overlay slot marked) and D5: C1.6, C1.15.
  - §8 a11y: C1.4, C1.9, C1.14, C1.17.
  - §9 tests and checklist: C1.1–C1.4, C1.10, C1.17, C2.6, C2.7.
  - §10 parts, PR metadata, galet rider, Arkaik, decision log and CLAUDE.md: C1.0, C1.5, C1.18, C2.1–C2.6.
  - §11 risks: C1.7/C1.8 (reselect), C1.16 (offscreen), C1.2 (stale snapshots), C1.10 (stale ids).
- **Known deviations, flagged for the PR bodies:**
  - Destinations live on the tab roots.
  - The reset runs in the resolved-signed-out handler.
  - The C1 hub tiles switch tabs.
  - Profile edges may be kept for web.
  - Stack E's `heldCard` key does not exist yet; C1 leaves its registration point.
- **Type consistency:** `TabNavigation.select/popToRoot/depth/snapshot/restore/reset/scrollToTop/scrollTopAnchor`, `Snapshot.decoded(from:)/encoded`, `RouteResolution`, `SoulRoute.resolve(_:known:refs:)`, `CollectionRoute.resolution(fetched:)`, `MomentsRevision.bump(from:)/changedOutside(_:)/value/lastOrigin`, `PebbleDetailSheet(momentId:origin:onPebbleUpdated:)`, `ProfileShortcutTile(title:systemImage:target:)` in C1 and `(title:systemImage:route:)` in C2. Every use above matches its definition.
