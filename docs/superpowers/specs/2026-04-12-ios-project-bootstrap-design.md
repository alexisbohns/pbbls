# iOS Project Bootstrap — Design

**Issue:** #200 · [Feat] Initialize Xcode project in monorepo
**Milestone:** M17 · iOS project bootstrap
**Date:** 2026-04-12
**Author:** brainstormed with Claude (mentorship mode — first SwiftUI project for the author)

## Goal

Deliver a buildable, runnable SwiftUI app shell at `apps/ios/` that launches in an iOS 17 simulator, shows a two-tab `TabView` (Path, Profile) with empty placeholder screens, has the Supabase Swift SDK wired up as an injected environment service (no network calls yet), and integrates with the Turborepo workspace. Zero product features.

This is the "hello world to TestFlight" moment described in the issue. The value is the project structure and tooling decisions — not any feature.

## Out of scope

Authentication UI. Network calls. Real data. Design system. Custom fonts. App icon artwork. Launch screen artwork. CI. Multi-environment split (dev/staging/prod). UI tests. Onboarding. Any port of features from the web app.

## Key decisions

| Decision | Choice | Rationale |
|---|---|---|
| Project generator | **XcodeGen** | `.xcodeproj` becomes a regeneratable artifact; no pbxproj merge conflicts; low magic. |
| Bundle id | `app.pbbls.ios` | Matches owner-provided Apple Developer setup. |
| Team id | `256Z7G8WLM` | Owner-provided. |
| Display name | `Pebbles` | |
| Deployment target | iOS 17.0 | Modern SwiftUI + `@Observable` without backports; ~95%+ device coverage. |
| Device family | iPhone only | Mirrors the mobile-first web PWA. Universal can be flipped later. |
| Supabase SDK scope | Declared + configured client injected via environment, **no network calls** | Proves the plumbing; doesn't introduce a loading state or error handling for behavior that doesn't exist yet. |
| Supabase environments | **One Supabase project** for Debug + Release | Simplest. Safe to promote to dev/prod split later by adding a second xcconfig — no code changes. |
| Config storage | `Config/Secrets.xcconfig` → `Info.plist` → typed `AppEnvironment` enum | Idiomatic iOS pattern. Secrets file git-ignored; `.example` committed. |
| Tab bar | Two tabs: **Path**, **Profile** | Matches current web IA (no tab bar on web; Record at top of Path; Collections/Glyphs/Souls accessed from Profile). |
| Test targets | **Unit tests only**, Swift Testing framework | Adding a test target later is annoying; creating it upfront is cheap. UI tests disproportionate for a shell. |
| Linting | **SwiftLint** with default rules (empty `.swiftlint.yml`) | Catch style issues early. Skip SwiftFormat for v1. |
| Sign in with Apple | Entitlement declared, not exercised | Account-level capability already enabled; declaring now means the next PR doesn't touch provisioning. |

## Architecture

### Project generation

XcodeGen owns the project definition via `apps/ios/project.yml`. The `.xcodeproj` bundle is a build artifact (git-ignored). To open the project, run `npm run generate --workspace=@pbbls/ios` (or `xcodegen generate`) and then open `Pebbles.xcodeproj`. This makes the `project.pbxproj` disposable and removes merge-conflict risk — a common source of pain in team iOS projects.

### Build configurations

Debug + Release, both pointing at the same Supabase project via `Config/Secrets.xcconfig`. The xcconfig values are injected into `Info.plist` at build time as `SupabaseURL` and `SupabaseAnonKey`, and read at runtime via a typed `AppEnvironment` enum.

### Code architecture

SwiftUI idioms, deliberately minimal:

- `PebblesApp.swift` — `@main` `App` entry. Creates a `SupabaseService` and injects it into the SwiftUI environment.
- `RootView.swift` — top-level view with the two-tab `TabView`.
- `Features/Path/PathView.swift`, `Features/Profile/ProfileView.swift` — placeholder views, each wrapping a `NavigationStack` with a navigation title.
- `Services/SupabaseService.swift` — `@Observable final class` wrapping a `SupabaseClient`.
- `Services/AppEnvironment.swift` — static enum reading config from `Info.plist` with crash-on-missing semantics.

