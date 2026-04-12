# iOS Project Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a buildable, runnable SwiftUI app shell at `apps/ios/` — two-tab TabView (Path, Profile), Supabase SDK wired via environment injection (no network calls), XcodeGen project generation, unit test target, Turborepo workspace integration. Resolves #200.

**Architecture:** XcodeGen owns the project definition in `project.yml`; the `.xcodeproj` is a git-ignored build artifact. Supabase config flows `Secrets.xcconfig` → `Info.plist` → typed `AppEnvironment` enum → `SupabaseService` (injected via SwiftUI environment). Zero product features — only project structure and tooling.

**Tech Stack:** Swift 5.9+, SwiftUI, iOS 17.0, Xcode 15+, XcodeGen, SwiftLint, Swift Testing, supabase-swift SPM package, Turborepo.

**Spec:** `docs/superpowers/specs/2026-04-12-ios-project-bootstrap-design.md`

**TDD note:** This is a scaffolding PR with no feature behavior to test. The "test" for most tasks is `xcodegen generate` succeeding, `xcodebuild` succeeding, or a manual simulator check. Only Task 9 writes a real (trivial) test, following Swift Testing conventions. Classical TDD doesn't apply to project bootstrap — the verification steps replace failing-test-first.

---

## Prerequisites check (do before starting)

- [ ] **Verify Xcode 15+ is installed**

```bash
xcodebuild -version
```

Expected: `Xcode 15.x` or higher. If missing, install from the Mac App Store.

- [ ] **Verify XcodeGen is installed**

```bash
which xcodegen && xcodegen --version
```

Expected: path printed, version printed. If missing:

```bash
brew install xcodegen
```

- [ ] **Verify SwiftLint is installed**

```bash
which swiftlint && swiftlint version
```

Expected: path + version. If missing:

```bash
brew install swiftlint
```

- [ ] **Confirm Supabase credentials are available**

You need the Supabase project URL and anon key from the existing Pebbles Supabase project. Confirm you can access them (probably in `packages/supabase/.env.local` or in the Supabase dashboard). You'll paste them into a local-only file in Task 3.

---

### Task 1: Create feature branch

**Files:** none (git operation)

- [ ] **Step 1: Verify working tree is clean relative to `main`**

```bash
cd /Users/alexis/code/pbbls
git status
```

Expected: on `main`, no uncommitted changes except possibly `.claude/launch.json` (which is acceptable). If there are other uncommitted changes, stash or commit them first.

- [ ] **Step 2: Pull latest main**

```bash
git fetch origin main && git checkout main && git pull --ff-only origin main
```

Expected: already up to date or fast-forwarded.

- [ ] **Step 3: Create feature branch**

```bash
git checkout -b feat/200-initialize-xcode-project
```

Expected: `Switched to a new branch 'feat/200-initialize-xcode-project'`.

---

### Task 2: Remove placeholder docs and set up directory skeleton

The current `apps/ios/` contains only placeholder `CLAUDE.md`, `README.md`, and `package.json`. We'll replace all three and add the real project structure.

**Files:**
- Create: `apps/ios/.gitignore`
- Create: `apps/ios/.swiftlint.yml`
- Create: `apps/ios/Pebbles/` (directory)
- Create: `apps/ios/Pebbles/Features/Path/` (directory)
- Create: `apps/ios/Pebbles/Features/Profile/` (directory)
- Create: `apps/ios/Pebbles/Services/` (directory)
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AppIcon.appiconset/` (directory)
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json`
- Create: `apps/ios/PebblesTests/` (directory)
- Create: `apps/ios/Config/` (directory)

- [ ] **Step 1: Create `apps/ios/.gitignore`**

```
# XcodeGen output
*.xcodeproj
*.xcworkspace

# Build artifacts
build/
DerivedData/
*.xcuserstate
xcuserdata/

# SwiftPM
.swiftpm/
.build/
Package.resolved

# Secrets
Config/Secrets.xcconfig
```

- [ ] **Step 2: Create `apps/ios/.swiftlint.yml` as an empty file**

The file exists so SwiftLint runs from this directory with default rules. Contents: a single comment line.

```
# Use SwiftLint defaults for v1. Tune rules when friction appears.
```

- [ ] **Step 3: Create the directory skeleton**

```bash
cd /Users/alexis/code/pbbls/apps/ios
mkdir -p Pebbles/Features/Path Pebbles/Features/Profile Pebbles/Services Pebbles/Resources/Assets.xcassets/AppIcon.appiconset PebblesTests Config
```

