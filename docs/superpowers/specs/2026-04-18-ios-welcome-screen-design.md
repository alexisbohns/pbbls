# iOS Welcome Screen — Design

**Date:** 2026-04-18
**Resolves:** #281
**Builds on:** #282 (post-signin onboarding)

## Context

When an unauthenticated user opens the app, they currently land directly on
`AuthView` (segmented Login / Sign Up form). Issue #281 asks for a prettier
pre-login landing page: the Pebbles logo as a persistent hero, an
auto-advancing 3-step carousel explaining what the app does, and two CTAs
that route into `AuthView` with the correct mode pre-selected.

The post-signin onboarding shipped in #282 (`Features/Onboarding/`) remains
untouched and continues to run after the user completes signup or login.
The welcome carousel is deliberately **distinct** from it — different
copy, different layout, different lifecycle (no persistence flag).

## Goals

- Replace the unauth landing with a welcome screen that has a persistent
  logo header, an auto-advancing carousel, and two footer CTAs.
- Route "Create an account" and "Log in" to `AuthView` with the right tab
  pre-selected.
- Re-show the welcome on every fresh unauth launch (no "seen it" flag).
- Keep the implementation surface small and self-contained; do not refactor
  the onboarding flow.

## Non-goals

- Changing the post-signin onboarding flow, its persistence, or its replay
  entry point.
- Introducing a shared `PagedCarousel` primitive across both flows (YAGNI
  with only two consumers; onboarding chrome differs meaningfully).
- Preserving typed email/password when the user pops back from `AuthView`
  to `WelcomeView`. Standard iOS push-nav behavior — fine as-is.
- Adding UI tests. Manual sim verification + unit tests on the config
  matches existing conventions.

## Acceptance (from issue #281)

- Unauth user opens the app → lands on welcome screen with carousel + two
  buttons (create account, login).
- Tap "Create an account" → lands on signup screen (AuthView, Sign Up tab).
- Tap "Log in" → lands on login screen (AuthView, Log In tab).
- Authenticated user opens the app → lands on their path (MainTabView).
  No change from today.

## Architecture

New feature folder, mirroring the shape of `Features/Onboarding/`:

```
apps/ios/Pebbles/Features/Welcome/
  WelcomeStep.swift         { id, title, description }  — data
  WelcomeSteps.swift        static config (3 entries)
  WelcomeSlideView.swift    renders one step (title + description)
  WelcomeView.swift         header + carousel + footer
```

New asset:

```
apps/ios/Pebbles/Resources/Assets.xcassets/
  WelcomeLogo.imageset/     single combined logo+wordmark SVG, "Preserves
                            Vector Representation" enabled. Placeholder
                            ships with the PR; final artwork swap later
                            requires no Swift changes.
```

Modified files (scoped, minimal):

- `Features/Auth/AuthView.swift` — add `init(initialMode: Mode = .login)`.
  Default preserves every existing call site.
- `RootView.swift` — wrap the unauth branch in `NavigationStack`;
  `WelcomeView` is the root; CTAs push `AuthView(initialMode:)`. Back
  button pops to welcome.

New test file:

- `PebblesTests/Features/Welcome/WelcomeStepsTests.swift` — mirrors
  `OnboardingStepsTests`: count, unique ids, non-empty copy, expected ids.

No changes to `OnboardingView`, `OnboardingSteps`, `OnboardingPageView`,
`OnboardingStep`, `OnboardingImage`, or the `hasSeenOnboarding` gate.

## Components

### WelcomeStep (data)

```swift
struct WelcomeStep: Identifiable {
    let id: String
    let title: String
    let description: String
}
```

Pure value type. No image field — the logo is persistent in the header,
so slides don't carry per-step art.

### WelcomeSteps (config)

Three entries, copy verbatim from the issue:

1. `id: "record"` — title `"Record in seconds."` — description
   `"Capture moments as they happen — no blank page, no pressure."`
2. `id: "enrich"` — title `"Enrich with meaning."` — description
   `"Add emotions, people, and reflections to each pebble."`
3. `id: "grow"` — title `"Grow your path."` — description
   `"Look back at your journey, at your own pace."`