No repository/use-case abstractions. No protocol extraction for `SupabaseService` yet. YAGNI — both will be added the moment the first consumer needs them.

## File-by-file breakdown

### `apps/ios/project.yml`

Declares the project: name `Pebbles`, deployment target iOS 17.0, iPhone-only, bundle id `app.pbbls.ios`, team `256Z7G8WLM`, display name `Pebbles`.

Two targets:
- `Pebbles` (iOS app) — sources `Pebbles/`, resources `Pebbles/Resources/`, entitlements `Pebbles/Pebbles.entitlements`, config files `Config/Secrets.xcconfig` applied to both Debug and Release configurations.
- `PebblesTests` (unit test bundle) — sources `PebblesTests/`, depends on `Pebbles`, uses Swift Testing.

One package dependency: `supabase-swift` from `https://github.com/supabase/supabase-swift`, pinned to the latest stable minor at implementation time. The `Pebbles` target links the `Supabase` product from that package.

### `apps/ios/Pebbles/PebblesApp.swift`

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

### `apps/ios/Pebbles/RootView.swift`

```swift
import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            PathView()
                .tabItem { Label("Path", systemImage: "point.topleft.down.to.point.bottomright.curvepath") }
            ProfileView()
                .tabItem { Label("Profile", systemImage: "person.crop.circle") }
        }
    }
}
```

### `apps/ios/Pebbles/Features/Path/PathView.swift`

```swift
import SwiftUI

struct PathView: View {
    var body: some View {
        NavigationStack {
            Text("Path")
                .navigationTitle("Path")
        }
    }
}
```

### `apps/ios/Pebbles/Features/Profile/ProfileView.swift`

Same shape as `PathView` with title "Profile".

### `apps/ios/Pebbles/Services/AppEnvironment.swift`

```swift
import Foundation

enum AppEnvironment {
    static let supabaseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
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

Fails loud and early if config is missing. Error messages are actionable. Consistent with the project's web-side discipline: no silent failures.

### `apps/ios/Pebbles/Services/SupabaseService.swift`

```swift
import Foundation
import Supabase
import Observation

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

`@Observable` is the iOS 17 macro that replaces `ObservableObject`. `final class` because SwiftUI environment objects must be reference types. The client is `let` because it is never swapped at runtime. The `SupabaseClient` initializer does **no network I/O** — safe to call synchronously during app launch.

### `apps/ios/Pebbles/Pebbles.entitlements`

Property-list XML declaring `com.apple.developer.applesignin = [Default]`. Nothing else.

### `apps/ios/Pebbles/Resources/Assets.xcassets/`

Empty asset catalog with an empty `AppIcon` slot. Xcode will emit a build warning about the missing icon; that warning is accepted for v1.

### `apps/ios/Pebbles/Resources/Info.plist`

Minimal plist with key entries:

- `CFBundleDisplayName = Pebbles`
- `CFBundleIdentifier = $(PRODUCT_BUNDLE_IDENTIFIER)`
- `SupabaseURL = $(SUPABASE_URL)` (injected from xcconfig)
- `SupabaseAnonKey = $(SUPABASE_ANON_KEY)` (injected from xcconfig)
- `UILaunchScreen = {}` (empty dict → SwiftUI default blank launch screen)
- `UISupportedInterfaceOrientations` = portrait only

### `apps/ios/Config/Secrets.example.xcconfig` (committed)

```
// Copy this file to Secrets.xcconfig and fill in real values.
// Secrets.xcconfig is git-ignored.

SUPABASE_URL = https:/$()/your-project.supabase.co
SUPABASE_ANON_KEY =
```

The `$()` escape is required because xcconfig parses `//` as a comment.

### `apps/ios/Config/Secrets.xcconfig` (git-ignored, created locally)

Real values. Never committed.

### `apps/ios/PebblesTests/PebblesTests.swift`

