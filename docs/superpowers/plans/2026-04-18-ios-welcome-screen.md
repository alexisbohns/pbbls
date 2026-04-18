# iOS Welcome Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a pre-login welcome screen (persistent logo header + auto-advancing 3-step carousel + two CTAs) that lands unauth users on a prettier surface than `AuthView` and routes "Create an account" / "Log in" to the right `AuthView` tab.

**Architecture:** New `Features/Welcome/` folder mirrors the shape of `Features/Onboarding/` (shipped in #282). `RootView`'s unauth branch wraps `WelcomeView` in a `NavigationStack`; the two footer buttons push `AuthView(initialMode: .signup|.login)` onto the stack. No changes to the post-signin onboarding flow — it still runs after successful auth.

**Tech Stack:** SwiftUI, iOS 17, Swift Testing, `@AppStorage`, `NavigationStack`, `UIPageControl.appearance()` for dot tinting, `@Environment(\.accessibilityReduceMotion)` to honor motion preferences.

**Branch:** `feat/281-ios-welcome-screen` (already created from `main`; the approved spec is already committed on this branch as `9bf43fa`).

**Spec:** `docs/superpowers/specs/2026-04-18-ios-welcome-screen-design.md`

---

## File Structure

### New files

| Path | Responsibility |
|---|---|
| `apps/ios/Pebbles/Features/Welcome/WelcomeStep.swift` | `struct WelcomeStep { id, title, description }` — pure value type |
| `apps/ios/Pebbles/Features/Welcome/WelcomeSteps.swift` | `enum WelcomeSteps { static let all }` — 3-entry config from issue #281 |
| `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift` | Leaf view — renders one step (title + description) |
| `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` | Root — persistent logo header + paged carousel + two-CTA footer + auto-advance |
| `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/Contents.json` | Image set manifest (preserves vector) |
| `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/welcome-logo.svg` | Placeholder SVG — swapped for final artwork later |
| `apps/ios/PebblesTests/Features/Welcome/WelcomeStepsTests.swift` | Swift Testing invariants on `WelcomeSteps.all` |

### Modified files

| Path | Change |
|---|---|
| `apps/ios/Pebbles/Features/Auth/AuthView.swift` | Add `init(initialMode: Mode = .login)`; default preserves existing call sites |
| `apps/ios/Pebbles/RootView.swift` | Wrap unauth branch in `NavigationStack { WelcomeView(...).navigationDestination(...) }`; add local `AuthRoute` enum and `authPath` state |
| `docs/arkaik/bundle.json` | Update existing `V-landing` node to `status: development` with iOS-aware description; add navigation edges from `V-landing` to `V-login` / `V-register` on iOS |

### Not modified (verified during exploration)

- `apps/ios/project.yml` — uses `sources: - path: Pebbles` glob; new files in `Features/Welcome/` and `Assets.xcassets/WelcomeLogo.imageset/` are picked up automatically. No edit needed.
- `OnboardingView`, `OnboardingPageView`, `OnboardingStep`, `OnboardingSteps`, `OnboardingImage`, or the `hasSeenOnboarding` gate — the post-signin flow is fully untouched.

---

## Baseline check (before Task 1)

Run once to confirm the working tree matches what this plan assumes. Not a step that changes code.

- [ ] **Verify branch and clean tree**

```bash
git branch --show-current
# Expected: feat/281-ios-welcome-screen

git log -1 --oneline
# Expected: <sha> docs(ios): spec for welcome screen (#281)

git status --short
# Expected: empty (aside from untracked .claude/scheduled_tasks.lock, which is a session artifact, not part of the repo)
```

---

## Task 1: Add `WelcomeStep` data type

**Files:**
- Create: `apps/ios/Pebbles/Features/Welcome/WelcomeStep.swift`

No test for this task — `WelcomeStep` is a pure value type with no behavior. Tests come in Task 2 against `WelcomeSteps.all`.

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Features/Welcome/WelcomeStep.swift
import Foundation

/// Single welcome-carousel slide's content. Intentionally has no image
/// field — the logo is rendered persistently in the header of
/// `WelcomeView`, not per slide.
struct WelcomeStep: Identifiable {
    let id: String
    let title: String
    let description: String
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/WelcomeStep.swift
git commit -m "feat(ios): add WelcomeStep data type

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Add `WelcomeSteps.all` config with failing tests first (TDD)

**Files:**
- Create: `apps/ios/PebblesTests/Features/Welcome/WelcomeStepsTests.swift`
- Create: `apps/ios/Pebbles/Features/Welcome/WelcomeSteps.swift`

- [ ] **Step 1: Write the failing test file**

```swift
// apps/ios/PebblesTests/Features/Welcome/WelcomeStepsTests.swift
import Foundation
import Testing
@testable import Pebbles

@Suite("WelcomeSteps")
struct WelcomeStepsTests {

    @Test("contains exactly 3 steps")
    func stepCount() {
        #expect(WelcomeSteps.all.count == 3)
    }

    @Test("step IDs are unique")
    func uniqueIds() {
        let ids = WelcomeSteps.all.map(\.id)
        #expect(Set(ids).count == ids.count)
    }

    @Test("every step has a non-empty title")
    func titlesNonEmpty() {
        for step in WelcomeSteps.all {
            #expect(!step.title.isEmpty)
        }
    }

    @Test("every step has a non-empty description")
    func descriptionsNonEmpty() {
        for step in WelcomeSteps.all {
            #expect(!step.description.isEmpty)
        }
    }

    @Test("step IDs match the spec order")
    func idsMatchSpecOrder() {
        let ids = WelcomeSteps.all.map(\.id)
        #expect(ids == ["record", "enrich", "grow"])
    }
}
```

- [ ] **Step 2: Run tests — expect compile failure**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: BUILD FAILED with an error like `cannot find 'WelcomeSteps' in scope` at each test reference. This confirms the test harness sees the missing symbol.

- [ ] **Step 3: Create the config file**

```swift
// apps/ios/Pebbles/Features/Welcome/WelcomeSteps.swift
import Foundation

/// The three slides shown on the pre-login welcome carousel. Editing copy
/// or reordering steps is a single-file change — `WelcomeView` reads
/// `.all` opaquely, and `WelcomeStepsTests` enforces count, unique ids,
/// and the expected id order.
enum WelcomeSteps {
    static let all: [WelcomeStep] = [
        .init(
            id: "record",
            title: "Record in seconds.",
            description: "Capture moments as they happen — no blank page, no pressure."
        ),
        .init(
            id: "enrich",
            title: "Enrich with meaning.",
            description: "Add emotions, people, and reflections to each pebble."
        ),
        .init(
            id: "grow",
            title: "Grow your path.",
            description: "Look back at your journey, at your own pace."
        )
    ]
}
```

- [ ] **Step 4: Run tests — expect pass**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass. The new `WelcomeSteps` suite reports 5 tests passing; the existing `OnboardingSteps` suite and all other suites remain green. Total test count increases by 5.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/WelcomeSteps.swift \
        apps/ios/PebblesTests/Features/Welcome/WelcomeStepsTests.swift
git commit -m "feat(ios): add WelcomeSteps config with invariant tests

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Ship the `WelcomeLogo` placeholder image set

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/Contents.json`
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/welcome-logo.svg`

Single combined logo+wordmark SVG. Placeholder ships today — final artwork swaps the SVG file later with zero Swift changes. `"preserves-vector-representation": true` ensures SwiftUI scales it crisply at any size, and `.renderingMode(.template)` in `WelcomeView` (Task 5) lets it tint via `foregroundStyle`.

- [ ] **Step 1: Create the image set directory**

```bash
mkdir -p apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset
```

- [ ] **Step 2: Write the `Contents.json`**

```json
{
  "images" : [
    {
      "filename" : "welcome-logo.svg",
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  },
  "properties" : {
    "preserves-vector-representation" : true
  }
}
```

Path: `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/Contents.json`

- [ ] **Step 3: Write the placeholder SVG**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 240" role="img" aria-label="pebbles">
  <ellipse cx="100" cy="100" rx="60" ry="64" fill="none" stroke="currentColor" stroke-width="3"/>
  <text x="100" y="210" text-anchor="middle" font-family="serif" font-size="32" fill="currentColor">pebbles</text>
</svg>
```

Path: `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/welcome-logo.svg`

`currentColor` defers color to `foregroundStyle` applied in SwiftUI. When the final artwork lands, drop the new SVG at this exact path and update `Contents.json`'s `filename` if the filename differs.

- [ ] **Step 4: Build to confirm the asset catalog compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`. If Xcode reports a vector-image warning, double-check that `"preserves-vector-representation"` is under `properties`, not `info`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset
git commit -m "feat(ios): add WelcomeLogo placeholder image set

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Create `WelcomeSlideView`

**Files:**
- Create: `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift`

Leaf view. One step → left-aligned title + description. No background, no illustration (the logo is in `WelcomeView`'s header). Horizontal padding lives here so the slide is self-contained and the parent's `TabView` doesn't need to know about it.

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift
import SwiftUI

/// Renders a single `WelcomeStep` as left-aligned title + description.
/// The welcome carousel has no per-slide illustration — the Pebbles logo
/// in `WelcomeView`'s persistent header plays that role — so this view
/// stays intentionally minimal.
struct WelcomeSlideView: View {
    let step: WelcomeStep

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(step.title)
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.pebblesForeground)

            Text(step.description)
                .font(.body)
                .foregroundStyle(Color.pebblesMutedForeground)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 24)
    }
}

