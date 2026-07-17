# iOS Handcrafted Logo Loader Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fake Rive splash loader with a native handcrafted glyph loader that draws the logo on, then boils it (#555) until the app is genuinely ready.

**Architecture:** Reuse the existing wobble engine (`SVGPathParser` → `WobblePathFlattener` → `WobbleOutlineBuilder` + `SVGTurbulence`) to build three boil variants (seeds 3/4/5) of the issue's logo SVG at launch, cached. A new `HandcraftedLogoView` plays a phased draw-on reveal, then a 4fps ping-pong boil, gated by real readiness in `RootView` (auth + reference-data load attempts settled) instead of a hardcoded timer.

**Tech Stack:** SwiftUI (iOS 17), Swift Testing, xcodegen. No new dependencies.

**Testing posture:** Per `apps/ios/CLAUDE.md` ("No UI tests for now"), pure logic (`LogoLoaderArt` parsing + variant building; service flags) is covered by Swift Testing unit tests (TDD). View/wiring tasks (`HandcraftedLogoView`, `RootView`, `WelcomeView`) are verified by `npm run build --workspace=@pbbls/ios` + manual simulator, matching how the existing wobble render views are verified.

**Reference:** spec at `docs/superpowers/specs/2026-07-17-ios-handcrafted-logo-loader-design.md`.

---

## File Structure

- **Create** `apps/ios/Pebbles/Resources/pbbls-logo-loader.svg` — the issue's exact logo SVG (fossil-as-strokes, opened strokes). Auto-bundled by xcodegen (same as `Resources/Outlines/*.svg`).
- **Create** `apps/ios/Pebbles/Features/Welcome/LogoLoaderArt.swift` — parse the SVG into reveal groups + render modes; build 3 wobble boil variants. Pure logic.
- **Create** `apps/ios/Pebbles/Features/Welcome/HandcraftedLogoView.swift` — the loader view (drawing → boiling → settled; Reduce Motion aware).
- **Modify** `apps/ios/Pebbles/Services/EmotionPaletteService.swift` — add `didFinishLoading`.
- **Modify** `apps/ios/Pebbles/Services/ReferenceDataService.swift` — add `didFinishLoading`.
- **Modify** `apps/ios/Pebbles/RootView.swift` — replace the 2.5s timer gate with the real readiness gate.
- **Modify** `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift` — swap the Rive logo for `HandcraftedLogoView`.
- **Create** `apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift` — unit tests for parsing + variants.

---

## Task 1: Bundle the logo SVG resource

**Files:**
- Create: `apps/ios/Pebbles/Resources/pbbls-logo-loader.svg`
- Test: `apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift`

- [ ] **Step 1: Create the SVG asset**

Create `apps/ios/Pebbles/Resources/pbbls-logo-loader.svg` with the exact SVG from issue #598 (verbatim):

