# iOS Pebble Read View — Improve Picture Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the photo-load glitch on the iOS pebble read view and replace the fixed 16:9 banner with a banner that adopts the closest of {16:9, 4:3, 1:1} based on the source image, revealed once both the stroke animation and the photo load are complete.

**Architecture:** A pure `BannerAspect` helper buckets a source `width/height` ratio. `PebbleReadBanner` owns its photo load directly (replacing `SnapImageView` for this surface), runs two independent gates (1.8s animation timer + image bytes ready), and flips a single `revealPhoto` state inside `withAnimation` so SwiftUI interpolates the layout transition.

**Tech Stack:** SwiftUI (iOS 17+), Swift Testing (`Testing` module), `@Observable`, `os.Logger`, Supabase Storage signed URLs, `URLSession.shared`. Project source-of-truth: `apps/ios/project.yml` (regenerate Xcode project via `npm run generate --workspace=@pbbls/ios` after adding files).

**Spec:** `docs/superpowers/specs/2026-04-30-ios-improve-picture-display-design.md`

**Branch:** `feat/335-improve-picture-display` (already created and contains the spec commit).

---

## File Structure

| Path | Action | Responsibility |
|---|---|---|
| `apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift` | Create | Pure helper: pick nearest of `{16:9, 4:3, 1:1}` for a given ratio. |
| `apps/ios/PebblesTests/BannerAspectTests.swift` | Create | Swift Testing coverage for `BannerAspect.nearest(to:)`. |
| `apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift` | Modify | Add `Timings.totalDuration` computed property. |
| `apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift` | Modify | Add a test for `totalDuration`. |
| `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` | Modify | Own photo load, run reveal gates, flip layout via `withAnimation`. |

`SnapImageView.swift` is left untouched — it remains available for any other caller, but `PebbleReadBanner` no longer uses it.

---

## Task 1: BannerAspect helper (TDD)

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift`
- Create: `apps/ios/PebblesTests/BannerAspectTests.swift`

- [ ] **Step 1: Write the failing test**

Create `apps/ios/PebblesTests/BannerAspectTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("BannerAspect")
struct BannerAspectTests {

    @Test("16:9 source picks .sixteenNine")
    func sixteenNineSource() {
        #expect(BannerAspect.nearest(to: 16.0 / 9.0) == .sixteenNine)
    }

    @Test("3:2 source picks .fourThree (closer than 16:9)")
    func threeTwoSource() {
        // r = 1.5; |1.5 - 1.333| = 0.167; |1.5 - 1.778| = 0.278 → 4:3 wins.
        #expect(BannerAspect.nearest(to: 3.0 / 2.0) == .fourThree)
    }

    @Test("4:3 source picks .fourThree")
    func fourThreeSource() {
        #expect(BannerAspect.nearest(to: 4.0 / 3.0) == .fourThree)
    }

    @Test("Square source picks .square")
    func squareSource() {
        #expect(BannerAspect.nearest(to: 1.0) == .square)
    }

    @Test("Portrait 9:16 source picks .square (no portrait bucket)")
    func portraitSource() {
        // r ≈ 0.5625; closer to 1.0 than to 1.333 or 1.778.
        #expect(BannerAspect.nearest(to: 9.0 / 16.0) == .square)
    }

    @Test("Extreme landscape 21:9 source picks .sixteenNine")
    func extremeLandscape() {
        #expect(BannerAspect.nearest(to: 21.0 / 9.0) == .sixteenNine)
    }

