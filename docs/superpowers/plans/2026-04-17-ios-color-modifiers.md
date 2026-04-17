# iOS color modifiers — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply Pebbles Blush Quartz palette to the iOS app via a single `.pebblesScreen()` modifier applied once per screen. No root-level token override attempts — that strategy was tried and rejected (see spec).

**Architecture:** Asset catalog holds the eight Pebbles colours (light + dark). A `Color+Pebbles.swift` extension surfaces them as typed accessors. A `PebblesScreen.swift` view modifier bundles tint / foreground / hidden-scroll-content-background / background / nav+tab toolbar backgrounds. Each screen calls `.pebblesScreen()` once inside its `NavigationStack`.

**Tech Stack:** SwiftUI iOS 17+, Xcode asset catalog, XcodeGen, SwiftLint.

**Spec:** `docs/superpowers/specs/2026-04-17-ios-color-modifiers-design.md`

**Pre-flight:** The eight colorsets from the prior (discarded) branch are backed up at `/tmp/pebbles-colorsets-backup/` and are reused verbatim. The discarded branch's tip commit (`b0b0ba1`) is reachable via reflog for recovery if ever needed.

---

## File map

**Created**
- `apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`
- `apps/ios/Pebbles/Resources/Assets.xcassets/Background.colorset/Contents.json`
- `apps/ios/Pebbles/Resources/Assets.xcassets/Foreground.colorset/Contents.json`
- `apps/ios/Pebbles/Resources/Assets.xcassets/Surface.colorset/Contents.json`
- `apps/ios/Pebbles/Resources/Assets.xcassets/SurfaceAlt.colorset/Contents.json`
- `apps/ios/Pebbles/Resources/Assets.xcassets/Muted.colorset/Contents.json`
- `apps/ios/Pebbles/Resources/Assets.xcassets/MutedForeground.colorset/Contents.json`
- `apps/ios/Pebbles/Resources/Assets.xcassets/Border.colorset/Contents.json`
- `apps/ios/Pebbles/Theme/Color+Pebbles.swift`
- `apps/ios/Pebbles/Theme/PebblesScreen.swift`

**Modified** (single-line `.pebblesScreen()` insertion — no other behaviour change)
- `apps/ios/Pebbles/Features/Auth/AuthView.swift`
- `apps/ios/Pebbles/Features/Main/MainTabView.swift`
- `apps/ios/Pebbles/Features/Path/PathView.swift`
- `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`
- `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`
- `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift`
- `apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift`
- `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift`

**Not modified**
- `project.yml` — `sources: - path: Pebbles` already globs new files under `Pebbles/`. New files are auto-included after `xcodegen generate`.
- `RootView.swift`, `ConsentCheckbox.swift`, `PebbleFormView.swift`, etc. — they inherit the environment from their parent's `.pebblesScreen()` call. No direct modifier application.

---

## Placement patterns

Three recurring view shapes in this codebase. The `.pebblesScreen()` modifier goes in different places for each:

### Pattern A — `NavigationStack { content.navigationTitle(...) }`

Apply `.pebblesScreen()` on the content **inside** the `NavigationStack`, after any `.navigationTitle` / `.toolbar` modifiers. Example (from `PathView.swift` after change):

```swift
var body: some View {
    NavigationStack {
        content
            .navigationTitle("Path")
            .pebblesScreen()   // ← inside NavigationStack
    }
    // existing .task / .sheet modifiers stay here, unchanged
}
```

Placement inside the `NavigationStack` closure is essential — the `.toolbarBackground(_, for: .navigationBar)` calls inside `.pebblesScreen()` must attach to the stack's nav bar.

### Pattern B — `TabView { ... }` (no NavigationStack)

Apply `.pebblesScreen()` directly on the `TabView` so `.toolbarBackground(_, for: .tabBar)` attaches:

```swift
var body: some View {
    TabView {
        PathView().tabItem { Label("Path", systemImage: "…") }
        ProfileView().tabItem { Label("Profile", systemImage: "person.crop.circle") }
    }
    .pebblesScreen()
}
```

### Pattern C — Plain view (no NavigationStack, no TabView)

Apply `.pebblesScreen()` on the outermost view in the body. The toolbar modifiers inside `.pebblesScreen()` are inert for this case — tint / foreground / background still apply.

```swift
var body: some View {
    VStack {
        // existing content
    }
    .pebblesScreen()
}
```

