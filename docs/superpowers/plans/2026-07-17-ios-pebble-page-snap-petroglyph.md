# iOS — Snap display on the Pebble's page (#599) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the iOS Pebble page top zone so the snap is shown entirely at its nearest aspect bucket with the petroglyph (backfill + outline + glyph) overlapping the top-right corner, using the new theme-aware glyph colors; petroglyph-only when there is no snap.

**Architecture:** A new presentational `SnapPetroglyphHeader` owns the layout (cover snap + top-right petroglyph, or centered petroglyph). `PebbleReadBanner` becomes the data+color wrapper: it loads the snap bytes, picks the `BannerAspect` bucket, computes scheme-aware colors, and feeds the existing `PebbleAnimatedRenderView` (the petroglyph) into the header. A theme-aware `EmotionPalette.petroglyphColors(intensity:scheme:)` implements the #599 color table, backed by two palette columns (`shaded_color`, `dark_color`) already added to the remote view.

**Dependency (DB, out of this branch):** the `shaded_color`/`dark_color` columns + the `v_emotions_with_palette` change are already applied on the remote, and the git migration + regenerated `database.ts` land via the parallel Android branch (`claude/issue-599-android-pebble-52orio`, migration `20260717000000_emotion_categories_shaded_dark.sql`). **This iOS branch adds no migration and no type regen** — the Swift client decodes the JSON columns directly (it does not consume `database.ts`), and the iOS unit tests use hardcoded palettes, so there is no DB dependency to reproduce here. A duplicate same-timestamp migration would collide, so we deliberately do not create one.

**Tech Stack:** SwiftUI (iOS 17), Swift Testing (`@Suite`/`@Test`/`#expect`), xcodegen + xcodebuild.

---

## File structure

- **Modify** `apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift` — add the `threeFour` (0.75) bucket.
- **Modify** `apps/ios/PebblesTests/BannerAspectTests.swift` — 3:4 selection, portrait → 3:4.
- **Modify** `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift` — decode `shaded_color`, `dark_color`.
- **Modify** `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift` — carry `dark`/`shaded`; add `petroglyphColors(forIntensity:scheme:)`.
- **Create** `apps/ios/PebblesTests/EmotionPalettePetroglyphColorsTests.swift` — the #599 table.
- **Create** `apps/ios/Pebbles/Features/Path/Read/SnapPetroglyphHeader.swift` — presentational layout.
- **Modify** `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` — data+color wrapper, drop reveal gate.

Tests run with: `npm run test --workspace=apps/ios` (xcodegen generate + `xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17'`). New Swift files are globbed by `project.yml` (`sources: - path: Pebbles` / `- path: PebblesTests`), so no `project.yml` edit is needed.

---

### Task 1: `BannerAspect` — add the 3:4 bucket

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift`
- Test: `apps/ios/PebblesTests/BannerAspectTests.swift`

- [ ] **Step 1: Update the tests** — replace the whole file with:

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

    @Test("4:3 source picks .fourThree")
    func fourThreeSource() {
        #expect(BannerAspect.nearest(to: 4.0 / 3.0) == .fourThree)
    }

    @Test("Square source picks .square")
    func squareSource() {
        #expect(BannerAspect.nearest(to: 1.0) == .square)
    }

    @Test("3:4 portrait source picks .threeFour")
    func threeFourSource() {
        #expect(BannerAspect.nearest(to: 3.0 / 4.0) == .threeFour)
    }

    @Test("Portrait 9:16 source picks .threeFour (nearest is 0.75, not 1.0)")
    func portraitSource() {
        // r ≈ 0.5625; |0.5625 - 0.75| = 0.1875 < |0.5625 - 1.0| = 0.4375 → 3:4 wins.
        #expect(BannerAspect.nearest(to: 9.0 / 16.0) == .threeFour)
    }

    @Test("3:2 source picks .fourThree (closer than 16:9)")
    func threeTwoSource() {
        // r = 1.5; |1.5 - 1.333| = 0.167; |1.5 - 1.778| = 0.278 → 4:3 wins.
        #expect(BannerAspect.nearest(to: 3.0 / 2.0) == .fourThree)
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
        #expect(BannerAspect.threeFour.cgRatio   == 3.0 / 4.0)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `npm run test --workspace=apps/ios`
Expected: FAIL — `.threeFour` is not a member of `BannerAspect`.

- [ ] **Step 3: Add the bucket** — replace the body of `BannerAspect.swift` with:

```swift
import CoreGraphics