#Preview("Short") {
    WelcomeSlideView(step: WelcomeSteps.all[0])
}

#Preview("Longer") {
    WelcomeSlideView(step: WelcomeSteps.all[1])
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift
git commit -m "feat(ios): add WelcomeSlideView leaf

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Create `WelcomeView` shell (no auto-advance yet)

**Files:**
- Create: `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift`

Ship the structure — header + paged carousel + footer + CTA callbacks — without the auto-advance loop. This keeps the commit focused on layout, and Task 6 layers in the timer behavior on a working baseline.

- [ ] **Step 1: Create the file**

```swift
// apps/ios/Pebbles/Features/Welcome/WelcomeView.swift
import SwiftUI
import UIKit

/// Pre-login landing. Persistent logo header, paged carousel of
/// `WelcomeSteps.all`, and two stacked CTAs that route into `AuthView`
/// with the correct mode. Auto-advance is added in a follow-up.
///
/// Navigation is owned by the parent: this view invokes `onCreateAccount`
/// and `onLogin` closures so `RootView` can drive the `NavigationPath`.
struct WelcomeView: View {
    let onCreateAccount: () -> Void
    let onLogin: () -> Void

    @State private var currentIndex: Int = 0

    init(onCreateAccount: @escaping () -> Void, onLogin: @escaping () -> Void) {
        self.onCreateAccount = onCreateAccount
        self.onLogin = onLogin

        // Page dot tint mirrors `OnboardingView`: accent for current,
        // muted foreground for inactive.
        UIPageControl.appearance().currentPageIndicatorTintColor = UIColor(named: "AccentColor")
        UIPageControl.appearance().pageIndicatorTintColor = UIColor(named: "MutedForeground")
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 48)

            Image("WelcomeLogo")
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .foregroundStyle(Color.pebblesForeground)
                .frame(maxWidth: 220, maxHeight: 220)

            Spacer()

            TabView(selection: $currentIndex) {
                ForEach(Array(WelcomeSteps.all.enumerated()), id: \.element.id) { index, step in
                    WelcomeSlideView(step: step)
                        .accessibilityElement(children: .combine)
                        .accessibilityLabel(
                            "Welcome step \(index + 1) of \(WelcomeSteps.all.count): \(step.title). \(step.description)"
                        )
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .frame(height: 160)

            VStack(spacing: 12) {
                Button {
                    onCreateAccount()
                } label: {
                    Text("Create an account")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)

                Button {
                    onLogin()
                } label: {
                    Text("Log in")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.bordered)
            }
            .padding(.horizontal, 24)
            .padding(.bottom, 32)
        }
        .pebblesScreen()
    }
}

#Preview {
    WelcomeView(
        onCreateAccount: {},
        onLogin: {}
    )
}
```