```swift
import Testing
@testable import Pebbles

@Suite("Pebbles smoke tests")
struct PebblesTests {
    @Test("Test target compiles and runs")
    func smokeTest() {
        #expect(1 + 1 == 2)
    }
}
```

Trivial passing test. Proves the target is wired correctly end-to-end without depending on feature code.

### `apps/ios/.gitignore`

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

`Package.resolved` is git-ignored because XcodeGen regenerates the project anyway and Supabase's versioning is stable enough for v1. Can be flipped to committed later if reproducibility becomes a concern.

### `apps/ios/.swiftlint.yml`

Empty file — use SwiftLint's built-in rules as-is. Tunable later.

### `apps/ios/package.json`

```json
{
  "name": "@pbbls/ios",
  "version": "0.0.0",
  "private": true,
  "scripts": {
    "generate": "xcodegen generate",
    "build": "xcodegen generate && xcodebuild -scheme Pebbles -destination 'generic/platform=iOS Simulator' build",
    "lint": "swiftlint"
  }
}
```

Turbo caches these the same way as any other workspace. On a non-macOS runner the scripts fail at the `xcodebuild`/`swiftlint` step — accepted, documented.

### `apps/ios/README.md`

Replaces the existing placeholder. Covers:
- Prerequisites: Xcode 15+, `brew install xcodegen swiftlint`.
- First-time setup: copy `Secrets.example.xcconfig` → `Secrets.xcconfig`, fill values, `npm run generate`, open `Pebbles.xcodeproj`.
- Build & run: select iOS 17 simulator, ⌘R.
- How the project is structured (XcodeGen source of truth, features/services/config layout).
- Troubleshooting: SPM resolution issues, missing iOS 17 simulator runtime, Sign in with Apple provisioning on device builds.

### `apps/ios/CLAUDE.md`

Replaces existing placeholder. Documents:
- XcodeGen is the source of truth. Never hand-edit `.xcodeproj`. Regenerate after editing `project.yml`.
- Supabase config: xcconfig → Info.plist → `AppEnvironment`. Never hardcode.
- Use `@Observable` (iOS 17) not `ObservableObject`.
- Use Swift Testing (`@Test`, `#expect`) not XCTest.
- No UI tests for now.
- Future logging: use `os.Logger`, not `print`.
- No silent failures — mirror the web-side discipline. Async errors must be logged or surfaced.

## Data flow

The shell does almost nothing at runtime. Explicit flow:

1. iOS loads the binary; reads `Info.plist`.
2. `PebblesApp.init()` runs. `@State private var supabase = SupabaseService()` evaluates.
3. `SupabaseService.init()` reads `AppEnvironment.supabaseURL` and `AppEnvironment.supabaseAnonKey`. Lazy statics; first access triggers the `Bundle.main.object(forInfoDictionaryKey:)` reads. Missing or malformed → `fatalError` with actionable message.
4. `SupabaseClient` is constructed. No network I/O in the initializer. Safe on main thread during launch.
5. `WindowGroup` creates `RootView` and attaches the service via `.environment(supabase)`.
6. `RootView.body` evaluates, producing a `TabView` with two tabs. SwiftUI renders the first tab (`PathView` inside a `NavigationStack`).
7. User sees the tab bar with "Path" selected and the "Path" navigation title. Nothing in the body.

Runtime behavior after launch: tapping tabs switches views. No state, no network, no persistence, no background work. Backgrounding/foregrounding has no effect.

Future PRs pull the service from the environment:

```swift
struct SomeFutureView: View {
    @Environment(SupabaseService.self) private var supabase
    // ...
}
```

This is the iOS equivalent of the web `DataProvider` hook pattern — views never construct the client themselves.

## Error handling & edge cases