/// Banner aspect-ratio bucket chosen for a source image. The pebble read
/// banner snaps the source's width/height ratio to the nearest of four fixed
/// buckets — 16:9, 4:3, 1:1, 3:4 — so landscape, square, and portrait uploads
/// are each shown at a sensible ratio (issue #599 added the 3:4 portrait
/// bucket).
///
/// Pure value type. No view dependencies; trivially unit-testable.
enum BannerAspect: Equatable {
    case sixteenNine
    case fourThree
    case square
    case threeFour

    /// CG ratio (width / height) for the bucket.
    var cgRatio: CGFloat {
        switch self {
        case .sixteenNine: return 16.0 / 9.0
        case .fourThree:   return 4.0 / 3.0
        case .square:      return 1.0
        case .threeFour:   return 3.0 / 4.0
        }
    }

    /// Pick the bucket whose `cgRatio` is closest to `ratio` (absolute
    /// distance). Portrait sources (`ratio < 1`) now bucket to `.threeFour`
    /// (0.75) rather than collapsing to `.square`.
    static func nearest(to ratio: CGFloat) -> BannerAspect {
        let candidates: [BannerAspect] = [.sixteenNine, .fourThree, .square, .threeFour]
        return candidates.min(by: { abs($0.cgRatio - ratio) < abs($1.cgRatio - ratio) })
            ?? .sixteenNine
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `npm run test --workspace=apps/ios`
Expected: PASS (all `BannerAspect` tests).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift apps/ios/PebblesTests/BannerAspectTests.swift
git commit -m "feat(ui): add 3:4 portrait aspect bucket (#599)"
```

---

### Task 2: Decode the new colors + theme-aware `petroglyphColors`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift`
- Test: `apps/ios/PebblesTests/EmotionPalettePetroglyphColorsTests.swift`

- [ ] **Step 1: Write the failing test** — create `EmotionPalettePetroglyphColorsTests.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

@Suite("EmotionPalette.petroglyphColors")
struct EmotionPalettePetroglyphColorsTests {

    // Distinct opaque hexes so each role is unambiguous in assertions.
    private func palette() -> EmotionPalette {
        EmotionPalette(
            primaryHex:   "#111111FF",
            secondaryHex: "#222222FF",
            lightHex:     "#333333FF",
            surfaceHex:   "#4444441A",
            shadedHex:    "#555555FF",
            darkHex:      "#666666FF"
        )!
    }

    // MARK: small / medium — theme-dependent

    @Test("small/medium light: stroke=primary, backfill=light")
    func smallMediumLight() {
        let colors = palette().petroglyphColors(forIntensity: 1, scheme: .light)
        #expect(colors.strokeHex == "#111111")
        #expect(colors.fillHex == "#333333")
        #expect(colors.fillOpacity == 1)
    }

    @Test("small/medium dark: stroke=secondary, backfill=dark")
    func smallMediumDark() {
        let colors = palette().petroglyphColors(forIntensity: 2, scheme: .dark)
        #expect(colors.strokeHex == "#222222")
        #expect(colors.fillHex == "#666666")
        #expect(colors.fillOpacity == 1)
    }

    // MARK: large — theme-independent

    @Test("large light: stroke=light, backfill=primary")
    func largeLight() {
        let colors = palette().petroglyphColors(forIntensity: 3, scheme: .light)
        #expect(colors.strokeHex == "#333333")
        #expect(colors.fillHex == "#111111")
        #expect(colors.fillOpacity == 1)
    }

    @Test("large dark: stroke=light, backfill=primary (same as light)")
    func largeDark() {
        let colors = palette().petroglyphColors(forIntensity: 3, scheme: .dark)
        #expect(colors.strokeHex == "#333333")
        #expect(colors.fillHex == "#111111")
        #expect(colors.fillOpacity == 1)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test --workspace=apps/ios`
Expected: FAIL — `EmotionPalette` has no `init(...shadedHex:darkHex:)` and no `petroglyphColors(forIntensity:scheme:)`.

- [ ] **Step 3: Add `shaded`/`dark` to `EmotionPalette` and the new method**

In `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift`, extend the stored properties, the init, and add the method.

3a. Add stored properties after the existing four `Color` lets and four `*Hex` lets:

```swift
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color
    let shaded: Color
    let dark: Color

    let primaryHex: String
    let secondaryHex: String
    let lightHex: String
    let surfaceHex: String
    let shadedHex: String
    let darkHex: String
```

3b. Replace the initializer signature and body:

```swift
    init?(
        primaryHex: String,
        secondaryHex: String,
        lightHex: String,
        surfaceHex: String,
        shadedHex: String,
        darkHex: String
    ) {
        // The palette hex columns on `emotion_categories` are populated by
        // hand in Supabase Studio and can carry stray surrounding whitespace.
        // Trim at the model boundary so the stored `*Hex` strings are clean
        // `#RRGGBBAA`: the SVG-injection helpers (`rgbHex` / `alphaComponent`
        // / `strokeHex`) gate on `count == 9`, which silently no-ops on a
        // padded string and would leak 8-digit hex into the render stack.
        let primaryHex = primaryHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let secondaryHex = secondaryHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let lightHex = lightHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let surfaceHex = surfaceHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let shadedHex = shadedHex.trimmingCharacters(in: .whitespacesAndNewlines)
        let darkHex = darkHex.trimmingCharacters(in: .whitespacesAndNewlines)
        guard
            let primary = Color(hex: primaryHex),
            let secondary = Color(hex: secondaryHex),
            let light = Color(hex: lightHex),
            let surface = Color(hex: surfaceHex),
            let shaded = Color(hex: shadedHex),
            let dark = Color(hex: darkHex)
        else {
            return nil
        }
        self.primary = primary
        self.secondary = secondary
        self.light = light
        self.surface = surface
        self.shaded = shaded
        self.dark = dark
        self.primaryHex = primaryHex
        self.secondaryHex = secondaryHex
        self.lightHex = lightHex
        self.surfaceHex = surfaceHex
        self.shadedHex = shadedHex
        self.darkHex = darkHex
    }
```

3c. In the `extension EmotionPalette { ... }` block (the one that already holds
`pebbleFrameColors(forIntensity:)`, `rgbHex`, `alphaComponent`), add the new
theme-aware method. Leave `pebbleFrameColors(forIntensity:)` untouched — it
still serves the timeline row:

```swift
    /// Theme-aware petroglyph colors for the pebble **page** (issue #599).
    /// "Stroke" = outline + glyph paths (one color); "backfill" = the
    /// silhouette fill behind them. Distinct from `pebbleFrameColors`, which
    /// keeps the older theme-neutral mapping for the timeline row.
    ///
    /// | intensity      | stroke (light / dark) | backfill (light / dark) |
    /// |----------------|-----------------------|-------------------------|
    /// | small, medium  | primary / secondary   | light / dark            |
    /// | large (3)      | light / light         | primary / primary       |
    func petroglyphColors(forIntensity intensity: Int, scheme: ColorScheme) -> PebbleFrameColors {
        let isDark = scheme == .dark
        switch intensity {
        case 3:
            return PebbleFrameColors(
                strokeHex:   EmotionPalette.rgbHex(lightHex),
                fillHex:     EmotionPalette.rgbHex(primaryHex),
                fillOpacity: EmotionPalette.alphaComponent(primaryHex)
            )
        default:
            let strokeSource = isDark ? secondaryHex : primaryHex
            let fillSource   = isDark ? darkHex : lightHex
            return PebbleFrameColors(
                strokeHex:   EmotionPalette.rgbHex(strokeSource),
                fillHex:     EmotionPalette.rgbHex(fillSource),
                fillOpacity: EmotionPalette.alphaComponent(fillSource)
            )
        }
    }
```

3d. Update the decoder in `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift`. Add the coding keys and decode the two new columns.

Add to the `CodingKeys` enum (after `surfaceColor`):

```swift
        case shadedColor   = "shaded_color"
        case darkColor     = "dark_color"
```

Replace the palette construction block (the `let primary … let surface` reads and the `guard let palette = EmotionPalette(...)`):

```swift
        let primary = try container.decode(String.self, forKey: .primaryColor)
        let secondary = try container.decode(String.self, forKey: .secondaryColor)
        let light = try container.decode(String.self, forKey: .lightColor)
        let surface = try container.decode(String.self, forKey: .surfaceColor)
        let shaded = try container.decode(String.self, forKey: .shadedColor)
        let dark = try container.decode(String.self, forKey: .darkColor)