### WelcomeSlideView

Leaf view. One step → left-aligned title (larger, semibold,
`Color.pebblesForeground`) + description (body,
`Color.pebblesMutedForeground`). No background, no horizontal padding —
the parent manages that so the swipe gesture area spans the screen.

### WelcomeView

Root of the feature. Three stacked zones inside a single `VStack`:

- **Header** — `Image("WelcomeLogo")` centered, ~200 pt tall, respects
  the top safe area. Rendered as `.template` with
  `.foregroundStyle(Color.pebblesForeground)` so light/dark mode works;
  if brand locks to a fixed tint, swap to `.original` when artwork lands.
- **Carousel** — `TabView(selection: $currentIndex)` with
  `.tabViewStyle(.page(indexDisplayMode: .always))`. One
  `WelcomeSlideView` per step. Auto-advances (see Data Flow). Page dots
  use the same `UIPageControl.appearance()` trick `OnboardingView`
  uses: accent for the active dot, `MutedForeground` for inactive.
- **Footer** — two stacked full-width buttons with 12 pt spacing:
  - "Create an account" — `.borderedProminent` — triggers
    `onCreateAccount()` callback.
  - "Log in" — `.bordered` (outlined) — triggers `onLogin()` callback.

Uses `.pebblesScreen()` modifier for theme background.

### AuthView modification

```swift
init(initialMode: Mode = .login) {
    _mode = State(initialValue: initialMode)
}
```

Default `.login` preserves all existing behavior. Today the only call
site is `RootView`, so the default is also the compatibility fallback.

### RootView modification

Unauth branch becomes a `NavigationStack` with a local route enum:

```swift
private enum AuthRoute: Hashable {
    case auth(AuthView.Mode)
}

// inside body, in the `else if supabase.session == nil` branch:
NavigationStack(path: $authPath) {
    WelcomeView(
        onCreateAccount: { authPath.append(AuthRoute.auth(.signup)) },
        onLogin:        { authPath.append(AuthRoute.auth(.login)) }
    )
    .navigationDestination(for: AuthRoute.self) { route in
        switch route {
        case .auth(let mode): AuthView(initialMode: mode)
        }
    }
}
```

Where `@State private var authPath = NavigationPath()` lives on
`RootView`. The path resets automatically when the `NavigationStack`
unmounts (i.e., when the user signs in and the top-level conditional
flips to `MainTabView`).

## Data Flow & Interactions

### Auto-advance

State in `WelcomeView`:

```swift
@State private var currentIndex: Int = 0
@State private var autoAdvanceTick: Int = 0
@Environment(\.accessibilityReduceMotion) private var reduceMotion
```

Auto-advance logic, scoped to the view lifetime:

```swift
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
```

- `.task(id:)` cancels and restarts when `autoAdvanceTick` changes.
- Any change to `currentIndex` (programmatic or manual swipe) bumps the
  tick → the in-flight `Task.sleep` is cancelled, a fresh 4-second
  countdown begins from the new slide.
- The loop wraps (modulo), so the carousel loops indefinitely until the
  user taps a CTA.
- `reduceMotion` honored: auto-advance exits early; swipe-only.

### Tap handlers

Footer buttons call back to `RootView` via closures passed into
`WelcomeView.init`. `RootView` owns the `NavigationPath` and appends
the correct `AuthRoute`.

### Return from AuthView

Native back button in the `NavigationStack` toolbar pops to
`WelcomeView`. When it remounts, the carousel starts fresh at
`currentIndex = 0` — acceptable.

### Successful signup / login

Supabase's `onAuthStateChange` flips `supabase.session` to non-nil.
`RootView`'s top-level `if supabase.session == nil` evaluates false,
the whole `NavigationStack` (with `AuthView` on top) unmounts, and
`MainTabView` + the post-signin `OnboardingView` full-screen cover take
over. No special handling needed — existing path.

## Error Handling & Edge Cases

Welcome is pure presentation — no network, no persistence, no Supabase
calls. Small error surface, a few items worth noting:

- **Missing logo asset** — `Image("WelcomeLogo")` renders nothing if
  absent (no crash). Placeholder ships on day one; deletion would be a
  setup bug caught in preview / build.
- **Slide count drift** — `WelcomeStepsTests` enforces exactly 3 steps,
  unique ids, non-empty copy. Caught at CI.
- **Accessibility** — auto-advance honors
  `@Environment(\.accessibilityReduceMotion)`: when true, the loop
  exits early and the user swipes manually. Each slide is announced by
  VoiceOver via an `.accessibilityLabel` of
  `"Welcome step N of 3: <title>. <description>"`.
- **Background / foreground** — iOS pauses `.task` at scene phase
  boundaries automatically; resumes on foreground. No special handling.
- **Form state on return** — popping from `AuthView` destroys the view;
  typed email/password is lost. Matches acceptance criteria and
  standard iOS push-nav behavior.
- **Late session restore** — if some token-restore path flips `session`
  non-nil while the user is on `WelcomeView`, `RootView`'s top-level
  conditional unmounts the entire `NavigationStack` cleanly.

## Testing & Verification

### Unit — `PebblesTests/Features/Welcome/WelcomeStepsTests.swift`

Swift Testing (`@Suite`, `@Test`, `#expect`). Mirrors
`OnboardingStepsTests`. Four assertions:

1. `WelcomeSteps.all.count == 3`.
2. Ids are unique.
3. All titles and descriptions are non-empty.
4. Ids in order: `["record", "enrich", "grow"]`.

No test for the auto-advance loop — timing behavior inside a SwiftUI
`.task` is not worth the test complexity for a cosmetic feature.
Verified in the simulator.

### Manual (simulator)

- Fresh launch unauth → lands on `WelcomeView`. Logo visible; first
  slide shows; dots show 3 with first active.
- Wait 4 s → advances to step 2, then 3, then loops to 1.
- Swipe to step 3 manually → auto-advance timer resets; 4 s later →
  loops to step 1.
- Enable VoiceOver / Reduce Motion → auto-advance stops; manual swipe
  still works; each slide is announced with label and position.
- Tap "Create an account" → pushes `AuthView` with Sign Up tab active.
  Back button returns to welcome.
- Tap "Log in" → pushes `AuthView` with Log In tab active. Back button
  returns to welcome.
- Complete signup → `NavigationStack` unmounts, `MainTabView` appears,
  post-signin `OnboardingView` full-screen cover shows (unchanged).
- Relaunch while unauth → lands on `WelcomeView` again (no persistence).
- Light / dark mode toggle → logo + copy remain readable; CTA
  hierarchy preserved.

### Build & lint

- `npm run build --workspace=@pbbls/ios` — BUILD SUCCEEDED.
- `npm run lint --workspace=@pbbls/ios` — clean for new files.
- `npm run test --workspace=@pbbls/ios` — new 4 tests pass; existing
  72 unaffected.

## Arkaik map update

`docs/arkaik/bundle.json` gets a new `V-welcome` view node in the unauth
subgraph, with edges:

- `V-welcome → V-auth` (label: signup) — from "Create an account"
- `V-welcome → V-auth` (label: login) — from "Log in"

The existing unauth entry edge is re-routed from `→ V-auth` to
`→ V-welcome`. Done via the `arkaik` skill alongside implementation.

## File summary

**New files**

- `apps/ios/Pebbles/Features/Welcome/WelcomeStep.swift`
- `apps/ios/Pebbles/Features/Welcome/WelcomeSteps.swift`
- `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift`
- `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift`
- `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/` (+ placeholder SVG + `Contents.json`)
- `apps/ios/PebblesTests/Features/Welcome/WelcomeStepsTests.swift`

**Modified files**

- `apps/ios/Pebbles/Features/Auth/AuthView.swift` — add `init(initialMode:)`
- `apps/ios/Pebbles/RootView.swift` — wrap unauth branch in `NavigationStack`
- `docs/arkaik/bundle.json` — add `V-welcome` node + edges
- `apps/ios/project.yml` — only if the new files need explicit source
  path entries (xcodegen typically picks up `Features/**` globs; verify
  during implementation)
