# Onboarding Screens — Design

**Issue:** [#280 — \[Feat\] Onboarding screens](https://github.com/Bohns/pbbls/issues/280)
**Scope:** iOS only (`apps/ios`)
**Milestone:** M23 · TestFlight V1
**Labels:** `feat`, `ios`, `ui`

## Goal

Introduce a 4-step onboarding flow that explains *why* Pebbles exists and *how* to use it. The flow is shown automatically the first time a user signs in on a device, and is replayable any time from an info icon on the Path screen.

The implementation must dissociate **screen content** (copy and image references) from **render** (SwiftUI views), so that copy and assets can be edited in a single config file without touching view code.

## Acceptance criteria (from issue)

- As a new user, when I sign up to the app, I land on the onboarding screens.
- As a logged-in user, when I tap the "info" icon in the Path screen, I replay the onboarding screens.

## Architectural decisions

| # | Decision | Choice |
|---|---|---|
| 1 | Where to persist "has seen onboarding" | `@AppStorage("hasSeenOnboarding")` (per-device UserDefaults). Replay button covers the cross-device gap. |
| 2 | How to present the flow | `.fullScreenCover` from both `RootView` (initial) and `PathView` (replay). One presentation pattern, two trigger points. |
| 3 | On-screen controls | Apple Tips-inspired: `TabView` `.page` style with dot indicator, no per-page Continue button, swipe to advance. Toolbar has a leading `xmark` close and a trailing **Skip** action. The last page shows a full-width prominent **Start your path** CTA. |
| 4 | Image source | Hybrid — `enum OnboardingImage { case asset(String); case remote(URL) }`. Ship V1 with local placeholders in `Assets.xcassets`; switch to remote URLs later by changing the config only. |
| 5 | Replay entry point | `info.circle` button in `PathView`'s trailing nav-bar toolbar. |

## Files to add

```
Pebbles/Features/Onboarding/
  OnboardingImage.swift       // enum: .asset(String) | .remote(URL)
  OnboardingStep.swift        // struct: id, image, title, description
  OnboardingSteps.swift       // static let all: [OnboardingStep] — the four steps
  OnboardingPageView.swift    // renders ONE step (image card, title, body)
  OnboardingView.swift        // TabView container + toolbar + last-page CTA
```

Asset catalog additions (`Pebbles/Resources/Assets.xcassets/`):

```
OnboardingIntro.imageset
OnboardingConcept.imageset
OnboardingQualify.imageset
OnboardingCarving.imageset
```

Each starts with a 1×1 transparent PNG so the build passes; final artwork is dropped in later.

## Files to edit

- `Pebbles/RootView.swift` — present `OnboardingView` as a `.fullScreenCover` over `MainTabView` after the user transitions from no-session to signed-in, when `hasSeenOnboarding` is `false`.
- `Pebbles/Features/Path/PathView.swift` — add a trailing toolbar `info.circle` button that presents `OnboardingView` as a `.fullScreenCover` (replay mode — does not touch `hasSeenOnboarding`).

No changes outside `apps/ios/`. No DB migrations. No Supabase RPCs. No web changes.

## The data model

```swift
// OnboardingImage.swift
enum OnboardingImage {
    case asset(String)
    case remote(URL)
}

// OnboardingStep.swift
struct OnboardingStep: Identifiable {
    let id: String              // stable key, e.g. "intro"
    let image: OnboardingImage
    let title: String
    let description: String
}

// OnboardingSteps.swift
enum OnboardingSteps {
    static let all: [OnboardingStep] = [
        .init(
            id: "intro",
            image: .asset("OnboardingIntro"),
            title: "Your life is a path.",
            description: """
            Every moment matters — the big ones and the quiet ones. \
            But most of them slip away before you even notice. \
            Pebbles helps you collect them, one by one.
            """
        ),
        .init(
            id: "concept",
            image: .asset("OnboardingConcept"),
            title: "Drop a pebble, keep the moment.",
            description: """
            A coffee with a friend. A concert that gave you chills. \
            A tough conversation. Record it in seconds — \
            no blank page, no pressure, no audience.
            """
        ),
        .init(
            id: "qualify",
            image: .asset("OnboardingQualify"),
            title: "Qualify and relate.",
            description: """
            Associate your moment with an emotion and a domain, \
            relate with people and add your pebble to a stack or a collection.
            """
        ),
        .init(
            id: "carving",
            image: .asset("OnboardingCarving"),
            title: "Get your pebble.",
            description: """
            Choose or carve a glyph to mark the moment. \
            Then reveal a unique pebble illustration to remember your moment.
            """
        )
    ]
}
```

Editing copy or reordering steps is a single-file change. Adding a fifth step is a single array entry.

## The render

### `OnboardingPageView`

Pure presentation of one step. Knows nothing about pagination or dismissal.

- Vertical layout: large illustration card (rounded rectangle on `Color.pebblesSurfaceAlt`), bold title (`title2.weight(.semibold)`), body description (`body`, `Color.pebblesMutedForeground`).
- Switches on the image enum:
  - `.asset(name)` → `Image(name).resizable().scaledToFit()`
  - `.remote(url)` → `AsyncImage(url:)` with `Color.pebblesSurfaceAlt` placeholder; failure renders the same placeholder.
- Padding: 24 horizontal, generous vertical spacing.

### `OnboardingView`

The container. Receives `steps` and an `onFinish` closure.

```swift
struct OnboardingView: View {
    let steps: [OnboardingStep]
    let onFinish: () -> Void

    @State private var currentIndex: Int = 0

    var body: some View {
        NavigationStack {
            TabView(selection: $currentIndex) {
                ForEach(Array(steps.enumerated()), id: \.element.id) { index, step in
                    OnboardingPageView(step: step)
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { onFinish() } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel("Close onboarding")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Skip") { onFinish() }
                }
            }
            .safeAreaInset(edge: .bottom) {
                if currentIndex == steps.count - 1 {
                    Button {
                        onFinish()
                    } label: {
                        Text("Start your path")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                    }
                    .buttonStyle(.borderedProminent)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 24)
                }
            }
            .pebblesScreen()
        }
    }
}
```

Why `onFinish` is a closure rather than a `@Binding`: the view doesn't need to read the persistence state, only signal when "done" happens. This keeps `OnboardingView` decoupled from `@AppStorage` and trivially previewable with `OnboardingView(steps: OnboardingSteps.all) {}`.

## Persistence & wiring

### Initial trigger — `RootView`

```swift
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @AppStorage("hasSeenOnboarding") private var hasSeenOnboarding = false
    @State private var isPresentingOnboarding = false

    var body: some View {
        ZStack {
            if supabase.isInitializing {
                Color.clear
            } else if supabase.session == nil {
                AuthView()
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
        .task { await supabase.start() }
        .onChange(of: supabase.session?.user.id) { _, newUserId in
            if newUserId != nil && !hasSeenOnboarding {
                isPresentingOnboarding = true
            }
        }
    }
}
```

Why `.onChange(of:)` on `session?.user.id` rather than reading `hasSeenOnboarding` synchronously: the cover is presented exactly once per session transition (nil → user) when the flag is false, instead of on every render.

### Replay trigger — `PathView`

```swift
@State private var isPresentingOnboarding = false

// inside `var body`:
.toolbar {
    ToolbarItem(placement: .topBarTrailing) {
        Button { isPresentingOnboarding = true } label: {
            Image(systemName: "info.circle")
        }
        .accessibilityLabel("Show how Pebbles works")
    }
}
.fullScreenCover(isPresented: $isPresentingOnboarding) {
    OnboardingView(steps: OnboardingSteps.all) {
        isPresentingOnboarding = false
    }
}
```

The replay path's `onFinish` does not write to `@AppStorage` — replay is idempotent and side-effect-free.

## Edge cases

| Scenario | Behavior | Acceptable? |
|---|---|---|
| User signs out and back in on the same device | `hasSeenOnboarding` is still true; onboarding does not re-show | Yes — replay button covers the rare case where they want it again |
| User reinstalls the app | UserDefaults wiped; onboarding shows on next signin | Yes — fresh device, fresh flow |
| User force-quits mid-onboarding | Flag stays false; onboarding re-presents on next launch | Yes — they didn't complete it |
| User signs in on a second device | Flag is per-device, so onboarding shows again | Acceptable per decision #1 — they can Skip |
| `RootView` `#Preview` | Empty session, onboarding never triggers | Add a dedicated `#Preview` for `OnboardingView` itself |

## Out of scope

- Any web-side onboarding (issue is iOS-labeled).
- Cross-device persistence (deferred — would be a profiles column in a future iteration if user feedback warrants it).
- Real artwork — placeholder PNGs ship with this PR; final artwork is a separate visual asset task.
- Animations beyond the default `TabView` `.page` swipe transition.
- Localization (no Pebbles iOS strings are localized today).
- Telemetry on which step the user dropped off at.

## Definition of done

- All 5 new files exist under `Pebbles/Features/Onboarding/` and are referenced by `project.yml` after running `xcodegen generate`.
- 4 placeholder image sets exist in `Assets.xcassets` (1×1 transparent PNGs are fine).
- A fresh signup → onboarding presents over the tab bar; tapping Skip, X, or Start your path on the last page sets `hasSeenOnboarding = true` and dismisses.
- Tapping `info.circle` in `PathView` re-presents onboarding without affecting `hasSeenOnboarding`.
- `npm run build --workspace=@pbbls/ios` and `npm run lint --workspace=@pbbls/ios` (or equivalent SwiftLint command) pass.
- `OnboardingView` has a `#Preview` block that compiles.
- Arkaik product map is updated (a new view node for Onboarding plus edges from Auth/Path).