**Rule of thumb during implementation**: read each file, find the outermost content view (inside the `NavigationStack` if one exists; otherwise the top-level view), and append `.pebblesScreen()` as the last modifier on that content. No other changes.

---

## Task 1: Restore the eight color sets to the asset catalog

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Background.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Foreground.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Surface.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/SurfaceAlt.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Muted.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/MutedForeground.colorset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Border.colorset/Contents.json`

The eight colorsets were computed and verified on the prior branch, and the directories are backed up at `/tmp/pebbles-colorsets-backup/`. Reuse them verbatim rather than recomputing hex values.

- [ ] **Step 1: Verify the backup is intact**

Run:
```
ls /tmp/pebbles-colorsets-backup/
```

Expected: exactly eight entries — `AccentColor.colorset`, `Background.colorset`, `Border.colorset`, `Foreground.colorset`, `Muted.colorset`, `MutedForeground.colorset`, `Surface.colorset`, `SurfaceAlt.colorset`.

If the backup is missing or incomplete, STOP and escalate as `BLOCKED` — hex values should not be recomputed here.

- [ ] **Step 2: Copy the eight colorsets into the asset catalog**

Run:
```
cp -R /tmp/pebbles-colorsets-backup/AccentColor.colorset \
      /tmp/pebbles-colorsets-backup/Background.colorset \
      /tmp/pebbles-colorsets-backup/Foreground.colorset \
      /tmp/pebbles-colorsets-backup/Surface.colorset \
      /tmp/pebbles-colorsets-backup/SurfaceAlt.colorset \
      /tmp/pebbles-colorsets-backup/Muted.colorset \
      /tmp/pebbles-colorsets-backup/MutedForeground.colorset \
      /tmp/pebbles-colorsets-backup/Border.colorset \
      /Users/alexis/code/pbbls/apps/ios/Pebbles/Resources/Assets.xcassets/
```

Verify with:
```
ls /Users/alexis/code/pbbls/apps/ios/Pebbles/Resources/Assets.xcassets/
```

Expected: the eight colorsets plus the existing `AppIcon.appiconset` and `Contents.json`.

- [ ] **Step 3: Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`

Expected output: `Loaded project` / `Created project at Pebbles.xcodeproj` with no errors.

- [ ] **Step 4: Build to confirm the asset catalog compiles**

Run: `npm run build --workspace=@pbbls/ios`

Expected: `** BUILD SUCCEEDED **`. A warning about the empty `AppIcon` asset is expected.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/AccentColor.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/Background.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/Foreground.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/Surface.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/SurfaceAlt.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/Muted.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/MutedForeground.colorset \
        apps/ios/Pebbles/Resources/Assets.xcassets/Border.colorset