| Failure | Mode | Mitigation |
|---|---|---|
| `Secrets.xcconfig` not copied or keys blank | `fatalError` on first launch | Actionable message names the fix. |
| Malformed `SUPABASE_URL` in xcconfig | `fatalError` (same branch) | Same message. No repair attempts. |
| SPM can't resolve `supabase-swift` | Build-time error in Xcode | README troubleshooting: reset package caches, regenerate. |
| XcodeGen not installed | `npm run build` fails `command not found` | README lists `brew install xcodegen` as step 1. No auto-install. |
| `xcodebuild` missing (Linux / no Xcode) | Script fails | Documented: "iOS builds require macOS + Xcode." No iOS CI yet. |
| iOS 17 simulator runtime missing | `xcodebuild` destination error | README points at `Xcode → Settings → Platforms`. |
| Sign in with Apple entitlement misconfigured on App ID | Device build provisioning error | README gotcha note. Simulator builds unaffected. |

No logging framework in v1 — there is nothing to log. When the first async operation lands, the expected pattern is `os.Logger`. Documented in `CLAUDE.md`.

Intentionally **not** handled because they cannot happen yet: network timeouts, auth state transitions, background refresh, deep links, push permission prompts.

## Testing

### In this PR

1. **Unit test target compiles and runs.** The trivial `#expect(1 + 1 == 2)` smoke test passes via ⌘U in Xcode and via `xcodebuild test` on the command line. Purpose: prove the target is correctly wired (sources, signing, scheme test-enabled) so the next PR can add real tests without yak-shaving the project structure.
2. **No tests of `SupabaseService` or `AppEnvironment`.** Both read from `Bundle.main.infoDictionary`, which in the test bundle context does not contain the app's custom keys. Testing would require protocol extraction — premature abstraction for zero behavior. Deferred until the first real consumer.

### Future testing approach (documented in `CLAUDE.md`, not implemented here)

- Pure functions → Swift Testing with `@Test` + `#expect`, no mocking.
- Views with Supabase → inject a protocol-based `SupabaseServicing` via `.environment(...)` in tests; swap in a fake. The protocol will be extracted the moment the first test needs it, not before.

### Manual acceptance checklist

**Setup (new clone).**
- [ ] `npm install` at repo root completes.
- [ ] `cd apps/ios && cp Config/Secrets.example.xcconfig Config/Secrets.xcconfig`, fill values.
- [ ] `npm run generate --workspace=@pbbls/ios` produces `apps/ios/Pebbles.xcodeproj`.
- [ ] Opening `Pebbles.xcodeproj` in Xcode shows the full file tree with no red (missing) files.

**Build.**
- [ ] `npm run build --workspace=@pbbls/ios` succeeds on macOS.
- [ ] Selecting an iOS 17 iPhone simulator and hitting ⌘R launches the app.
- [ ] Only expected build warning is the missing `AppIcon` asset.
- [ ] `npm run lint --workspace=@pbbls/ios` reports zero errors.

**Runtime in simulator.**
- [ ] App launches to a `TabView` with two tabs: "Path" (selected) and "Profile".
- [ ] "Path" screen shows navigation title "Path" and nothing else.
- [ ] Tapping "Profile" switches tabs and shows title "Profile".
- [ ] Tapping back to "Path" restores it.
- [ ] No crashes or fatal errors.
- [ ] Force-quit and relaunch works.

**Failure-mode verification (proves error messages work).**
- [ ] Temporarily rename `Config/Secrets.xcconfig`, regenerate, launch → app crashes with the actionable message in the Xcode console.
- [ ] Restore file; normal launch resumes.

**Test target.**
- [ ] ⌘U in Xcode runs the smoke test; it passes.
- [ ] `xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15'` exits 0.

**Repo hygiene.**
- [ ] `git status` after a successful build shows no tracked changes for `.xcodeproj`, `Package.resolved`, `DerivedData/`, or `Secrets.xcconfig`.
- [ ] `docs/arkaik/bundle.json` updated with iOS app nodes per the `arkaik` skill.
- [ ] `apps/ios/CLAUDE.md` and `apps/ios/README.md` replace existing placeholders.

## PR metadata

- **Branch:** `feat/200-initialize-xcode-project`
- **PR title:** `feat(ios): initialize Xcode project with SwiftUI shell`
- **PR body:** starts with `Resolves #200`, lists key files, includes implementation notes.
- **Labels:** propose inheriting issue labels; confirm at PR time.
- **Milestone:** M17 · iOS project bootstrap.