        guard let palette = EmotionPalette(
            primaryHex: primary,
            secondaryHex: secondary,
            lightHex: light,
            surfaceHex: surface,
            shadedHex: shaded,
            darkHex: dark
        ) else {
            throw DecodingError.dataCorruptedError(
                forKey: .primaryColor,
                in: container,
                debugDescription: "Palette hex strings failed to parse"
            )
        }
        self.palette = palette
```

- [ ] **Step 4: Find and fix the other `EmotionPalette(` call sites**

The initializer signature changed, so every construction must pass the two new
args. `EmotionWithPalette.swift` (Step 3d) and the new test (Step 1) are already
done. The remaining sites are all in tests — patch each by inserting
`shadedHex:` and `darkHex:` immediately after the `surfaceHex:` argument, reusing
that site's existing opaque hexes (e.g. `shadedHex: <its secondaryHex value>,
darkHex: <its primaryHex value>`):

- `PebblesTests/PathPebbleRowNameColorTests.swift:8`
- `PebblesTests/PebbleFrameColorsTests.swift:10`, `:57`, `:77`
- `PebblesTests/EmotionPaletteTests.swift:56`, `:71`, `:101`
- `PebblesTests/LocalizationTests.swift:53`

Caution: if any of these sites deliberately passes an **invalid** hex to assert
the failable init returns `nil` (check `EmotionPaletteTests.swift`), keep the
new `shadedHex`/`darkHex` args **valid** so the test still isolates the field it
means to corrupt. Run `grep -rn "EmotionPalette(" apps/ios/Pebbles apps/ios/PebblesTests`
to confirm no site is missed. Expected: the project compiles with no "missing
argument for parameter" errors.

- [ ] **Step 5: Run tests to verify they pass**

Run: `npm run test --workspace=apps/ios`
Expected: PASS (`EmotionPalette.petroglyphColors` suite + existing suites).

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift apps/ios/PebblesTests/EmotionPalettePetroglyphColorsTests.swift
git commit -m "feat(ui): theme-aware petroglyph colors for pebble page (#599)"
```

---

### Task 3: `SnapPetroglyphHeader` — presentational layout

Pure layout, verified in previews/simulator (no unit test — iOS convention is
no view/UI tests for now).

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/SnapPetroglyphHeader.swift`

- [ ] **Step 1: Create the component**

```swift
import SwiftUI

/// Presentational top-zone layout for the pebble page (issue #599).
///
/// - **With a snap:** the photo fills the content width at its `BannerAspect`
///   bucket (cover-cropped, rounded), tilted −4°, with the petroglyph
///   overlapping the top-right corner, tilted +7°.
/// - **Without a snap:** the petroglyph is centered as the page heading.
///
/// Pure layout — it loads nothing. `PebbleReadBanner` owns the async snap load
/// and the color selection. Edit mode will reuse this view later with a
/// placeholder snap state, so it stays view-mode-agnostic.
struct SnapPetroglyphHeader<Petroglyph: View>: View {
    /// Decoded snap bytes; nil until they load (or when there is no snap).
    let snapImage: UIImage?
    /// Whether a snap is expected for this pebble. Drives the layout from first
    /// paint (petroglyph top-right) independent of whether `snapImage` has
    /// decoded, so the petroglyph never jumps from center to corner.
    let hasSnapSlot: Bool
    @ViewBuilder var petroglyph: () -> Petroglyph

    private let cornerRadius: CGFloat = 24
    private let petroglyphBox: CGFloat = 120
    private let petroglyphInset: CGFloat = 16
    private let snapTilt: Double = -4
    private let petroglyphTilt: Double = 7

    var body: some View {
        if hasSnapSlot {
            withSnapLayout
        } else {
            // No snap → petroglyph centered as heading content, no tilt.
            petroglyph()
                .frame(width: petroglyphBox, height: petroglyphBox)
                .frame(maxWidth: .infinity)
        }
    }

    private var withSnapLayout: some View {
        ZStack(alignment: .topTrailing) {
            snapArea
                .rotationEffect(.degrees(snapTilt))

            petroglyph()
                .frame(width: petroglyphBox, height: petroglyphBox)
                .rotationEffect(.degrees(petroglyphTilt))
                .offset(x: petroglyphInset, y: -petroglyphInset)
        }
        // Reserve the inset so the petroglyph poking past the top-right corner
        // isn't clipped by the ZStack bounds / surrounding VStack spacing.
        .padding(.top, petroglyphInset)
        .padding(.trailing, petroglyphInset)
    }

    @ViewBuilder
    private var snapArea: some View {
        // Bucket ratio is known only once the image decodes; until then the
        // placeholder holds a neutral square so the petroglyph already sits
        // top-right. On decode the image cross-fades in and the container
        // settles to its bucket ratio.
        let aspect = snapImage.map {
            BannerAspect.nearest(to: $0.size.width / max($0.size.height, 1))
        }
        Color.system.muted
            .aspectRatio(aspect?.cgRatio ?? 1, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay {
                if let snapImage {
                    Image(uiImage: snapImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .transition(.opacity)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .accessibilityHidden(true)
            .animation(.easeOut(duration: 0.25), value: snapImage)
    }
}

#Preview("No snap · petroglyph centered") {
    SnapPetroglyphHeader(snapImage: nil, hasSnapSlot: false) {
        RoundedRectangle(cornerRadius: 24)
            .fill(Color.system.secondary)
    }
    .padding(.horizontal, 16)
    .background(Color.system.background)
}

#Preview("Snap slot · placeholder (no bytes)") {
    SnapPetroglyphHeader(snapImage: nil, hasSnapSlot: true) {
        RoundedRectangle(cornerRadius: 24)
            .fill(Color.system.secondary)
    }
    .padding(.horizontal, 16)
    .background(Color.system.background)
}
```

- [ ] **Step 2: Verify it compiles + previews render**

Run: `npm run build --workspace=apps/ios`
Expected: BUILD SUCCEEDED. (Optionally open the file in Xcode and confirm both previews render: centered petroglyph, and a muted square placeholder with the petroglyph poking out top-right.)

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/SnapPetroglyphHeader.swift
git commit -m "feat(ui): SnapPetroglyphHeader top-zone layout (#599)"
```

---

### Task 4: Rewire `PebbleReadBanner` to the new layout + colors

Replace the bottom-centered reveal banner with the data+color wrapper around
`SnapPetroglyphHeader`. Drop the timed reveal gate (show-together). Compute
scheme-aware colors. Fall back to the centered (no-snap) layout on a settled
load failure.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`

- [ ] **Step 1: Replace the file body**

```swift
import SwiftUI
import os

/// Top zone of the pebble read view (issue #599).
///
/// Loads the snap bytes in the background and composes them with the petroglyph
/// via `SnapPetroglyphHeader`:
/// - With a snap → the photo (cover-cropped to its `BannerAspect` bucket, tilted)
///   with the petroglyph overlapping the top-right corner.
/// - Without a snap (or after a settled load failure) → the petroglyph centered
///   as the page heading.
///
/// The petroglyph and snap appear together — the petroglyph keeps its own
/// entry animation (`PebbleAnimatedRenderView`); there is no timed reveal gate.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    let renderSvg: String?
    let renderVersion: String?
    let emotionId: UUID
    let valence: Valence

    @Environment(SnapURLCache.self) private var snapURLs
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme

    @State private var loadedImage: UIImage?
    /// True once a snap load attempt has settled in failure (no image). Used to
    /// collapse to the centered layout instead of holding an empty placeholder.
    @State private var loadFailed: Bool = false

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-read-banner")

    /// A snap slot is expected when the pebble has a storage path AND the load
    /// hasn't hard-failed. A still-loading snap keeps the slot (placeholder);
    /// only a settled failure drops to the centered petroglyph.
    private var hasSnapSlot: Bool {
        snapStoragePath != nil && !loadFailed
    }

    var body: some View {
        SnapPetroglyphHeader(snapImage: loadedImage, hasSnapSlot: hasSnapSlot) {
            renderedPebble
        }
        .frame(maxWidth: .infinity)
        .task(id: snapStoragePath) {
            await loadPhotoIfNeeded()
        }
    }

    // MARK: - Petroglyph (backfill + outline + glyph)

    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            let palette = palettes.palette(for: emotionId)
            let colors = palette?.petroglyphColors(forIntensity: valence.intensity, scheme: colorScheme)
            let strokeHex = colors?.strokeHex ?? Color.accent.primaryHex
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: Color(hex: strokeHex) ?? Color.accent.primary,
                strokeColorHex: strokeHex,
                fillHex: colors?.fillHex ?? Color.accent.primaryHex,
                fillOpacity: colors?.fillOpacity ?? 1,
                size: valence.sizeGroup,
                polarity: valence.polarity,
                renderVersion: renderVersion
            )
        } else {
            EmptyView()
        }
    }