- [ ] **Step 2: Build to confirm it compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`. Open the preview in Xcode to spot-check the layout — manual sim hook-up happens after Task 9.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/WelcomeView.swift
git commit -m "feat(ios): add WelcomeView shell with header, carousel, CTAs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Add auto-advance with Reduce Motion support

**Files:**
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift`

Carousel rotates every 4 seconds. Any change to `currentIndex` (programmatic or manual swipe) bumps an `autoAdvanceTick`; `.task(id:)` re-runs whenever the tick changes, which cancels the in-flight `Task.sleep` and starts a fresh countdown. When Reduce Motion is on, the loop exits immediately and the user advances manually.

- [ ] **Step 1: Add the auto-advance state and environment**

Find this block:

```swift
    @State private var currentIndex: Int = 0
```

Replace with:

```swift
    @State private var currentIndex: Int = 0
    @State private var autoAdvanceTick: Int = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
```

- [ ] **Step 2: Attach `.task(id:)` and `.onChange(of:)` to the root `VStack`**

Find the closing of `body`, which currently ends with:

```swift
        }
        .pebblesScreen()
    }
```

Replace with:

```swift
        }
        .task(id: autoAdvanceTick) {
            guard !reduceMotion else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(4))
                if Task.isCancelled { break }
                withAnimation {
                    currentIndex = (currentIndex + 1) % WelcomeSteps.all.count
                }
            }
        }
        .onChange(of: currentIndex) { _, _ in
            autoAdvanceTick &+= 1
        }
        .pebblesScreen()
    }
```