- [ ] **Step 4: Create the asset catalog root `Contents.json`**

Path: `apps/ios/Pebbles/Resources/Assets.xcassets/Contents.json`

```json
{
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 5: Create the empty `AppIcon.appiconset/Contents.json`**

Path: `apps/ios/Pebbles/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json`

```json
{
  "images" : [
    {
      "idiom" : "universal",
      "platform" : "ios",
      "size" : "1024x1024"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

This declares an iOS app icon slot with no image file — Xcode will emit a warning at build time, accepted for v1.

- [ ] **Step 6: Verify the tree**

```bash
cd /Users/alexis/code/pbbls/apps/ios && find . -type f -not -path '*/\.turbo/*' | sort
```

Expected output:
```
./.gitignore
./.swiftlint.yml
./CLAUDE.md
./Pebbles/Resources/Assets.xcassets/AppIcon.appiconset/Contents.json
./Pebbles/Resources/Assets.xcassets/Contents.json
./README.md
./package.json
```

(Empty directories `Features/Path`, `Features/Profile`, `Services`, `PebblesTests`, `Config` will not appear in `find -type f` but should exist.)

- [ ] **Step 7: Commit scaffolding**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/.gitignore apps/ios/.swiftlint.yml apps/ios/Pebbles/Resources/Assets.xcassets
git commit -m "chore(ios): add gitignore, swiftlint config, and asset catalog skeleton"
```

---

### Task 3: Create xcconfig files for Supabase config

**Files:**
- Create: `apps/ios/Config/Secrets.example.xcconfig` (committed)
- Create: `apps/ios/Config/Secrets.xcconfig` (git-ignored, local only)

- [ ] **Step 1: Create `apps/ios/Config/Secrets.example.xcconfig`**

Exact contents:

```
// Copy this file to Secrets.xcconfig and fill in real values.
// Secrets.xcconfig is git-ignored.
//
// The $() escape below is required — xcconfig parses // as a comment,
// so the URL scheme separator has to be broken up.

SUPABASE_URL = https:/$()/your-project.supabase.co
SUPABASE_ANON_KEY =
```

- [ ] **Step 2: Create `apps/ios/Config/Secrets.xcconfig` with real values**

```
SUPABASE_URL = https:/$()/YOUR_PROJECT_REF.supabase.co
SUPABASE_ANON_KEY = YOUR_ACTUAL_ANON_KEY
```

Replace `YOUR_PROJECT_REF` and `YOUR_ACTUAL_ANON_KEY` with the real values from the existing Pebbles Supabase project. These come from the same source the web app uses (check `packages/supabase/.env.local` or the Supabase dashboard → Project Settings → API).

- [ ] **Step 3: Verify the real file will not be tracked**

```bash
cd /Users/alexis/code/pbbls
git check-ignore -v apps/ios/Config/Secrets.xcconfig
```

Expected: prints a line referencing `apps/ios/.gitignore:18:Config/Secrets.xcconfig` (or similar, depending on gitignore rule position). If it prints nothing, `.gitignore` is misconfigured — fix before continuing.

- [ ] **Step 4: Commit only the example file**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Config/Secrets.example.xcconfig
git commit -m "chore(ios): add xcconfig template for Supabase credentials"
```

Verify `git status` shows `Secrets.xcconfig` is **not** staged and **not** listed as untracked.

---

### Task 4: Write `project.yml` (XcodeGen configuration)

**Files:**
- Create: `apps/ios/project.yml`

- [ ] **Step 1: Create `apps/ios/project.yml`**

Exact contents:

```yaml
name: Pebbles
options:
  bundleIdPrefix: app.pbbls
  deploymentTarget:
    iOS: "17.0"
  createIntermediateGroups: true
  generateEmptyDirectories: true

configs:
  Debug: debug
  Release: release

settings:
  base:
    DEVELOPMENT_TEAM: 256Z7G8WLM
    SWIFT_VERSION: "5.9"
    IPHONEOS_DEPLOYMENT_TARGET: "17.0"
    TARGETED_DEVICE_FAMILY: "1" # 1 = iPhone only
    ENABLE_USER_SCRIPT_SANDBOXING: YES
  configs:
    Debug:
      SWIFT_ACTIVE_COMPILATION_CONDITIONS: DEBUG
      SWIFT_OPTIMIZATION_LEVEL: "-Onone"
    Release:
      SWIFT_OPTIMIZATION_LEVEL: "-O"

packages:
  Supabase:
    url: https://github.com/supabase/supabase-swift
    from: "2.0.0"

targets:
  Pebbles:
    type: application
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      - path: Pebbles
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: app.pbbls.ios
        PRODUCT_NAME: Pebbles
        INFOPLIST_FILE: Pebbles/Resources/Info.plist
        CODE_SIGN_ENTITLEMENTS: Pebbles/Pebbles.entitlements
        CODE_SIGN_STYLE: Automatic
        ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon
        ENABLE_PREVIEWS: YES
      configs:
        Debug:
          baseConfig: Config/Secrets.xcconfig
        Release:
          baseConfig: Config/Secrets.xcconfig
    dependencies:
      - package: Supabase
        product: Supabase

  PebblesTests:
    type: bundle.unit-test
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      - path: PebblesTests
    settings:
      base:
        PRODUCT_BUNDLE_IDENTIFIER: app.pbbls.ios.tests
        GENERATE_INFOPLIST_FILE: YES
    dependencies:
      - target: Pebbles

schemes:
  Pebbles:
    build:
      targets:
        Pebbles: all
        PebblesTests: [test]
    run:
      config: Debug
    test:
      config: Debug
      targets:
        - PebblesTests
    profile:
      config: Release
    analyze:
      config: Debug
    archive:
      config: Release
```

Notes on choices:
- `TARGETED_DEVICE_FAMILY: "1"` is iPhone only. "1,2" would be iPhone+iPad.
- `from: "2.0.0"` uses semver "up to next major". Supabase Swift SDK 2.x is current stable.
- `baseConfig` on each config applies the xcconfig. Same file for Debug and Release because we agreed on one Supabase project.
- `GENERATE_INFOPLIST_FILE: YES` on the test target avoids needing a second Info.plist.
- `CODE_SIGN_STYLE: Automatic` lets Xcode manage provisioning profiles for the dev team.
- `ENABLE_USER_SCRIPT_SANDBOXING: YES` is the Xcode 15 default; keep it.

- [ ] **Step 2: Do not run xcodegen yet**

We need `Info.plist`, `.entitlements`, and all Swift source files in place first, otherwise XcodeGen will complain about missing sources. Proceed to Task 5.

- [ ] **Step 3: Commit the project.yml**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/project.yml
git commit -m "feat(ios): add XcodeGen project.yml for Pebbles app"
```

---

### Task 5: Write `Info.plist` and `Pebbles.entitlements`

**Files:**
- Create: `apps/ios/Pebbles/Resources/Info.plist`
- Create: `apps/ios/Pebbles/Pebbles.entitlements`

- [ ] **Step 1: Create `apps/ios/Pebbles/Resources/Info.plist`**

Exact contents:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key>
    <string>$(DEVELOPMENT_LANGUAGE)</string>
    <key>CFBundleExecutable</key>
    <string>$(EXECUTABLE_NAME)</string>
    <key>CFBundleIdentifier</key>
    <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
    <key>CFBundleInfoDictionaryVersion</key>
    <string>6.0</string>
    <key>CFBundleName</key>
    <string>$(PRODUCT_NAME)</string>
    <key>CFBundleDisplayName</key>
    <string>Pebbles</string>
    <key>CFBundlePackageType</key>
    <string>$(PRODUCT_BUNDLE_PACKAGE_TYPE)</string>
    <key>CFBundleShortVersionString</key>
    <string>0.1.0</string>
    <key>CFBundleVersion</key>
    <string>1</string>
    <key>LSRequiresIPhoneOS</key>
    <true/>
    <key>UILaunchScreen</key>
    <dict/>
    <key>UISupportedInterfaceOrientations</key>
    <array>
        <string>UIInterfaceOrientationPortrait</string>
    </array>
    <key>SupabaseURL</key>
    <string>$(SUPABASE_URL)</string>
    <key>SupabaseAnonKey</key>
    <string>$(SUPABASE_ANON_KEY)</string>
</dict>
</plist>
```

The last two keys pull from the xcconfig at build time and will be readable at runtime via `Bundle.main.object(forInfoDictionaryKey:)`.

- [ ] **Step 2: Create `apps/ios/Pebbles/Pebbles.entitlements`**

Exact contents:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.developer.applesignin</key>
    <array>
        <string>Default</string>
    </array>
</dict>
</plist>
```

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Resources/Info.plist apps/ios/Pebbles/Pebbles.entitlements
git commit -m "feat(ios): add Info.plist and Sign in with Apple entitlement"
```

---

### Task 6: Write services — `AppEnvironment.swift` and `SupabaseService.swift`

**Files:**
- Create: `apps/ios/Pebbles/Services/AppEnvironment.swift`
- Create: `apps/ios/Pebbles/Services/SupabaseService.swift`

- [ ] **Step 1: Create `AppEnvironment.swift`**

Exact contents:

```swift
import Foundation

/// Typed access to build-time configuration values injected via
/// `Config/Secrets.xcconfig` → `Info.plist`. Fails loud and early if
/// a value is missing so setup bugs don't become runtime mysteries.
enum AppEnvironment {
    static let supabaseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
              !raw.isEmpty,
              let url = URL(string: raw) else {
            fatalError("SupabaseURL missing or invalid in Info.plist. Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?")
        }
        return url
    }()

    static let supabaseAnonKey: String = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String,
              !key.isEmpty else {
            fatalError("SupabaseAnonKey missing in Info.plist. Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?")
        }
        return key
    }()
}
```

- [ ] **Step 2: Create `SupabaseService.swift`**

Exact contents:

```swift
import Foundation
import Supabase
import Observation

/// Wraps the Supabase client and exposes it via the SwiftUI environment.
/// Views pull this out with `@Environment(SupabaseService.self)`.
///
/// The client initializer performs no network I/O, so creating this
/// during app launch is safe on the main thread.
@Observable
final class SupabaseService {
    let client: SupabaseClient

    init() {
        self.client = SupabaseClient(
            supabaseURL: AppEnvironment.supabaseURL,
            supabaseKey: AppEnvironment.supabaseAnonKey
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Services/
git commit -m "feat(ios): add AppEnvironment and SupabaseService"
```

---

### Task 7: Write app entry point and root view

**Files:**
- Create: `apps/ios/Pebbles/PebblesApp.swift`
- Create: `apps/ios/Pebbles/RootView.swift`

- [ ] **Step 1: Create `PebblesApp.swift`**

Exact contents:

```swift
import SwiftUI

@main
struct PebblesApp: App {
    @State private var supabase = SupabaseService()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
        }
    }
}
```

- [ ] **Step 2: Create `RootView.swift`**

Exact contents:

```swift
import SwiftUI

struct RootView: View {
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
    }
}

#Preview {
    RootView()
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/RootView.swift
git commit -m "feat(ios): add app entry point and RootView with two-tab TabView"
```

---

### Task 8: Write placeholder feature views

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/PathView.swift`
- Create: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

- [ ] **Step 1: Create `PathView.swift`**

Exact contents:

```swift
import SwiftUI

struct PathView: View {
    var body: some View {
        NavigationStack {
            Text("Path")
                .foregroundStyle(.secondary)
                .navigationTitle("Path")
        }
    }
}

#Preview {
    PathView()
}
```

- [ ] **Step 2: Create `ProfileView.swift`**

Exact contents:

```swift
import SwiftUI

struct ProfileView: View {
    var body: some View {
        NavigationStack {
            Text("Profile")
                .foregroundStyle(.secondary)
                .navigationTitle("Profile")
        }
    }
}

#Preview {
    ProfileView()
}
```

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/
git commit -m "feat(ios): add PathView and ProfileView placeholders"
```

---

### Task 9: Write the unit test target smoke test

**Files:**
- Create: `apps/ios/PebblesTests/PebblesTests.swift`

- [ ] **Step 1: Create `PebblesTests.swift`**

Exact contents:

```swift
import Testing
@testable import Pebbles

@Suite("Pebbles smoke tests")
struct PebblesSmokeTests {
    @Test("Test target compiles and runs")
    func smokeTest() {
        #expect(1 + 1 == 2)
    }
}
```

This is intentionally trivial. Its only purpose is to prove the test target is correctly wired (sources, signing, scheme test-enabled) so the next PR can add real tests without fighting the project structure.

- [ ] **Step 2: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/
git commit -m "test(ios): add smoke test to prove unit test target is wired"
```

---

### Task 10: Update `package.json` with real scripts

**Files:**
- Modify: `apps/ios/package.json`

- [ ] **Step 1: Replace `apps/ios/package.json` entirely**

Exact new contents:

```json
{
  "name": "@pbbls/ios",
  "version": "0.0.0",
  "private": true,
  "scripts": {
    "generate": "xcodegen generate",
    "build": "xcodegen generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build",
    "lint": "swiftlint",
    "test": "xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15'"
  }
}
```

Notes:
- `generate` is the convenience script for regenerating the project after editing `project.yml`.
- `build` regenerates then builds (safe against stale `.xcodeproj`).
- `test` regenerates then runs the unit tests. Not in the original spec but natural to add — Turbo will cache it.
- `lint` runs SwiftLint in the workspace directory; it picks up `.swiftlint.yml`.

- [ ] **Step 2: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/package.json
git commit -m "chore(ios): wire XcodeGen/xcodebuild/swiftlint scripts into workspace"
```

---

### Task 11: Replace placeholder README and CLAUDE.md

**Files:**
- Modify: `apps/ios/README.md`
- Modify: `apps/ios/CLAUDE.md`

- [ ] **Step 1: Replace `apps/ios/README.md` entirely**

Exact new contents:

````markdown
# @pbbls/ios

Native iOS app for Pebbles. SwiftUI, iOS 17+, iPhone-only.

## Prerequisites

- macOS with Xcode 15 or later
- iOS 17 simulator runtime (Xcode → Settings → Platforms)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen) and [SwiftLint](https://github.com/realm/SwiftLint):
  ```bash
  brew install xcodegen swiftlint
  ```

## First-time setup

1. Copy the config template and fill in real Supabase values:
   ```bash
   cp Config/Secrets.example.xcconfig Config/Secrets.xcconfig
   ```
   Edit `Config/Secrets.xcconfig` and set `SUPABASE_URL` and `SUPABASE_ANON_KEY`. Get them from the Pebbles Supabase dashboard or from another developer via a secure channel. **Never commit this file.**

2. Generate the Xcode project:
   ```bash
   npm run generate --workspace=@pbbls/ios
   ```
   This produces `Pebbles.xcodeproj` — a build artifact, git-ignored.

3. Open `Pebbles.xcodeproj` in Xcode.

4. Select an iOS 17 iPhone simulator and hit ⌘R.

You should see a two-tab app: Path and Profile.

## Project structure

```
apps/ios/
  project.yml              XcodeGen source of truth — edit this, not .xcodeproj
  Pebbles/
    PebblesApp.swift       @main entry
    RootView.swift         Top-level TabView
    Features/              Feature folders (placeholder views for now)
    Services/              SupabaseService, AppEnvironment
    Resources/             Info.plist, Assets.xcassets
    Pebbles.entitlements   Sign in with Apple
  PebblesTests/            Unit tests (Swift Testing)
  Config/
    Secrets.example.xcconfig  Committed template
    Secrets.xcconfig          Git-ignored, local only
```

## Workflow

- Edit `project.yml`, then run `npm run generate` (or `xcodegen generate`) before opening Xcode. Never hand-edit `.xcodeproj`.
- `npm run build --workspace=@pbbls/ios` — regenerate + build from the command line.
- `npm run test --workspace=@pbbls/ios` — run unit tests in the simulator.
- `npm run lint --workspace=@pbbls/ios` — SwiftLint.

## Troubleshooting

**App crashes on launch with "SupabaseURL missing or invalid":** you haven't copied `Secrets.example.xcconfig` → `Secrets.xcconfig` or the values are blank. Fix the file and hit ⌘R again. Xcode does not need to be restarted.

**Xcode says "Failed to resolve dependency supabase-swift":** `File → Packages → Reset Package Caches`, then `npm run generate`.

**xcodebuild errors with "Unable to find a destination matching iOS Simulator":** install the iOS 17 simulator runtime via `Xcode → Settings → Platforms`.

**First device build fails with a provisioning error about Sign in with Apple:** the capability must be enabled on the `app.pbbls.ios` App ID in your Apple Developer account. Simulator builds are not affected.

## Status

V1 shell. Two empty tabs, Supabase SDK wired but not used. Features will be added in subsequent PRs.
````

- [ ] **Step 2: Replace `apps/ios/CLAUDE.md` entirely**

Exact new contents:

````markdown
# @pbbls/ios — agent context

Native iOS app for Pebbles. SwiftUI, iOS 17+, iPhone-only.

## Source of truth

- **`project.yml` is the Xcode project source of truth.** `.xcodeproj` is a git-ignored build artifact. Never hand-edit the pbxproj. After editing `project.yml`, run `xcodegen generate` (or `npm run generate --workspace=@pbbls/ios`).
- **Supabase config flows xcconfig → Info.plist → `AppEnvironment`.** Never hardcode keys in Swift. Never read `Bundle.main.infoDictionary` from arbitrary code — go through `AppEnvironment`.

## Swift conventions

- **iOS 17 APIs only.** No backports, no `if #available` guards. The deployment target is 17.0.
- **Use `@Observable` not `ObservableObject`.** It's the iOS 17 replacement and works with `@Environment(Type.self)` injection.
- **Use Swift Testing not XCTest.** `@Suite`, `@Test`, `#expect`. New code goes in `PebblesTests/` using the `Testing` module.
- **No UI tests for now.** Add a `PebblesUITests` target in a dedicated PR when smoke tests are actually needed.
- **Use `os.Logger` not `print`.** When async operations land, every error path logs — mirror the web-side discipline that silent failures are bugs.
- **Use `.task { ... }` for view-scoped async work.** It cancels automatically when the view disappears. Don't kick off `Task { ... }` from `.onAppear` without a reason.

## Data layer

- Views never construct `SupabaseClient` themselves. They pull `SupabaseService` from the environment: `@Environment(SupabaseService.self) private var supabase`.
- When a test needs to fake Supabase, extract a `SupabaseServicing` protocol at that moment — not before. YAGNI.

## Error handling

- `AppEnvironment` crashes with `fatalError` if config is missing. That's a setup bug, not a runtime condition.
- Runtime async failures must be surfaced: either logged with `os.Logger` or reflected in view state. No empty catch blocks.

## Folder layout

```
Pebbles/
  PebblesApp.swift          @main entry
  RootView.swift            Top-level TabView
  Features/<Feature>/       One folder per feature; matches web apps/web/components/<feature>/
  Services/                 Non-view code (Supabase, environment, future repositories)
  Resources/                Info.plist, Assets.xcassets, entitlements-adjacent files
```

Features map roughly to the web app's navigation structure: Path (home/timeline), Profile (access to Collections, Glyphs, Souls).

## What's scaffolded but not used yet

- `SupabaseService` is created and injected; no view calls it yet.
- `Pebbles.entitlements` declares Sign in with Apple; no sign-in UI yet.
- Asset catalog has an empty `AppIcon` slot — Xcode warns at build time. Expected.
````

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/README.md apps/ios/CLAUDE.md
git commit -m "docs(ios): replace placeholder README and CLAUDE.md with real setup docs"
```

---

### Task 12: Generate the Xcode project for the first time

**Files:** none directly modified; this step creates `apps/ios/Pebbles.xcodeproj/` (git-ignored).

- [ ] **Step 1: Run XcodeGen**

```bash
cd /Users/alexis/code/pbbls/apps/ios
xcodegen generate
```

Expected stdout ends with `Generated project successfully.` or similar. No errors.

- [ ] **Step 2: Verify the project was created**

```bash
ls apps/ios/Pebbles.xcodeproj/ 2>&1
```

From the repo root. Expected: directory listing including `project.pbxproj`, `xcshareddata/`.

- [ ] **Step 3: Verify git does not see the project as tracked**

```bash
cd /Users/alexis/code/pbbls
git status
```

Expected: `apps/ios/Pebbles.xcodeproj` should **not** appear (either as staged, modified, or untracked). If it does, the `.gitignore` rule is wrong — fix before continuing.

- [ ] **Step 4: If Step 1 failed**

Common XcodeGen errors and fixes:
- "Target 'Pebbles' has sources, but the file does not exist" → check all Swift source files from Tasks 5–9 exist at the exact paths specified in Task 2.
- "Package 'Supabase' not found" → verify the `packages:` block in `project.yml` has correct URL and version.
- YAML parse errors → check indentation in `project.yml`. YAML is indent-sensitive; use two spaces consistently.

Do not proceed to Task 13 until `xcodegen generate` succeeds and `git status` is clean.

---

### Task 13: Build and run in the simulator (manual verification)

**Files:** none — this is acceptance testing.

- [ ] **Step 1: Open the project in Xcode**

```bash
open apps/ios/Pebbles.xcodeproj
```

- [ ] **Step 2: Wait for Swift Package Manager to resolve Supabase**

Xcode top bar shows "Resolving Package Dependencies". First-time resolution can take 30–90 seconds. Wait for it to finish. If it fails, see README troubleshooting.

- [ ] **Step 3: Select an iOS 17 iPhone simulator**

In the Xcode toolbar, click the destination dropdown next to the Pebbles scheme. Pick "iPhone 15" (or any iOS 17 iPhone simulator). If none are available, install the iOS 17 runtime via Xcode → Settings → Platforms.

- [ ] **Step 4: Build and run**

Press `⌘R` (or Product → Run).

Expected in the simulator within 10–30 seconds:
- App launches.
- TabView visible at the bottom with two tabs: **Path** (selected) and **Profile**.
- The Path screen shows the large navigation title "Path" at the top and faint "Path" text in the body.
- Tapping Profile switches tabs; title changes to "Profile".
- Tapping Path switches back.
- No crashes.

Expected build warnings (acceptable):
- `AppIcon` asset is missing image assignments.
- Possibly: "Update to recommended project settings" — dismiss, do not apply.

Unexpected:
- **Crash with `SupabaseURL missing`** → `Config/Secrets.xcconfig` wasn't found or has blank values. Verify the file and regenerate.
- **Build error about Sign in with Apple provisioning** → on simulator, this should not happen. If it does, check that `CODE_SIGN_STYLE: Automatic` is in `project.yml` and your Xcode is signed into an Apple ID via Xcode → Settings → Accounts.

- [ ] **Step 5: Force-quit and relaunch (verify clean relaunch)**

In the simulator, stop the app with `⌘.` in Xcode, then run again with `⌘R`. Expected: same clean launch.

---

### Task 14: Run the unit test suite

**Files:** none.

- [ ] **Step 1: Run tests in Xcode**

With the project open, press `⌘U`.

Expected: test navigator shows one passing test — `PebblesSmokeTests.smokeTest`. Xcode toolbar shows "Test Succeeded".

- [ ] **Step 2: Run tests from the command line**

```bash
cd /Users/alexis/code/pbbls
npm run test --workspace=@pbbls/ios
```

Expected: `xcodebuild test` output ending with `** TEST SUCCEEDED **` and `Test Suite 'All tests' passed`. Exit code 0.

If the destination name "iPhone 15" is not available, edit `apps/ios/package.json` to use an available device (e.g. `iPhone 14`, `iPhone 16`) and re-run. This change should then be committed as part of Task 10's work — amend the earlier commit is fine, or add a small fix commit.

---

### Task 15: Failure-mode verification

Proves the actionable error messages in `AppEnvironment` actually work.

**Files:** none permanently modified.

- [ ] **Step 1: Temporarily rename `Secrets.xcconfig`**

```bash
cd /Users/alexis/code/pbbls/apps/ios
mv Config/Secrets.xcconfig Config/Secrets.xcconfig.bak
```

- [ ] **Step 2: Regenerate and run**

```bash
xcodegen generate
```

Then in Xcode, hit `⌘R`.

Expected: The app crashes on launch. In the Xcode debug console, you should see the message:

```
SupabaseURL missing or invalid in Info.plist. Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?
```

If instead you see no crash, or a different crash message, the error handling in `AppEnvironment.swift` is wrong — fix it before continuing.

- [ ] **Step 3: Restore the file**

```bash
cd /Users/alexis/code/pbbls/apps/ios
mv Config/Secrets.xcconfig.bak Config/Secrets.xcconfig
xcodegen generate
```

- [ ] **Step 4: Re-run in Xcode to confirm normal launch**

Hit `⌘R`. Expected: app launches normally, two tabs visible.

No commit from this task — nothing permanently changed.

---

### Task 16: Update Arkaik bundle

Per `CLAUDE.md`, adding iOS app surface + two new screens is a product architecture change and must be reflected in `docs/arkaik/bundle.json`.

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Invoke the arkaik skill**

Use the `arkaik` skill (loaded via Skill tool) to guide the surgical update. The skill explains the schema and includes a validation script.

- [ ] **Step 2: Add nodes for the iOS surface**

At minimum:
- An "iOS app" platform/surface node (if one doesn't exist — check first).
- A `PathView` screen node under the iOS surface, status "placeholder".
- A `ProfileView` screen node under the iOS surface, status "placeholder".

Do **not** duplicate the web's Path/Profile nodes — these are iOS-specific screens. Link them to the same conceptual features if the schema supports cross-platform feature grouping; otherwise leave unlinked for now.

- [ ] **Step 3: Run the arkaik validation script**

Per the skill's instructions. Expected: validation passes.

- [ ] **Step 4: Commit**

```bash
cd /Users/alexis/code/pbbls
git add docs/arkaik/bundle.json
git commit -m "docs(ios): add iOS app surface and placeholder screens to Arkaik bundle"
```

---

### Task 17: Final verification and PR

**Files:** none (verification + PR creation).

- [ ] **Step 1: Run the full workspace build from the root**

```bash
cd /Users/alexis/code/pbbls
npm run build --workspace=@pbbls/ios
```

Expected: exits 0 with a successful `xcodebuild` output.

- [ ] **Step 2: Run the workspace lint**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: SwiftLint runs and reports zero errors. Warnings are acceptable for v1.

- [ ] **Step 3: Run the workspace tests**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 4: Verify `git status` is clean**

```bash
git status
```

Expected: working tree clean. No untracked `.xcodeproj`, `Package.resolved`, `DerivedData/`, or `Secrets.xcconfig`. If any of these are showing, add them to `.gitignore` (but they should already be covered by Task 2).

- [ ] **Step 5: Push the branch**

```bash
git push -u origin feat/200-initialize-xcode-project
```

- [ ] **Step 6: Open the PR**

```bash
gh pr create --title "feat(ios): initialize Xcode project with SwiftUI shell" --body "$(cat <<'EOF'
Resolves #200.

## Summary

Initializes the iOS app at `apps/ios/` as a buildable SwiftUI shell with two tabs (Path, Profile) and the Supabase Swift SDK wired up via environment injection. No product features — just project structure and tooling.

## Key decisions

- **XcodeGen** owns the project definition (`project.yml`); `.xcodeproj` is a git-ignored build artifact.
- **iOS 17.0**, iPhone only, bundle id `app.pbbls.ios`, team `256Z7G8WLM`.
- **One Supabase project** for Debug + Release (can split later without code changes).
- **Config flow:** `Config/Secrets.xcconfig` → `Info.plist` → typed `AppEnvironment` enum → `SupabaseService`.
- **Swift Testing** for the unit test target (one trivial smoke test to prove wiring).
- **Sign in with Apple** entitlement declared, not yet exercised.

## Files changed

- `apps/ios/project.yml` — XcodeGen config
- `apps/ios/Pebbles/PebblesApp.swift`, `RootView.swift` — app entry, TabView
- `apps/ios/Pebbles/Features/Path/PathView.swift`, `Features/Profile/ProfileView.swift` — placeholder screens
- `apps/ios/Pebbles/Services/AppEnvironment.swift`, `SupabaseService.swift` — config + client
- `apps/ios/Pebbles/Resources/Info.plist`, `Pebbles.entitlements`, asset catalog
- `apps/ios/Config/Secrets.example.xcconfig` — committed template
- `apps/ios/PebblesTests/PebblesTests.swift` — smoke test
- `apps/ios/package.json`, `.gitignore`, `.swiftlint.yml` — tooling
- `apps/ios/README.md`, `CLAUDE.md` — replaced placeholders
- `docs/arkaik/bundle.json` — iOS surface + placeholder screens

## Test plan

- [ ] Clone fresh, copy `Secrets.example.xcconfig` → `Secrets.xcconfig`, fill values.
- [ ] `npm run generate --workspace=@pbbls/ios` succeeds.
- [ ] Open `Pebbles.xcodeproj` in Xcode 15+, select iOS 17 iPhone simulator, ⌘R.
- [ ] App launches to two-tab TabView (Path, Profile). Tapping between tabs works. No crash.
- [ ] ⌘U runs the smoke test; it passes.
- [ ] `npm run build --workspace=@pbbls/ios` succeeds from the command line.
- [ ] `npm run lint --workspace=@pbbls/ios` reports zero errors.
- [ ] Temporarily rename `Secrets.xcconfig`; relaunch app; verify actionable crash message in console.

## Follow-up work

- App icon design
- Launch screen design
- First real feature (likely auth via Sign in with Apple, wiring into the existing Supabase `SupabaseService`)
- Dev/prod Supabase split (if/when pre-launch testing demands it)
EOF
)"
```

- [ ] **Step 7: Apply labels and milestone**

Per project workflow, confirm with the user before applying. Proposed:
- **Species label:** `feat` (inherited from issue #200)
- **Scope label:** confirm with user — likely `facility` (this is infra/bootstrap) or a new `ios` scope if one is created
- **Milestone:** `M17 · iOS project bootstrap` (inherited from issue)

```bash
gh pr edit <PR_NUMBER> --add-label feat --add-label facility --milestone "M17 · iOS project bootstrap"
```

Return the PR URL.

---

## Self-review checklist (for plan author)

Before handing off for execution:

- [ ] Every spec section has a corresponding task.
- [ ] No "TBD", "TODO", "implement later", or vague "add error handling" notes.
- [ ] Every code block is complete and paste-ready.
- [ ] File paths are exact and consistent across tasks.
- [ ] Swift type names are consistent: `SupabaseService`, `AppEnvironment`, `PathView`, `ProfileView`, `RootView`, `PebblesApp`, `PebblesSmokeTests`.
- [ ] `project.yml` references files that are created in earlier tasks (Info.plist, entitlements, sources, xcconfig).
- [ ] No task depends on output from a later task.