    // MARK: - Snap load

    private func loadPhotoIfNeeded() async {
        loadedImage = nil
        loadFailed = false
        guard let path = snapStoragePath else { return }
        do {
            let urls = try await snapURLs.signedURLs(storagePath: path)
            var request = URLRequest(url: urls.original)
            request.timeoutInterval = 30
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let image = UIImage(data: data) else {
                Self.logger.error("decode failed for \(path, privacy: .public)")
                loadFailed = true
                return
            }
            loadedImage = image
        } catch {
            Self.logger.error(
                "photo load failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
            loadFailed = true
        }
    }
}

#Preview("Without photo · medium") {
    let supabase = SupabaseService()
    return PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionId: UUID(),
        valence: .neutralMedium
    )
    .padding()
    .background(Color.system.background)
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(SnapURLCache(client: supabase.client))
}

#Preview("Without photo · large") {
    let supabase = SupabaseService()
    return PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionId: UUID(),
        valence: .highlightLarge
    )
    .padding()
    .background(Color.system.background)
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(SnapURLCache(client: supabase.client))
}
```

- [ ] **Step 2: Verify the build (types + views compile)**

Run: `npm run build --workspace=apps/ios`
Expected: BUILD SUCCEEDED. No reference remains to the removed `BannerAspect`
usage inside `PebbleReadBanner` (it now lives in `SnapPetroglyphHeader`), no
reference to `animationFinished` / `revealPhoto` / `PebbleAnimationTimings`
inside this file.

- [ ] **Step 3: Run the full test suite**

Run: `npm run test --workspace=apps/ios`
Expected: PASS. In particular `PebbleReadBanner`-adjacent suites still build
(the reveal-gate state was internal, not part of any test's public surface).

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift
git commit -m "feat(ui): snap + top-right petroglyph on pebble page (#599)"
```