- [ ] **Step 3: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 4: Run tests to confirm nothing regressed**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass (no new tests; this is UI timing and is verified manually in Task 11).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/WelcomeView.swift
git commit -m "feat(ios): auto-advance welcome carousel with Reduce Motion respect

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: Add `initialMode` parameter to `AuthView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Auth/AuthView.swift`

Add an initializer that lets callers seed the segmented-control mode. Default value `.login` preserves every existing call site (currently only `RootView`, which won't pass a value until Task 8).

`AuthView.Mode` is already `enum Mode: String, CaseIterable, Identifiable`. `String`-backed enums get `Hashable` conformance synthesized automatically — no extra conformance needed for it to work as a `NavigationPath` value in Task 8.

- [ ] **Step 1: Locate the `@State` declarations**

The current `AuthView` struct body starts around line 8 with `enum Mode`, then `@Environment`, then a block of `@State` declarations starting with `@State private var mode: Mode = .login` (around line 17).

- [ ] **Step 2: Change the `mode` state to be initialized from a parameter**

Find:

```swift
    @State private var mode: Mode = .login
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false
    @State private var termsAccepted = false
    @State private var privacyAccepted = false
    @State private var presentedLegalDoc: LegalDoc?
```

Replace with:

```swift
    @State private var mode: Mode
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false
    @State private var termsAccepted = false
    @State private var privacyAccepted = false
    @State private var presentedLegalDoc: LegalDoc?

    init(initialMode: Mode = .login) {
        _mode = State(initialValue: initialMode)
    }
```

That's the only change to this file. The rest of the file — `var body`, `canSubmit`, `submit`, the preview — is untouched.

- [ ] **Step 3: Build to confirm every call site still compiles**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`. `RootView` still calls `AuthView()` (bare) and should compile cleanly against the new defaulted `initialMode`.

- [ ] **Step 4: Run tests**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Auth/AuthView.swift
git commit -m "feat(ios): let AuthView accept an initial mode

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: Wire `RootView` → `WelcomeView` → `AuthView` via `NavigationStack`

**Files:**
- Modify: `apps/ios/Pebbles/RootView.swift`

Replace the direct `AuthView()` call in the unauth branch with a `NavigationStack` rooted at `WelcomeView`. Two CTA callbacks push an `AuthRoute.auth(.signup)` or `.auth(.login)` onto the path; `navigationDestination(for:)` renders `AuthView(initialMode:)` with the correct mode. Back button pops to `WelcomeView`.

When Supabase's session becomes non-nil, the top-level `if supabase.session == nil` flips to false and the entire `NavigationStack` unmounts — no extra cleanup needed. `authPath` is reset naturally because it's `@State` on a view that just got destroyed.

- [ ] **Step 1: Replace the whole `RootView.swift` file**

The diff touches several parts (new state, new enum, new navigation wrapper) so a full rewrite is clearer than interleaved edits. Keep the docstrings and the `.task` / `.onChange(of:)` auth observer exactly as they were.

```swift
// apps/ios/Pebbles/RootView.swift
import SwiftUI

/// Top-level auth gate. Reads `SupabaseService` from the environment and
/// decides whether to show the pre-login welcome flow or the main tab bar.
///
/// During `isInitializing`, renders `Color.clear` so the user never sees a
/// flash of the wrong screen while the keychain session is being read.
/// `.task { await supabase.start() }` subscribes to auth state changes for
/// the lifetime of this view (= the lifetime of the app).
///
/// Unauth: `NavigationStack` rooted at `WelcomeView`. The two CTAs push
/// `AuthView(initialMode:)` with the correct tab preselected; the native
/// back button pops to welcome. On successful signup/login the whole
/// stack unmounts as `supabase.session` flips non-nil.
///
/// Auth (first transition from no-session to signed-in per device):
/// presents `OnboardingView` as a `.fullScreenCover` over `MainTabView`.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false
    @State private var authPath = NavigationPath()

    private enum AuthRoute: Hashable {
        case auth(AuthView.Mode)
    }

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                NavigationStack(path: $authPath) {
                    WelcomeView(
                        onCreateAccount: { authPath.append(AuthRoute.auth(.signup)) },
                        onLogin:        { authPath.append(AuthRoute.auth(.login)) }
                    )
                    .navigationDestination(for: AuthRoute.self) { route in
                        switch route {
                        case .auth(let mode):
                            AuthView(initialMode: mode)
                        }
                    }
                }
            } else {
                MainTabView()
                    .fullScreenCover(isPresented: $isPresentingOnboarding) {
                        OnboardingView(steps: OnboardingSteps.all) {
                            hasSeenOnboarding = true
                            isPresentingOnboarding = false
                        }
                    }
            }
        }
        .task {
            await supabase.start()
        }
        // Relies on supabase.start() being kicked off in .task above.
        // session?.user.id is nil when this observer is registered, so the
        // first authStateChanges event delivers a real nil→id transition
        // even for users already signed in from a prior launch.
        .onChange(of: supabase.session?.user.id) { _, newUserId in
            if newUserId != nil && !hasSeenOnboarding {
                isPresentingOnboarding = true
            }
        }
    }
}