git commit -m "$(cat <<'EOF'
feat(ios): add Pebbles color tokens to asset catalog (#270)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add `Color+Pebbles.swift` token extension

**Files:**
- Create: `apps/ios/Pebbles/Theme/Color+Pebbles.swift`

- [ ] **Step 1: Create the `Theme` folder and file**

Write `apps/ios/Pebbles/Theme/Color+Pebbles.swift` with exactly this content:

```swift
import SwiftUI

extension Color {
    static let pebblesBackground      = Color("Background")
    static let pebblesForeground      = Color("Foreground")
    static let pebblesSurface         = Color("Surface")
    static let pebblesSurfaceAlt      = Color("SurfaceAlt")
    static let pebblesMuted           = Color("Muted")
    static let pebblesMutedForeground = Color("MutedForeground")
    static let pebblesBorder          = Color("Border")
    static let pebblesAccent          = Color("AccentColor")
}
```

The `Write` tool will create the `Theme/` directory automatically.

- [ ] **Step 2: Regenerate the Xcode project so it picks up the new source file**

Run: `npm run generate --workspace=@pbbls/ios`

Expected: no errors.

- [ ] **Step 3: Build to confirm compilation**

Run: `npm run build --workspace=@pbbls/ios`

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=@pbbls/ios`

Expected: zero violations across all Swift files.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Theme/Color+Pebbles.swift
git commit -m "$(cat <<'EOF'
feat(ios): add Pebbles color token extension (#270)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add `PebblesScreen.swift` view modifier

**Files:**
- Create: `apps/ios/Pebbles/Theme/PebblesScreen.swift`

- [ ] **Step 1: Create the file**

Write `apps/ios/Pebbles/Theme/PebblesScreen.swift` with exactly this content:

```swift
import SwiftUI

private struct PebblesScreen: ViewModifier {
    func body(content: Content) -> some View {
        content
            .tint(.pebblesAccent)
            .foregroundStyle(.pebblesForeground)
            .scrollContentBackground(.hidden)
            .background(Color.pebblesBackground)
            .toolbarBackground(Color.pebblesBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarBackground(Color.pebblesBackground, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
    }
}

extension View {
    /// Applies the Pebbles design-system styling: tint, foreground, background,
    /// hidden scroll-content background, and nav/tab toolbar backgrounds.
    ///
    /// Apply inside a `NavigationStack` so the toolbar modifiers attach to the
    /// correct bar. Modifiers that don't apply to the current context (e.g.
    /// `.toolbarBackground` when there is no toolbar) are inert.
    func pebblesScreen() -> some View {
        modifier(PebblesScreen())
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`

Expected: no errors.

- [ ] **Step 3: Build**

Run: `npm run build --workspace=@pbbls/ios`

Expected: `** BUILD SUCCEEDED **`. `.pebblesScreen()` is defined but not yet called, so no observable behaviour change.

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=@pbbls/ios`

Expected: zero violations.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Theme/PebblesScreen.swift
git commit -m "$(cat <<'EOF'
feat(ios): add pebblesScreen view modifier (#270)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Apply `.pebblesScreen()` to the four top-level screens

**Files:**
- Modify: `apps/ios/Pebbles/Features/Auth/AuthView.swift`
- Modify: `apps/ios/Pebbles/Features/Main/MainTabView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

Each change is a single-line addition following one of the patterns above. Read each file first, identify the correct placement (Pattern A / B / C from the "Placement patterns" section), then append `.pebblesScreen()`.

- [ ] **Step 1: Modify `AuthView.swift`**

Read the file, find the outermost view in `body`, and append `.pebblesScreen()` as the last modifier on that view.

If `AuthView` has no `NavigationStack`, follow Pattern C: `.pebblesScreen()` goes on the outermost view in `body`. If it does have one, follow Pattern A: `.pebblesScreen()` goes on the content inside the `NavigationStack`, after any `.navigationTitle` / `.toolbar` modifiers.

Make no other changes to this file.

- [ ] **Step 2: Modify `MainTabView.swift`**

`MainTabView` is Pattern B — `TabView` with no `NavigationStack`. Append `.pebblesScreen()` directly on the `TabView`:

```swift
var body: some View {
    TabView {
        PathView()
            .tabItem {
                Label("Path", systemImage: "point.topleft.down.to.point.bottomright.curvepath")
            }

        ProfileView()
            .tabItem {
                Label("Profile", systemImage: "person.crop.circle")
            }
    }
    .pebblesScreen()
}
```

No other changes.

- [ ] **Step 3: Modify `PathView.swift`**

`PathView` is Pattern A — `NavigationStack { content.navigationTitle("Path") }`. Append `.pebblesScreen()` inside the `NavigationStack`, after `.navigationTitle`:

```swift
var body: some View {
    NavigationStack {
        content
            .navigationTitle("Path")
            .pebblesScreen()
    }
    .task { await load() }
    .sheet(isPresented: $isPresentingCreate) { /* … */ }
    .sheet(item: $selectedPebbleId) { id in /* … */ }
    .sheet(item: $presentedDetailPebbleId) { id in /* … */ }
}
```

The `.task` and `.sheet` modifiers outside the `NavigationStack` are untouched. No other changes.

- [ ] **Step 4: Modify `ProfileView.swift`**

Read `apps/ios/Pebbles/Features/Profile/ProfileView.swift` first. It is expected to match Pattern A (`NavigationStack { content.navigationTitle(...) }`), but confirm by reading before editing. Apply `.pebblesScreen()` on the content inside the `NavigationStack`, after any `.navigationTitle` / `.toolbar` modifier.

No other changes.

- [ ] **Step 5: Build**

Run: `npm run build --workspace=@pbbls/ios`

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Lint**

Run: `npm run lint --workspace=@pbbls/ios`

Expected: zero violations.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Auth/AuthView.swift \
        apps/ios/Pebbles/Features/Main/MainTabView.swift \
        apps/ios/Pebbles/Features/Path/PathView.swift \
        apps/ios/Pebbles/Features/Profile/ProfileView.swift
git commit -m "$(cat <<'EOF'
feat(ios): apply pebblesScreen modifier to top-level screens (#270)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Apply `.pebblesScreen()` to list and detail views

**Files:**
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`

These views are pushed into the parent's `NavigationStack` (they don't own one themselves). Apply `.pebblesScreen()` on the outermost content view in each file's `body` — typically the `List` or the view that wraps it.

For each of the five files below: read the file, find the outermost content view in `body`, append `.pebblesScreen()` as the last modifier on it. Make no other changes.

- [ ] **Step 1: Modify `CollectionsListView.swift`**

Read `apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift`. Find the outermost view in `body`. Append `.pebblesScreen()` as the last modifier on it.

- [ ] **Step 2: Modify `GlyphsListView.swift`**

Read `apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift`. Same change as Step 1.

- [ ] **Step 3: Modify `SoulsListView.swift`**

Read `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`. Same change.

- [ ] **Step 4: Modify `CollectionDetailView.swift`**

Read `apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift`. Same change.

- [ ] **Step 5: Modify `SoulDetailView.swift`**

Read `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`. Same change.

- [ ] **Step 6: Build**

Run: `npm run build --workspace=@pbbls/ios`

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 7: Lint**

Run: `npm run lint --workspace=@pbbls/ios`

Expected: zero violations.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Profile/Lists/CollectionsListView.swift \
        apps/ios/Pebbles/Features/Profile/Lists/GlyphsListView.swift \
        apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift \
        apps/ios/Pebbles/Features/Profile/Views/CollectionDetailView.swift \
        apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift
git commit -m "$(cat <<'EOF'
feat(ios): apply pebblesScreen modifier to profile list and detail views (#270)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Apply `.pebblesScreen()` to all sheets

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift`

Each sheet is expected to wrap its content in a `NavigationStack` (Pattern A). Apply `.pebblesScreen()` on the content inside the `NavigationStack`, after any `.navigationTitle` / `.toolbar` / `.toolbarTitleDisplayMode` / `.navigationBarTitleDisplayMode` modifier.

If any file turns out not to use a `NavigationStack` (Pattern C instead), still apply `.pebblesScreen()` as the last modifier on the outermost view — the toolbar parts of the modifier are inert in that case.

For each of the ten files: read first, identify the placement, append `.pebblesScreen()`. No other changes.

- [ ] **Step 1: Modify `CreatePebbleSheet.swift`**

Read `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`. Apply `.pebblesScreen()` per the pattern above.

- [ ] **Step 2: Modify `EditPebbleSheet.swift`**

Read `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`. Same change.

- [ ] **Step 3: Modify `PebbleDetailSheet.swift`**

Read `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`. Same change.

- [ ] **Step 4: Modify `CreateCollectionSheet.swift`**

Read `apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift`. Same change.

- [ ] **Step 5: Modify `EditCollectionSheet.swift`**

Read `apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift`. Same change.

- [ ] **Step 6: Modify `CreateSoulSheet.swift`**

Read `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`. Same change.

- [ ] **Step 7: Modify `EditSoulSheet.swift`**

Read `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`. Same change.

- [ ] **Step 8: Modify `BounceExplainerSheet.swift`**

Read `apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift`. Same change.

- [ ] **Step 9: Modify `KarmaExplainerSheet.swift`**

Read `apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift`. Same change.

- [ ] **Step 10: Modify `LegalDocumentSheet.swift`**

Read `apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift`. Same change.

- [ ] **Step 11: Sanity check coverage with grep**

Run:
```
grep -rn "\.pebblesScreen()" apps/ios/Pebbles/Features/ | wc -l
```

Expected: `19` (4 from Task 4 + 5 from Task 5 + 10 from Task 6). If the number is lower, find the missing file and add the modifier. If higher, investigate duplicates.

- [ ] **Step 12: Check for any `NavigationStack` in Features without `.pebblesScreen()` on it**

Run:
```
grep -rln "NavigationStack" apps/ios/Pebbles/Features/
```

Every file in the result should also contain `.pebblesScreen()`. Cross-check. If any file has `NavigationStack` but not `.pebblesScreen()`, that's drift between plan and reality — escalate or add it.

- [ ] **Step 13: Build**

Run: `npm run build --workspace=@pbbls/ios`

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 14: Lint**

Run: `npm run lint --workspace=@pbbls/ios`

Expected: zero violations.

- [ ] **Step 15: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/CreateCollectionSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/EditCollectionSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/BounceExplainerSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/KarmaExplainerSheet.swift \
        apps/ios/Pebbles/Features/Auth/LegalDocumentSheet.swift
git commit -m "$(cat <<'EOF'
feat(ios): apply pebblesScreen modifier to all sheets (#270)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Clean rebuild, fresh install, visual verification, PR

This task has no Swift code changes — only verification and PR creation.

- [ ] **Step 1: Clean build**

Run:
```
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild clean -scheme Pebbles -destination 'generic/platform=iOS Simulator'
```

Expected: `** CLEAN SUCCEEDED **`.

- [ ] **Step 2: Build for iPhone 17 simulator**

From the repo root:
```
cd /Users/alexis/code/pbbls/apps/ios
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' build
```

Expected: `** BUILD SUCCEEDED **`. Takes a few minutes; let it finish.

- [ ] **Step 3: Force-install the fresh `.app` on the booted simulator**

Find the built `.app` path (it's under `~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build/Products/Debug-iphonesimulator/Pebbles.app`). Then:

```
APP_PATH="$(ls -d ~/Library/Developer/Xcode/DerivedData/Pebbles-*/Build/Products/Debug-iphonesimulator/Pebbles.app | head -1)"
xcrun simctl uninstall booted app.pbbls.ios
xcrun simctl install booted "$APP_PATH"
xcrun simctl launch booted app.pbbls.ios
```

Expected: the app launches on the simulator. Reinstalling is essential — iOS simulator aggressively caches the installed `.app`, so a previous build could shadow the new one and cause misleading visual results.

- [ ] **Step 4: Capture a light-mode screenshot and visually verify**

Ensure the simulator is in light mode (Settings → Developer → Dark Appearance off; or from Simulator menu: Features → Toggle Appearance until light). Then:

```
sleep 2
xcrun simctl io booted screenshot /tmp/pebbles-light.png
```

Open `/tmp/pebbles-light.png`. Verify:

- App background is warm Pebbles `#F8F0F0`, not iOS `.systemGroupedBackground` cold grey.
- Tab bar icons / labels: the selected tab is tinted Pebbles dusty rose `#C07A7A`, not iOS blue. Tab bar background is Pebbles Background, not iOS translucent system grey.
- `PathView`: "Record a pebble" button text / `plus.circle.fill` icon are Pebbles accent, not iOS blue.
- `ProfileView`: the list row chevrons and icons are Pebbles accent, not iOS blue.
- Nav bar (where one exists): background Pebbles, title text Pebbles Foreground.
- On the auth screen: `ConsentCheckbox` checked state shows Pebbles accent, not iOS blue.

If any of these still render as iOS defaults, STOP and investigate — do not claim done. Likely causes: `.pebblesScreen()` missing from a file, or placed outside the `NavigationStack`.

- [ ] **Step 5: Switch to dark mode and capture a dark-mode screenshot**

In the simulator: Features → Toggle Appearance (`⇧⌘A`). Then:

```
sleep 2
xcrun simctl io booted screenshot /tmp/pebbles-dark.png
```

Verify:
- Background is Pebbles dark `#120809`, not iOS `.systemGroupedBackground` dark.
- Accent is Pebbles dark rose `#CE7E8A`, not iOS dark blue.
- Foreground text is Pebbles `#E7DADC`, not pure white.
- Nav and tab bars match Pebbles dark Background.

- [ ] **Step 6: Open the PR**

Follow the PR workflow from `CLAUDE.md`:

- Branch: `feat/270-ios-color-modifiers` (already the working branch).
- Push the branch to origin if not already pushed.
- PR title: `feat(ios): apply Pebbles color system via screen modifier (#270)`.
- PR body starts with `Resolves #270`. Summarise:
  - Adds eight Pebbles colour tokens to the asset catalog.
  - Adds a typed `Color` extension and a single `.pebblesScreen()` view modifier.
  - Applies `.pebblesScreen()` once per screen (19 call sites).
  - Prior attempt (root-level token override) on branch `feat/270-ios-color-system` was discarded — see the "Prior attempt" section of the spec for rationale.
- Attach the two simulator screenshots (`/tmp/pebbles-light.png`, `/tmp/pebbles-dark.png`) to the PR body under a "Screenshots" section.
- Propose inheriting labels (`feat`, `ios`, `ui`) and milestone (`M23 · TestFlight V1`) from issue #270 and ask the user to confirm before applying.