```svg
<svg width="251" height="251" viewBox="0 0 251 251" fill="none" xmlns="http://www.w3.org/2000/svg">
<g id="pbbls-logo">
<path id="pebble-outline" d="M9.73975 204.602C-14.292 160.776 30.442 46.7762 73.0724 14.3301C141.189 -37.514 261.087 101.057 246.835 177.176C232.368 254.444 48.7188 275.688 9.73975 204.602Z" stroke="black" stroke-width="6"/>
<g id="creature">
<path id="creature-01-body" d="M50.1204 116.649C52.495 113.084 58.3131 109.948 67.5745 107.241C75.7768 104.843 75.8845 98.2713 81.7615 92.5376C84.851 89.5234 85.8264 89.82 84.6879 93.4274C83.5494 97.0347 83.139 100.229 83.4566 103.012C83.7743 105.794 86.8269 109.378 92.6145 113.765C98.4021 118.152 101.181 123.301 100.951 129.211C100.721 135.122 101.131 139.661 102.182 142.83C103.233 145.998 105.049 149.39 107.629 153.007C110.209 156.623 115.928 159.881 124.785 162.78C133.642 165.679 139.889 166.74 143.525 165.961C147.162 165.182 152.525 163.111 159.615 159.749C166.705 156.386 169.744 156.01 168.732 158.621C167.72 161.232 165.057 165.275 160.742 170.75C156.428 176.224 150.675 180.965 143.484 184.971C136.293 188.977 128.243 190.936 119.332 190.847C110.422 190.758 101.333 189.034 92.0665 185.677C82.8 182.319 76.0589 179.06 71.8431 175.899C67.6274 172.739 64.22 169.432 61.6211 165.98C59.0221 162.528 57.2556 159.232 56.3214 156.093C55.3872 152.955 55.1077 148.73 55.4831 143.419C55.8584 138.108 54.4648 133.209 51.3022 128.724C48.1397 124.238 47.7457 120.213 50.1204 116.649Z" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-02-ear" d="M94.4112 111.956C102.002 109.39 107.935 108.631 112.212 109.68C116.488 110.728 117.815 112.861 116.191 116.078C114.566 119.296 111.709 121.597 107.62 122.983L101.485 125.062" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-03-ear" d="M47.0504 119.833L40.9548 115.256C36.891 112.205 35.2844 109.641 36.1348 107.565C36.9852 105.489 38.4318 104.192 40.4746 103.673C42.4793 103.164 47.8211 105.049 56.5 109.329" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-04-left_eye" d="M70.5821 120.795C71.1167 119.285 70.3258 117.629 68.8155 117.096C67.3052 116.563 65.6473 117.355 65.1127 118.865L67.8474 119.83L70.5821 120.795ZM64.8837 119.511C64.349 121.021 65.14 122.677 66.6503 123.21C68.1606 123.743 69.8184 122.951 70.3531 121.442L67.6184 120.477L64.8837 119.511ZM67.8474 119.83L65.1127 118.865L64.8837 119.511L67.6184 120.477L70.3531 121.442L70.5821 120.795L67.8474 119.83Z" fill="black"/>
<path id="creature-05-right_eye" d="M80.6375 122.674C80.6377 121.018 79.2942 119.677 77.6367 119.678C75.9793 119.679 74.6356 121.023 74.6354 122.679L77.6365 122.677L80.6375 122.674ZM74.6353 123.692C74.6352 125.349 75.9786 126.69 77.6361 126.689C79.2935 126.688 80.6372 125.344 80.6374 123.688L77.6364 123.69L74.6353 123.692ZM77.6365 122.677L74.6354 122.679L74.6353 123.692L77.6364 123.69L80.6374 123.688L80.6375 122.674L77.6365 122.677Z" fill="black"/>
<path id="creature-06-smile" d="M50.4253 126.937C56.9517 131.138 62.6899 133.673 67.6399 134.542C72.5898 135.411 76.3341 135.633 78.8728 135.207" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-07-paw" d="M52.3611 152.688C44.4601 153.102 41.7903 154.454 44.3517 156.745C46.9131 159.035 47.6156 161.326 46.459 163.615C45.3025 165.905 46.9684 166.378 51.4567 165.033L58.1893 163.017" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-08-paw" d="M68.2201 173.091C56.243 179.227 53.0891 182.518 58.7584 182.965C64.4277 183.412 67.7931 185.474 68.8547 189.151C69.9163 192.827 71.3788 192.898 73.2422 189.364L76.0374 184.063" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-09-paw" d="M127.373 193.668C130.827 202.826 133.049 205.835 134.038 202.695C135.027 199.556 136.896 198.372 139.646 199.143C142.395 199.915 142.779 198.467 140.797 194.799L137.823 189.298" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-10-paw" d="M148.419 185.288C158.148 189.517 162.74 190.2 162.197 187.338C161.653 184.476 163.051 182.768 166.389 182.213C169.728 181.659 170.051 179.759 167.359 176.512L163.322 171.643" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-11-crete" d="M104.152 136.779C110.966 137.976 113.682 140.677 112.3 144.882C110.918 149.087 111.219 149.922 113.201 147.387C115.183 144.852 116.967 143.523 118.552 143.399C120.137 143.275 121.044 145.482 121.272 150.021C121.5 154.559 122.997 155.473 125.765 152.763C128.532 150.052 130.304 150.463 131.079 153.994C131.854 157.526 132.69 157.66 133.588 154.397C134.485 151.134 135.575 148.914 136.858 147.738C138.141 146.561 139.764 146.286 141.727 146.911C143.691 147.536 144.608 148.984 144.479 151.255L144.249 154.962C144.249 154.962 149.023 150.027 152.235 151.255C156.026 152.705 154.231 161.439 154.231 161.439" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="creature-12-belly" d="M80.6209 157.929C87.2459 164.169 93.1944 168.157 98.4663 169.895C103.738 171.632 107.847 173.559 110.792 175.675C113.346 177.51 115.582 178.517 117.5 178.697" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
</g>
<g id="fossil">
<path id="fossil-01-shell" d="M212.732 163.746C208.555 169.444 199.156 163.739 204.899 156.487C210.542 149.362 222.653 158.047 220.042 169.962C217.431 181.876 188.459 183.339 190.801 158.562C191.96 146.303 201.998 137.5 213.776 139.917C228.918 143.025 237.618 159.258 233.618 173.071C228.784 189.763 204.899 207.893 197.455 179.403" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-02-line" d="M208.635 166.92C209.633 170.408 209.307 173.663 207.637 176.885" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-03-line" d="M203.226 163.673C202.147 166.92 197.654 169.411 194.161 169.909" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-04-line" d="M203.733 156.362C200.103 156.506 195.658 155.461 193.662 151.973" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-05-line" d="M208.654 152.759C206.489 149.846 205.713 145.112 206.139 141.51" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-06-line" d="M215.123 154.962C215.706 151.382 218.727 147.203 221.612 144.997" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-07-line" d="M219.962 161.18C222.176 158.305 227.009 156.535 230.596 155.959" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-08-line" d="M221.612 168.389C225.098 167.367 229.852 168.262 233.091 169.906" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-09-line" d="M218.118 176.387C221.196 178.314 223.691 182.841 224.607 186.351" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
<path id="fossil-10-line" d="M210.486 179.521C211.082 183.099 210.16 188.322 208.136 191.334" stroke="black" stroke-width="6" stroke-linecap="round" stroke-linejoin="round"/>
</g>
<path id="pebble-vein-1" d="M54.6052 32.2685C72.7324 29.9336 90.7912 16.4466 113.001 22.7996C135.564 29.2534 137.743 50.6926 157.922 62.6587C179.544 75.4808 189.576 72.8856 218.814 83.5872" stroke="black" stroke-width="6"/>
<path id="pebble-vein-2" d="M37.6353 60.1679C37.6353 60.1679 79.3566 35.0302 103.518 41.2323C131.025 48.2929 126.741 64.0952 151.433 78.1045C179.192 93.8537 192.072 75.3747 221.309 86.0763" stroke="black" stroke-width="6"/>
</g>
</svg>
```