#Preview {
    RootView()
        .environment(SupabaseService())
}
```

- [ ] **Step 2: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`.

If the compiler complains that `AuthView.Mode` is not `Hashable`, verify the existing declaration in `AuthView.swift` is `enum Mode: String, CaseIterable, Identifiable` — `String`-backed enums get `Hashable` synthesized for free. If the compiler error is something else, surface it; don't paper over by adding conformances speculatively.

- [ ] **Step 3: Run tests**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/RootView.swift
git commit -m "feat(ios): route unauth users through WelcomeView

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: Lint pass

**Files:**
- No direct file edits planned; if SwiftLint flags the new files, fix in place.

- [ ] **Step 1: Run the linter**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: zero new violations in files under `Features/Welcome/` or `PebblesTests/Features/Welcome/`. The three pre-existing violations from PR #282 (in unrelated files) are acceptable to leave — the rule is "clean for new files."

- [ ] **Step 2: If the new files produce violations, fix them in place**

Common ones to expect:
- Trailing newlines / trailing whitespace — delete them.
- Force unwraps — there are none in the new code; if the linter flags one, re-read the change.
- Line length — break long lines.

- [ ] **Step 3: Commit lint fixes (only if fixes were needed)**

```bash
git add -u apps/ios/Pebbles/Features/Welcome/
git commit -m "quality(ios): resolve SwiftLint violations in Welcome

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

If no violations were flagged, skip the commit.

---

## Task 10: Update the Arkaik bundle

**Files:**
- Modify: `docs/arkaik/bundle.json`

The bundle already contains a `V-landing` node described as "Public landing page with app branding, login button, and create-account link." This is exactly our welcome screen conceptually — **do not** introduce a new `V-welcome` node. Update `V-landing` instead and add navigation edges to `V-login` and `V-register` on iOS.

The `arkaik` skill is the source of truth for surgical updates to this file. Use it — do not hand-edit node lists.

- [ ] **Step 1: Invoke the arkaik skill**

Ask the skill to:
1. Update the description of node `V-landing` to note that the iOS implementation lands in PR for #281 (a persistent logo header, auto-advancing 3-step carousel, and two CTAs that push into `AuthView`). Keep the `platforms` array as `["web", "ios", "android"]`. Status stays `development`.
2. Add two navigation edges (surgical, iOS-scoped):
   - `V-landing → V-register` — label/metadata reflecting the "Create an account" CTA.
   - `V-landing → V-login` — label/metadata reflecting the "Log in" CTA.
3. If the bundle already has an equivalent implicit "app entry" edge that pointed directly to `V-login` or `V-register`, re-route it so `V-landing` is the entry point on iOS.
4. Run the bundle's validation script before saving.

The skill's own workflow covers node-update patterns, edge-add patterns, the validation script, and the exact edge id naming convention (`e-<source>-<target>`).

- [ ] **Step 2: Confirm the diff is small and surgical**

```bash
git diff docs/arkaik/bundle.json
```

The diff should touch only `V-landing`'s description (1–3 lines) and add 1–2 edge objects. No unrelated nodes, no regeneration of the whole file.

- [ ] **Step 3: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "chore(arkaik): wire iOS welcome screen through V-landing

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 11: Full verification (build, tests, lint, manual sim)

This is the final gate. No code changes expected unless a problem surfaces.

- [ ] **Step 1: Build**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: `BUILD SUCCEEDED`.

- [ ] **Step 2: Unit tests**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: all tests pass. `WelcomeSteps` suite reports 5/5; the existing suites (including `OnboardingSteps`) remain green. Total count increases by exactly 5 relative to main.

- [ ] **Step 3: Lint**

```bash
npm run lint --workspace=@pbbls/ios
```

Expected: no new violations in `Features/Welcome/` or `PebblesTests/Features/Welcome/`.

- [ ] **Step 4: Manual simulator checklist**

Run the app on a fresh simulator (or one with no cached Supabase session). Confirm each item:

- [ ] Fresh launch while unauth → lands on `WelcomeView`. Logo visible in the top portion; first slide shows "Record in seconds." / "Capture moments..."; page dots show 3 with the first active in the accent color and the other two in muted foreground.
- [ ] Wait 4 s → advances to slide 2 ("Enrich with meaning."). Wait another 4 s → advances to slide 3 ("Grow your path."). Wait another 4 s → loops back to slide 1.
- [ ] Swipe to slide 3 manually → auto-advance timer resets; 4 s after the swipe, it loops to slide 1.
- [ ] Enable Reduce Motion (Settings → Accessibility → Motion → Reduce Motion on the simulator, or `xcrun simctl ui booted accessibility reduce-motion --enable`) → auto-advance stops. Manual swipe still works.
- [ ] Enable VoiceOver → each slide is announced as "Welcome step N of 3: <title>. <description>". Both CTA buttons are reachable.
- [ ] Tap "Create an account" → pushes `AuthView` with the **Sign Up** tab selected and the consent checkboxes visible. Back arrow in the nav bar returns to the welcome screen.
- [ ] Tap "Log in" → pushes `AuthView` with the **Log In** tab selected. Back arrow returns to the welcome screen.
- [ ] Complete signup with a valid email/password and accepted consents → `NavigationStack` unmounts, `MainTabView` appears, and the post-signin `OnboardingView` `.fullScreenCover` presents (unchanged behavior from #282). Dismissing the onboarding cover lands on the Path tab; subsequent launches skip the onboarding cover.
- [ ] Sign out → returns to `WelcomeView`, not straight to `AuthView`. Relaunching while unauth also lands on `WelcomeView` (no persistence flag for welcome itself).
- [ ] Toggle dark mode → logo (template-tinted) and copy remain readable; CTA hierarchy (prominent signup vs. outlined login) is preserved.

- [ ] **Step 5: If any check fails, diagnose root cause and fix before continuing**

Do not rationalize around a failing check. If the page dots don't change color, something is wrong with the `UIPageControl.appearance()` call or asset names. If auto-advance doesn't restart after manual swipe, the `.onChange(of: currentIndex)` isn't firing — probably because the modifier landed on the wrong view in the hierarchy. Fix the root cause, add a test if it's testable, and commit.

- [ ] **Step 6: (Optional) Snapshot the final test count**

For the PR description:

```bash
npm run test --workspace=@pbbls/ios 2>&1 | tail -20
```

Note the total test count (should be 77 — previously 72, plus 5 new `WelcomeSteps` tests).

---

## Ready for PR

All tasks complete. The PR should follow the CLAUDE.md workflow:

- Branch: `feat/281-ios-welcome-screen` (already matches the convention).
- PR title: `feat(ios): pre-login welcome screen (#281)`.
- PR body: starts with `Resolves #281`, lists the new and modified files, notes "builds on #282," includes the manual sim checklist as the Test plan.
- Labels / milestone: propose inheriting from issue #281 — `feat`, `ui`, `ios`, milestone `M23 · TestFlight V1`. Ask the user to confirm before creating the PR.
- Run `npm run build --workspace=@pbbls/ios` and `npm run lint --workspace=@pbbls/ios` one last time before opening the PR.