    @Test("cgRatio matches the bucket")
    func cgRatioValues() {
        #expect(BannerAspect.sixteenNine.cgRatio == 16.0 / 9.0)
        #expect(BannerAspect.fourThree.cgRatio   == 4.0 / 3.0)
        #expect(BannerAspect.square.cgRatio      == 1.0)
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project so the new test file is picked up, then run the tests to verify they fail**

Run from repo root:

```bash
npm run generate --workspace=@pbbls/ios
```

Then run the new test suite:

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/BannerAspectTests \
  test 2>&1 | tail -30
```

Expected: build fails with "cannot find type 'BannerAspect' in scope" (or equivalent).

- [ ] **Step 3: Write the minimal implementation**

Create `apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift`:

```swift
import CoreGraphics

/// Banner aspect-ratio bucket chosen for a source image. The pebble read
/// banner snaps the source's width/height ratio to the nearest of three
/// fixed buckets — 16:9, 4:3, 1:1 — so portrait or near-square uploads no
/// longer get cropped to a forced landscape strip.
///
/// Pure value type. No view dependencies; trivially unit-testable.
enum BannerAspect: Equatable {
    case sixteenNine
    case fourThree
    case square

    /// CG ratio (width / height) for the bucket.
    var cgRatio: CGFloat {
        switch self {
        case .sixteenNine: return 16.0 / 9.0
        case .fourThree:   return 4.0 / 3.0
        case .square:      return 1.0
        }
    }

    /// Pick the bucket whose `cgRatio` is closest to `ratio` (absolute
    /// distance). Portrait sources (`ratio < 1`) always bucket to `.square`
    /// since 1.0 is the smallest of the three candidates — no special case.
    static func nearest(to ratio: CGFloat) -> BannerAspect {
        let candidates: [BannerAspect] = [.sixteenNine, .fourThree, .square]
        return candidates.min(by: { abs($0.cgRatio - ratio) < abs($1.cgRatio - ratio) })
            ?? .sixteenNine
    }
}
```

- [ ] **Step 4: Regenerate and run the tests to verify they pass**

```bash
npm run generate --workspace=@pbbls/ios
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/BannerAspectTests \
  test 2>&1 | tail -30
```

Expected: `Test Suite 'BannerAspectTests' passed`, all 7 tests succeed.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift \
        apps/ios/PebblesTests/BannerAspectTests.swift \
        apps/ios/Pebbles.xcodeproj
git commit -m "feat(ios): add BannerAspect helper for picture sizing (#335)"
```

(`Pebbles.xcodeproj` is git-ignored per project conventions, so the `git add` for it is a no-op — `git status` will not show the regenerated project. Only the two source files commit.)

---

## Task 2: PebbleAnimationTimings.totalDuration (TDD)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift`
- Modify: `apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift`

- [ ] **Step 1: Write the failing test**

Open `apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift`. Add this `@Test` inside the existing `@Suite struct PebbleAnimationTimingsTests`:

```swift
@Test("totalDuration equals settle.delay + settle.duration")
func totalDuration() throws {
    let timings = try #require(PebbleAnimationTimings.forVersion("0.1.0"))
    #expect(timings.totalDuration == timings.settle.delay + timings.settle.duration)
    #expect(timings.totalDuration > 0)
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/PebbleAnimationTimingsTests/totalDuration \
  test 2>&1 | tail -20
```

Expected: build error — `value of type 'PebbleAnimationTimings.Timings' has no member 'totalDuration'`.

- [ ] **Step 3: Add the extension**

In `apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift`, append (after the closing `}` of `enum PebbleAnimationTimings`):

```swift
extension PebbleAnimationTimings.Timings {
    /// Total time from `onAppear` until the settle pulse ends. Used by the
    /// pebble read banner to gate the photo reveal until the stroke animation
    /// has finished drawing.
    var totalDuration: Double { settle.delay + settle.duration }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -only-testing:PebblesTests/PebbleAnimationTimingsTests \
  test 2>&1 | tail -30
```

Expected: all tests in `PebbleAnimationTimingsTests` pass, including `totalDuration`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift \
        apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift
git commit -m "feat(ios): expose Timings.totalDuration for reveal gating (#335)"
```

---

## Task 3: PebbleReadBanner — own the photo load

**Goal:** Replace `SnapImageView` usage in the read banner with a self-owned async load, so the banner has direct access to the loaded `UIImage` and can size itself by aspect ratio. No layout/animation changes yet — that's Task 4.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`

**Note:** there are no UI tests in this project (per `apps/ios/CLAUDE.md`: "No UI tests for now"). Verification for Tasks 3–4 is build + Xcode previews + manual smoke.

- [ ] **Step 1: Replace the file's contents**

Open `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` and replace the entire contents with:

```swift
import SwiftUI
import os

/// Top zone of the pebble read view.
///
/// Sequencing (issue #335):
/// 1. Phase 1 — render the no-photo layout regardless of whether the pebble
///    has a snap. Pebble centered in its 120pt zone. Photo bytes load in the
///    background; the stroke animation runs in parallel.
/// 2. Phase 2 — once both the stroke animation has finished AND the bytes
///    have been decoded into a `UIImage`, flip `revealPhoto` inside a
///    `withAnimation`. The banner inserts above the pebble box at the
///    bucketed aspect ratio (`BannerAspect`), the pebble box settles into
///    its overlap position, and content below shifts down.
///
/// Without a snap, Phase 2 never fires.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    let renderSvg: String?
    let renderVersion: String?
    let emotionColorHex: String
    let valence: Valence

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var loadedImage: UIImage?
    @State private var animationFinished: Bool = false
    @State private var revealPhoto: Bool = false

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-read-banner")

    private let bannerCornerRadius: CGFloat = 24
    private let boxSize: CGFloat = 120
    private let boxCornerRadius: CGFloat = 24

    var body: some View {
        // Phase 1 layout (no banner). Phase 2 is wired in Task 4.
        renderedPebble
            .frame(maxWidth: .infinity, minHeight: boxSize)
            .task(id: snapStoragePath) {
                await loadPhotoIfNeeded()
            }
            .task(id: renderVersion) {
                await waitForAnimationToFinish()
            }
    }

    // MARK: - Phase 1 background work

    private func loadPhotoIfNeeded() async {
        guard let path = snapStoragePath else { return }
        do {
            let urls = try await PebbleSnapRepository(client: supabase.client)
                .signedURLs(storagePrefix: path)
            let (data, _) = try await URLSession.shared.data(for: URLRequest(url: urls.original))
            guard let image = UIImage(data: data) else {
                Self.logger.error(
                    "decode failed for \(path, privacy: .public)"
                )
                return
            }
            loadedImage = image
        } catch {
            Self.logger.error(
                "photo load failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
        }
    }

    private func waitForAnimationToFinish() async {
        // Static pebble (no animation) → reveal as soon as the photo is ready.
        guard !reduceMotion,
              let timings = PebbleAnimationTimings.forVersion(renderVersion) else {
            animationFinished = true
            return
        }
        do {
            try await Task.sleep(for: .seconds(timings.totalDuration))
        } catch {
            return // cancellation: view disappeared, leave state as-is.
        }
        animationFinished = true
    }

    // MARK: - Pebble rendering (unchanged)

    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: emotionColorHex,
                renderVersion: renderVersion
            )
            .frame(height: pebbleHeight)
        } else {
            EmptyView()
        }
    }

    /// Pebble height inside the 120pt box, scaled by valence size group
    /// so higher-intensity pebbles read bigger than lower-intensity ones
    /// while still fitting comfortably.
    private var pebbleHeight: CGFloat {
        switch valence.sizeGroup {
        case .small:  return 80
        case .medium: return 100
        case .large:  return 116
        }
    }
}

#Preview("Without photo · medium") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .neutralMedium
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Without photo · large") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .highlightLarge
    )
    .padding()
    .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Build and verify the project still compiles**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build 2>&1 | tail -20
```

Expected: `BUILD SUCCEEDED`. The view file currently ignores `loadedImage` and `revealPhoto` — that's intentional for this task; Task 4 wires them into the view body. Swift may warn about unused variables; that's acceptable mid-plan.

- [ ] **Step 3: Run all existing tests to confirm nothing regressed**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  test 2>&1 | tail -30
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift
git commit -m "refactor(ios): pebble read banner owns its photo load (#335)"
```

---

## Task 4: PebbleReadBanner — reveal gating + bucketed banner

**Goal:** Wire the loaded image and animation gate into the view body. When both are ready, flip `revealPhoto` inside `withAnimation` and render the banner above the pebble at the bucketed aspect ratio with the pebble box overlapping its bottom edge.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`

- [ ] **Step 1: Replace the `body` and add the reveal logic**

In `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`, replace the `body` block with:

```swift
    var body: some View {
        VStack(spacing: 0) {
            if revealPhoto, let image = loadedImage {
                bannerWithPhoto(image: image)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            } else {
                renderedPebble
                    .frame(maxWidth: .infinity, minHeight: boxSize)
            }
        }
        .task(id: snapStoragePath) {
            await loadPhotoIfNeeded()
        }
        .task(id: renderVersion) {
            await waitForAnimationToFinish()
        }
        .onChange(of: loadedImage) { _, _ in revealIfReady() }
        .onChange(of: animationFinished) { _, _ in revealIfReady() }
    }

    private func revealIfReady() {
        guard !revealPhoto, loadedImage != nil, animationFinished else { return }
        let animation: Animation = reduceMotion
            ? .easeOut(duration: 0.25)
            : .easeOut(duration: 0.45)
        withAnimation(animation) {
            revealPhoto = true
        }
    }

    // MARK: - Phase 2 banner

    @ViewBuilder
    private func bannerWithPhoto(image: UIImage) -> some View {
        let aspect = BannerAspect.nearest(to: image.size.width / max(image.size.height, 1))
        // Half-overlap pattern: the pebble box renders as an overlay that
        // intentionally extends below the banner's bottom edge. Do NOT wrap
        // this view in a `.clipShape` / `.clipped()` ancestor — the
        // overflow is the design.
        Image(uiImage: image)
            .resizable()
            .aspectRatio(contentMode: .fill)
            .frame(maxWidth: .infinity)
            .aspectRatio(aspect.cgRatio, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: bannerCornerRadius))
            .accessibilityHidden(true)
            .overlay(alignment: .bottom) {
                pebbleBox
                    .offset(y: boxSize / 2)
            }
            .padding(.bottom, boxSize / 2)
            .frame(maxWidth: .infinity)
    }

    private var pebbleBox: some View {
        renderedPebble
            .frame(width: boxSize, height: boxSize)
            .background(
                RoundedRectangle(cornerRadius: boxCornerRadius)
                    .fill(Color.pebblesBackground)
            )
    }
```

(Leave the existing `renderedPebble`, `pebbleHeight`, `loadPhotoIfNeeded`, `waitForAnimationToFinish` from Task 3 in place. The previous body is fully replaced by the new one above.)

- [ ] **Step 2: Add a "with photo" preview**

At the bottom of the file, add a third `#Preview` block (next to the existing two no-photo previews):

```swift
#Preview("With photo (preview-only stub)") {
    // Preview cannot reach Supabase Storage; this preview only shows the
    // no-photo path. Manual smoke verification (Task 5) covers the with-photo
    // sequencing in the simulator.
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .neutralMedium
    )
    .padding()
    .background(Color.pebblesBackground)
}
```

- [ ] **Step 3: Build**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build 2>&1 | tail -20
```

Expected: `BUILD SUCCEEDED`, no warnings about unused `loadedImage` / `revealPhoto`.

- [ ] **Step 4: Run all tests**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  test 2>&1 | tail -30
```

Expected: all tests pass (`BannerAspectTests`, `PebbleAnimationTimingsTests` including `totalDuration`, plus the existing suites).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift
git commit -m "feat(ios): bucketed banner with sequenced photo reveal (#335)"
```

---

## Task 5: Manual smoke verification

**Goal:** Confirm the four behaviors that build + previews can't verify — sequencing, aspect-ratio bucketing, Reduce Motion, and the no-photo path.

**Files:** none (manual checks).

- [ ] **Step 1: Run the app in the simulator**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  build 2>&1 | tail -10
```

Then either open `apps/ios/Pebbles.xcodeproj` in Xcode and Cmd+R, or use `xcrun simctl install` + `xcrun simctl launch`. Sign in with a test account that has at least three pebbles: one without a snap, one with a landscape snap, and one with a portrait snap.

- [ ] **Step 2: Verify Phase 1 → Phase 2 sequencing on a pebble with a landscape snap**

Open the pebble read sheet. Expected:
- For ~1.8s the pebble draws on its own with no photo visible.
- After the settle pulse ends, the banner fades + slides in above the pebble; content below shifts down. No layout flash, no photo flicker mid-animation.

- [ ] **Step 3: Verify aspect-ratio bucketing**

Open a pebble whose snap is a portrait or square photo. Expected: the banner renders square (1:1), not stretched, with the photo center-cropped to cover. Open a 3:2 landscape pebble: banner renders 4:3. Open a 16:9 pebble: banner renders 16:9.

- [ ] **Step 4: Verify Reduce Motion**

Settings → Accessibility → Motion → Reduce Motion ON. Reopen a pebble with a snap. Expected: pebble renders statically (no stroke draw), and the photo fades in (no slide) as soon as bytes arrive — no 1.8s wait.

- [ ] **Step 5: Verify the no-photo path**

Open a pebble without a snap. Expected: identical to today's no-photo layout. No banner ever inserts. No log lines from the `pebble-read-banner` category.

- [ ] **Step 6: Verify the failure path (best-effort)**

Toggle the simulator to airplane mode after the read sheet has opened (so the signed URL or the byte fetch fails). Expected: stroke animation completes normally, banner never reveals, one error log line in Console under `pebble-read-banner`.

- [ ] **Step 7: No commit**

This task only verifies; nothing to commit.

---

## Task 6: Final lint, build, and PR

**Files:** none.

- [ ] **Step 1: Run lint**

```bash
npm run lint
```

Expected: no errors. (The web/admin apps' lint may also run; only iOS-touched code should produce diagnostics, and there should be none.)

- [ ] **Step 2: Run the full iOS build + tests one more time**

```bash
xcodebuild -project apps/ios/Pebbles.xcodeproj \
  -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  test 2>&1 | tail -30
```

Expected: `TEST SUCCEEDED`.

- [ ] **Step 3: Push the branch and open the PR**

```bash
git push -u origin feat/335-improve-picture-display
```

Then open the PR with `gh pr create`, following the PR Workflow Checklist in `CLAUDE.md`:

- Title: `feat(ios): improve picture display on pebble read view`
- Body starts with `Resolves #335`
- Inherit labels (`feat`, `ios`, `ui`) and milestone (`M25 · Improved core UX`) from the issue — confirm with the user before applying.

```bash
gh pr create \
  --title "feat(ios): improve picture display on pebble read view" \
  --body "$(cat <<'EOF'
Resolves #335

## Summary
- Pebble stroke animation now has exclusive stage time; the photo loads in the background and reveals only after both the 1.8s animation and the byte decode are complete.
- Banner snaps to the closest of {16:9, 4:3, 1:1} based on the source image, replacing the fixed 16:9 strip. Portrait sources bucket to 1:1 with center-cropped cover.
- Reduce Motion respected: opacity-only fade, no animation gate.
- No schema changes, no upload-pipeline changes.

## Key files
- `apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift` (new)
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`
- `apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift`
- `apps/ios/PebblesTests/BannerAspectTests.swift` (new)
- `apps/ios/PebblesTests/PebbleAnimationTimingsTests.swift`

## Test plan
- [x] Unit: `BannerAspectTests` covers 16:9, 3:2→4:3, 4:3, square, portrait→square, 21:9
- [x] Unit: `PebbleAnimationTimingsTests` includes `totalDuration`
- [x] Manual: sequencing on landscape snap (Phase 1 → Phase 2 reveal)
- [x] Manual: bucketing on portrait, square, 3:2, 16:9 sources
- [x] Manual: Reduce Motion (no slide, no 1.8s wait)
- [x] Manual: no-snap pebble unchanged
- [x] Manual: airplane-mode failure path (no reveal, log line)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 4: Confirm labels and milestone with the user before they're applied**

Per project rules, never create a PR without labels and milestone. The default proposed above is `feat`, `ios`, `ui`, milestone `M25 · Improved core UX` (inherited from the issue). Wait for user confirmation, then apply with `gh pr edit <number> --add-label … --milestone …` if `gh pr create` didn't accept them inline.

---

## Self-review

**Spec coverage:**
- Phase 1/Phase 2 sequencing → Task 3 (load) + Task 4 (reveal) ✓
- 1.8s + bytes-loaded gates → Task 4 `revealIfReady` ✓
- Aspect-ratio bucketing → Task 1 + Task 4 `bannerWithPhoto` ✓
- Reduce Motion behavior → Task 4 (`waitForAnimationToFinish` early-returns; opacity-only transition) ✓
- Failure logging → Task 3 `loadPhotoIfNeeded` (signed URL + decode + bytes) ✓
- No schema / no `SnapImageView` removal / no edit-form changes → respected; `SnapImageView` left untouched ✓
- Manual smoke for the four UX-critical behaviors → Task 5 ✓

**Placeholders:** none ("TBD", "TODO", etc. absent; every code step contains the actual code).

**Type consistency:** `BannerAspect.nearest(to:)`, `BannerAspect.cgRatio`, `Timings.totalDuration`, `loadedImage`, `animationFinished`, `revealPhoto`, `revealIfReady()`, `loadPhotoIfNeeded()`, `waitForAnimationToFinish()` — all referenced consistently across tasks.