- [ ] **Step 2: Write the failing bundle test**

Create `apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift`:

```swift
import Testing
import Foundation
@testable import Pebbles

@Suite
struct LogoLoaderArtTests {

    @Test("Loader SVG is bundled")
    func assetBundled() throws {
        let url = Bundle.main.url(forResource: "pbbls-logo-loader", withExtension: "svg")
        #expect(url != nil, "missing pbbls-logo-loader.svg")
    }
}
```

- [ ] **Step 3: Run the test — expect PASS once the resource is bundled**

Run: `npm run test --workspace=@pbbls/ios -- -only-testing:PebblesTests/LogoLoaderArtTests/assetBundled`
Expected: PASS (the SVG is picked up as a bundle resource by xcodegen). If it FAILS with a missing resource, confirm the file is under `Pebbles/Resources/` and re-run `npm run generate --workspace=@pbbls/ios`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/pbbls-logo-loader.svg apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift
git commit -m "feat(ui): bundle handcrafted logo loader svg"
```

---

## Task 2: `LogoLoaderArt` — parse groups + render modes

**Files:**
- Create: `apps/ios/Pebbles/Features/Welcome/LogoLoaderArt.swift`
- Test: `apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift`

- [ ] **Step 1: Write the failing parse test**

Append to `LogoLoaderArtTests.swift`:

```swift
extension LogoLoaderArtTests {
    @Test("Parsed groups split by id prefix and render mode")
    func parsedGroups() throws {
        let parsed = try #require(LogoLoaderArt.parseGroups())
        // viewBox is the issue's 251-box.
        #expect(parsed.viewBox == CGRect(x: 0, y: 0, width: 251, height: 251))
        // Every group's combined stroke path is non-empty (`isEmptyGroup` is
        // the internal CGPath helper defined in LogoLoaderArt.swift).
        #expect(!parsed.outline.isEmptyGroup)
        #expect(!parsed.creatureStrokes.isEmptyGroup)
        #expect(!parsed.fossilAndVeins.isEmptyGroup)
        // The two eyes are fills.
        #expect(!parsed.eyeFills.isEmptyGroup)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test --workspace=@pbbls/ios -- -only-testing:PebblesTests/LogoLoaderArtTests/parsedGroups`
Expected: FAIL / build error — `LogoLoaderArt` is undefined.

- [ ] **Step 3: Implement the parser**

Create `apps/ios/Pebbles/Features/Welcome/LogoLoaderArt.swift`:

```swift
import CoreGraphics
import Foundation

/// The logo split into reveal groups, in the SVG's own viewBox space, before
/// wobbling. Combined `CGPath`s per group so the reveal trim runs across
/// subpaths in draw order.
struct LogoParsedGroups {
    let viewBox: CGRect
    /// The enclosing stone outline (reveal phase 1).
    let outline: CGPath
    /// All creature strokes except the eyes (reveal phase 2).
    let creatureStrokes: CGPath
    /// Fossil strokes + the two pebble veins (reveal phase 3).
    let fossilAndVeins: CGPath
    /// The two eyes — filled regions, no trim reveal.
    let eyeFills: CGPath
}

extension CGPath {
    /// True when the path encloses no area (empty combined group).
    var isEmptyGroup: Bool { boundingBoxOfPath.isNull || boundingBoxOfPath.isEmpty }
}

enum LogoLoaderArt {

    private static let resourceName = "pbbls-logo-loader"

    /// Parse the bundled loader SVG into reveal groups. Returns nil if the
    /// asset is missing or unparseable.
    static func parseGroups() -> LogoParsedGroups? {
        guard
            let url = Bundle.main.url(forResource: resourceName, withExtension: "svg"),
            let raw = try? String(contentsOf: url, encoding: .utf8),
            let viewBox = parseViewBox(in: raw)
        else { return nil }

        let outline = CGMutablePath()
        let creature = CGMutablePath()
        let fossilVeins = CGMutablePath()
        let eyes = CGMutablePath()

        for element in pathElements(in: raw) {
            guard let path = SVGPathParser.parse(element.d) else { continue }
            switch bucket(for: element) {
            case .outline:      outline.addPath(path)
            case .creature:     creature.addPath(path)
            case .fossilVeins:  fossilVeins.addPath(path)
            case .eyes:         eyes.addPath(path)
            }
        }

        return LogoParsedGroups(
            viewBox: viewBox,
            outline: outline.copy() ?? outline,
            creatureStrokes: creature.copy() ?? creature,
            fossilAndVeins: fossilVeins.copy() ?? fossilVeins,
            eyeFills: eyes.copy() ?? eyes
        )
    }

    // MARK: - Bucketing

    private enum Bucket { case outline, creature, fossilVeins, eyes }

    private static func bucket(for element: PathElement) -> Bucket {
        let id = element.id
        if id == "pebble-outline" { return .outline }
        if id.hasPrefix("pebble-vein") || id.hasPrefix("fossil") { return .fossilVeins }
        if id.contains("eye") { return .eyes }
        // Remaining creature-* strokes.
        return .creature
    }

    // MARK: - Minimal SVG scanning

    struct PathElement { let id: String; let d: String }

    /// Every `<path>` element's `id` and `d`, in document order.
    private static func pathElements(in svg: String) -> [PathElement] {
        guard let regex = try? NSRegularExpression(pattern: "<path\\b[^>]*>") else { return [] }
        let full = NSRange(svg.startIndex..., in: svg)
        return regex.matches(in: svg, range: full).compactMap { match -> PathElement? in
            guard let range = Range(match.range, in: svg) else { return nil }
            let tag = String(svg[range])
            guard let d = attribute("d", in: tag) else { return nil }
            return PathElement(id: attribute("id", in: tag) ?? "", d: d)
        }
    }

    /// First `name="…"` value in `tag`. The word boundary keeps `d=` from
    /// matching `id=` (mirrors `WobbleRenderer.attribute`).
    private static func attribute(_ name: String, in tag: String) -> String? {
        guard
            let regex = try? NSRegularExpression(pattern: "(?<![\\w-])\(name)=\"([^\"]*)\""),
            let match = regex.firstMatch(in: tag, range: NSRange(tag.startIndex..., in: tag)),
            let range = Range(match.range(at: 1), in: tag)
        else { return nil }
        return String(tag[range])
    }

    private static func parseViewBox(in svg: String) -> CGRect? {
        guard let value = attribute("viewBox", in: svg) else { return nil }
        let parts = value.split(whereSeparator: { $0 == " " || $0 == "," }).compactMap { Double($0) }
        guard parts.count == 4, parts[2] > 0, parts[3] > 0 else { return nil }
        return CGRect(x: parts[0], y: parts[1], width: parts[2], height: parts[3])
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test --workspace=@pbbls/ios -- -only-testing:PebblesTests/LogoLoaderArtTests/parsedGroups`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/LogoLoaderArt.swift apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift
git commit -m "feat(ui): parse logo loader svg into reveal groups"
```

---

## Task 3: `LogoLoaderArt` — build 3 boil variants

**Files:**
- Modify: `apps/ios/Pebbles/Features/Welcome/LogoLoaderArt.swift`
- Test: `apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift`

- [ ] **Step 1: Write the failing variants test**

Append to `LogoLoaderArtTests.swift`:

```swift
extension LogoLoaderArtTests {
    @Test("Builds three distinct boil variants")
    func boilVariants() throws {
        let art = try #require(LogoLoaderArt.build())
        #expect(art.variants.count == 3)
        for v in art.variants {
            #expect(!v.outline.ink.isEmptyGroup)
            #expect(!v.creature.ink.isEmptyGroup)
            #expect(!v.fossilVeins.ink.isEmptyGroup)
            #expect(!v.eyes.isEmptyGroup)
        }
        // Seeds 3/4/5 must produce different geometry (boil, not a freeze).
        #expect(art.variants[0].outline.ink.boundingBoxOfPath
                != art.variants[1].outline.ink.boundingBoxOfPath)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test --workspace=@pbbls/ios -- -only-testing:PebblesTests/LogoLoaderArtTests/boilVariants`
Expected: FAIL / build error — `LogoLoaderArt.build`, `LogoLoaderVariant` undefined.

- [ ] **Step 3: Implement variant building**

Add to `LogoLoaderArt.swift` (new types + the `build()` factory; reuse `WobbleParams`, `SVGTurbulence`, `WobblePathFlattener`, `WobbleOutlineBuilder`):

```swift
/// Wobbled artwork for the whole logo at one boil seed.
struct LogoLoaderVariant {
    let outline: WobbleArt        // reveal phase 1 (ink + centerline)
    let creature: WobbleArt       // reveal phase 2
    let fossilVeins: WobbleArt    // reveal phase 3
    let eyes: CGPath              // displaced fill regions (no reveal)
}

/// The parsed logo plus its three boil variants. Built once, cached.
struct LogoLoaderModel {
    let viewBox: CGRect
    let variants: [LogoLoaderVariant]
}

extension LogoLoaderArt {

    /// SVG-authored stroke half-width, in the logo's own viewBox units.
    private static let strokeHalfWidth = Double(PebbleStroke.outlineWidth) / 2   // 3
    /// Boil variant seeds (issue #555 §2.3: variant k → seed 3 + k).
    private static let seeds = [3, 4, 5]

    private static let cached: LogoLoaderModel? = buildUncached()

    /// Cached accessor — the wobble build runs at most once per process.
    static func build() -> LogoLoaderModel? { cached }

    private static func buildUncached() -> LogoLoaderModel? {
        guard let groups = parseGroups() else { return nil }
        // Wobble in the SVG's own viewBox with §2.1-scaled params — the
        // established backdrop pattern (WobbleRenderer.backdropArt), visually
        // equivalent to normalizing into the 200-box.
        let params = WobbleParams.scaled(for: groups.viewBox.size)
        let variants = seeds.map { seed -> LogoLoaderVariant in
            let noise = SVGTurbulence(seed: seed)
            return LogoLoaderVariant(
                outline: strokeArt(groups.outline, params: params, noise: noise),
                creature: strokeArt(groups.creatureStrokes, params: params, noise: noise),
                fossilVeins: strokeArt(groups.fossilAndVeins, params: params, noise: noise),
                eyes: displacedFill(groups.eyeFills, params: params, noise: noise)
            )
        }
        return LogoLoaderModel(viewBox: groups.viewBox, variants: variants)
    }

    private static func strokeArt(_ path: CGPath, params: WobbleParams, noise: SVGTurbulence) -> WobbleArt {
        let polylines = WobblePathFlattener.flatten(path, step: params.flattenStep)
        return WobbleOutlineBuilder.art(for: polylines, halfWidth: strokeHalfWidth, params: params, noise: noise)
    }

    /// Displace a fill region's contours directly (mirrors
    /// `WobbleRenderer.backdropArt` — fills wobble their edge, no outline).
    private static func displacedFill(_ path: CGPath, params: WobbleParams, noise: SVGTurbulence) -> CGPath {
        let out = CGMutablePath()
        for polyline in WobblePathFlattener.flatten(path, step: params.flattenStep) {
            let displaced = polyline.points.map { params.displace($0, using: noise) }
            guard displaced.count > 2, let first = displaced.first else { continue }
            out.move(to: first)
            for point in displaced.dropFirst() { out.addLine(to: point) }
            out.closeSubpath()
        }
        return out.copy() ?? out
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `npm run test --workspace=@pbbls/ios -- -only-testing:PebblesTests/LogoLoaderArtTests/boilVariants`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/LogoLoaderArt.swift apps/ios/PebblesTests/Features/Welcome/LogoLoaderArtTests.swift
git commit -m "feat(ui): build three boil variants of the logo loader"
```

---

## Task 4: `HandcraftedLogoView` — static + boil + draw-on

**Files:**
- Create: `apps/ios/Pebbles/Features/Welcome/HandcraftedLogoView.swift`

This is a view; verify by build (`npm run build --workspace=@pbbls/ios`) + `#Preview` in the simulator, per the no-UI-tests rule.

- [ ] **Step 1: Implement the view**

Create `apps/ios/Pebbles/Features/Welcome/HandcraftedLogoView.swift`:

```swift
import SwiftUI

/// The handcrafted logo loader. Plays a phased draw-on reveal, then boils
/// (#555) until `shouldSettle` flips true, then holds static. Under Reduce
/// Motion it renders static variant 0 immediately and never boils.
///
/// The view does not dismiss itself — `RootView` gates the launch on
/// `onDrawComplete` + real readiness. `shouldSettle` only tells the logo to
/// stop boiling.
struct HandcraftedLogoView: View {
    /// Parent → "app is ready; stop boiling and settle to static."
    let shouldSettle: Bool
    /// Fired once the draw-on finishes (immediately under Reduce Motion).
    var onDrawComplete: () -> Void = {}
    /// Logo ink colour. The artwork is authored black; default adapts to scheme.
    var color: Color = .system.foreground

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: LogoLoaderModel?
    @State private var phase: Phase = .drawing
    @State private var outlineProgress: Double = 0
    @State private var creatureProgress: Double = 0
    @State private var fossilVeinProgress: Double = 0
    @State private var eyesIn = false

    private enum Phase { case drawing, boiling, settled }

    // Draw-on choreography (seconds). Tunable in the simulator.
    private static let outline = (delay: 0.0, duration: 0.55)
    private static let creature = (delay: 0.45, duration: 0.70)
    private static let fossilVein = (delay: 1.00, duration: 0.55)
    private static var totalDrawDuration: Double { fossilVein.delay + fossilVein.duration }

    // Boil: 4fps ping-pong (#555 §1/§3).
    private static let boilFPS: Double = 4
    private static let boilOrder = [0, 1, 2, 1]

    var body: some View {
        Group {
            if let model {
                switch phase {
                case .drawing: drawingBody(model)
                case .boiling: boilingBody(model)
                case .settled: filledLogo(model.variants[0], model: model)
                }
            } else {
                Color.clear   // asset missing: render nothing, gate still proceeds
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
        .task { await run() }
        .onChange(of: shouldSettle) { _, settle in
            if settle && phase == .boiling { phase = .settled }
        }
    }

    // MARK: - Orchestration

    private func run() async {
        if model == nil { model = LogoLoaderArt.build() }
        guard model != nil else { onDrawComplete(); return }

        if reduceMotion {
            outlineProgress = 1; creatureProgress = 1; fossilVeinProgress = 1; eyesIn = true
            phase = .settled                 // static; never boil (#555 §3.1)
            onDrawComplete()
            return
        }

        startDrawOn()
        try? await Task.sleep(for: .seconds(Self.totalDrawDuration))
        onDrawComplete()
        // Draw finished: boil unless the app is already ready.
        phase = shouldSettle ? .settled : .boiling
    }

    private func startDrawOn() {
        withAnimation(.easeOut(duration: Self.outline.duration).delay(Self.outline.delay)) {
            outlineProgress = 1
        }
        withAnimation(.easeOut(duration: Self.creature.duration).delay(Self.creature.delay)) {
            creatureProgress = 1
        }
        withAnimation(.easeOut(duration: 0.25).delay(Self.creature.delay + Self.creature.duration - 0.1)) {
            eyesIn = true    // eyes pop in at the tail of the creature phase
        }
        withAnimation(.easeOut(duration: Self.fossilVein.duration).delay(Self.fossilVein.delay)) {
            fossilVeinProgress = 1
        }
    }

    // MARK: - Rendering

    /// Draw-on: variant 0, each stroke group's ink revealed by a trimmed
    /// centerline mask (mirrors PebbleAnimatedRenderView.animatedBody).
    @ViewBuilder
    private func drawingBody(_ model: LogoLoaderModel) -> some View {
        let variant = model.variants[0]
        GeometryReader { proxy in
            let maskWidth = WobbleMask.lineWidth(viewBox: model.viewBox, frame: proxy.size)
            ZStack {
                revealGroup(variant.outline, progress: outlineProgress, maskWidth: maskWidth, viewBox: model.viewBox)
                revealGroup(variant.creature, progress: creatureProgress, maskWidth: maskWidth, viewBox: model.viewBox)
                revealGroup(variant.fossilVeins, progress: fossilVeinProgress, maskWidth: maskWidth, viewBox: model.viewBox)
                eyeShape(variant.eyes, viewBox: model.viewBox)
                    .opacity(eyesIn ? 1 : 0)
                    .scaleEffect(eyesIn ? 1 : 0.6)
            }
        }
    }

    @ViewBuilder
    private func revealGroup(_ art: WobbleArt, progress: Double, maskWidth: CGFloat, viewBox: CGRect) -> some View {
        WobbledPathShape(path: art.ink, layerTransform: .identity, viewBox: viewBox)
            .fill(color)
            .mask {
                WobbledPathShape(path: art.centerline, layerTransform: .identity, viewBox: viewBox)
                    .trim(from: 0, to: progress)
                    .stroke(Color.white, style: StrokeStyle(lineWidth: maskWidth, lineCap: .round, lineJoin: .round))
            }
    }

    /// Boil: cycle the three variant inks on a wall-clock timer (#555 §3).
    private func boilingBody(_ model: LogoLoaderModel) -> some View {
        TimelineView(.periodic(from: .now, by: 1.0 / Self.boilFPS)) { context in
            let tick = Int(context.date.timeIntervalSinceReferenceDate * Self.boilFPS)
            let index = Self.boilOrder[tick % Self.boilOrder.count]
            filledLogo(model.variants[index], model: model)
        }
    }

    /// Static fully-revealed logo for one variant.
    @ViewBuilder
    private func filledLogo(_ variant: LogoLoaderVariant, model: LogoLoaderModel) -> some View {
        ZStack {
            inkShape(variant.outline.ink, viewBox: model.viewBox)
            inkShape(variant.creature.ink, viewBox: model.viewBox)
            inkShape(variant.fossilVeins.ink, viewBox: model.viewBox)
            eyeShape(variant.eyes, viewBox: model.viewBox)
        }
    }

    private func inkShape(_ path: CGPath, viewBox: CGRect) -> some View {
        WobbledPathShape(path: path, layerTransform: .identity, viewBox: viewBox).fill(color)
    }

    private func eyeShape(_ path: CGPath, viewBox: CGRect) -> some View {
        WobbledPathShape(path: path, layerTransform: .identity, viewBox: viewBox).fill(color)
    }
}

#Preview("Draw-on then boil") {
    HandcraftedLogoView(shouldSettle: false)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.system.background)
}

#Preview("Settled (static)") {
    HandcraftedLogoView(shouldSettle: true)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.system.background)
}
```

- [ ] **Step 2: Generate + build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Lint**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: no new violations in `HandcraftedLogoView.swift` / `LogoLoaderArt.swift`. (If `identifier_name` fires on short locals, add a scoped `// swiftlint:disable:next` as neighbouring wobble files do.)

- [ ] **Step 4: Manual preview check**

Open `HandcraftedLogoView.swift` in Xcode, run the "Draw-on then boil" preview. Confirm: outline draws first → creature → fossil+veins, eyes pop in, then the line boils. Confirm the "Settled" preview is static.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Welcome/HandcraftedLogoView.swift
git commit -m "feat(ui): handcrafted logo loader view with draw-on and boil"
```

---

## Task 5: Reference services — `didFinishLoading`

**Files:**
- Modify: `apps/ios/Pebbles/Services/EmotionPaletteService.swift`
- Modify: `apps/ios/Pebbles/Services/ReferenceDataService.swift`

- [ ] **Step 1: Add the flag to `EmotionPaletteService`**

In `EmotionPaletteService.swift`, add the property next to `hasLoaded`:

```swift
    private(set) var hasLoaded: Bool = false
    /// True once a load attempt has settled — success OR failure. The launch
    /// loader gates on this (not `hasLoaded`) so a failed reference fetch still
    /// lets the app open with an empty cache instead of boiling forever.
    private(set) var didFinishLoading: Bool = false
```

Wrap the body of `load()` so the flag flips on every exit path:

```swift
    func load() async {
        defer { didFinishLoading = true }
        do {
            let rows: [EmotionWithPalette] = try await client
                .from("v_emotions_with_palette")
                .select()
                .execute()
                .value
            self.byEmotionId = Dictionary(uniqueKeysWithValues: rows.map { ($0.id, $0) })
            self.hasLoaded = true
            logger.info("loaded \(rows.count, privacy: .public) palette rows")
        } catch {
            logger.error("palette load failed: \(error.localizedDescription, privacy: .private)")
        }
    }
```

Also update the stale doc comment on the type (it references `RootView.minSplashSeconds` / "≥ 2.5s"): replace that sentence with "Loaded once from `RootView.task` during the handcrafted logo loader; the loader gates on `didFinishLoading`."

- [ ] **Step 2: Add the flag to `ReferenceDataService`**

Same change in `ReferenceDataService.swift`: add the `didFinishLoading` property (identical doc comment), add `defer { didFinishLoading = true }` as the first line of `load()`, and update the type doc comment's "hidden behind the splash" / 2.5s reference to mention the loader gates on `didFinishLoading`. Leave `refreshSouls()` / `refreshCollections()` untouched.

- [ ] **Step 3: Build**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Services/EmotionPaletteService.swift apps/ios/Pebbles/Services/ReferenceDataService.swift
git commit -m "feat(core): reference services report load-attempt completion"
```

---

## Task 6: `RootView` + `WelcomeView` wiring — real readiness gate, drop Rive

These two files change each other's call signature, so they compile as one unit. Do both, then build once.

**Files:**
- Modify: `apps/ios/Pebbles/RootView.swift`
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeView.swift`

### Part A — `RootView.swift`

- [ ] **Step 1: Replace the timer gate with the readiness gate**

In `RootView.swift`:

1. Delete `private static let minSplashSeconds` and the `@State private var minSplashDone = false`.
2. Add:

```swift
    @State private var logoDrawComplete = false
    @State private var loaderCeilingReached = false

    /// Safety ceiling: if reference data never settles (e.g. a wedged
    /// network beyond the client's own timeout), open the app anyway rather
    /// than boiling indefinitely. Normal launches settle in well under this.
    private static let loaderCeilingSeconds: TimeInterval = 8
```

3. Replace the gate computed properties:

```swift
    /// Auth resolved AND both reference-data load attempts settled (success or
    /// failure), OR the safety ceiling elapsed.
    private var dataReady: Bool {
        (!supabase.isInitializing && palettes.didFinishLoading && refs.didFinishLoading)
            || loaderCeilingReached
    }

    /// The loader dismisses only once the draw-on has played AND the app is
    /// ready — the handcrafted logo IS the loader (no spinner).
    private var canProceed: Bool { dataReady && logoDrawComplete }

    private var canShowAuthedTabs: Bool {
        supabase.session != nil && canProceed
    }

    private var welcomeContentRevealed: Bool {
        supabase.session == nil && canProceed
    }
```

4. Replace the splash-timer `.task` (the `Task.sleep(minSplashSeconds)` one) with the ceiling task, and pass the draw-complete callback down. The `WelcomeView` call gains `onLogoDrawComplete`:

```swift
                    WelcomeView(
                        contentRevealed: welcomeContentRevealed,
                        appReady: canProceed,
                        onLogoDrawComplete: { logoDrawComplete = true },
                        onCreateAccount: { authPath.append(AuthRoute.auth(.signup)) },
                        onLogin: { authPath.append(AuthRoute.auth(.login)) }
                    )
```

And swap the sleep task:

```swift
        .task {
            try? await Task.sleep(for: .seconds(Self.loaderCeilingSeconds))
            loaderCeilingReached = true
        }
```

Keep the existing `.task { await supabase.start() }`, `.task { await palettes.load() }`, `.task { await refs.load() }`, and the `onChange` handlers unchanged.

Note: `PathView` (authed branch) does not host the logo, so for an authed user `logoDrawComplete` must still flip. It does: `WelcomeView` is the view shown while `!canProceed` (the `else` branch), so its logo runs the draw-on during load and fires `onLogoDrawComplete` before the swap to `PathView`.

### Part B — `WelcomeView.swift`

- [ ] **Step 2: Replace the Rive logo**

In `WelcomeView.swift`:

1. Remove `import RiveRuntime`.
2. Add the two new inputs and remove the Rive view model:

```swift
    let contentRevealed: Bool
    /// True once the app is ready to be shown (drives boil → settle).
    let appReady: Bool
    /// Fired when the logo draw-on completes (bubbles to RootView's gate).
    var onLogoDrawComplete: () -> Void = {}
    let onCreateAccount: () -> Void
    let onLogin: () -> Void
```

Delete `@State private var logoViewModel = RiveViewModel(fileName: "pbbls-logo-appear_idle")`.

3. Replace the logo slot in `body` (the `logoViewModel.view()...` block) with:

```swift
                HandcraftedLogoView(
                    shouldSettle: appReady,
                    onDrawComplete: onLogoDrawComplete
                )
                .containerRelativeFrame(.horizontal) { width, _ in width * 0.33 }
```

4. In `.onAppear`, remove `logoViewModel.play()`. Keep the `runReveal()` kickoff:

```swift
        .onAppear {
            if contentRevealed && revealStep == 0 {
                Task { await runReveal() }
            }
        }
```

5. Update the type doc comment: it describes the Rive `pbbls-logo.riv` playing through the splash. Replace with a sentence describing the `HandcraftedLogoView` loader (draw-on → boil until ready → settle) held centered, then translated to the header on reveal.

6. Update both `#Preview` blocks to pass `appReady:` (`false` for "Splash phase", `true` for "Revealed") and drop nothing else.

- [ ] **Step 3: Build both files together**

Run: `npm run build --workspace=@pbbls/ios`
Expected: BUILD SUCCEEDED (RootView + WelcomeView compile together).

- [ ] **Step 4: Lint**

Run: `npm run lint --workspace=@pbbls/ios`
Expected: no new violations.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/RootView.swift apps/ios/Pebbles/Features/Welcome/WelcomeView.swift
git commit -m "feat(core): gate launch on real readiness with handcrafted loader, drop rive splash"
```

---

## Task 7: Full verification + regression sweep

**Files:** none (verification only).

- [ ] **Step 1: Full build + test + lint**

Run:
```bash
npm run build --workspace=@pbbls/ios
npm run test --workspace=@pbbls/ios
npm run lint --workspace=@pbbls/ios
```
Expected: BUILD SUCCEEDED; all tests pass (including the three `LogoLoaderArtTests`); no new lint violations.

- [ ] **Step 2: Manual simulator verification (the real acceptance surface)**

Launch on `iPhone 17` simulator and confirm, per the spec's testing section:
- **Cold launch (unauth):** logo draws on (outline → creature → fossil+veins, eyes pop), then boils, then — once palettes/refs settle — settles to static and the welcome content reveals with the logo translating to the header. No spinner, no view-swap glitch.
- **Throttled network** (Simulator → Features → Network Link Conditioner, or airplane mode): the loader boils and persists, then opens (the `didFinishLoading` / ceiling path proceeds; app opens with empty pickers on hard failure, not a hang).
- **Returning authed user:** draw-on plays, then swaps to `PathView`; no infinite boil.
- **Reduce Motion** (Settings → Accessibility): static logo, no draw-on, no boil; app still opens once ready.

- [ ] **Step 3: Confirm no stray Rive references in the launch flow**

Run: `grep -rn "RiveRuntime\|logoViewModel\|pbbls-logo-appear_idle\|minSplashSeconds\|minSplashDone" apps/ios/Pebbles`
Expected: matches only in `WeekRollCairnCell.swift` (`RiveRuntime`) and `Color+Rive.swift` — none in `RootView.swift` / `WelcomeView.swift`.

- [ ] **Step 4: Update the Arkaik map if needed**

The splash/loader is a launch-flow behavior change, not a new/removed view node, route, data model, or endpoint. Invoke the `arkaik` skill only if it determines the loader is a tracked node whose status/description changed; otherwise this is a no-op (record the decision in the PR body).

- [ ] **Step 5: Final commit (if any verification fixups were made)**

```bash
git add -A
git commit -m "chore(ui): logo loader verification fixups"
```

---

## Self-Review Notes (for the executor)

- **Spec coverage:** issue SVG source (T1) · parse groups + eye fills (T2) · 3 boil variants seeds 3/4/5 (T3) · draw-on then boil, never simultaneous, Reduce-Motion static (T4) · failure-safe gate (T5 services + T6 gate) · remove Rive entirely + real readiness gate (T6) · verification incl. throttled network + Reduce Motion (T7).
- **Draw order** is outline → creature → veins+fossil per the product decision. Eyes are fills, popped in during the creature phase (they cannot be trim-revealed).
- **Type names** are consistent across tasks: `LogoLoaderArt` (enum, static API), `LogoParsedGroups`, `LogoLoaderVariant`, `LogoLoaderModel`, `HandcraftedLogoView`. `isEmptyGroup` is the `CGPath` helper used in tests.
- **Tuning** (draw timings, boil fps, `WobbleMask.widthInViewBoxUnits`, stroke weight, ceiling seconds) is expected to be dialed in during T7's manual pass; the committed numbers are sensible defaults.
```