---

### Task 5: Verify in the simulator + final checks

**Files:** none (verification only).

- [ ] **Step 1: Confirm no stale reveal-gate tests reference removed state**

Run: `grep -rn "revealPhoto\|animationFinished\|BannerAspect" apps/ios/PebblesTests`
Expected: only `BannerAspectTests.swift` hits (from Task 1). If any test
references `revealPhoto`/`animationFinished`, delete those assertions — the
timed-reveal choreography was removed. (None are expected; the current suite
has no such test.)

- [ ] **Step 2: Run the app and exercise the pebble page**

Run: `npm run build --workspace=apps/ios` then launch in the simulator (or use the `run` skill).
Verify by observation:
- A pebble **with** a snap: photo shown entirely at a sensible bucket, tilted −4°, petroglyph overlapping the top-right corner tilted +7°, appearing together with its entry animation. Colors match the #599 table (toggle Dark Mode: small/medium backfill switches light→dark, stroke primary→secondary; large stays light stroke / primary backfill).
- A **portrait** snap buckets to 3:4 (taller than wide) rather than square.
- A pebble **without** a snap: petroglyph centered as the heading, no tilt.

- [ ] **Step 3: Localizable / strings check**

No new user-facing strings are introduced (the snap image is
`accessibilityHidden`). Confirm: `git diff --stat` shows no change to
`apps/ios/Pebbles/Resources/Localizable.xcstrings`. If it changed unexpectedly,
open it in Xcode and confirm no `New`/`Stale` entries.

- [ ] **Step 4: Arkaik check**

No screen/route/data-model/endpoint was added or renamed — the pebble page node
already exists. Skip the Arkaik update. (Confirm the pebble read view already
has a node in `docs/arkaik/bundle.json`; if for some reason it doesn't, add the
map entry per the `arkaik` skill — otherwise no-op.)

- [ ] **Step 5: Lint**

Run: `npm run lint --workspace=apps/ios`
Expected: no new violations in the touched files. Fix any that appear.

---

## Notes for the PR (not a build step)

- **Lab Note (EN/FR):** this is a `feat` touching a user-visible view, so the PR
  body needs a bilingual Lab Note proposal (title + 1–2 sentence EN/FR summary)
  per the root `CLAUDE.md` PR checklist.
- **Decision log:** likely a no-op — this implements a designed feature, not a
  reversed/significant architectural decision. Skip unless something notable
  surfaces during implementation.
- **Labels/milestone:** inherit from issue #599 — `feat`, `ui`, milestone
  "M37 · Vision Pebble Page". Confirm with the requester before opening the PR.
- **Cross-surface:** the `shaded_color`/`dark_color` columns + view now exist for
  web/Android to adopt the same #599 layout in their own follow-ups.
```
