# iOS — Animated Splash + Polished Welcome Screen

**Issue:** [#357](https://github.com/alexisbohns/pbbls/issues/357) — Enhanced opening and welcome screen
**Milestone:** M29 · Extended login and signup on iOS
**Status:** Design approved, pending implementation plan

## Problem

The current welcome screen is functional but flat: a static logo header, a left-aligned carousel, four buttons, and a clamped terms disclaimer. There is no splash — `RootView` renders `Color.clear` while the auth keychain session is being read, then snaps to either `WelcomeView` or `MainTabView`.

The issue calls for a polished opening: a stroke-by-stroke "drawing" of the Pebbles logo on launch, then a gentle staged reveal of the welcome content for unauth users (or a direct transition to Path for authenticated users), plus a list of visual refinements to the welcome screen itself.

## Acceptance criteria (from the issue)

1. When I open the app, I see a splash screen with an animated drawing of the Pebbles logo.
2. As an unauth user, when the splash ends, the gallery slider and sign-in buttons appear gently, one by one.
3. As an authenticated user, when the splash ends, the Path is displayed.
4. Logo is in accent color (splash and welcome use the same asset/component).
5. Slider content is centered.
6. Slide title font is Ysabeau Semibold, muted foreground color.
7. Slide description unchanged except centered.
8. "Create an account" button: rounded accent background, white label.
9. "Log in" button: transparent background, accent border, accent label.
10. Apple button unchanged.
11. Google button: white background, soft grey border.
12. All button labels: medium weight (not regular).
13. Terms disclaimer: not clamped (no ellipsis), centered.

## Architecture

### Splash strategy

A new `SplashView` becomes the root content of `RootView` while a `splashPhase` state is `.playing` or `.ready`. It runs on every cold launch regardless of auth status. `supabase.start()` runs in parallel with the splash animation. When both the animation completes AND `supabase.isInitializing` is false, `RootView` transitions to either `WelcomeView` (with a staged reveal) or `MainTabView`.

This eliminates the existing `Color.clear` flash during initialization. Splash logo end-state and welcome logo are visually identical (same artwork, same accent tint, similar position) so the transition feels like the rest of the screen filling in around the logo.

The splash plays once per process. Returning from background does not re-trigger it because `RootView`'s `@State` survives backgrounding.

### State machine in `RootView`

```swift
private enum SplashPhase {
    case playing      // splash visible, animation in progress
    case ready        // splash done, but isInitializing might still be true
    case finished     // both splash done AND auth resolved → show next screen
}
@State private var splashPhase: SplashPhase = .playing
```

Transitions:
1. App opens → `splashPhase = .playing`. `SplashView` renders fullscreen, animation begins.
2. Splash `onComplete` fires → `splashPhase = .ready`. Logo holds at end-state.
3. `supabase.isInitializing` flips false AND `splashPhase == .ready` → `splashPhase = .finished`. RootView swaps to next screen.
4. Auth resolves before splash finishes: held in `.ready` until splash is done.
5. Splash finishes before auth resolves: logo holds at end-state until `isInitializing` flips. Worst-case extended splash.

```swift
ZStack {
    if splashPhase != .finished {
        SplashView(onComplete: { splashPhase = .ready })
    } else if supabase.session == nil {
        NavigationStack(path: $authPath) { WelcomeView(...) }
    } else {
        MainTabView().fullScreenCover(...) { OnboardingView(...) }
    }
}
.task { await supabase.start() }
.onChange(of: supabase.isInitializing) { _, isInit in
    if !isInit && splashPhase == .ready { splashPhase = .finished }
}
.onChange(of: splashPhase) { _, phase in
    if phase == .ready && !supabase.isInitializing { splashPhase = .finished }
}
```

The existing onboarding `.fullScreenCover` continues to fire off the auth transition; that transition now happens after `.finished`.

## Components

### `LogoSVGModel` (new)

`apps/ios/Pebbles/Features/Welcome/LogoSVGModel.swift` — small XML parser modeled on `PebbleSVGModel.swift` but simpler: returns `(viewBox: CGRect, paths: [CGPath])` in document order, with no layer-kind classification (the logo is one ordered stream of strokes).

```swift
struct LogoSVGModel {
    let viewBox: CGRect
    let paths: [CGPath]   // in document order

    init?(svg: String)
}
```

Reuses `SVGPathParser` from `apps/ios/Pebbles/Features/Glyph/Utils/SVGPath.swift` (already supports `M`, `L`, `Q`, `C`). Parses on first use and caches in `SplashView`'s `@State`. On parse failure, returns nil; `SplashView` falls back to a static logo.

### `SplashView` (new)

`apps/ios/Pebbles/Features/Welcome/SplashView.swift`. Public surface:

```swift
struct SplashView: View {
    let onComplete: () -> Void
}
```

Renders each parsed path as a `Shape` with `.trim(from: 0, to: pathProgress[i])` and `.stroke(Color.pebblesAccent, lineWidth: 6, lineCap: .round, lineJoin: .round)`. Path scaling uses the same viewBox→rect fit pattern as `LayerShape` in `PebbleAnimatedRenderView.swift:108` so the stroke width remains proportional inside the fitted rect.

Animation timing:
- 14 paths drawn with overlapping starts: each path begins drawing 80ms after the previous.
- Each path's draw duration: 700ms with `.easeOut`.
- Total: ~13 × 80 + 700 ≈ 1.84s.
- A single `.task` orchestrates with `withAnimation(...).delay(...)`.
- Stroked color is `Color.pebblesAccent` from frame 0 (no color crossfade).
- `onComplete` fires when the last path finishes.

Reduce Motion: if `\.accessibilityReduceMotion` is true, render the logo as a static accent-tinted image and call `onComplete` immediately on appear.

### `WelcomeView` (modified)

Adds a `revealStep` state machine driven by a `.task` that runs once on appear:

| Step | Element | Delay from welcome appear | Fade duration |
|------|---------|----------------------------|----------------|
| 0 | Logo (already visible from splash) | 0 | — |
| 1 | Carousel (slider) | 150ms | 350ms |
| 2 | "Create an account" button | 450ms | 300ms |
| 3 | "Log in" button | 600ms | 300ms |
| 4 | "Continue with Apple" button | 750ms | 300ms |
| 5 | "Continue with Google" button | 900ms | 300ms |
| 6 | Terms disclaimer | 1100ms | 300ms |

Implementation: `@State private var revealStep: Int = 0` plus a `.task` that bumps it sequentially with `withAnimation`. Each element wraps in `.opacity(revealStep >= N ? 1 : 0)`. Reduce Motion → set `revealStep = 6` immediately on appear.

Visual changes:
1. Logo color: `Color.pebblesForeground` → `Color.pebblesAccent` (`WelcomeView.swift:40`).
2. All button labels: `.fontWeight(.medium)`.
3. "Create an account": keep `.borderedProminent` accent (unchanged behavior).
4. "Log in": replace `.buttonStyle(.bordered)` with a custom inline style — clear background, `Color.pebblesAccent` border (1pt), `Color.pebblesAccent` label.
5. Apple button: unchanged.
6. Google button: replace `.buttonStyle(.bordered)` with a custom inline style — white background, `Color.pebblesBorder` border (1pt), default foreground.
7. Terms disclaimer: confirm no `lineLimit` upstream; add `.fixedSize(horizontal: false, vertical: true)` if any container constraint is producing an ellipsis.

### `WelcomeSlideView` (modified)

- Change `VStack(alignment: .leading)` → `VStack(alignment: .center)`.
- Change `.frame(maxWidth: .infinity, alignment: .leading)` → `.frame(maxWidth: .infinity, alignment: .center)`.
- Title font: `.font(.custom("Ysabeau-SemiBold", size: 22))`.
- Title color: `Color.pebblesMutedForeground` (was `pebblesForeground`).
- Description: unchanged styling, `.multilineTextAlignment(.center)`.

### Authenticated path

If `supabase.session != nil` after the splash, `RootView` swaps directly to `MainTabView` with SwiftUI's default crossfade. No staged reveal — matches "the Path is displayed."

## Assets

### Logo SVG

Replace `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/welcome-logo.svg` with the SVG from issue #357 (300×300 viewBox, 14 stroked paths inside `<g id="pebbles-logo-strokes">`). Keep the imageset name `WelcomeLogo` so the existing `Image("WelcomeLogo")` reference continues to resolve. The fallback static render path (Reduce Motion, parse failure) uses the same imageset rendered as a template image with `.foregroundStyle(Color.pebblesAccent)`.

Note: the new artwork is **different** from the current `WelcomeLogo` SVG. This is an intentional replacement, not an addition.

### Ysabeau font

- Drop `Ysabeau-SemiBold.ttf` (or `.otf`) into `apps/ios/Pebbles/Resources/Fonts/`.
- Register in `apps/ios/project.yml` under the target's `Info.plist > UIAppFonts`.
- Run `npm run generate --workspace=@pbbls/ios` (or `xcodegen generate`) to regenerate the `.xcodeproj`.
- `Pebbles/Resources/Fonts/` may need to be added as a folder reference or resource group in `project.yml` if it isn't already covered by a wildcard.

The font file must be provided; this design does not include a download step.

## Error handling

- **SVG parse failure:** `LogoSVGModel.init?` returns nil → `SplashView` falls back to a static accent-tinted `Image("WelcomeLogo")` and immediately calls `onComplete`. Logged via `os.Logger(subsystem: "app.pbbls.ios", category: "splash")` at `.error`. Mirrors `PebbleAnimatedRenderView.swift:38`.
- **Font missing:** `.font(.custom("Ysabeau-SemiBold", ...))` silently falls back to system if the font fails to register. A `#if DEBUG` `assertionFailure` in `PebblesApp` verifies `UIFont.fontNames(forFamilyName: "Ysabeau")` is non-empty so a missing font fails loud in dev but degrades gracefully in prod.
- **Splash hangs (auth never resolves):** acceptable — preserves the existing trust in `isInitializing`. No new failure mode introduced. No timeout: a timeout would mask real bugs.
- **Reduce Motion:** splash skips the animation and immediately calls `onComplete`. Welcome reveal jumps to `revealStep = 6` on appear. No staggered fade.

## Testing

Project uses Swift Testing (`@Suite`, `@Test`, `#expect`) — no XCTest, no UI tests.

- `apps/ios/PebblesTests/LogoSVGModelTests.swift` — new suite:
  - parses the bundled `WelcomeLogo` SVG and `#expect`s 14 paths in document order
  - `#expect`s `viewBox == CGRect(0, 0, 300, 300)`
  - `#expect`s nil for malformed SVG (empty string, no `<svg>`, no `<path>` children)

Manual QA checklist (PR description):
- cold launch (unauth) → splash → staggered welcome reveal
- cold launch (auth) → splash → Path
- foreground from background → no splash replay
- Reduce Motion → no draw animation, instant reveal
- slow network simulated → splash holds at end-state until auth resolves
- dark mode → accent color is theme-aware via `Color.pebblesAccent`

## File touch summary

**New:**
- `apps/ios/Pebbles/Features/Welcome/SplashView.swift`
- `apps/ios/Pebbles/Features/Welcome/LogoSVGModel.swift`
- `apps/ios/Pebbles/Resources/Fonts/Ysabeau-SemiBold.{ttf|otf}` (provided by user)
- `apps/ios/PebblesTests/LogoSVGModelTests.swift`

**Modified:**
- `apps/ios/Pebbles/RootView.swift` — splash phase state machine
- `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` — accent logo, button restyling, staged reveal, terms unclamping
- `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift` — center alignment, Ysabeau title, muted color
- `apps/ios/Pebbles/Resources/Assets.xcassets/WelcomeLogo.imageset/welcome-logo.svg` — replaced with new artwork
- `apps/ios/project.yml` — register `Ysabeau-SemiBold` under `UIAppFonts`; regen via `xcodegen`

**Out of scope:**
- The native iOS launch screen (the static frame iOS shows during binary load) — separate concern, can harmonize visually later.
- `OnboardingView`, `AuthView`, `MainTabView`.

## Rollout

Single PR:
- Branch: `feat/357-enhanced-welcome-and-splash`
- Title: `feat(ios): animated splash and polished welcome screen`
- Body opens with `Resolves #357`, lists modified files, includes the manual QA checklist
- Labels: inherit issue's `ios`, `ui`; species label `feat`
- Milestone: M29 (inherited from issue)
