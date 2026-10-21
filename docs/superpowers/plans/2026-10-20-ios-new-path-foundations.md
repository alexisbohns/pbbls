# iOS New Path foundations (stack B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the iOS Spine (#1130) and the held card (#1146) three foundations: the variable Ysabeau with one rank table (`SpineType`), a CI reference-image gate, and the lit-stone fixes (#1097, the iOS half of #1143).

**Architecture:** Three stacked parts, B1 → B2 → B3. B1 bundles Android's variable roman, adds a memoised descriptor bridge (`YsabeauFace`) and a rank table that owns its Dynamic Type policy. B2 adds a hand-rolled `ImageRenderer` + exact-pixel-diff harness, references committed by a label-driven CI workflow. B3 moves the stone on purpose (soft silhouette, matte river, grittier lava) and is the gate's first intended diff.

**Tech Stack:** SwiftUI (iOS 17, Swift 5.9), CoreText / UIKit font descriptors, Metal stitchable shaders, Swift Testing, XcodeGen, GitHub Actions (macos-26, Xcode 26.3), `gh stack`.

**Spec (the contract):** `docs/superpowers/specs/2026-10-20-ios-new-path-foundations-design.md`. The spec and this plan live on `docs/1161-ios-new-path-specs`. Merge that docs PR first, or the stack branched from `main` will not contain them.

**Read before starting:** `CLAUDE.md`, `AGENTS.md`, `apps/ios/CLAUDE.md`, `.claude/skills/gh-stack/SKILL.md`, `.claude/skills/android-screenshot-gate/SKILL.md`.

**Standing rules for every task:**
- After `xcodegen generate` (which `npm run test --workspace=@pbbls/ios` runs), when a file was added, deleted or moved, delete the derived data before trusting a build: `rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*`. A stale build keeps serving a deleted font or an old object.
- No `ScrollView` and no `containerRelativeFrame` in anything rendered by a test.
- Swift Testing only (`@Suite`, `@Test`, `#expect`, `#require`). No XCTest.
- `Localizable.xcstrings` is edited as text at its anchors (the Edit tool), never re-serialised.
- Commit messages: `type(scope): description`, lowercase, no period.

**Single-test command** used throughout (replace the suite name):

```bash
cd /Users/alexis/code/pbbls/apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/<SuiteType> -quiet
```

`npm run test --workspace=@pbbls/ios` runs `xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17'` (the whole suite, EN). `npm run lint --workspace=@pbbls/ios` runs `swiftlint` in `apps/ios`.

## File map

| Part | File | Responsibility |
|---|---|---|
| B1 | `apps/ios/Pebbles/Resources/Ysabeau.ttf` (new, copy of Android's `ysabeau.ttf`) | Variable roman |
| B1 | `apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf` (deleted) | — |
| B1 | `apps/ios/Pebbles/Resources/Info.plist` | `UIAppFonts` swap |
| B1 | `apps/ios/Pebbles/Theme/YsabeauFace.swift` (new) | Descriptor bridge: one memoised `UIFont` per (size, wght, italic, features) |
| B1 | `apps/ios/Pebbles/Theme/SpineType.swift` (new) | The rank table, `Cut`, `Scaling`, `Face`, size bands |
| B1 | `apps/ios/Pebbles/Theme/SpineTypeModifier.swift` (new) | Pure resolution (`SpineType.resolve`) and `View.spineType(_:)` |
| B1 | `apps/ios/Pebbles/Theme/Font+Pebbles.swift` | `ysabeauSemibold*` rebuilt on `YsabeauFace`; shared Caveat descriptor |
| B1 | `apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift`, `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift` | Drop the dead PostScript name |
| B1 | `apps/ios/Pebbles/Resources/Localizable.xcstrings`, `apps/ios/Pebbles/Features/Karma/KarmaReason.swift` | #1101 rider |
| B1 | `apps/ios/PebblesTests/YsabeauItalicFontTests.swift`, `YsabeauFaceTests.swift` (new), `SpineTypeTests.swift` (new), `SpineTypeResolveTests.swift` (new), `LocalizationTests.swift` | Tests |
| B1 | `.github/workflows/ios.yml` | `cmp` step + path filter |
| B2 | `apps/ios/PebblesTests/References/Support/ReferenceConfig.swift` (new) | Scheme, locale, Dynamic Type, width, file naming |
| B2 | `apps/ios/PebblesTests/References/Support/ReferenceImage.swift` (new) | Render, compare, record, diff writer |
| B2 | `apps/ios/PebblesTests/References/Support/ReferenceImageTests.swift` (new) | Pure-part tests + scheme probe |
| B2 | `apps/ios/PebblesTests/References/References.swift` (new) | Root `References` suite (serialised) |
| B2 | `apps/ios/PebblesTests/References/StoneReferences.swift`, `WelcomeReferences.swift` (new) | First references |
| B2 | `apps/ios/PebblesTests/References/__References__/**` (CI-written) | Reference PNGs |
| B2 | `apps/ios/project.yml` | Keep reference PNGs out of the test bundle |
| B2 | `.github/workflows/ios.yml`, `.github/workflows/ios-references.yml` (new) | Gate + rebaseline |
| B2 | `docs/decisions/log.md` | One appended entry |
| B3 | `apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift` | Only `headlineInk` left |
| B3 | `apps/ios/Pebbles/Features/Path/Render/Wobble/WobbleRenderer.swift` | `wobble:` flag, `softBackdropArt` |
| B3 | `apps/ios/Pebbles/Features/Path/Stone/StoneView.swift` | One `silhouette`, chosen by polarity |
| B3 | `apps/ios/Pebbles/Resources/Stone/stone.metal` | Matte river, fine tooth, lava grain |
| B3 | `apps/ios/Pebbles/Features/Path/Stone/StoneMaterial.swift` | Starting values, then the lab's |
| B3 | `apps/ios/PebblesTests/Wobble/WobbleRendererTests.swift`, `apps/ios/PebblesTests/Stone/StoneSilhouetteTests.swift` (new), `apps/ios/PebblesTests/StoneLab/StoneMaterialTests.swift` | Tests |

---

## Part B1 — feat/1158-ios-variable-ysabeau

Labels `feat`, `ui`, `ios`, `no-lab-note` · milestone `M64 · New Path` · PR body `Resolves #1158` and `Part of #1101`.

### Task B1.0: Create the stack and capture the "before" screens

**Files:** none.

- [ ] **Step 1: Configure git for non-interactive `gh stack`**

```bash
cd /Users/alexis/code/pbbls
git config rerere.enabled true
git config remote.pushDefault origin
```

- [ ] **Step 2: Branch from an updated `main` and create the stack**

```bash
cd /Users/alexis/code/pbbls
git fetch origin
git switch main
git pull --ff-only origin main
gh stack init feat/1158-ios-variable-ysabeau
git branch --show-current
```

Expected: `feat/1158-ios-variable-ysabeau`.

- [ ] **Step 3: Photograph the "before" on the iPhone 15**

Install the current `main` build on the phone from Xcode (scheme Pebbles, the iPhone 15 as destination). Screenshot, in EN and then in FR (Settings → Pebbles → Language): the welcome carousel slide 2 (sign out first), the record flow title, and one Path row with a named pebble and one untitled pebble. Keep the six screenshots outside the repo. They are the reference for Task B1.8.

### Task B1.1: Bundle the variable roman and drop the static cut

**Files:**
- Create: `apps/ios/Pebbles/Resources/Ysabeau.ttf`
- Delete: `apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf`
- Modify: `apps/ios/Pebbles/Resources/Info.plist:46`
- Test: `apps/ios/PebblesTests/YsabeauItalicFontTests.swift`

- [ ] **Step 1: Write the failing tests**

Add these two tests inside `struct YsabeauItalicFontTests`, after `postScriptNameResolves()`:

```swift
    @Test("the variable roman is bundled and the static SemiBold is gone")
    func variableRomanReplacesStaticCut() {
        #expect(Bundle.main.url(forResource: "Ysabeau", withExtension: "ttf") != nil, "missing font: Ysabeau.ttf")
        #expect(Bundle.main.url(forResource: "Ysabeau SemiBold", withExtension: "ttf") == nil)
        #expect(UIFont(name: "Ysabeau-SemiBold", size: 17) == nil)
    }

    @Test("Ysabeau-Bold, the roman's default instance, resolves upright")
    func romanDefaultInstanceResolves() {
        let font = UIFont(name: "Ysabeau-Bold", size: 17)
        #expect(font?.fontName == "Ysabeau-Bold")
        #expect(font?.fontDescriptor.symbolicTraits.contains(.traitItalic) == false)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run the single-test command with `YsabeauItalicFontTests`.
Expected: FAIL. `variableRomanReplacesStaticCut` fails on `Ysabeau.ttf` missing (and on `Ysabeau-SemiBold` still resolving); `romanDefaultInstanceResolves` fails with `fontName` nil.

- [ ] **Step 3: Swap the files**

```bash
cd /Users/alexis/code/pbbls
cp apps/android/app/src/main/res/font/ysabeau.ttf apps/ios/Pebbles/Resources/Ysabeau.ttf
git rm -q "apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf"
shasum apps/ios/Pebbles/Resources/Ysabeau.ttf
```

Expected sha1: `6adcb36b8b9e9df0ed5ed4393e67fd0932ca1952`.

In `apps/ios/Pebbles/Resources/Info.plist`, replace

```xml
		<string>Ysabeau SemiBold.ttf</string>
```

with

```xml
		<string>Ysabeau.ttf</string>
```

`project.yml` needs no change: `Pebbles/` is a source path and resources are inferred.

- [ ] **Step 4: Clear derived data and run the tests**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
```

Run the single-test command with `YsabeauItalicFontTests`.
Expected: PASS (4 tests). The app still compiles: `ysabeauSemibold` builds a descriptor by name and silently falls back, which Task B1.4 fixes.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Resources/Ysabeau.ttf apps/ios/Pebbles/Resources/Info.plist apps/ios/PebblesTests/YsabeauItalicFontTests.swift
git commit -m "feat(ios): bundle the variable ysabeau roman in place of the static semibold"
```

(`git rm` already staged the deletion.)

### Task B1.2: CI keeps the iOS and Android Ysabeau files identical

**Files:**
- Modify: `.github/workflows/ios.yml`

- [ ] **Step 1: Add the Android font folder to both path filters**

In `.github/workflows/ios.yml`, in **both** the `pull_request.paths` and the `push.paths` lists, after `- 'packages/supabase/**'`, add:

```yaml
      - 'apps/android/app/src/main/res/font/**'
```

- [ ] **Step 2: Add the `cmp` step to the `verify` job**

In the `verify` job, after the `Verify colour contrast` step, add:

```yaml
      # The variable Ysabeau is one font on both native apps (#1158): the
      # Spine's rank table on each surface pins the same `wght` values, so a
      # different file is a different design. Android is the source; a font
      # bump there turns this check red until apps/ios carries the same bytes
      # (the path filter above includes Android's font folder for that reason).
      - name: Verify Ysabeau matches Android
        run: |
          cmp "apps/ios/Pebbles/Resources/Ysabeau.ttf" "apps/android/app/src/main/res/font/ysabeau.ttf"
          cmp "apps/ios/Pebbles/Resources/Ysabeau Italic.ttf" "apps/android/app/src/main/res/font/ysabeau_italic.ttf"
```

- [ ] **Step 3: Run the check locally**

```bash
cd /Users/alexis/code/pbbls
cmp "apps/ios/Pebbles/Resources/Ysabeau.ttf" "apps/android/app/src/main/res/font/ysabeau.ttf" && cmp "apps/ios/Pebbles/Resources/Ysabeau Italic.ttf" "apps/android/app/src/main/res/font/ysabeau_italic.ttf" && echo identical
```

Expected: `identical`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ios.yml
git commit -m "chore(ios): fail ci when the ysabeau files drift from android"
```

### Task B1.3: `YsabeauFace`, the descriptor bridge

**Files:**
- Create: `apps/ios/Pebbles/Theme/YsabeauFace.swift`
- Create: `apps/ios/PebblesTests/YsabeauFaceTests.swift`
- Modify: `apps/ios/PebblesTests/YsabeauItalicFontTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/YsabeauFaceTests.swift`:

```swift
import CoreText
import Foundation
import Testing
import UIKit
@testable import Pebbles

/// Proves the axis and the features were *applied*, not only stored: a
/// descriptor can carry a variation CoreText then ignores, so every check here
/// measures shaped output (advances, glyph ids) as well as reading attributes.
@Suite("YsabeauFace")
struct YsabeauFaceTests {

    @Test("300, 480 and 650 resolve to three distinct weights on the wght axis")
    func weightsApply() throws {
        let weights = [300, 480, 650]
        let fonts = weights.map { YsabeauFace.uiFont(size: 17, weight: $0, italic: false, features: []) }
        #expect(fonts.map(wght) == [300, 480, 650])
        let advances = fonts.map { advance("mmmm", font: $0) }
        #expect(advances[0] < advances[1], "300 → 480 did not widen: \(advances)")
        #expect(advances[1] < advances[2], "480 → 650 did not widen: \(advances)")
    }

    @Test("small caps substitute both lower case (smcp) and capitals (c2sc)")
    func smallCapsApply() {
        let plain = YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [])
        let caps = YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [.smallCaps])
        #expect(shapedGlyphs("abc", font: plain) != shapedGlyphs("abc", font: caps))
        #expect(shapedGlyphs("ABC", font: plain) != shapedGlyphs("ABC", font: caps))
    }

    @Test("tabular figures share one advance and carry lnum + tnum")
    func tabularApplies() {
        let font = YsabeauFace.uiFont(size: 17, weight: 400, italic: false, features: [.tabular])
        #expect(advance("1", font: font) == advance("0", font: font))
        let tags = featureTags(font)
        #expect(tags.contains("lnum"), "feature settings: \(tags)")
        #expect(tags.contains("tnum"), "feature settings: \(tags)")
    }

    @Test("lining proportional figures keep their own advances")
    func liningIsProportional() {
        let font = YsabeauFace.uiFont(size: 17, weight: 400, italic: false, features: [.lining])
        #expect(advance("1", font: font) != advance("0", font: font))
    }

    @Test("a missing face falls back to the system font without crashing")
    func missingFaceFallsBack() {
        let font = YsabeauFace.uiFont(size: 17, weight: 600, italic: false, features: [.lining],
                                      faceName: "Ysabeau-DoesNotExist")
        #expect(font.familyName == UIFont.systemFont(ofSize: 17).familyName)
        #expect(font.pointSize == 17)
    }

    @Test("one UIFont per (size, weight, italic, features)")
    func memoised() {
        let first = YsabeauFace.uiFont(size: 13, weight: 560, italic: false, features: [.tabular])
        let second = YsabeauFace.uiFont(size: 13, weight: 560, italic: false, features: [.tabular])
        #expect(first === second)
    }
}

// MARK: - CoreText probes

func wght(_ font: UIFont) -> Double? {
    let variation = CTFontCopyVariation(font as CTFont) as? [NSNumber: NSNumber]
    return variation?[NSNumber(value: YsabeauFace.weightAxis)]?.doubleValue
}

func advance(_ string: String, font: UIFont) -> Double {
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: font]))
    return CTLineGetTypographicBounds(line, nil, nil, nil)
}

func shapedGlyphs(_ string: String, font: UIFont) -> [CGGlyph] {
    let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: font]))
    let runs = CTLineGetGlyphRuns(line) as? [CTRun] ?? []
    return runs.flatMap { run -> [CGGlyph] in
        var glyphs = [CGGlyph](repeating: 0, count: CTRunGetGlyphCount(run))
        CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
        return glyphs
    }
}

func featureTags(_ font: UIFont) -> [String] {
    let settings = CTFontCopyFeatureSettings(font as CTFont) as? [[String: Any]] ?? []
    return settings.compactMap { $0[kCTFontOpenTypeFeatureTag as String] as? String }
}
```

Add the italic-axis test to `apps/ios/PebblesTests/YsabeauItalicFontTests.swift`. Add `@testable import Pebbles` under the existing imports, then this test inside the struct:

```swift
    @Test("the italic at 380 stays italic and reads 380 on the wght axis")
    func italicTakesTheAxis() {
        let font = YsabeauFace.uiFont(size: 17, weight: 380, italic: true, features: [])
        #expect(font.fontDescriptor.symbolicTraits.contains(.traitItalic))
        #expect(wght(font) == 380)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run the single-test command with `YsabeauFaceTests`.
Expected: build FAIL, `cannot find 'YsabeauFace' in scope`.

- [ ] **Step 3: Write `YsabeauFace`**

Create `apps/ios/Pebbles/Theme/YsabeauFace.swift`:

```swift
import CoreText
import Foundation
import os
import UIKit

/// The OpenType features a Ysabeau rank may ask for. The tag strings are the
/// ones Android's `SpineTypography` passes, so both surfaces shape alike.
enum YsabeauFeature: Hashable, CaseIterable {
    /// `SC`: small caps from lower case and from capitals.
    case smallCaps
    /// `LIN`: lining, proportional figures.
    case lining
    /// `TAB`: lining, tabular figures.
    case tabular

    var tags: [String] {
        switch self {
        case .smallCaps: return ["smcp", "c2sc"]
        case .lining:    return ["lnum", "pnum"]
        case .tabular:   return ["lnum", "tnum"]
        }
    }
}

/// The variable Ysabeau at an exact axis value (#1158).
///
/// Both bundled files (`Ysabeau.ttf`, `Ysabeau Italic.ttf`, byte-identical to
/// Android's, checked by `ios.yml`) are variable fonts with one `wght` axis,
/// 1 to 1000, default 700. `UIFont(name:)` only ever resolves that default
/// instance (`Ysabeau-Bold`, `Ysabeau-BoldItalic`), so the weight is set
/// through `kCTFontVariationAttribute`, the path Caveat Bold already ships on.
/// Features go in as OpenType tags.
///
/// The italic has no Lower Case feature, so it has no `smcp`: never pair
/// `italic` with `.smallCaps` (`SpineTypeTests` holds the rank table to that).
///
/// New pattern (raised in the B1 PR): one memoised `UIFont` per
/// (face, size, weight, features), bridged to SwiftUI as `Font(UIFont)`.
enum YsabeauFace {
    static let romanName = "Ysabeau-Bold"
    static let italicName = "Ysabeau-BoldItalic"
    /// `'wght'` as a four-char code (`kCTFontVariationAxisIdentifierKey`).
    static let weightAxis = 0x77_67_68_74

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "fonts")
    private static let lock = NSLock()
    private static let cache = NSCache<NSString, UIFont>()
    private static var loggedMissing: Set<String> = []

    private static let variationAttribute = UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String)
    private static let featureTagKey = UIFontDescriptor.FeatureKey(rawValue: kCTFontOpenTypeFeatureTag as String)
    private static let featureValueKey = UIFontDescriptor.FeatureKey(rawValue: kCTFontOpenTypeFeatureValue as String)

    /// One `UIFont` at an exact axis value. Memoised by (size, weight, italic, features).
    static func uiFont(size: CGFloat, weight: Int, italic: Bool, features: Set<YsabeauFeature>) -> UIFont {
        uiFont(size: size, weight: weight, italic: italic, features: features,
               faceName: italic ? italicName : romanName)
    }

    /// `faceName` is injectable so a test can prove the missing-face fallback.
    static func uiFont(size: CGFloat, weight: Int, italic: Bool, features: Set<YsabeauFeature>,
                       faceName: String) -> UIFont {
        let tags = featureTags(features)
        let key = "\(faceName)|\(size)|\(weight)|\(italic)|\(tags.joined(separator: ","))" as NSString
        lock.lock()
        defer { lock.unlock() }
        if let cached = cache.object(forKey: key) { return cached }
        let font = build(size: size, weight: weight, italic: italic, tags: tags, faceName: faceName)
        cache.setObject(font, forKey: key)
        return font
    }

    /// The de-duplicated, sorted tag list for a feature set.
    static func featureTags(_ features: Set<YsabeauFeature>) -> [String] {
        Array(Set(features.flatMap(\.tags))).sorted()
    }

    /// The `UIFont.Weight` nearest an axis value, for the system fallback.
    static func nearestSystemWeight(_ weight: Int) -> UIFont.Weight {
        let table: [(Int, UIFont.Weight)] = [
            (100, .ultraLight), (200, .thin), (300, .light), (400, .regular), (500, .medium),
            (600, .semibold), (700, .bold), (800, .heavy), (900, .black)
        ]
        return table.min { abs($0.0 - weight) < abs($1.0 - weight) }?.1 ?? .regular
    }

    // Called under `lock`.
    private static func build(size: CGFloat, weight: Int, italic: Bool, tags: [String], faceName: String) -> UIFont {
        guard let base = UIFont(name: faceName, size: size) else {
            if loggedMissing.insert(faceName).inserted {
                logger.error("\(faceName, privacy: .public) failed to load; falling back to the system font")
            }
            let system = UIFont.systemFont(ofSize: size, weight: nearestSystemWeight(weight))
            guard italic, let descriptor = system.fontDescriptor.withSymbolicTraits(.traitItalic) else { return system }
            return UIFont(descriptor: descriptor, size: size)
        }
        var attributes: [UIFontDescriptor.AttributeName: Any] = [variationAttribute: [weightAxis: weight]]
        if !tags.isEmpty {
            attributes[.featureSettings] = tags.map { [featureTagKey: $0, featureValueKey: 1] }
        }
        return UIFont(descriptor: base.fontDescriptor.addingAttributes(attributes), size: size)
    }
}
```

- [ ] **Step 4: Run the tests**

Run the single-test command with `YsabeauFaceTests`, then with `YsabeauItalicFontTests`.
Expected: PASS (6 and 5 tests).

If `smallCapsApply` or `tabularApplies` fails, the OpenType-tag form is not mapped on this runtime (spec §Risks). Replace the `featureSettings` line with the AAT pairs and re-run:

```swift
            attributes[.featureSettings] = tags.compactMap { aatPairs[$0] }.flatMap { $0 }
```

with, inside `YsabeauFace`:

```swift
    /// AAT fallback: Lower Case 37/1, Upper Case 38/1, Number Case 21/1, Number Spacing 6/0 (tnum) or 6/1 (pnum).
    private static let aatPairs: [String: [[UIFontDescriptor.FeatureKey: Int]]] = [
        "smcp": [[.type: 37, .selector: 1]],
        "c2sc": [[.type: 38, .selector: 1]],
        "lnum": [[.type: 21, .selector: 1]],
        "tnum": [[.type: 6, .selector: 0]],
        "pnum": [[.type: 6, .selector: 1]]
    ]
```

and change `featureTags(_ font:)` in the test to also accept type/selector entries. Record which form shipped in the Lessons section at the end of this plan.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Theme/YsabeauFace.swift apps/ios/PebblesTests/YsabeauFaceTests.swift apps/ios/PebblesTests/YsabeauItalicFontTests.swift
git commit -m "feat(ios): add the ysabeau descriptor bridge with axis and feature proofs"
```

### Task B1.4: Rebuild the existing helpers on the variable face

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Font+Pebbles.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift:23`
- Modify: `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift:4-5,14`
- Test: `apps/ios/PebblesTests/YsabeauFaceTests.swift`

- [ ] **Step 1: Write the failing test**

Add to `YsabeauFaceTests`:

```swift
    @Test("the shared Caveat descriptor sets 700 on the axis")
    func caveatBoldDescriptor() {
        let font = UIFont.pebblesCaveat(size: 34, weight: 700)
        #expect(font.fontName.hasPrefix("Caveat"))
        #expect(wght(font) == 700)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run the single-test command with `YsabeauFaceTests`.
Expected: build FAIL, `type 'UIFont' has no member 'pebblesCaveat'`.

- [ ] **Step 3: Rewrite the helpers**

In `apps/ios/Pebbles/Theme/Font+Pebbles.swift`:

Delete the `FontLoadLog` enum (lines 5-11, it has no caller after this task) and the `import os` line.

Replace the whole `ysabeauSemibold(_:)` and `ysabeauSemiboldItalic(_:)` functions, doc comments included, with:

```swift
    /// Ysabeau at weight 600 with lining, proportional figures (`lnum, pnum`),
    /// so digits align to the cap height. The variable roman since #1158
    /// (`YsabeauFace`); the old static SemiBold cut is gone.
    static func ysabeauSemibold(_ size: CGFloat) -> Font {
        Font(YsabeauFace.uiFont(size: size, weight: 600, italic: false, features: [.lining]))
    }

    /// Ysabeau's true italic at weight 600, beside `ysabeauSemibold` at the
    /// same colour and with the same figures.
    static func ysabeauSemiboldItalic(_ size: CGFloat) -> Font {
        Font(YsabeauFace.uiFont(size: size, weight: 600, italic: true, features: [.lining]))
    }
```

Replace the body of `caveatBold(_:)` (keep its doc comment) with:

```swift
    fileprivate static func caveatBold(_ size: CGFloat) -> Font {
        Font(UIFont.pebblesCaveat(size: size, weight: 700))
    }
```

Append at the end of the file:

```swift
extension UIFont {
    /// Caveat at an exact `wght`. `UIFont(name:)` only resolves the variable
    /// file's default instance (400), so the axis is set explicitly; `Caveat-Bold`
    /// is not a resolvable name. One descriptor for `PebblesFont.valenceWord*`
    /// and `SpineType.lockupWord`. Falls back to the bold system font.
    static func pebblesCaveat(size: CGFloat, weight: Int) -> UIFont {
        guard let base = UIFont(name: "Caveat-Regular", size: size) else {
            return UIFont.systemFont(ofSize: size, weight: .bold)
        }
        let descriptor = base.fontDescriptor.addingAttributes([
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [
                YsabeauFace.weightAxis: weight
            ]
        ])
        return UIFont(descriptor: descriptor, size: size)
    }
}
```

In `apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift`, replace

```swift
            .font(.custom("Ysabeau-SemiBold", size: 24))
```

with

```swift
            .font(.ysabeauSemibold(24))
```

In `apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift`, replace

```swift
/// The title uses the bundled Ysabeau-SemiBold display face in muted
/// foreground; the description keeps the system body font and is also
```

with

```swift
/// The title uses Ysabeau at 600 (`Font.ysabeauSemibold`) in muted
/// foreground; the description keeps the system body font and is also
```

and replace

```swift
                .font(.custom("Ysabeau-SemiBold", size: 22))
```

with

```swift
                .font(.ysabeauSemibold(22))
```

The other eleven call sites (spec §B1.6) are untouched.

- [ ] **Step 4: Run the tests and confirm no PostScript name is left**

Run the single-test command with `YsabeauFaceTests`.
Expected: PASS (7 tests).

```bash
cd /Users/alexis/code/pbbls/apps/ios
grep -rn 'Ysabeau-SemiBold\|Ysabeau SemiBold' Pebbles PebblesTests PebblesWidget Shared
```

Expected: only the `UIFont(name: "Ysabeau-SemiBold", size: 17) == nil` assertion and the `"Ysabeau SemiBold"` bundle assertion in `YsabeauItalicFontTests.swift`.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Theme/Font+Pebbles.swift apps/ios/Pebbles/Features/Path/Read/PebbleReadTitle.swift apps/ios/Pebbles/Features/Welcome/WelcomeSlideView.swift apps/ios/PebblesTests/YsabeauFaceTests.swift
git commit -m "feat(ios): rebuild the ysabeau helpers on the variable face"
```

### Task B1.5: The `SpineType` rank table

**Files:**
- Create: `apps/ios/Pebbles/Theme/SpineType.swift`
- Create: `apps/ios/PebblesTests/SpineTypeTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/SpineTypeTests.swift`. The vector transcribes spec §B1.3 row by row, the way Android's `SpineTypographyTest` pins its ranks; `u = 4` and two cuts exercise the parameterised ranks.

```swift
import SwiftUI
import Testing
@testable import Pebbles

@Suite("SpineType · rank table")
struct SpineTypeTests {

    struct Row {
        let name: String
        let actual: SpineType.Style
        let expected: SpineType.Style
    }

    typealias S = SpineType.Style
    static let u: CGFloat = 4
    static let cut = SpineType.Cut(size: 19, weight: 480, italic: false, trackingEm: 0.01)
    static let italicCut = SpineType.Cut(size: 15, weight: 360, italic: true, trackingEm: 0)
    static let sc: Set<YsabeauFeature> = [.smallCaps]
    static let lin: Set<YsabeauFeature> = [.lining]
    static let tab: Set<YsabeauFeature> = [.tabular]
    static let card = SpineType.Scaling.card(cap: 1.3)

    // swiftlint:disable line_length
    static let rows: [Row] = [
        Row(name: "kicker", actual: SpineType.kicker, expected: S(size: 12, weight: 600, trackingEm: 0.2, features: sc, scaling: .text(.caption))),
        Row(name: "title", actual: SpineType.title, expected: S(size: 40, weight: 300, trackingEm: -0.026, features: lin, lineHeight: 40.8, scaling: .text(.largeTitle))),
        Row(name: "summary", actual: SpineType.summary, expected: S(size: 18, weight: 380, italic: true, scaling: .text(.body))),
        Row(name: "sectionLabel", actual: SpineType.sectionLabel, expected: S(size: 11.5, weight: 600, trackingEm: 0.2, features: sc, scaling: .text(.caption2))),
        Row(name: "masthead", actual: SpineType.masthead, expected: S(size: 11.5, weight: 400, trackingEm: 0.18, features: sc, scaling: .text(.caption2))),
        Row(name: "mastheadNumber", actual: SpineType.mastheadNumber, expected: S(size: 11.5, weight: 650, trackingEm: 0.18, features: sc, scaling: .text(.caption2))),
        Row(name: "personName", actual: SpineType.personName, expected: S(size: 15, weight: 600, trackingEm: 0.1, features: sc, scaling: .text(.subheadline))),
        Row(name: "dayLetter", actual: SpineType.dayLetter, expected: S(size: 9.5, weight: 600, features: sc, lineHeight: 12, scaling: .text(.caption2))),
        Row(name: "contentsDay", actual: SpineType.contentsDay, expected: S(size: 10.5, weight: 600, trackingEm: 0.18, features: sc, scaling: .text(.caption2))),
        Row(name: "contentsTime", actual: SpineType.contentsTime, expected: S(size: 12.5, weight: 400, trackingEm: 0.04, features: tab, scaling: .text(.caption))),
        Row(name: "personsFold", actual: SpineType.personsFold, expected: S(size: 16, weight: 380, italic: true, scaling: .text(.body))),
        Row(name: "entryTime", actual: SpineType.entryTime, expected: S(size: 13, weight: 560, trackingEm: 0.06, features: tab, scaling: .text(.footnote))),
        Row(name: "entryEmotion", actual: SpineType.entryEmotion, expected: S(size: 12, weight: 600, trackingEm: 0.12, features: sc, scaling: .text(.caption))),
        Row(name: "people", actual: SpineType.people, expected: S(size: 15, weight: 400, lineHeight: 22.5, scaling: .text(.subheadline))),
        Row(name: "soulName", actual: SpineType.soulName, expected: S(size: 13, weight: 620, trackingEm: 0.1, features: sc, scaling: .text(.footnote))),
        Row(name: "dayNumeral", actual: SpineType.dayNumeral, expected: S(size: 25, weight: 330, trackingEm: -0.02, features: lin, scaling: .text(.title2))),
        Row(name: "dayWeekday", actual: SpineType.dayWeekday, expected: S(size: 10, weight: 600, trackingEm: 0.2, features: sc, scaling: .text(.caption2))),
        Row(name: "mouthLabel", actual: SpineType.mouthLabel, expected: S(size: 12, weight: 600, trackingEm: 0.18, features: sc, scaling: .text(.caption))),
        Row(name: "mouthLine", actual: SpineType.mouthLine, expected: S(size: 16, weight: 380, italic: true, scaling: .text(.body))),
        Row(name: "rollNumeral", actual: SpineType.rollNumeral, expected: S(size: 14, weight: 600, trackingEm: 0.1, features: [.smallCaps, .lining], scaling: .text(.footnote))),
        Row(name: "still", actual: SpineType.still, expected: S(size: 15, weight: 380, italic: true, scaling: .text(.subheadline))),
        Row(name: "contentsName", actual: SpineType.contentsName(cut), expected: S(size: 16.5, weight: 480, lineHeight: 19.8, scaling: .text(.body))),
        Row(name: "contentsName·italic", actual: SpineType.contentsName(italicCut), expected: S(size: 16.5, weight: 360, italic: true, lineHeight: 19.8, scaling: .text(.body))),
        Row(name: "entryName", actual: SpineType.entryName(cut), expected: S(size: 19, weight: 480, trackingEm: 0.01, lineHeight: 19 * 1.12, scaling: .text(.body))),
        Row(name: "entryName·italic", actual: SpineType.entryName(italicCut), expected: S(size: 15, weight: 360, italic: true, lineHeight: 15 * 1.12, scaling: .text(.subheadline))),
        Row(name: "entryName·untitled", actual: SpineType.entryName(.untitled(size: 22)), expected: S(size: 22, weight: 340, italic: true, lineHeight: 22 * 1.12, scaling: .text(.title2))),
        Row(name: "cardIndexNumeral", actual: SpineType.cardIndexNumeral(u: u), expected: S(size: 5.3 * u, weight: 600, trackingEm: -0.045, features: lin, lineHeight: 5.3 * u * 0.86, scaling: card)),
        Row(name: "cardIndexDay", actual: SpineType.cardIndexDay(u: u), expected: S(size: 1.8 * u, weight: 650, trackingEm: 0.14, features: sc, scaling: card)),
        Row(name: "cardRail", actual: SpineType.cardRail(u: u), expected: S(size: 1.95 * u, weight: 560, trackingEm: 0.07, features: tab, scaling: card)),
        Row(name: "cardRailEmotion", actual: SpineType.cardRailEmotion(u: u), expected: S(size: 1.95 * u, weight: 650, trackingEm: 0.15, features: sc, scaling: card)),
        Row(name: "cardTitle", actual: SpineType.cardTitle(u: u, cut: cut), expected: S(size: 4.4 * u, weight: 480, trackingEm: -0.012, lineHeight: 4.4 * u * 1.1, scaling: card)),
        Row(name: "cardTitle·italic", actual: SpineType.cardTitle(u: u, cut: italicCut), expected: S(size: 4.4 * u, weight: 360, trackingEm: -0.012, italic: true, lineHeight: 4.4 * u * 1.1, scaling: card)),
        Row(name: "cardSoul", actual: SpineType.cardSoul(u: u), expected: S(size: 2.5 * u, weight: 650, trackingEm: 0.12, features: sc, scaling: card)),
        Row(name: "cardMore", actual: SpineType.cardMore(u: u), expected: S(size: 2.5 * u, weight: 560, features: tab, scaling: card)),
        Row(name: "cardOthers", actual: SpineType.cardOthers(u: u), expected: S(size: 2.6 * u, weight: 380, italic: true, scaling: card)),
        Row(name: "cardSlipName", actual: SpineType.cardSlipName(u: u, cut: cut), expected: S(size: 6.4 * u, weight: 480, trackingEm: -0.014, lineHeight: 6.4 * u * 1.08, scaling: card)),
        Row(name: "cardSlipName·italic", actual: SpineType.cardSlipName(u: u, cut: italicCut), expected: S(size: 6.4 * u, weight: 360, trackingEm: -0.014, italic: true, lineHeight: 6.4 * u * 1.08, scaling: card)),
        Row(name: "cardFooterGrade", actual: SpineType.cardFooterGrade, expected: S(size: 10.5, weight: 650, trackingEm: 0.16, features: sc, scaling: .text(.caption2))),
        Row(name: "cardFooterNote", actual: SpineType.cardFooterNote, expected: S(size: 12.5, weight: 380, italic: true, scaling: .text(.caption))),
        Row(name: "cardTurnTarget", actual: SpineType.cardTurnTarget, expected: S(size: 13, weight: 380, italic: true, scaling: .text(.footnote))),
        Row(name: "cardLeafDateline", actual: SpineType.cardLeafDateline, expected: S(size: 10.5, weight: 600, trackingEm: 0.18, features: [.smallCaps, .lining], scaling: .text(.caption2))),
        Row(name: "cardLeafBody", actual: SpineType.cardLeafBody, expected: S(size: 17, weight: 380, lineHeight: 27, scaling: .text(.body))),
        Row(name: "cardLockupSpan", actual: SpineType.cardLockupSpan, expected: S(size: 10, weight: 650, trackingEm: 0.22, features: sc, scaling: .text(.caption2))),
        Row(name: "cardEmotion", actual: SpineType.cardEmotion, expected: S(size: 20, weight: 400, scaling: .text(.title3))),
        Row(name: "cardBandName", actual: SpineType.cardBandName, expected: S(size: 14, weight: 400, italic: true, scaling: .text(.footnote))),
        Row(name: "bareTitle", actual: SpineType.bareTitle, expected: S(size: 34, weight: 300, trackingEm: -0.02, lineHeight: 38, scaling: .text(.largeTitle))),
        Row(name: "placeBody", actual: SpineType.placeBody, expected: S(size: 16, weight: 380, lineHeight: 24, scaling: .text(.body))),
        Row(name: "lockupWord·small", actual: SpineType.lockupWord(.small), expected: S(size: 34, weight: 700, scaling: card, face: .caveat, isLowercase: true, inkOverhang: 6)),
        Row(name: "lockupWord·medium", actual: SpineType.lockupWord(.medium), expected: S(size: 44, weight: 700, scaling: card, face: .caveat, inkOverhang: 8)),
        Row(name: "lockupWord·large", actual: SpineType.lockupWord(.large), expected: S(size: 56, weight: 700, scaling: card, face: .caveat, inkOverhang: 10)),
        Row(name: "postmarkRing", actual: SpineType.postmarkRing(8), expected: S(size: 8, weight: 650, features: lin, scaling: .none))
    ]
    // swiftlint:enable line_length

    @Test("every rank matches the spec table", arguments: rows.map(\.name))
    func rankMatches(name: String) throws {
        let row = try #require(Self.rows.first { $0.name == name })
        #expect(row.actual == row.expected, "\(name): \(row.actual) ≠ \(row.expected)")
    }

    @Test("no italic rank carries small caps (the italic has no smcp)")
    func noItalicSmallCaps() {
        for row in Self.rows where row.actual.italic {
            #expect(!row.actual.features.contains(.smallCaps), "\(row.name) pairs italic with small caps")
        }
    }

    @Test("lockupWord is the only Caveat rank; postmarkRing the only unscaled one")
    func singletons() {
        let caveat = Set(Self.rows.filter { $0.actual.face == .caveat }.map { String($0.name.split(separator: "·")[0]) })
        let unscaled = Set(Self.rows.filter { $0.actual.scaling == .none }.map { $0.name })
        #expect(caveat == ["lockupWord"])
        #expect(unscaled == ["postmarkRing"])
    }

    @Test("every fixed text rank's style is its size band")
    func fixedRanksFollowTheBands() {
        for row in Self.rows {
            guard case .text(let style) = row.actual.scaling else { continue }
            #expect(style == SpineType.textStyle(forSize: row.actual.size), "\(row.name)")
        }
    }

    @Test("size bands hold their edges", arguments: [
        (9.5, Font.TextStyle.caption2), (11.5, .caption2), (12, .caption), (12.5, .caption),
        (13, .footnote), (14, .footnote), (15, .subheadline), (16, .body), (19, .body),
        (20, .title3), (21, .title3), (22, .title2), (26, .title2), (27, .title), (33, .title),
        (34, .largeTitle), (56, .largeTitle)
    ] as [(CGFloat, Font.TextStyle)])
    func bands(size: CGFloat, style: Font.TextStyle) {
        #expect(SpineType.textStyle(forSize: size) == style, "\(size) pt")
    }

    @Test("the untitled cut is the 340 italic, untracked")
    func untitledCut() {
        #expect(SpineType.Cut.untitled(size: 18) == SpineType.Cut(size: 18, weight: 340, italic: true, trackingEm: 0))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run the single-test command with `SpineTypeTests`.
Expected: build FAIL, `cannot find 'SpineType' in scope`.

- [ ] **Step 3: Write the table**

Create `apps/ios/Pebbles/Theme/SpineType.swift`:

```swift
import SwiftUI

/// The Spine's and the held card's typographic ranks (#1158), one for one with
/// Android's `SpineTypography`
/// (`apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/SpineTypography.kt`):
/// same names, sizes in points (Android's sp), weights on Ysabeau's `wght`
/// axis, tracking in em.
///
/// Feature views never name a size, a weight or a feature. They pick a rank
/// here and apply it with `.spineType(_:)`, which also owns Dynamic Type
/// (spec §B1.5): a view never scales Spine or card text itself. Never put
/// `.bold()` or `.fontWeight()` on a `spineType` text.
///
/// New patterns (raised in the B1 PR): a rank model that owns its own scaling
/// (`Style.scaling`), and `Cut` as the only logic-to-view handoff for a name's
/// weight.
enum SpineType {
    enum Face: Equatable { case ysabeau, caveat }

    enum Scaling: Equatable {
        /// Scales with the text style through `UIFontMetrics`, up to `.accessibility2`.
        case text(Font.TextStyle)
        /// Card-unit ranks: scale with the content size up to `cap`, no further.
        case card(cap: CGFloat)
        /// Sized by its caller (`RingFit`).
        case none
    }

    /// A name's cut. The Spine's `EntryType` port (#1130) builds the valence
    /// cuts; `untitled(size:)` is the untitled italic. No other struct, tuple or
    /// raw weight crosses from logic into a view.
    struct Cut: Equatable {
        var size: CGFloat
        var weight: Int
        var italic: Bool
        var trackingEm: CGFloat

        /// Android writes it inside `SpineEntry.kt` as `UNTITLED_WEIGHT`; on iOS it
        /// lives here, so no view names it.
        static let untitledWeight = 340

        static func untitled(size: CGFloat) -> Cut {
            Cut(size: size, weight: untitledWeight, italic: true, trackingEm: 0)
        }
    }

    struct Style: Equatable {
        var size: CGFloat
        var weight: Int
        var trackingEm: CGFloat = 0
        var italic: Bool = false
        var features: Set<YsabeauFeature> = []
        /// Points at the rank's size; nil is the font's natural line height.
        var lineHeight: CGFloat?
        var scaling: Scaling
        var face: Face = .ysabeau
        var isLowercase: Bool = false
        /// Horizontal room the ink needs past its advance (Caveat only).
        var inkOverhang: CGFloat = 0
    }

    static let cardCap: CGFloat = 1.3
    private static let card = Scaling.card(cap: cardCap)

    // MARK: - Fixed ranks (the Spine)

    static let kicker = Style(size: 12, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption))
    static let title = Style(size: 40, weight: 300, trackingEm: -0.026, features: [.lining], lineHeight: 40.8,
                             scaling: .text(.largeTitle))
    static let summary = Style(size: 18, weight: 380, italic: true, scaling: .text(.body))
    static let sectionLabel = Style(size: 11.5, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption2))
    /// The masthead's way back a week: the body's 400; only the number is bold.
    static let masthead = Style(size: 11.5, weight: 400, trackingEm: 0.18, features: [.smallCaps], scaling: .text(.caption2))
    static let mastheadNumber = Style(size: 11.5, weight: 650, trackingEm: 0.18, features: [.smallCaps],
                                      scaling: .text(.caption2))
    static let personName = Style(size: 15, weight: 600, trackingEm: 0.1, features: [.smallCaps], scaling: .text(.subheadline))
    static let dayLetter = Style(size: 9.5, weight: 600, features: [.smallCaps], lineHeight: 12, scaling: .text(.caption2))
    static let contentsDay = Style(size: 10.5, weight: 600, trackingEm: 0.18, features: [.smallCaps], scaling: .text(.caption2))
    static let contentsTime = Style(size: 12.5, weight: 400, trackingEm: 0.04, features: [.tabular], scaling: .text(.caption))
    /// The persons' fold, "and *N* others".
    static let personsFold = Style(size: 16, weight: 380, italic: true, scaling: .text(.body))
    static let entryTime = Style(size: 13, weight: 560, trackingEm: 0.06, features: [.tabular], scaling: .text(.footnote))
    static let entryEmotion = Style(size: 12, weight: 600, trackingEm: 0.12, features: [.smallCaps], scaling: .text(.caption))
    static let people = Style(size: 15, weight: 400, lineHeight: 22.5, scaling: .text(.subheadline))
    static let soulName = Style(size: 13, weight: 620, trackingEm: 0.1, features: [.smallCaps], scaling: .text(.footnote))
    static let dayNumeral = Style(size: 25, weight: 330, trackingEm: -0.02, features: [.lining], scaling: .text(.title2))
    static let dayWeekday = Style(size: 10, weight: 600, trackingEm: 0.2, features: [.smallCaps], scaling: .text(.caption2))
    static let mouthLabel = Style(size: 12, weight: 600, trackingEm: 0.18, features: [.smallCaps], scaling: .text(.caption))
    static let mouthLine = Style(size: 16, weight: 380, italic: true, scaling: .text(.body))
    static let rollNumeral = Style(size: 14, weight: 600, trackingEm: 0.1, features: [.smallCaps, .lining],
                                   scaling: .text(.footnote))
    static let still = Style(size: 15, weight: 380, italic: true, scaling: .text(.subheadline))

    // MARK: - Ranks that take a name's cut

    static func contentsName(_ cut: Cut) -> Style {
        Style(size: 16.5, weight: cut.weight, italic: cut.italic, lineHeight: 19.8, scaling: .text(.body))
    }

    static func entryName(_ cut: Cut) -> Style {
        Style(size: cut.size, weight: cut.weight, trackingEm: cut.trackingEm, italic: cut.italic,
              lineHeight: cut.size * 1.12, scaling: .text(textStyle(forSize: cut.size)))
    }

    // MARK: - Card-unit ranks (`u` in points)

    static func cardIndexNumeral(u: CGFloat) -> Style {
        let size = 5.3 * u
        return Style(size: size, weight: 600, trackingEm: -0.045, features: [.lining], lineHeight: size * 0.86, scaling: card)
    }

    static func cardIndexDay(u: CGFloat) -> Style {
        Style(size: 1.8 * u, weight: 650, trackingEm: 0.14, features: [.smallCaps], scaling: card)
    }

    static func cardRail(u: CGFloat) -> Style {
        Style(size: 1.95 * u, weight: 560, trackingEm: 0.07, features: [.tabular], scaling: card)
    }

    static func cardRailEmotion(u: CGFloat) -> Style {
        Style(size: 1.95 * u, weight: 650, trackingEm: 0.15, features: [.smallCaps], scaling: card)
    }

    static func cardTitle(u: CGFloat, cut: Cut) -> Style {
        let size = 4.4 * u
        return Style(size: size, weight: cut.weight, trackingEm: -0.012, italic: cut.italic, lineHeight: size * 1.1, scaling: card)
    }

    static func cardSoul(u: CGFloat) -> Style {
        Style(size: 2.5 * u, weight: 650, trackingEm: 0.12, features: [.smallCaps], scaling: card)
    }

    static func cardMore(u: CGFloat) -> Style {
        Style(size: 2.5 * u, weight: 560, features: [.tabular], scaling: card)
    }

    /// The strip's "& twelve others".
    static func cardOthers(u: CGFloat) -> Style {
        Style(size: 2.6 * u, weight: 380, italic: true, scaling: card)
    }

    /// The photo-less recto's slip: the entry's cut at 6.4 card units.
    static func cardSlipName(u: CGFloat, cut: Cut) -> Style {
        let size = 6.4 * u
        return Style(size: size, weight: cut.weight, trackingEm: -0.014, italic: cut.italic, lineHeight: size * 1.08, scaling: card)
    }

    // MARK: - The held card's fixed ranks

    static let cardFooterGrade = Style(size: 10.5, weight: 650, trackingEm: 0.16, features: [.smallCaps], scaling: .text(.caption2))
    static let cardFooterNote = Style(size: 12.5, weight: 380, italic: true, scaling: .text(.caption))
    static let cardTurnTarget = Style(size: 13, weight: 380, italic: true, scaling: .text(.footnote))
    static let cardLeafDateline = Style(size: 10.5, weight: 600, trackingEm: 0.18, features: [.smallCaps, .lining],
                                        scaling: .text(.caption2))
    static let cardLeafBody = Style(size: 17, weight: 380, lineHeight: 27, scaling: .text(.body))
    static let cardLockupSpan = Style(size: 10, weight: 650, trackingEm: 0.22, features: [.smallCaps], scaling: .text(.caption2))
    static let cardEmotion = Style(size: 20, weight: 400, scaling: .text(.title3))
    static let cardBandName = Style(size: 14, weight: 400, italic: true, scaling: .text(.footnote))
    static let bareTitle = Style(size: 34, weight: 300, trackingEm: -0.02, lineHeight: 38, scaling: .text(.largeTitle))
    static let placeBody = Style(size: 16, weight: 380, lineHeight: 24, scaling: .text(.body))

    /// The valence word at the top of the card's verso: Caveat Bold at 34 / 44 / 56
    /// by size group, lowercase for a day event, with `PebblesFont.valenceWord*`'s
    /// ink overhang. The one non-Ysabeau rank.
    static func lockupWord(_ group: ValenceSizeGroup) -> Style {
        switch group {
        case .small:
            return Style(size: 34, weight: 700, scaling: card, face: .caveat, isLowercase: true, inkOverhang: 6)
        case .medium:
            return Style(size: 44, weight: 700, scaling: card, face: .caveat, inkOverhang: 8)
        case .large:
            return Style(size: 56, weight: 700, scaling: card, face: .caveat, inkOverhang: 10)
        }
    }

    /// The postmark ring: capitals (small caps are too small at ring size), sized by `RingFit`.
    static func postmarkRing(_ size: CGFloat) -> Style {
        Style(size: size, weight: 650, features: [.lining], scaling: .none)
    }

    // MARK: - Size bands

    /// The text style a size scales with: ≤ 11.5 caption2 · 12–12.5 caption ·
    /// 13–14 footnote · 15 subheadline · 16–19 body · 20–21 title3 · 22–26 title2 ·
    /// 27–33 title · ≥ 34 largeTitle.
    static func textStyle(forSize size: CGFloat) -> Font.TextStyle {
        switch size {
        case ..<12: return .caption2
        case ..<13: return .caption
        case ..<15: return .footnote
        case ..<16: return .subheadline
        case ..<20: return .body
        case ..<22: return .title3
        case ..<27: return .title2
        case ..<34: return .title
        default:    return .largeTitle
        }
    }
}
```

- [ ] **Step 4: Run the tests**

Run the single-test command with `SpineTypeTests`.
Expected: PASS (every row, plus the five property tests).

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Theme/SpineType.swift apps/ios/PebblesTests/SpineTypeTests.swift
git commit -m "feat(ios): add the spine type rank table mirroring android"
```

### Task B1.6: Resolution and the `spineType(_:)` modifier

**Files:**
- Create: `apps/ios/Pebbles/Theme/SpineTypeModifier.swift`
- Create: `apps/ios/PebblesTests/SpineTypeResolveTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/SpineTypeResolveTests.swift`:

```swift
import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("SpineType · Dynamic Type and Bold Text")
struct SpineTypeResolveTests {

    private func size(_ style: SpineType.Style, _ dynamicType: DynamicTypeSize) -> CGFloat {
        SpineType.resolve(style, dynamicTypeSize: dynamicType, legibilityWeight: nil).size
    }

    private let ax2Traits = UITraitCollection(preferredContentSizeCategory: .accessibilityLarge)
    private let cut = SpineType.Cut(size: 19, weight: 480, italic: false, trackingEm: 0)

    @Test(".accessibility2 is UIKit's accessibilityLarge")
    func categoryMapping() {
        #expect(SpineType.contentSizeCategory(.large) == .large)
        #expect(SpineType.contentSizeCategory(.accessibility2) == .accessibilityLarge)
        #expect(SpineType.contentSizeCategory(.accessibility5) == .accessibilityExtraExtraExtraLarge)
    }

    @Test("kicker is 12 pt at .large and follows caption up to .accessibility2")
    func kickerScales() {
        #expect(size(SpineType.kicker, .large) == 12)
        let expected = UIFontMetrics(forTextStyle: .caption1).scaledValue(for: 12, compatibleWith: ax2Traits)
        #expect(size(SpineType.kicker, .accessibility2) == expected)
        #expect(expected > 12)
    }

    @Test(".accessibility5 resolves to the .accessibility2 size")
    func textCapsAtAx2() {
        #expect(size(SpineType.kicker, .accessibility5) == size(SpineType.kicker, .accessibility2))
        #expect(size(SpineType.cardLeafBody, .accessibility5) == size(SpineType.cardLeafBody, .accessibility2))
    }

    @Test("card ranks stop at ×1.3")
    func cardCap() {
        let title = SpineType.cardTitle(u: 4, cut: cut)
        let large = size(title, .large)
        #expect(abs(size(title, .accessibility2) - large * 1.3) < 0.0001)
        #expect(abs(size(title, .accessibility5) - large * 1.3) < 0.0001)
        let lockup = SpineType.lockupWord(.large)
        #expect(abs(size(lockup, .accessibility2) - 56 * 1.3) < 0.0001)
    }

    @Test("postmarkRing never scales", arguments: DynamicTypeSize.allCases)
    func ringIsFixed(dynamicType: DynamicTypeSize) {
        #expect(size(SpineType.postmarkRing(8), dynamicType) == 8)
    }

    @Test("Bold Text adds 100 to the axis and caps at 800")
    func boldText() {
        #expect(SpineType.resolve(SpineType.kicker, dynamicTypeSize: .large, legibilityWeight: .bold).weight == 700)
        #expect(SpineType.resolve(SpineType.kicker, dynamicTypeSize: .large, legibilityWeight: .regular).weight == 600)
        var heavy = SpineType.kicker
        heavy.weight = 750
        #expect(SpineType.resolve(heavy, dynamicTypeSize: .large, legibilityWeight: .bold).weight == 800)
        let resolved = SpineType.resolve(SpineType.kicker, dynamicTypeSize: .large, legibilityWeight: .bold)
        #expect(wght(resolved.uiFont) == 700)
    }

    @Test("tracking is em × resolved size; line spacing is the line height less the font's")
    func trackingAndLineSpacing() {
        let kicker = SpineType.resolve(SpineType.kicker, dynamicTypeSize: .large, legibilityWeight: nil)
        #expect(abs(kicker.tracking - 2.4) < 0.0001)
        #expect(kicker.lineSpacing == 0)
        let title = SpineType.resolve(SpineType.title, dynamicTypeSize: .large, legibilityWeight: nil)
        #expect(abs(title.lineSpacing - (40.8 - title.uiFont.lineHeight)) < 0.0001)
        #expect(title.lineSpacing < 0, "title asks for less than Ysabeau's natural 1.343 em")
    }

    @Test("lockupWord resolves through the Caveat descriptor at 700")
    func lockupFace() {
        let resolved = SpineType.resolve(SpineType.lockupWord(.medium), dynamicTypeSize: .large, legibilityWeight: nil)
        #expect(resolved.uiFont.fontName.hasPrefix("Caveat"))
        #expect(wght(resolved.uiFont) == 700)
    }

    /// Spec §Risks: iOS 17 `Text` has no line-height API, so a tight rank relies
    /// on a negative `lineSpacing`. Two lines of `title` must stack at the font's
    /// natural line height plus 40.8 pt (≈ 40 × 1.343 + 40.8 = 94.5 pt).
    @Test("probe: a two-line title stacks at natural + 40.8 pt")
    @MainActor
    func titleLineHeightProbe() throws {
        let view = Text(verbatim: "Mmm\nMmm").spineType(SpineType.title).fixedSize()
        let renderer = ImageRenderer(content: view)
        renderer.scale = 1
        let height = try #require(renderer.uiImage).size.height
        let natural = YsabeauFace.uiFont(size: 40, weight: 300, italic: false, features: [.lining]).lineHeight
        #expect(abs(height - (natural + 40.8)) <= 1, "measured \(height) pt, natural line \(natural) pt")
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run the single-test command with `SpineTypeResolveTests`.
Expected: build FAIL, `type 'SpineType' has no member 'resolve'`.

- [ ] **Step 3: Write the resolution and the modifier**

Create `apps/ios/Pebbles/Theme/SpineTypeModifier.swift`:

```swift
import SwiftUI
import UIKit

extension SpineType {
    /// A rank resolved for one environment.
    struct Resolved {
        let size: CGFloat
        let weight: Int
        let tracking: CGFloat
        /// Added between lines; negative when the rank asks for less than the
        /// font's natural line height.
        let lineSpacing: CGFloat
        let uiFont: UIFont
    }

    /// Text ranks stop growing here (≈ 194 % at body size), the iOS twin of
    /// Android's font-scale 2.0 ceiling.
    static let dynamicTypeCeiling: DynamicTypeSize = .accessibility2

    /// Pure, so tests pin Dynamic Type and Bold Text without a view.
    static func resolve(_ style: Style, dynamicTypeSize: DynamicTypeSize, legibilityWeight: LegibilityWeight?) -> Resolved {
        let traits = UITraitCollection(
            preferredContentSizeCategory: contentSizeCategory(min(dynamicTypeSize, dynamicTypeCeiling))
        )
        let size: CGFloat
        switch style.scaling {
        case .text(let textStyle):
            size = UIFontMetrics(forTextStyle: uiTextStyle(textStyle)).scaledValue(for: style.size, compatibleWith: traits)
        case .card(let cap):
            size = style.size * min(cap, UIFontMetrics(forTextStyle: .body).scaledValue(for: 1, compatibleWith: traits))
        case .none:
            size = style.size
        }
        // A custom face gets no Bold Text from the system, and the Spine is text-heavy.
        let weight = legibilityWeight == .bold ? min(style.weight + 100, 800) : style.weight
        let uiFont: UIFont
        switch style.face {
        case .ysabeau:
            uiFont = YsabeauFace.uiFont(size: size, weight: weight, italic: style.italic, features: style.features)
        case .caveat:
            uiFont = UIFont.pebblesCaveat(size: size, weight: weight)
        }
        let factor = style.size > 0 ? size / style.size : 1
        let lineSpacing = style.lineHeight.map { $0 * factor - uiFont.lineHeight } ?? 0
        return Resolved(size: size, weight: weight, tracking: style.trackingEm * size, lineSpacing: lineSpacing, uiFont: uiFont)
    }

    static func contentSizeCategory(_ size: DynamicTypeSize) -> UIContentSizeCategory {
        switch size {
        case .xSmall:         return .extraSmall
        case .small:          return .small
        case .medium:         return .medium
        case .large:          return .large
        case .xLarge:         return .extraLarge
        case .xxLarge:        return .extraExtraLarge
        case .xxxLarge:       return .extraExtraExtraLarge
        case .accessibility1: return .accessibilityMedium
        case .accessibility2: return .accessibilityLarge
        case .accessibility3: return .accessibilityExtraLarge
        case .accessibility4: return .accessibilityExtraExtraLarge
        case .accessibility5: return .accessibilityExtraExtraExtraLarge
        @unknown default:     return .large
        }
    }

    static func uiTextStyle(_ style: Font.TextStyle) -> UIFont.TextStyle {
        switch style {
        case .largeTitle:  return .largeTitle
        case .title:       return .title1
        case .title2:      return .title2
        case .title3:      return .title3
        case .headline:    return .headline
        case .subheadline: return .subheadline
        case .body:        return .body
        case .callout:     return .callout
        case .footnote:    return .footnote
        case .caption:     return .caption1
        case .caption2:    return .caption2
        default:           return .body
        }
    }
}

extension View {
    /// Apply a Spine rank: font, tracking, line spacing and case, resolved from
    /// the SwiftUI environment (not `UITraitCollection.current`), so it reacts to
    /// Dynamic Type and a test can pin it with `.environment(\.dynamicTypeSize, …)`.
    /// Never add `.bold()` or `.fontWeight()` after it.
    func spineType(_ style: SpineType.Style) -> some View {
        modifier(SpineTypeModifier(style: style))
    }
}

private struct SpineTypeModifier: ViewModifier {
    let style: SpineType.Style
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    func body(content: Content) -> some View {
        let resolved = SpineType.resolve(style, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        content
            .font(Font(resolved.uiFont))
            .tracking(resolved.tracking)
            .lineSpacing(resolved.lineSpacing)
            .textCase(style.isLowercase ? .lowercase : nil)
    }
}
```

- [ ] **Step 4: Run the tests**

Run the single-test command with `SpineTypeResolveTests`.
Expected: PASS, every test.

If only `titleLineHeightProbe` fails and the measured height is ≈ 107 pt (two natural lines, ≈ 2 × 53.7), the runtime clamps a negative `lineSpacing` to zero. Do not guess a workaround (spec §Risks): wrap the `#expect` in

```swift
        withKnownIssue("iOS clamps negative lineSpacing; #1130 decides natural spacing or per-line layout") {
            #expect(abs(height - (natural + 40.8)) <= 1, "measured \(height) pt, natural line \(natural) pt")
        }
```

and write the measured height into the Lessons section of this plan.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Theme/SpineTypeModifier.swift apps/ios/PebblesTests/SpineTypeResolveTests.swift
git commit -m "feat(ios): resolve spine type ranks for dynamic type and bold text"
```

### Task B1.7: Rider #1101, French says galet

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings:5561,6111,6127`
- Modify: `apps/ios/Pebbles/Features/Karma/KarmaReason.swift:10-11`
- Test: `apps/ios/PebblesTests/LocalizationTests.swift`

**Deviation from spec §B1.8, flag it in the PR body:** the spec's test says no `fr` value contains "caillou" **or "pebble"**. On `main`, 14 other French values contain "pebble" (the brand "Pebbles", "pebbler", and "pebble" as a noun in strings such as "Aucun pebble cette semaine"), none of which are among the three values this rider changes. That assertion would fail the suite. The test below pins "caillou" everywhere and the three galet values exactly; the remaining French "pebble" nouns stay with #1101's web/Android follow-through, for the maintainer to scope.

- [ ] **Step 1: Write the failing test**

In `apps/ios/PebblesTests/LocalizationTests.swift`, inside `struct LocalizationCatalogFileTests`, after `everyEntryHasBothLocales()`, add:

```swift
    @Test("French says galet, never caillou (#1101)")
    func frenchSaysGalet() throws {
        let bundle = Bundle(for: CatalogProbe.self)
        let url = try #require(bundle.url(forResource: "Localizable", withExtension: "xcstrings"))
        let catalog = try JSONDecoder().decode(Catalog.self, from: Data(contentsOf: url))

        for (key, entry) in catalog.strings {
            for value in entry.localizations?["fr"]?.values ?? [] {
                #expect(value.range(of: "caillou", options: .caseInsensitive) == nil, "fr '\(key)' says caillou: \(value)")
            }
        }
        #expect(catalog.strings["New pebble"]?.localizations?["fr"]?.values == ["Nouveau galet"])
        #expect(catalog.strings["Pebble created"]?.localizations?["fr"]?.values == ["Galet créé"])
        #expect(catalog.strings["Pebble enriched"]?.localizations?["fr"]?.values == ["Galet enrichi"])
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run the single-test command with `LocalizationCatalogFileTests`.
Expected: FAIL on "Caillou créé", "Caillou enrichi" and the three exact values.

- [ ] **Step 3: Edit the catalog as text, then the doc comment**

With the Edit tool on `apps/ios/Pebbles/Resources/Localizable.xcstrings` (each `old_string` is unique in the file):

- `"value" : "Nouveau pebble"` → `"value" : "Nouveau galet"`
- `"value" : "Caillou créé"` → `"value" : "Galet créé"`
- `"value" : "Caillou enrichi"` → `"value" : "Galet enrichi"`

All three nouns are masculine, so nothing else in the strings moves.

In `apps/ios/Pebbles/Features/Karma/KarmaReason.swift`, replace

```swift
    /// User-facing label, localized. French copy matches web verbatim
    /// ("Caillou créé" / "Caillou enrichi").
```

with

```swift
    /// User-facing label, localized. French says galet (#1101):
    /// "Galet créé" / "Galet enrichi".
```

- [ ] **Step 4: Run the test and check the diff is three lines**

Run the single-test command with `LocalizationCatalogFileTests`.
Expected: PASS.

```bash
cd /Users/alexis/code/pbbls
git diff --stat apps/ios/Pebbles/Resources/Localizable.xcstrings
```

Expected: `1 file changed, 3 insertions(+), 3 deletions(-)`. Anything larger means the file was re-serialised: revert it and redo the three edits.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Resources/Localizable.xcstrings apps/ios/Pebbles/Features/Karma/KarmaReason.swift apps/ios/PebblesTests/LocalizationTests.swift
git commit -m "fix(ios): say galet in the french karma reasons and new-pebble button"
```

### Task B1.8: Verify B1 and open its PR

- [ ] **Step 1: Lint**

```bash
cd /Users/alexis/code/pbbls && npm run lint --workspace=@pbbls/ios
```

Expected: no error-severity violation (warnings are advisory).

- [ ] **Step 2: Whole suite, EN**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
cd /Users/alexis/code/pbbls && npm run test --workspace=@pbbls/ios
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 3: The FR pass over B1's locale-independent suites**

```bash
cd /Users/alexis/code/pbbls/apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -testLanguage fr -testRegion FR -only-testing:PebblesTests/YsabeauFaceTests -only-testing:PebblesTests/YsabeauItalicFontTests -only-testing:PebblesTests/SpineTypeTests -only-testing:PebblesTests/SpineTypeResolveTests -only-testing:PebblesTests/LocalizationCatalogFileTests -quiet
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 4: Manual check on the iPhone 15**

Install the branch build. Compare against Task B1.0's six screenshots: welcome slide 2, record title, one Path row (named + untitled), in EN and FR. Expected: the same design; small glyph differences from the newer release are acceptable, a visibly bolder/lighter title or a fallback system font is not. In FR, earn karma once (create a pebble) and confirm the capsule reads "Galet créé", and the new-pebble button reads "Nouveau galet".

- [ ] **Step 5: Push and open the PR**

```bash
cd /Users/alexis/code/pbbls
gh stack submit --auto
gh pr edit feat/1158-ios-variable-ysabeau --title "feat(ios): variable ysabeau and the spine type rank table" --add-label feat,ui,ios,no-lab-note --milestone "M64 · New Path" --body-file - <<'EOF'
Resolves #1158
Part of #1101 (iOS: three French values; web and Android close it)

## What
- `Ysabeau.ttf` (Android's variable roman, byte-identical; `ios.yml` `cmp`s both Ysabeau pairs) replaces the static `Ysabeau SemiBold.ttf`.
- `YsabeauFace`: one memoised `UIFont` per (size, wght, italic, features).
- `SpineType`: every rank of Android's `SpineTypography`, with its Dynamic Type policy; `.spineType(_:)` applies it.
- `ysabeauSemibold*` rebuilt on the variable face at 600; the two `Ysabeau-SemiBold` PostScript call sites now use the helper.
- FR: "Nouveau galet", "Galet créé", "Galet enrichi".

## New patterns, for review
- The variable-font descriptor bridge (`YsabeauFace`).
- A rank model that owns its own scaling (`SpineType.Style.scaling`: `.text`, `.card(cap:)`, `.none`), resolved from the SwiftUI environment.
- `SpineType.Cut` as the logic-to-view handoff for a name's weight.

## Notes
- The spec's "no fr value contains pebble" assertion is narrowed to "caillou": 14 other French values contain "pebble" (brand, "pebbler", the noun) and are outside this rider.
- Line-height probe: <write the measured result from Task B1.6>.

## Checks
- `npm run lint --workspace=@pbbls/ios`, `npm run test --workspace=@pbbls/ios`, the FR pass over the type and catalog suites.
- On the iPhone 15, EN and FR: welcome, record title and a Path row unchanged.
EOF
```

Before running it, replace the `<write the measured result from Task B1.6>` line with the real outcome ("passes within 1 pt" or the measured height). Arkaik: none applies.

---

## Part B2 — quality/1159-ios-reference-images

Labels `quality`, `ios`, `no-lab-note` · milestone `M64 · New Path` · PR body `Resolves #1159`.

### Task B2.0: Add the branch to the stack

- [ ] **Step 1**

```bash
cd /Users/alexis/code/pbbls
git switch feat/1158-ios-variable-ysabeau
gh stack add quality/1159-ios-reference-images
git branch --show-current
```

Expected: `quality/1159-ios-reference-images`.

### Task B2.1: `ReferenceConfig` and the file naming

**Files:**
- Create: `apps/ios/PebblesTests/References/Support/ReferenceConfig.swift`
- Create: `apps/ios/PebblesTests/References/Support/ReferenceImageTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/References/Support/ReferenceImageTests.swift`:

```swift
import CoreGraphics
import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("Reference harness")
struct ReferenceImageTests {

    @Test("file names cover every scheme, locale and Dynamic Type combination")
    func fileNames() {
        let expected: [(ReferenceScheme, ReferenceLocale, DynamicTypeSize, String)] = [
            (.paper, .en, .large, "slide-paper-en.png"),
            (.paper, .fr, .large, "slide-paper-fr.png"),
            (.night, .en, .large, "slide-night-en.png"),
            (.night, .fr, .large, "slide-night-fr.png"),
            (.paper, .en, .accessibility2, "slide-paper-en-ax2.png"),
            (.paper, .fr, .accessibility2, "slide-paper-fr-ax2.png"),
            (.night, .en, .accessibility2, "slide-night-en-ax2.png"),
            (.night, .fr, .accessibility2, "slide-night-fr-ax2.png")
        ]
        for (scheme, locale, dynamicType, name) in expected {
            let config = ReferenceConfig(scheme: scheme, locales: [locale], dynamicType: dynamicType)
            #expect(config.fileName("slide", locale: locale) == name)
        }
    }

    @Test("a reference only runs in the passes it declares")
    func localeSkip() {
        let englishOnly = ReferenceConfig(scheme: .paper, locales: [.en])
        #expect(englishOnly.runs(in: .en))
        #expect(!englishOnly.runs(in: .fr))
        let both = ReferenceConfig(scheme: .night, locales: [.en, .fr])
        #expect(both.runs(in: .fr))
    }

    @Test("the reference width is the iPhone 15's 393 pt")
    func defaultWidth() {
        #expect(ReferenceConfig(scheme: .paper).width == 393)
        #expect(ReferenceConfig(scheme: .paper).dynamicType == .large)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run the single-test command with `ReferenceImageTests`.
Expected: build FAIL, `cannot find 'ReferenceConfig' in scope`.

- [ ] **Step 3: Write the config**

Create `apps/ios/PebblesTests/References/Support/ReferenceConfig.swift`:

```swift
import SwiftUI
import UIKit

/// Paper is the light scheme, Night the dark one.
enum ReferenceScheme: String, CaseIterable, Sendable {
    case paper, night

    var colorScheme: ColorScheme { self == .paper ? .light : .dark }
    var userInterfaceStyle: UIUserInterfaceStyle { self == .paper ? .light : .dark }
}

/// The locale pass this process runs. `String(localized:)` ignores the SwiftUI
/// environment, so French is a whole second run (`xcodebuild -testLanguage fr`),
/// and a reference declares which passes render it.
enum ReferenceLocale: String, CaseIterable, Sendable {
    case en, fr

    static var current: ReferenceLocale {
        Bundle.main.preferredLocalizations.first?.hasPrefix("fr") == true ? .fr : .en
    }
}

/// Every pin a reference carries (spec §B2 D3). Width, scale, scheme, locale,
/// time zone and Dynamic Type are set here; the device and runtime are pinned
/// in `ios.yml`.
struct ReferenceConfig {
    /// The citable reference width: an iPhone 15, the maintainer's device.
    static let phoneWidth: CGFloat = 393

    var scheme: ReferenceScheme
    var locales: Set<ReferenceLocale> = [.en]
    /// `.large`, or `.accessibility2` for an `-ax2` variant.
    var dynamicType: DynamicTypeSize = .large
    /// nil renders a component at its own fixed frame.
    var width: CGFloat? = ReferenceConfig.phoneWidth

    func runs(in locale: ReferenceLocale) -> Bool {
        locales.contains(locale)
    }

    /// `<name>-<paper|night>-<en|fr>[-ax2].png`
    func fileName(_ name: String, locale: ReferenceLocale) -> String {
        precondition(dynamicType == .large || dynamicType == .accessibility2,
                     "references render at .large or .accessibility2 only")
        let suffix = dynamicType == .accessibility2 ? "-ax2" : ""
        return "\(name)-\(scheme.rawValue)-\(locale.rawValue)\(suffix).png"
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
```

Run the single-test command with `ReferenceImageTests`.
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/References/Support
git commit -m "test(ios): add the reference config and its naming"
```

### Task B2.2: The pixel diff, the PNG I/O and the paths

**Files:**
- Create: `apps/ios/PebblesTests/References/Support/ReferenceImage.swift`
- Modify: `apps/ios/PebblesTests/References/Support/ReferenceImageTests.swift`

- [ ] **Step 1: Write the failing tests**

Add to `ReferenceImageTests`:

```swift
    private func solid(_ width: Int, _ height: Int, rgba: [UInt8]) -> [UInt8] {
        Array((0..<(width * height)).map { _ in rgba }.joined())
    }

    @Test("identical images differ in no pixel")
    func identical() throws {
        let bytes = solid(10, 10, rgba: [10, 20, 30, 255])
        let a = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        let b = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        #expect(ReferenceImage.compare(a, b) == .differing(fraction: 0))
    }

    @Test("one channel of one pixel in a hundred is 1 %")
    func onePixel() throws {
        var bytes = solid(10, 10, rgba: [10, 20, 30, 255])
        let a = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        bytes[4 * 37 + 2] = 31
        let b = try #require(ReferenceImage.image(fromRGBA: bytes, width: 10, height: 10))
        #expect(ReferenceImage.compare(a, b) == .differing(fraction: 0.01))
        #expect(0.01 > ReferenceImage.threshold)
    }

    @Test("a size mismatch always fails")
    func sizeMismatch() throws {
        let a = try #require(ReferenceImage.image(fromRGBA: solid(10, 10, rgba: [0, 0, 0, 255]), width: 10, height: 10))
        let b = try #require(ReferenceImage.image(fromRGBA: solid(10, 11, rgba: [0, 0, 0, 255]), width: 10, height: 11))
        #expect(ReferenceImage.compare(a, b) == .sizeMismatch)
    }

    @Test("the diff paints differing pixels magenta over a grey copy")
    func diffImage() throws {
        var bytes = solid(2, 1, rgba: [255, 255, 255, 255])
        let reference = try #require(ReferenceImage.image(fromRGBA: bytes, width: 2, height: 1))
        bytes[0] = 0
        let actual = try #require(ReferenceImage.image(fromRGBA: bytes, width: 2, height: 1))
        let diff = try #require(ReferenceImage.diffImage(actual, reference))
        #expect(Array(ReferenceImage.rgba(diff)) == [255, 0, 255, 255, 255, 255, 255, 255])
    }

    @Test("references sit beside the suite file; diffs go to apps/ios/build")
    func paths() {
        let paths = ReferenceImage.paths(
            file: "nineStones-paper-en.png",
            filePath: "/r/apps/ios/PebblesTests/References/StoneReferences.swift"
        )
        #expect(paths.reference.path == "/r/apps/ios/PebblesTests/References/__References__/StoneReferences/nineStones-paper-en.png")
        #expect(paths.actual.path == "/r/apps/ios/build/reference-diffs/StoneReferences/nineStones-paper-en.actual.png")
        #expect(paths.diff.path == "/r/apps/ios/build/reference-diffs/StoneReferences/nineStones-paper-en.diff.png")
    }

    @Test("a written PNG reads back byte for byte")
    func pngRoundTrip() throws {
        let bytes = solid(3, 2, rgba: [12, 34, 56, 255])
        let image = try #require(ReferenceImage.image(fromRGBA: bytes, width: 3, height: 2))
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("round-trip-\(UUID()).png")
        try ReferenceImage.writePNG(image, to: url)
        let read = try #require(ReferenceImage.readPNG(at: url))
        #expect(ReferenceImage.compare(image, read) == .differing(fraction: 0))
    }
```

- [ ] **Step 2: Run to verify they fail**

Run the single-test command with `ReferenceImageTests`.
Expected: build FAIL, `cannot find 'ReferenceImage' in scope`.

- [ ] **Step 3: Write the pure half of the harness**

Create `apps/ios/PebblesTests/References/Support/ReferenceImage.swift`:

```swift
import CoreGraphics
import Foundation
import ImageIO
import SwiftUI
import Testing
import UIKit
import UniformTypeIdentifiers

/// The iOS reference-image gate (#1159). Renders a view with `ImageRenderer`,
/// compares it pixel for pixel with a committed PNG, and records a new PNG only
/// when the process sees `PBBLS_RECORD_REFERENCES=1` (xcodebuild passes it in as
/// `TEST_RUNNER_PBBLS_RECORD_REFERENCES=1`). References are rendered on CI only
/// (`ios-references.yml`, the `rebaseline-ios-screenshots` label): a local render
/// is for looking at, never for committing.
///
/// New pattern (raised in the B2 PR): a hand-rolled harness, no dependency, the
/// same metric as Android's gate (the fraction of pixels that differ).
enum ReferenceImage {
    /// The fraction of pixels allowed to differ, exact per pixel (any RGBA
    /// channel). Calibrated in B2 with at least 2× headroom against a one-word
    /// re-wording (see the plan's Lessons). NEVER widen it: a change that moves
    /// pixels on purpose re-baselines. If calibration shows less headroom, lower it.
    static let threshold = 0.0005

    /// 2× keeps PNGs small; parity with Android is judged by eye.
    static let scale: CGFloat = 2

    enum Comparison: Equatable {
        case sizeMismatch
        case differing(fraction: Double)
    }

    struct Paths {
        let reference: URL
        let actual: URL
        let diff: URL
    }

    enum ReferenceError: Error {
        case cannotWrite(URL)
    }

    static var isRecording: Bool {
        ProcessInfo.processInfo.environment["PBBLS_RECORD_REFERENCES"] == "1"
    }

    // MARK: - Pixels

    /// Straight sRGB RGBA8, premultiplied last: the one layout every compare reads.
    static func rgba(_ image: CGImage) -> [UInt8] {
        var bytes = [UInt8](repeating: 0, count: image.width * image.height * 4)
        guard let space = CGColorSpace(name: CGColorSpace.sRGB) else { return bytes }
        bytes.withUnsafeMutableBytes { buffer in
            let context = CGContext(
                data: buffer.baseAddress, width: image.width, height: image.height, bitsPerComponent: 8,
                bytesPerRow: image.width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
            )
            context?.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        }
        return bytes
    }

    static func image(fromRGBA bytes: [UInt8], width: Int, height: Int) -> CGImage? {
        guard bytes.count == width * height * 4,
              let space = CGColorSpace(name: CGColorSpace.sRGB),
              let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
            space: space, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }

    /// Re-draws any image into the RGBA8 sRGB layout, so a recorded PNG and a
    /// fresh render are compared in the same bytes.
    static func normalized(_ image: CGImage) -> CGImage? {
        Self.image(fromRGBA: rgba(image), width: image.width, height: image.height)
    }

    private static func differs(_ a: [UInt8], _ b: [UInt8], at i: Int) -> Bool {
        a[i] != b[i] || a[i + 1] != b[i + 1] || a[i + 2] != b[i + 2] || a[i + 3] != b[i + 3]
    }

    static func compare(_ actual: CGImage, _ reference: CGImage) -> Comparison {
        guard actual.width == reference.width, actual.height == reference.height else { return .sizeMismatch }
        let pixels = actual.width * actual.height
        guard pixels > 0 else { return .differing(fraction: 0) }
        let a = rgba(actual)
        let b = rgba(reference)
        var differing = 0
        for i in stride(from: 0, to: a.count, by: 4) where differs(a, b, at: i) {
            differing += 1
        }
        return .differing(fraction: Double(differing) / Double(pixels))
    }

    /// Differing pixels in magenta over a 30 % grey copy of the reference.
    static func diffImage(_ actual: CGImage, _ reference: CGImage) -> CGImage? {
        guard actual.width == reference.width, actual.height == reference.height else { return nil }
        let a = rgba(actual)
        let b = rgba(reference)
        var out = [UInt8](repeating: 255, count: a.count)
        for i in stride(from: 0, to: a.count, by: 4) {
            if differs(a, b, at: i) {
                out[i] = 255; out[i + 1] = 0; out[i + 2] = 255
            } else {
                let luma = (Int(b[i]) * 299 + Int(b[i + 1]) * 587 + Int(b[i + 2]) * 114) / 1000
                let grey = UInt8(255 - (255 - luma) * 3 / 10)
                out[i] = grey; out[i + 1] = grey; out[i + 2] = grey
            }
        }
        return image(fromRGBA: out, width: actual.width, height: actual.height)
    }

    // MARK: - Files

    /// `filePath` is the suite file's `#filePath`; the simulator shares the host
    /// filesystem, so nothing is bundled.
    static func paths(file: String, filePath: String) -> Paths {
        let suiteFile = URL(fileURLWithPath: filePath)
        let suite = suiteFile.deletingPathExtension().lastPathComponent
        let referencesDir = suiteFile.deletingLastPathComponent()
        let iosRoot = referencesDir.deletingLastPathComponent().deletingLastPathComponent()
        let base = (file as NSString).deletingPathExtension
        let diffs = iosRoot.appendingPathComponent("build/reference-diffs/\(suite)")
        return Paths(
            reference: referencesDir.appendingPathComponent("__References__/\(suite)/\(file)"),
            actual: diffs.appendingPathComponent("\(base).actual.png"),
            diff: diffs.appendingPathComponent("\(base).diff.png")
        )
    }

    static func readPNG(at url: URL) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
        return CGImageSourceCreateImageAtIndex(source, 0, nil)
    }

    static func writePNG(_ image: CGImage, to url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.png.identifier as CFString, 1, nil)
        else { throw ReferenceError.cannotWrite(url) }
        CGImageDestinationAddImage(destination, image, nil)
        guard CGImageDestinationFinalize(destination) else { throw ReferenceError.cannotWrite(url) }
    }
}
```

- [ ] **Step 4: Run the tests**

Run the single-test command with `ReferenceImageTests`.
Expected: PASS (9 tests).

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/References/Support
git commit -m "test(ios): add the reference pixel diff and png io"
```

### Task B2.3: Render, assert, record, and the scheme probe

**Files:**
- Modify: `apps/ios/PebblesTests/References/Support/ReferenceImage.swift`
- Modify: `apps/ios/PebblesTests/References/Support/ReferenceImageTests.swift`

- [ ] **Step 1: Write the failing probe test**

Add to `ReferenceImageTests`:

```swift
    /// Role-free on purpose (stack B reads no colour role): a dynamic sRGB
    /// swatch resolved through the trait collection, so this checks both the
    /// SwiftUI environment and the `performAsCurrent` pin.
    @Test("scheme probe: paper renders white, Night renders black", arguments: ReferenceScheme.allCases)
    @MainActor
    func schemeProbe(scheme: ReferenceScheme) throws {
        let swatch = UIColor { $0.userInterfaceStyle == .dark ? .black : .white }
        let view = Rectangle().fill(Color(uiColor: swatch)).frame(width: 10, height: 10)
        let image = try #require(ReferenceImage.render(view, config: ReferenceConfig(scheme: scheme, width: nil), locale: .en))
        #expect(image.width == 20 && image.height == 20)
        let bytes = ReferenceImage.rgba(image)
        let centre = (10 * 20 + 10) * 4
        let expected: UInt8 = scheme == .paper ? 255 : 0
        #expect(Array(bytes[centre..<centre + 4]) == [expected, expected, expected, 255])
    }
```

- [ ] **Step 2: Run to verify it fails**

Run the single-test command with `ReferenceImageTests`.
Expected: build FAIL, `type 'ReferenceImage' has no member 'render'`.

- [ ] **Step 3: Add render and assertMatches**

Append inside `enum ReferenceImage`, after the `// MARK: - Files` section:

```swift
    // MARK: - Render and assert

    /// Renders under every pin of `config` and `locale`, then normalises the bytes.
    @MainActor
    static func render<V: View>(_ view: V, config: ReferenceConfig, locale: ReferenceLocale) -> CGImage? {
        let content = view
            .frame(width: config.width)
            .environment(\.colorScheme, config.scheme.colorScheme)
            .environment(\.locale, Locale(identifier: locale.rawValue))
            .environment(\.timeZone, TimeZone(identifier: "Europe/Paris") ?? .gmt)
            .environment(\.dynamicTypeSize, config.dynamicType)
        let renderer = ImageRenderer(content: content)
        renderer.scale = scale
        renderer.proposedSize = ProposedViewSize(width: config.width, height: nil)
        var image: CGImage?
        // A UIColor resolved against `UITraitCollection.current` ignores the
        // SwiftUI environment; this makes it follow the scheme too.
        UITraitCollection(userInterfaceStyle: config.scheme.userInterfaceStyle).performAsCurrent {
            image = renderer.cgImage
        }
        return image.flatMap(normalized)
    }

    /// Compares `view` with `__References__/<Suite>/<name>-<scheme>-<locale>[-ax2].png`
    /// beside the calling suite file. A missing reference fails; it is never
    /// recorded implicitly. Returns without a check in a locale pass the config
    /// does not declare.
    @MainActor
    static func assertMatches<V: View>(
        _ view: V,
        named name: String,
        config: ReferenceConfig,
        filePath: String = #filePath,
        sourceLocation: SourceLocation = #_sourceLocation
    ) {
        let locale = ReferenceLocale.current
        guard config.runs(in: locale) else { return }
        let paths = paths(file: config.fileName(name, locale: locale), filePath: filePath)
        guard let actual = render(view, config: config, locale: locale) else {
            Issue.record("\(name): ImageRenderer produced no image", sourceLocation: sourceLocation)
            return
        }
        if isRecording {
            do {
                try writePNG(actual, to: paths.reference)
            } catch {
                Issue.record("\(name): could not record \(paths.reference.path): \(error)", sourceLocation: sourceLocation)
            }
            return
        }
        guard let reference = readPNG(at: paths.reference) else {
            try? writePNG(actual, to: paths.actual)
            Issue.record("""
                \(name): no reference at \(paths.reference.path). References are rendered on CI only: \
                add the rebaseline-ios-screenshots label to the PR.
                """, sourceLocation: sourceLocation)
            return
        }
        switch compare(actual, reference) {
        case .sizeMismatch:
            try? writePNG(actual, to: paths.actual)
            Issue.record("""
                \(name): size \(actual.width)×\(actual.height) px ≠ reference \(reference.width)×\(reference.height) px. \
                Actual at \(paths.actual.path)
                """, sourceLocation: sourceLocation)
        case .differing(let fraction) where fraction > threshold:
            try? writePNG(actual, to: paths.actual)
            if let diff = diffImage(actual, reference) { try? writePNG(diff, to: paths.diff) }
            let percent = String(format: "%.4f", fraction * 100)
            Issue.record("""
                \(name): \(percent) % of pixels differ (threshold \(threshold * 100) %). \
                Actual and diff in \(paths.diff.deletingLastPathComponent().path)
                """, sourceLocation: sourceLocation)
        case .differing:
            return
        }
    }
```

- [ ] **Step 4: Run the tests**

Run the single-test command with `ReferenceImageTests`.
Expected: PASS (11 test cases). If the Night case comes out white, the scheme pin is not reaching the render: stop and fix `render` before any reference exists (spec §Risks, "ImageRenderer colour scheme").

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/References/Support
git commit -m "test(ios): render, record and assert reference images"
```

### Task B2.4: Keep reference PNGs out of the test bundle

**Files:**
- Modify: `apps/ios/project.yml` (the `PebblesTests.sources` entry)

- [ ] **Step 1: Exclude the folder**

In `apps/ios/project.yml`, replace

```yaml
  PebblesTests:
    type: bundle.unit-test
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      - path: PebblesTests
```

with

```yaml
  PebblesTests:
    type: bundle.unit-test
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      # The reference PNGs are read and written through #filePath (the
      # simulator shares the host filesystem), never from the bundle. Left in,
      # XcodeGen copies them as resources, and two suites with the same file
      # name would collide ("Multiple commands produce").
      - path: PebblesTests
        excludes:
          - "References/__References__/**"
```

- [ ] **Step 2: Regenerate and build the tests**

```bash
cd /Users/alexis/code/pbbls/apps/ios && xcodegen generate && rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-* && xcodebuild build-for-testing -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -quiet
```

Expected: `** TEST BUILD SUCCEEDED **`.

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/project.yml
git commit -m "chore(ios): keep reference pngs out of the test bundle"
```

### Task B2.5: The first stone references

**Files:**
- Create: `apps/ios/PebblesTests/References/References.swift`
- Create: `apps/ios/PebblesTests/References/StoneReferences.swift`

- [ ] **Step 1: Write the suites**

Create `apps/ios/PebblesTests/References/References.swift`:

```swift
import Testing

/// Root of every reference-image suite, so CI can run them alone
/// (`-only-testing:PebblesTests/References`). Serialised: `ImageRenderer` work
/// runs on the main actor anyway, and one render at a time keeps Metal quiet.
@Suite(.serialized)
struct References {}
```

Create `apps/ios/PebblesTests/References/StoneReferences.swift`:

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The nine lit stones on the brand palette, laid out as `StoneView`'s
    /// "Nine stones · brand" preview: Metal, the wobbled silhouette and both
    /// schemes. B3 moves these on purpose.
    @Suite("Stone references")
    @MainActor
    struct StoneReferences {

        @Test(.enabled(if: ReferenceLocale.current == .en), arguments: ReferenceScheme.allCases)
        func nineStones(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(NineStonesFixture(isFlat: false), named: "nineStones",
                                         config: ReferenceConfig(scheme: scheme, width: nil))
        }

        /// The Low Power drawing.
        @Test(.enabled(if: ReferenceLocale.current == .en), arguments: ReferenceScheme.allCases)
        func nineStonesFlat(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(NineStonesFixture(isFlat: true), named: "nineStonesFlat",
                                         config: ReferenceConfig(scheme: scheme, width: nil))
        }

        /// The `layerEffect` check (spec D6): a lit stone with no shadow, no
        /// edge, no sheen and no carving is the shader alone. If it draws
        /// nothing, the window fallback is needed.
        @Test("the lit stone draws material under ImageRenderer")
        func litStoneDrawsMaterial() throws {
            let config = ReferenceConfig(scheme: .paper, width: nil)
            let lit = try #require(ReferenceImage.render(ShaderOnlyStone(isFlat: false), config: config, locale: .en))
            let flat = try #require(ReferenceImage.render(ShaderOnlyStone(isFlat: true), config: config, locale: .en))
            let bytes = ReferenceImage.rgba(lit)
            let centre = ((lit.height / 2) * lit.width + lit.width / 2) * 4
            #expect(bytes[centre + 3] == 255, "the lit stone's centre is transparent: the shader drew nothing")
            guard case .differing(let fraction) = ReferenceImage.compare(lit, flat) else {
                Issue.record("lit and flat stones differ in size")
                return
            }
            #expect(fraction > 0.1, "the shader changed only \(fraction * 100) % of pixels")
        }
    }
}

private struct NineStonesFixture: View {
    let isFlat: Bool

    var body: some View {
        VStack(spacing: 12) {
            ForEach(ValenceSizeGroup.allCases) { size in
                HStack(spacing: 12) {
                    ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                        if let valence = Valence.allCases.first(where: { $0.sizeGroup == size && $0.polarity == polarity }) {
                            StoneView(
                                valence: valence, palette: .brand,
                                material: .starting(for: polarity), tones: .starting(for: polarity),
                                light: .rest, height: 110, isFlat: isFlat
                            )
                        }
                    }
                }
            }
        }
        .padding()
        .background(Color.system.background)
    }
}

private struct ShaderOnlyStone: View {
    let isFlat: Bool

    var body: some View {
        var material = StoneMaterial.starting(for: .neutral)
        material.sheen = 0
        return StoneView(
            valence: .neutralMedium, palette: .brand, material: material, tones: .starting(for: .neutral),
            light: .rest, height: 110, isFlat: isFlat, look: .flat, carving: .blank
        )
    }
}
```

- [ ] **Step 2: Run them**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
```

Run the single-test command with `References`.
Expected: `litStoneDrawsMaterial` PASSES; the four `nineStones*` cases FAIL with "no reference at …/__References__/StoneReferences/…". That failure is correct: references come from CI. If `litStoneDrawsMaterial` fails, do Task B2.6 before going on; otherwise skip B2.6.

- [ ] **Step 3: Look at a local render (never commit it)**

```bash
cd /Users/alexis/code/pbbls/apps/ios && TEST_RUNNER_PBBLS_RECORD_REFERENCES=1 xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/References -quiet
open PebblesTests/References/__References__/StoneReferences
```

Expected: four PNGs; the lit stones show grain, rim and sheen, Night on a dark page. Then delete them:

```bash
rm -rf /Users/alexis/code/pbbls/apps/ios/PebblesTests/References/__References__
```

- [ ] **Step 4: Commit (no PNGs)**

```bash
cd /Users/alexis/code/pbbls
git status --short apps/ios/PebblesTests/References
git add apps/ios/PebblesTests/References/References.swift apps/ios/PebblesTests/References/StoneReferences.swift
git commit -m "test(ios): add the nine-stone references"
```

### Task B2.6 (only if `litStoneDrawsMaterial` failed): the window fallback

**Files:**
- Modify: `apps/ios/PebblesTests/References/Support/ReferenceConfig.swift`
- Modify: `apps/ios/PebblesTests/References/Support/ReferenceImage.swift`
- Modify: `apps/ios/PebblesTests/References/StoneReferences.swift`

- [ ] **Step 1: Add the mode to the config**

In `struct ReferenceConfig`, add after `width`:

```swift
    enum Mode { case imageRenderer, window }
    /// `.window` hosts the view in an offscreen `UIWindow` and captures it with
    /// `drawHierarchy`: for content `ImageRenderer` draws empty (Metal layer effects).
    var mode: Mode = .imageRenderer
```

- [ ] **Step 2: Add the window render and route `render` through it**

Inside `enum ReferenceImage`, add:

```swift
    @MainActor
    static func renderInWindow<V: View>(_ view: V, config: ReferenceConfig) -> CGImage? {
        let host = UIHostingController(rootView: view)
        let fitting = host.sizeThatFits(in: CGSize(width: config.width ?? .greatestFiniteMagnitude,
                                                   height: .greatestFiniteMagnitude))
        let size = CGSize(width: config.width ?? fitting.width, height: fitting.height)
        let window = UIWindow(frame: CGRect(origin: .zero, size: size))
        window.overrideUserInterfaceStyle = config.scheme.userInterfaceStyle
        window.rootViewController = host
        window.isHidden = false
        host.view.frame = window.bounds
        host.view.layoutIfNeeded()
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            _ = host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
        }
        window.isHidden = true
        return image.cgImage.flatMap(normalized)
    }
```

In `render`, insert directly after the `let content = …` declaration (before `let renderer`):

```swift
        if config.mode == .window {
            return renderInWindow(content, config: config)
        }
```

- [ ] **Step 3: Use it for the stones**

In `StoneReferences.swift`, add `mode: .window` to every `ReferenceConfig(scheme:…, width: nil)` (memberwise order: `scheme`, `locales`, `dynamicType`, `width`, `mode`).

- [ ] **Step 4: Run and commit**

Run the single-test command with `References`.
Expected: `litStoneDrawsMaterial` PASSES. Record "window fallback built: ImageRenderer drew the layer effect empty" in the Lessons section.

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/References
git commit -m "test(ios): render metal stones through an offscreen window"
```

### Task B2.7: The welcome slide references

**Files:**
- Create: `apps/ios/PebblesTests/References/WelcomeReferences.swift`

- [ ] **Step 1: Write the suite**

```swift
import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// A pure SwiftUI view in B1's variable Ysabeau: it exercises the face,
    /// the locale pass and Dynamic Type.
    @Suite("Welcome references")
    @MainActor
    struct WelcomeReferences {

        @Test(arguments: ReferenceScheme.allCases)
        func slide(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SlideFixture(), named: "slide",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(.enabled(if: ReferenceLocale.current == .fr))
        func slideAx2() {
            ReferenceImage.assertMatches(SlideFixture(), named: "slide",
                                         config: ReferenceConfig(scheme: .paper, locales: [.fr], dynamicType: .accessibility2))
        }
    }
}

private struct SlideFixture: View {
    var body: some View {
        WelcomeSlideView(step: WelcomeSteps.all[1])
            .padding(.vertical, 24)
            .background(Color.system.background)
    }
}
```

- [ ] **Step 2: Run EN and FR**

Run the single-test command with `References`, then:

```bash
cd /Users/alexis/code/pbbls/apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -testLanguage fr -testRegion FR -only-testing:PebblesTests/References -quiet
```

Expected: EN fails only on missing `slide-paper-en.png`, `slide-night-en.png` (and the stones); FR fails only on missing `slide-paper-fr.png`, `slide-night-fr.png`, `slide-paper-fr-ax2.png`, and the stone tests are skipped. If the FR run reports the stone tests as failing rather than skipped, or no welcome failure at all, `-testLanguage` is not reaching `Bundle.main.preferredLocalizations`: fix `ReferenceLocale.current` before going on.

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/References/WelcomeReferences.swift
git commit -m "test(ios): add the welcome slide references in en and fr"
```

### Task B2.8: The gate in `ios.yml`

**Files:**
- Modify: `.github/workflows/ios.yml` (the `test` job)

- [ ] **Step 1: Pin the device and runtime**

Add to the `test` job, under `runs-on: macos-26`:

```yaml
    # The reference images depend on the simulator runtime, so the whole test
    # job runs on one pinned device and runtime (#1159): iPhone 17 on iOS 26.2,
    # the runtime Xcode 26.3's SDK targets on this image. Moving either is a
    # deliberate PR that re-baselines every reference in one reviewed commit.
    env:
      SIM_DEVICE: iPhone 17
      SIM_RUNTIME: com.apple.CoreSimulator.SimRuntime.iOS-26-2
      TEST_RUNNER_TZ: Europe/Paris
```

Replace the whole `Resolve a simulator` step (its comment included) with:

```yaml
      # Pinned, not resolved: a reference rendered on whatever iPhone the image
      # carries makes a drift impossible to bisect. Fails loudly when the image
      # drops the pin, like the Xcode check above.
      - name: Resolve the pinned simulator
        id: sim
        run: |
          udid=$(xcrun simctl list devices available --json | python3 -c '
          import json, os, sys
          devices = json.load(sys.stdin)["devices"].get(os.environ["SIM_RUNTIME"], [])
          match = [d["udid"] for d in devices if d["name"] == os.environ["SIM_DEVICE"]]
          if not match:
              raise SystemExit("%s on %s is gone from the runner image; raise the pin in its own PR and re-baseline" % (os.environ["SIM_DEVICE"], os.environ["SIM_RUNTIME"]))
          print(match[0])
          ')
          echo "udid=$udid" >> "$GITHUB_OUTPUT"
          xcrun simctl list devices available | grep -F "$udid"
```

- [ ] **Step 2: Add the French pass and the diff upload**

After the `Run tests` step (it keeps its command; `TEST_RUNNER_TZ` now comes from the job env), add:

```yaml
      # `String(localized:)` ignores the SwiftUI environment, so French is a
      # second process-level pass over the references only. Each reference
      # declares its locales and is skipped in the other pass.
      - name: French references
        working-directory: apps/ios
        run: |
          xcodebuild test-without-building \
            -scheme Pebbles \
            -destination "id=${{ steps.sim.outputs.udid }}" \
            -derivedDataPath build \
            -testLanguage fr \
            -testRegion FR \
            -only-testing:PebblesTests/References \
            -quiet \
            CODE_SIGNING_ALLOWED=NO
```

After the `Upload test results` step, add:

```yaml
      # <name>.actual.png and <name>.diff.png (differing pixels in magenta) for
      # every reference that moved. A change that moves pixels on purpose is
      # re-baselined with the `rebaseline-ios-screenshots` label; never widen
      # ReferenceImage.threshold instead.
      - name: Upload reference diffs
        if: failure()
        uses: actions/upload-artifact@v7
        with:
          name: ios-reference-diffs
          path: apps/ios/build/reference-diffs/**
          if-no-files-found: ignore
```

- [ ] **Step 3: Lint the YAML**

```bash
cd /Users/alexis/code/pbbls && ruby -ryaml -e 'YAML.load_file(".github/workflows/ios.yml"); puts "ok"'
```

Expected: `ok`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ios.yml
git commit -m "quality(ios): gate reference images on a pinned simulator in en and fr"
```

### Task B2.9: The rebaseline workflow

**Files:**
- Create: `.github/workflows/ios-references.yml`

- [ ] **Step 1: Write the workflow**

```yaml
name: iOS references (re-baseline)

# Regenerates the committed reference images under
# apps/ios/PebblesTests/References/__References__/ and pushes them.
#
# ios.yml validates those references on every PR, so a change that moves a
# rendered pixel on purpose lands red until they are regenerated. This
# workflow is that one command, and it mirrors android-screenshots.yml step for
# step. It is deliberately NOT part of ios.yml: it is the only iOS workflow
# that writes to the repository, and the pre-merge gate stays read-only.
#
# Two ways in, both manual:
#   - label a PR `rebaseline-ios-screenshots`: the new PNGs are committed to the
#     PR branch (same-repo PRs only; a fork PR skips, see the job's `if`)
#   - run it from the Actions tab on any ref
#
# The references are rendered here on the same image, Xcode, device and
# runtime that ios.yml validates on, so the baseline and its oracle come off
# the same renderer. A local render is for looking at; never commit one.
on:
  workflow_dispatch:
  pull_request:
    types: [labeled]

concurrency:
  group: ios-references-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: write

jobs:
  rebaseline:
    if: >-
      ${{ github.event_name == 'workflow_dispatch' ||
          (github.event.label.name == 'rebaseline-ios-screenshots' &&
           github.event.pull_request.head.repo.full_name == github.repository) }}
    name: Re-render and commit the iOS reference images
    runs-on: macos-26
    # Same pins as ios.yml's test job. Change them together, never apart.
    env:
      SIM_DEVICE: iPhone 17
      SIM_RUNTIME: com.apple.CoreSimulator.SimRuntime.iOS-26-2
      TEST_RUNNER_TZ: Europe/Paris
    steps:
      - name: Checkout
        uses: actions/checkout@v7
        with:
          # On a pull_request event the default checkout is the merge commit,
          # which is detached and cannot be pushed. Take the head branch instead.
          ref: ${{ github.event_name == 'pull_request' && github.event.pull_request.head.ref || github.ref }}

      - name: Select Xcode 26.3
        run: |
          if [ ! -d /Applications/Xcode_26.3.app ]; then
            echo "Xcode 26.3 is gone from the runner image. Installed:"
            ls -d /Applications/Xcode*.app
            echo "Raise the pin in ios.yml and here in one PR, and re-baseline."
            exit 1
          fi
          sudo xcode-select -s /Applications/Xcode_26.3.app
          xcodebuild -version

      - name: Install XcodeGen
        run: brew install xcodegen

      # Placeholders, as in ios.yml: the suite is offline.
      - name: Write placeholder Secrets.xcconfig
        working-directory: apps/ios
        run: |
          cat > Config/Secrets.xcconfig <<'EOF'
          SUPABASE_URL = https:/$()/placeholder.supabase.co
          SUPABASE_ANON_KEY = placeholder-anon-key
          EOF

      - name: Generate Xcode project
        working-directory: apps/ios
        run: xcodegen generate

      - name: Resolve the pinned simulator
        id: sim
        run: |
          udid=$(xcrun simctl list devices available --json | python3 -c '
          import json, os, sys
          devices = json.load(sys.stdin)["devices"].get(os.environ["SIM_RUNTIME"], [])
          match = [d["udid"] for d in devices if d["name"] == os.environ["SIM_DEVICE"]]
          if not match:
              raise SystemExit("%s on %s is gone from the runner image; raise the pin in its own PR and re-baseline" % (os.environ["SIM_DEVICE"], os.environ["SIM_RUNTIME"]))
          print(match[0])
          ')
          echo "udid=$udid" >> "$GITHUB_OUTPUT"
          xcrun simctl list devices available | grep -F "$udid"

      # Removing the folder first is what drops the references of deleted tests.
      - name: Clear the old references
        run: git rm -rq --ignore-unmatch apps/ios/PebblesTests/References/__References__

      - name: Record the English references
        working-directory: apps/ios
        env:
          TEST_RUNNER_PBBLS_RECORD_REFERENCES: '1'
        run: |
          xcodebuild test \
            -scheme Pebbles \
            -destination "id=${{ steps.sim.outputs.udid }}" \
            -derivedDataPath build \
            -skipMacroValidation \
            -only-testing:PebblesTests/References \
            -quiet \
            CODE_SIGNING_ALLOWED=NO

      - name: Record the French references
        working-directory: apps/ios
        env:
          TEST_RUNNER_PBBLS_RECORD_REFERENCES: '1'
        run: |
          xcodebuild test-without-building \
            -scheme Pebbles \
            -destination "id=${{ steps.sim.outputs.udid }}" \
            -derivedDataPath build \
            -testLanguage fr \
            -testRegion FR \
            -only-testing:PebblesTests/References \
            -quiet \
            CODE_SIGNING_ALLOWED=NO

      # The calibration's first half, kept for every re-baseline: a second
      # render of the same tree must match the one just recorded, byte for byte
      # inside the threshold. A red step here means the renders are not
      # deterministic, and committing them would make ios.yml flaky.
      - name: Verify the new references reproduce
        working-directory: apps/ios
        run: |
          for lang in en fr; do
            xcodebuild test-without-building \
              -scheme Pebbles \
              -destination "id=${{ steps.sim.outputs.udid }}" \
              -derivedDataPath build \
              -testLanguage "$lang" \
              -only-testing:PebblesTests/References \
              -quiet \
              CODE_SIGNING_ALLOWED=NO
          done

      - name: Commit the references
        id: commit
        env:
          TARGET_REF: ${{ github.event_name == 'pull_request' && github.event.pull_request.head.ref || github.ref_name }}
          DEFAULT_BRANCH: ${{ github.event.repository.default_branch }}
        run: |
          set -euo pipefail
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add apps/ios/PebblesTests/References/__References__

          if git diff --cached --quiet; then
            echo "The references already match this revision — nothing to commit."
            echo "changed=false" >> "$GITHUB_OUTPUT"
            exit 0
          fi

          echo "changed=true" >> "$GITHUB_OUTPUT"
          git commit -m "test(ios): re-baseline reference images"

          # Never write the references straight onto the default branch: a
          # re-baseline is a reviewable change like any other.
          if [ "$TARGET_REF" = "$DEFAULT_BRANCH" ]; then
            branch="test/ios-reference-rebaseline-${GITHUB_RUN_NUMBER}"
            git push origin "HEAD:refs/heads/$branch"
            echo "branch=$branch" >> "$GITHUB_OUTPUT"
            echo "Pushed $branch — open a PR from it."
          else
            git push origin "HEAD:$TARGET_REF"
            echo "branch=$TARGET_REF" >> "$GITHUB_OUTPUT"
          fi

      - name: Next step
        if: ${{ steps.commit.outputs.changed == 'true' }}
        run: |
          {
            echo "References committed to \`${{ steps.commit.outputs.branch }}\`."
            echo
            echo "**The iOS check is not green yet.** This push is attributed to"
            echo "\`github-actions[bot]\`, so the runs queued against the new commit are held"
            echo "at \`action_required\` pending approval. Do one of:"
            echo
            echo "- approve them from the PR's **Checks** tab, or"
            echo '  `gh api --method POST repos/${{ github.repository }}/actions/runs/<run-id>/approve`'
            echo "- push any further commit yourself, which runs them without approval"
            echo
            echo "Do **not** re-run the failed iOS run: a re-run replays the commit it was"
            echo "created for, which is the tree from before the re-baseline."
          } >> "$GITHUB_STEP_SUMMARY"
```

- [ ] **Step 2: Lint the YAML**

```bash
cd /Users/alexis/code/pbbls && ruby -ryaml -e 'YAML.load_file(".github/workflows/ios-references.yml"); puts "ok"'
```

Expected: `ok`.

- [ ] **Step 3: Create the label (one-time)**

```bash
gh label create rebaseline-ios-screenshots --color 0E8A16 --description "Re-render and commit the iOS reference images (ios-references.yml)"
```

Expected: `✓ Label "rebaseline-ios-screenshots" created`.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ios-references.yml
git commit -m "quality(ios): re-baseline reference images from a label"
```

### Task B2.10: Record the decision

**Files:**
- Modify: `docs/decisions/log.md` (append only)

- [ ] **Step 1: Append the entry**

Append at the end of `docs/decisions/log.md`:

```markdown

## 2026-10-20 — iOS reference images: a hand-rolled ImageRenderer gate, rendered on CI only (#1159)

- **Status:** taken
- **Scope:** ios, quality
- **Context:** The iOS Spine (#1130) and the held card (#1146) need a visual gate before they are built. Android has one (#847); iOS had none, and `project.yml` pins every package exactly.
- **Decision:**
  - Swift Testing renders named views with `ImageRenderer` (`PebblesTests/References/Support/`), compares them pixel for pixel with PNGs under `PebblesTests/References/__References__/`, and fails above 0.05 % of differing pixels, a size mismatch, or a missing reference.
  - Pins: 393 pt (iPhone 15) at 2×, paper/Night, EN and a second `-testLanguage fr` pass, Europe/Paris, `.large` plus `-ax2` at `.accessibility2`; Xcode 26.3, iPhone 17 on iOS 26.2 for the whole `test` job.
  - References are rendered on CI only: the `rebaseline-ios-screenshots` label runs `ios-references.yml`, which records, re-renders to prove the renders reproduce, and commits.
- **Why:** No dependency (`swift-snapshot-testing` would add three pinned packages and a metric that is not Android's); no XCTest; the same metric as the Android gate; #793 shows two Xcode builds disagreeing on the same code.
- **Consequences:**
  - Never commit a local render; never widen `ReferenceImage.threshold`; lower it if calibration shows under 2× headroom.
  - No `ScrollView` or `containerRelativeFrame` in a rendered view; lazy stacks become eager in fixtures.
  - Moving Xcode, the device or the runtime is its own PR that re-baselines every reference.
- **Supersedes / Superseded-by:** none.
- **Refs:** #1159, #793, `docs/superpowers/specs/2026-10-20-ios-new-path-foundations-design.md`, `docs/superpowers/plans/2026-10-20-ios-new-path-foundations.md`.
```

- [ ] **Step 2: Commit**

```bash
cd /Users/alexis/code/pbbls
git add docs/decisions/log.md
git commit -m "docs: record the ios reference image gate decision"
```

### Task B2.11: Open the PR and let CI render the references

- [ ] **Step 1: Local verification**

```bash
cd /Users/alexis/code/pbbls && npm run lint --workspace=@pbbls/ios
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
npm run test --workspace=@pbbls/ios
```

Expected: lint clean of errors; the suite fails only on the missing references (4 stone, 2 welcome cases). Every other test passes.

- [ ] **Step 2: Push and open the PR**

```bash
cd /Users/alexis/code/pbbls
git fetch origin
gh stack submit --auto
gh pr edit quality/1159-ios-reference-images --title "quality(ios): reference images in ci" --add-label quality,ios,no-lab-note --milestone "M64 · New Path" --body-file - <<'EOF'
Resolves #1159

## What
- `PebblesTests/References/Support/`: `ImageRenderer` + exact-pixel diff (0.05 % of pixels), record mode via `TEST_RUNNER_PBBLS_RECORD_REFERENCES=1`, diffs to `apps/ios/build/reference-diffs/`.
- First references: `StoneReferences.nineStones` (+ flat), paper and Night; `WelcomeReferences.slide`, paper and Night, EN and FR, plus `-ax2` paper FR.
- `ios.yml`: iPhone 17 on iOS 26.2 pinned for the whole test job, Europe/Paris, a French pass over the references, `ios-reference-diffs` on failure.
- `ios-references.yml` + the `rebaseline-ios-screenshots` label: records EN and FR, proves a second render reproduces, commits.

## New patterns, for review
- The hand-rolled reference harness (`#filePath` I/O, `TEST_RUNNER_` record mode).
- Label-driven CI rebaseline on iOS, the first iOS workflow that writes to the repo.
- A pinned simulator device and runtime for the whole iOS test job.
- A two-pass locale run where each reference declares its locales.

## Calibration
<filled in by Task B2.12>
EOF
```

- [ ] **Step 3: Add the label and wait for the bot commit**

```bash
gh pr edit quality/1159-ios-reference-images --add-label rebaseline-ios-screenshots
gh run watch "$(gh run list --workflow ios-references.yml --branch quality/1159-ios-reference-images --limit 1 --json databaseId -q '.[0].databaseId')"
```

Expected: the run succeeds, including "Verify the new references reproduce" (the byte-identical half of the calibration), and pushes `test(ios): re-baseline reference images`.

- [ ] **Step 4: Approve the held runs and pull the bot commit**

```bash
for id in $(gh run list --branch quality/1159-ios-reference-images --status action_required --json databaseId -q '.[].databaseId'); do
  gh api --method POST "repos/{owner}/{repo}/actions/runs/$id/approve"
done
git fetch origin
git switch quality/1159-ios-reference-images
git merge --ff-only origin/quality/1159-ios-reference-images
ls -la apps/ios/PebblesTests/References/__References__/*
```

Expected: 9 PNGs (`nineStones-{paper,night}-en`, `nineStonesFlat-{paper,night}-en`, `slide-{paper,night}-{en,fr}`, `slide-paper-fr-ax2`). Open them and check: stones show material; Night is dark; the FR slide is French; the `-ax2` slide is visibly larger. Write the PNG sizes into Lessons; if one passes 1 MB, note it for later stacks (they slice tall states rather than lower the scale). Then `ios.yml` must be green on the bot commit.

### Task B2.12: Calibrate the threshold's headroom

- [ ] **Step 1: Push a deliberate one-word change**

In `apps/ios/PebblesTests/References/WelcomeReferences.swift`, temporarily change the fixture body to:

```swift
        WelcomeSlideView(step: WelcomeStep(id: "calibration", title: "Enrich with purpose.",
                                           description: WelcomeSteps.all[1].description))
```

(The welcome slide is the tallest reference carrying a label; one word of its title is re-worded.)

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/PebblesTests/References/WelcomeReferences.swift
git commit -m "test(ios): calibration probe, revert me"
git push origin quality/1159-ios-reference-images
```

- [ ] **Step 2: Read the fraction**

Wait for `ios.yml`. Expected: red, with an `ios-reference-diffs` artifact, and the log line `slide: X % of pixels differ (threshold 0.05 %)` for `slide-paper-en` and `slide-night-en`. The smallest X must be ≥ 0.1 % (2× the threshold). If it is lower, lower `ReferenceImage.threshold` to at most half of X (as a fraction), with the numbers in its comment, and re-run Step 1.

- [ ] **Step 3: Revert, record, push**

```bash
git revert --no-edit HEAD
git push origin quality/1159-ios-reference-images
```

Write the measured fraction into Lessons and into the PR body's Calibration section (`gh pr edit quality/1159-ios-reference-images --body-file …` with the full body). Expected: `ios.yml` green again.

- [ ] **Step 4: Rebase the stack's upper layers onto the bot commit**

Only once B3 exists; at this point nothing sits above B2. Keep for later: whenever the bot pushes to a stack branch, `git fetch origin`, fast-forward that branch, then `gh stack rebase --upstack` and `gh stack push`.

---

## Part B3 — fix/1097-ios-lit-stone-materials

Labels `fix`, `ui`, `ios`, `no-lab-note` · milestone `M64 · New Path` · PR body `Resolves #1097` and `Part of #1143` (the iOS half). The stone moves on purpose: this part is the gate's first intended diff.

### Task B3.0: Add the branch to the stack

- [ ] **Step 1**

```bash
cd /Users/alexis/code/pbbls
git fetch origin
git switch quality/1159-ios-reference-images
git merge --ff-only origin/quality/1159-ios-reference-images
gh stack add fix/1097-ios-lit-stone-materials
git branch --show-current
```

Expected: `fix/1097-ios-lit-stone-materials`.

### Task B3.1: Delete the dead `ValenceStoneStyle` members (#1143, iOS)

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift`

- [ ] **Step 1: Check whether stack A3b already did it**

```bash
cd /Users/alexis/code/pbbls
git fetch origin
git grep -n "inkHexes\|selectedWashHexes" origin/main -- apps/ios
git grep -n "ValenceStoneStyle" -- apps/ios
```

If the first command prints nothing, A3b landed the deletion: skip to Task B3.2 (no-op). Otherwise the second must list only `ValenceStoneStyle.swift` itself and `ValenceHeadlineView.swift:19` (`headlineInk(for:)`). Any other caller: stop and report.

- [ ] **Step 2: Reduce the file to `headlineInk`**

Replace the whole content of `apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift` with:

```swift
import SwiftUI

/// The headline word's fill for the picked valence.
///
/// Until #975 this type also drew the valence stones themselves (a wash, an
/// ink and a mesh per polarity). The picker draws lit `StoneView`s since, so
/// those members had no caller and were deleted with their hex tables (#1143,
/// iOS half): iOS carries no red valence stop. A later port of the mesh starts
/// from the retuned tables in
/// `docs/superpowers/specs/2026-10-20-ios-new-path-foundations-design.md` §B3.1,
/// never from git history.
enum ValenceStoneStyle {
    /// Since #974 the stones are lit brand-coloured stone (`StoneTones.starting`:
    /// dark, shaded and primary bodies), so the word takes the brand tone its
    /// stone wears. Lowlight stays the system foreground: at headline size the
    /// near-black `shaded` reads as disabled in light mode and vanishes in dark mode.
    static func headlineInk(for polarity: ValencePolarity) -> AnyShapeStyle {
        switch polarity {
        case .lowlight:  return AnyShapeStyle(Color.system.foreground)
        // The neutral stone's body is the mid `shaded` tone.
        case .neutral:   return AnyShapeStyle(Color.accent.shaded)
        case .highlight: return AnyShapeStyle(Color.accent.primary)
        }
    }
}
```

- [ ] **Step 2b: Confirm no hex table is left**

```bash
grep -c '"#' /Users/alexis/code/pbbls/apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift
```

Expected: `0`.

- [ ] **Step 3: Build and run the valence suites**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
cd /Users/alexis/code/pbbls/apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/ValenceArtTests -only-testing:PebblesTests/ValenceMetadataTests -quiet
```

Expected: `** TEST SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Path/Valence/ValenceStoneStyle.swift
git commit -m "fix(ios): delete the dead valence stone tables and their red stops"
```

### Task B3.2: A soft (unwobbled) backdrop silhouette

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Render/Wobble/WobbleRenderer.swift:120-171`
- Test: `apps/ios/PebblesTests/Wobble/WobbleRendererTests.swift`

- [ ] **Step 1: Write the failing tests**

Add inside `struct WobbleRendererTests`:

```swift
    @Test("the soft silhouette is the asset's own path, point for point")
    func softBackdropIsUnwobbled() throws {
        let url = try #require(Bundle.main.url(forResource: "medium-neutral", withExtension: "svg"))
        let raw = try String(contentsOf: url, encoding: .utf8)
        let soft = try #require(WobbleRenderer.softBackdropArt(size: .medium, polarity: .neutral))
        let wobbled = try #require(WobbleRenderer.backdropArt(size: .medium, polarity: .neutral))

        let d = try #require(Self.attribute("d", in: raw))
        let parsed = try #require(SVGPathParser.parse(d))
        let step = WobbleParams.scaled(for: soft.viewBox.size).flattenStep
        let expected = WobblePathFlattener.flatten(parsed, step: step)
            .filter { $0.points.count > 2 }
            .flatMap(\.points)

        #expect(Self.points(of: soft.path) == expected)
        #expect(soft.path != wobbled.path)
        #expect(soft.viewBox == wobbled.viewBox)
        #expect(soft.usesEvenOddFill == wobbled.usesEvenOddFill)
    }

    @Test("the soft silhouette is memoised apart from the wobbled one")
    func softBackdropIsCached() throws {
        let first = try #require(WobbleRenderer.softBackdropArt(size: .large, polarity: .highlight))
        let second = try #require(WobbleRenderer.softBackdropArt(size: .large, polarity: .highlight))
        #expect(first === second)
        #expect(first !== WobbleRenderer.backdropArt(size: .large, polarity: .highlight))
    }

    private static func attribute(_ name: String, in svg: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: "(?<![\\w-])\(name)=\"([^\"]*)\""),
              let match = regex.firstMatch(in: svg, range: NSRange(svg.startIndex..., in: svg)),
              let range = Range(match.range(at: 1), in: svg) else { return nil }
        return String(svg[range])
    }

    private static func points(of path: CGPath) -> [CGPoint] {
        var result: [CGPoint] = []
        path.applyWithBlock { element in
            switch element.pointee.type {
            case .moveToPoint, .addLineToPoint: result.append(element.pointee.points[0])
            default: break
            }
        }
        return result
    }
```

- [ ] **Step 2: Run to verify they fail**

Run the single-test command with `WobbleRendererTests`.
Expected: build FAIL, `type 'WobbleRenderer' has no member 'softBackdropArt'`.

- [ ] **Step 3: Implement**

In `apps/ios/Pebbles/Features/Path/Render/Wobble/WobbleRenderer.swift`, replace the whole `backdropArt(size:polarity:)` function with:

```swift
    static func backdropArt(size: ValenceSizeGroup, polarity: ValencePolarity) -> WobbleBackdropArt? {
        let name = "\(size.rawValue)-\(polarity.rawValue)"
        return memoized(backdropCache, key: name as NSString) {
            outlineAsset(named: name).flatMap { backdropArt(named: name, fromAsset: $0, wobble: true) }
        }
    }

    /// The same outline asset with no displacement: the soft edge the gem and
    /// river stones wear (#1097, web #1088). Memoised under `"<name>-soft"`.
    static func softBackdropArt(size: ValenceSizeGroup, polarity: ValencePolarity) -> WobbleBackdropArt? {
        let name = "\(size.rawValue)-\(polarity.rawValue)"
        return memoized(backdropCache, key: "\(name)-soft" as NSString) {
            outlineAsset(named: name).flatMap { backdropArt(named: name, fromAsset: $0, wobble: false) }
        }
    }

    private static func outlineAsset(named name: String) -> String? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "svg"),
              let raw = try? String(contentsOf: url, encoding: .utf8) else {
            logger.error("wobble backdrop: missing outline asset \(name, privacy: .public).svg")
            return nil
        }
        return raw
    }

    private static func backdropArt(named name: String, fromAsset raw: String, wobble: Bool) -> WobbleBackdropArt? {
        guard let art = backdropArt(fromAsset: raw, wobble: wobble) else {
            logger.error("wobble backdrop: could not parse outline asset \(name, privacy: .public).svg")
            return nil
        }
        return art
    }
```

Change the signature and the displacement line of `backdropArt(fromAsset:)`:

```swift
    static func backdropArt(fromAsset raw: String, wobble: Bool = true) -> WobbleBackdropArt? {
```

and

```swift
            let displaced = wobble ? polyline.points.map { params.displace($0, using: noise) } : polyline.points
```

Update its doc comment's last sentence to: "`wobble: false` keeps the parsed contour and skips only the displacement."

- [ ] **Step 4: Run the tests**

Run the single-test command with `WobbleRendererTests`.
Expected: PASS, the existing tests included.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Path/Render/Wobble/WobbleRenderer.swift apps/ios/PebblesTests/Wobble/WobbleRendererTests.swift
git commit -m "feat(ios): add an unwobbled backdrop silhouette"
```

### Task B3.3: Gem and river wear the soft silhouette

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Stone/StoneView.swift`
- Create: `apps/ios/PebblesTests/Stone/StoneSilhouetteTests.swift`

- [ ] **Step 1: Write the failing test**

```swift
import Testing
@testable import Pebbles

@Suite("StoneView · silhouette")
struct StoneSilhouetteTests {

    @Test("only the lava (lowlight) stone keeps the wobbled edge")
    func silhouetteByPolarity() {
        #expect(StoneView.usesWobbledSilhouette(.lowlight))
        #expect(!StoneView.usesWobbledSilhouette(.neutral))
        #expect(!StoneView.usesWobbledSilhouette(.highlight))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

```bash
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
```

Run the single-test command with `StoneSilhouetteTests`.
Expected: build FAIL, `type 'StoneView' has no member 'usesWobbledSilhouette'`.

- [ ] **Step 3: One silhouette for all four layers**

In `apps/ios/Pebbles/Features/Path/Stone/StoneView.swift`, after the `seed` property, add:

```swift
    /// Lava keeps the wobbled edge; gem and river take the asset's own soft
    /// outline (#1097, web #1088). The glyph stays wobbled either way.
    static func usesWobbledSilhouette(_ polarity: ValencePolarity) -> Bool {
        polarity == .lowlight
    }

    /// The one silhouette body, edge, shadow and sheen are drawn from.
    private var silhouette: WobbleBackdropArt? {
        Self.usesWobbledSilhouette(valence.polarity)
            ? WobbleRenderer.backdropArt(size: size, polarity: valence.polarity)
            : WobbleRenderer.softBackdropArt(size: size, polarity: valence.polarity)
    }
```

Then replace each of the four occurrences (in `bodyLayer`, `edgeLayer`, `shadowLayer`, `sheenLayer`) of

```swift
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
```

with

```swift
        if let art = silhouette {
```

Update the type's doc comment "the wobbled backdrop silhouette" → "the backdrop silhouette (wobbled for lava, soft for gem and river)".

```bash
grep -c "WobbleRenderer.backdropArt\|WobbleRenderer.softBackdropArt" /Users/alexis/code/pbbls/apps/ios/Pebbles/Features/Path/Stone/StoneView.swift
```

Expected: `2` (both inside `silhouette`).

- [ ] **Step 4: Run the test**

Run the single-test command with `StoneSilhouetteTests`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Path/Stone/StoneView.swift apps/ios/PebblesTests/Stone/StoneSilhouetteTests.swift
git commit -m "fix(ios): give gem and river stones a soft outline"
```

### Task B3.4: Matte river, fine tooth, grittier lava (shader)

**Files:**
- Modify: `apps/ios/Pebbles/Resources/Stone/stone.metal` (the `m == 0` and `m == 1` branches of `stone`)

No unit test can see a shader: the check is the reference diff in Task B3.7 and the device in Task B3.6. Uniform order is untouched, so `StoneMaterialTests.uniformOrder` keeps guarding the signature.

- [ ] **Step 1: Lava grain**

In the Blackstone branch, replace

```metal
        col = mix(col, ink * 0.6, pit);
```

with

```metal
        col = mix(col, ink * 0.6, pit);
        // Fine grain at a fixed amplitude (the web lava grain, #1097).
        col *= 1.0 - 0.32 * (fractal(p, scale * 6.0, 1, seed + 11u) - 0.5);
```

- [ ] **Step 2: River: broad dim light and a tooth driven by `pits`**

In the River branch, replace

```metal
        Lit l = lit(n, L, 18.0);
        float shade = 0.6 + 0.6 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
```

with

```metal
        // Exponent 6, not 18: a broad, dim light with no hot spot (#1097).
        Lit l = lit(n, L, 6.0);
        float shade = 0.6 + 0.6 * l.diffuse;
        col = body * mix(1.0, shade, contrast);
        // A fine tooth. River ignored `pits` until now; it is its grain.
        col *= 1.0 - pits * (fractal(p, scale * 6.0, 1, seed + 11u) - 0.5);
```

Update the branch's leading comment to: `// River: continuous fine grain, one axis stretched into sediment banding, a matte broad specular and a fine tooth set by pits.`

- [ ] **Step 3: Build and run the stone suites**

```bash
cd /Users/alexis/code/pbbls/apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -only-testing:PebblesTests/StoneMaterialTests -only-testing:PebblesTests/References -quiet
```

Expected: `StoneMaterialTests`, `litStoneDrawsMaterial` and the welcome slides PASS; `nineStones-*` and `nineStonesFlat-*` FAIL above the threshold (the intended move: the shader moves the lit stones, and the soft silhouette from Task B3.3 moves the flat gem and river stones too). Open `apps/ios/build/reference-diffs/StoneReferences/` to look.

- [ ] **Step 4: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Resources/Stone/stone.metal
git commit -m "fix(ios): matte the river stone and roughen the lava grain"
```

### Task B3.5: Starting material values

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Stone/StoneMaterial.swift` (`starting(for:)`)
- Test: `apps/ios/PebblesTests/StoneLab/StoneMaterialTests.swift`

- [ ] **Step 1: Write the failing test**

Add to `StoneMaterialTests`:

```swift
    @Test("starting tables are the lab's picks (#1097)")
    func startingTables() {
        #expect(StoneMaterial.starting(for: .lowlight) == StoneMaterial(
            kind: .lava, scale: 0.07, relief: 3.6, contrast: 1, sheen: 0.05,
            facetDensity: 1, glitter: 0, pits: 1, banding: 0, lipWidth: 0.5, lipOpacity: 0.84))
        #expect(StoneMaterial.starting(for: .neutral) == StoneMaterial(
            kind: .river, scale: 0.2, relief: 1.28, contrast: 0.5, sheen: 0.06,
            facetDensity: 1, glitter: 0, pits: 0.14, banding: 0.5, lipWidth: 1.2, lipOpacity: 0.46))
        #expect(StoneMaterial.starting(for: .highlight) == StoneMaterial(
            kind: .gem, scale: 0.11, relief: 1.94, contrast: 0.59, sheen: 0.35,
            facetDensity: 3, glitter: 1, pits: 0, banding: 0, lipWidth: 1.47, lipOpacity: 0.94))
    }
```

- [ ] **Step 2: Run to verify it fails**

Run the single-test command with `StoneMaterialTests`.
Expected: FAIL on lowlight and neutral.

- [ ] **Step 3: Set the starting values (spec §B3.3)**

In `StoneMaterial.starting(for:)`, replace the lowlight and neutral returns with:

```swift
        case .lowlight:
            return StoneMaterial(kind: .lava, scale: 0.07, relief: 3.6, contrast: 1, sheen: 0.05,
                                 facetDensity: 1, glitter: 0, pits: 1, banding: 0,
                                 lipWidth: 0.5, lipOpacity: 0.84)
        case .neutral:
            return StoneMaterial(kind: .river, scale: 0.2, relief: 1.28, contrast: 0.5, sheen: 0.06,
                                 facetDensity: 1, glitter: 0, pits: 0.14, banding: 0.5,
                                 lipWidth: 1.2, lipOpacity: 0.46)
```

Change the doc comment to: "Starting values for #1097 (spec §B3.3), replaced by the lab's Copy output in the next commit."

- [ ] **Step 4: Run the test**

Run the single-test command with `StoneMaterialTests`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Path/Stone/StoneMaterial.swift apps/ios/PebblesTests/StoneLab/StoneMaterialTests.swift
git commit -m "fix(ios): start river and lava from the web material ratios"
```

### Task B3.6: Tune on the phone and paste the lab's values

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Stone/StoneMaterial.swift`
- Modify: `apps/ios/PebblesTests/StoneLab/StoneMaterialTests.swift`

- [ ] **Step 1: Run the lab on the iPhone 15**

Install this branch's Debug build on the iPhone 15. Open Profile → Settings → Developer → Stone lab. Press **Reset** so the lab starts from the new `starting` values (an older saved pick would mask them). Beside it, open web `/sandbox/design` (the LitStone specimens) on a laptop. Tune the river (neutral) and lava (lowlight) knobs until gem and river read soft and matte and lava reads harsher and finer, as on web. Press **Copy**, and paste the clipboard somewhere you can read it (Notes, then AirDrop).

- [ ] **Step 2: Paste the values**

The Copy output reads, per polarity:

```
[lowlight] blackstone
  scale … relief … contrast … sheen …
  lipWidth … lipOpacity … pits … banding … facetDensity … glitter … rimWidth … rimStrength …
  body … ink … lipLight … lipShadow …
```

Transcribe, for each of `[lowlight]`, `[neutral]` and `[highlight]`, the fields `scale`, `relief`, `contrast`, `sheen`, `facetDensity`, `glitter`, `pits`, `banding`, `lipWidth`, `lipOpacity` into the matching `StoneMaterial(…)` in `starting(for:)`. Add `rimWidth:` and `rimStrength:` arguments only where they differ from the defaults `4.00` and `0.60`. If the `outline … carvingScale …` line or a `body/ink/lipLight/lipShadow` pick differs from `StoneLook.standard` / `StoneTones.starting`, stop: those are out of this part's scope, report them instead of pasting.

Change the doc comment to: "The maintainer's picks on the iPhone 15 (<date>), pasted from the lab's Copy button (#1097)."

Copy the same numbers into `startingTables()` in `StoneMaterialTests`, so the test transcribes the paste.

- [ ] **Step 3: Run the tests and reset the phone**

Run the single-test command with `StoneMaterialTests`.
Expected: PASS. On the phone, press **Reset** in the lab so its saved values fall back to the shipped ones.

- [ ] **Step 4: Commit**

```bash
cd /Users/alexis/code/pbbls
git add apps/ios/Pebbles/Features/Path/Stone/StoneMaterial.swift apps/ios/PebblesTests/StoneLab/StoneMaterialTests.swift
git commit -m "fix(ios): paste the stone lab picks for river and lava"
```

### Task B3.7: Verify B3, re-baseline, open the PR

- [ ] **Step 1: Lint and the whole suite**

```bash
cd /Users/alexis/code/pbbls && npm run lint --workspace=@pbbls/ios
rm -rf ~/Library/Developer/Xcode/DerivedData/Pebbles-*
npm run test --workspace=@pbbls/ios
```

Expected: lint clean of errors; the only failures are the `StoneReferences.nineStones` and `nineStonesFlat` cases (the intended move). Everything else, the welcome references included, passes.

- [ ] **Step 2: The FR pass over the references**

```bash
cd /Users/alexis/code/pbbls/apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 17' -testLanguage fr -testRegion FR -only-testing:PebblesTests/References -quiet
```

Expected: `** TEST SUCCEEDED **` (stones skipped in FR; the FR slides unchanged). Local Xcode renders can differ from CI's (the runtime differs); if the slides fail here but the diff shows antialiasing noise only, CI's result is the authority.

- [ ] **Step 3: Manual checks**

On the iPhone 15: the Stone lab beside web `/sandbox/design` LitStone; the valence picker on a real pebble in light and dark (the headline word colour unchanged); Low Power Mode on (stones draw flat, no shader).

- [ ] **Step 4: Push, open the PR, re-baseline**

```bash
cd /Users/alexis/code/pbbls
git fetch origin
gh stack submit --auto
gh pr edit fix/1097-ios-lit-stone-materials --title "fix(ios): lit stone materials match web" --add-label fix,ui,ios,no-lab-note --milestone "M64 · New Path" --body-file - <<'EOF'
Resolves #1097
Part of #1143 (iOS half; the Android follow-up applies the retuned tables to `ValenceMesh`)

## What
- Gem and river draw from the asset's own soft outline (`WobbleRenderer.softBackdropArt`); lava keeps the wobble; the glyph stays wobbled.
- `stone.metal`: river Blinn exponent 18 → 6 and a fine tooth driven by `pits`; lava gains a fixed 0.32 fine grain. Uniform order unchanged.
- `StoneMaterial.starting`: the Stone lab's picks on the iPhone 15.
- `ValenceStoneStyle` keeps only `headlineInk`: every dead member and its hex tables are gone, so iOS carries no red valence stop.

## References
`nineStones` paper and Night move on purpose, re-baselined by `rebaseline-ios-screenshots`. Compare the triptych with web `/sandbox/design` LitStone.

## Checks
- `npm run lint --workspace=@pbbls/ios`, `npm run test --workspace=@pbbls/ios`, the FR reference pass.
- iPhone 15: the lab beside the web specimens, the valence picker light/dark, Low Power Mode on.
EOF
gh pr edit fix/1097-ios-lit-stone-materials --add-label rebaseline-ios-screenshots
```

- [ ] **Step 5: Approve, pull the bot commit, review the triptych**

```bash
gh run watch "$(gh run list --workflow ios-references.yml --branch fix/1097-ios-lit-stone-materials --limit 1 --json databaseId -q '.[0].databaseId')"
for id in $(gh run list --branch fix/1097-ios-lit-stone-materials --status action_required --json databaseId -q '.[].databaseId'); do
  gh api --method POST "repos/{owner}/{repo}/actions/runs/$id/approve"
done
git fetch origin
git switch fix/1097-ios-lit-stone-materials
git merge --ff-only origin/fix/1097-ios-lit-stone-materials
git diff --stat HEAD~1 -- apps/ios/PebblesTests/References/__References__
```

Expected: only `StoneReferences/*` PNGs changed; the welcome PNGs did not. Open the new `nineStones-*` beside web LitStone. `ios.yml` must go green on the bot commit.

- [ ] **Step 6: Before merging the stack**

If B2 or B1 moves again (a review fix), fast-forward the moved branch from `origin`, `gh stack rebase --upstack`, and on a PNG conflict under `__References__` keep the upper branch's side (`git checkout --theirs -- apps/ios/PebblesTests/References/__References__` during the rebase), then re-label `rebaseline-ios-screenshots` on the upper PR so CI re-renders. Rebase and re-run CI before `gh stack merge --yes` (bot commits break linearity). Arkaik: none applies.

---

## Self-review against the spec

| Spec item | Task |
|---|---|
| §B1.1 files, `UIAppFonts`, `cmp` + path filter | B1.1, B1.2 |
| §B1.2 `YsabeauFace`, OpenType tags, fallback log once, helpers keep names | B1.3, B1.4 |
| §B1.3 rank table, `Cut`, `Cut.untitled`, `lockupWord` (Caveat, overhang), `postmarkRing`, `textStyle(forSize:)` | B1.5 |
| §B1.4 modifier from the environment, Bold Text +100 cap 800, tracking, line spacing | B1.6 |
| §B1.5 policies `.text` to `.accessibility2`, `.card(cap: 1.3)`, `.none` | B1.5, B1.6 |
| §B1.6 call-site audit (two PostScript sites edited, eleven untouched), manual EN/FR check | B1.4, B1.0, B1.8 |
| §B1.7 three FR values as text, `KarmaReason` comment, "Part of #1101" | B1.7, B1.8 |
| §B1.8 font, face, rank, DT, bold, probe, localisation tests | B1.1, B1.3–B1.7 (localisation test narrowed, flagged) |
| §B2 D1–D6, folder layout, record mode, missing fails, diff writer | B2.1–B2.6 |
| §B2.2 first references | B2.5, B2.7 |
| §B2.3 pinned device, TZ, FR pass, diff upload | B2.8 |
| §B2.4 workflow, label, step summary | B2.9 |
| §B2.5 harness tests, scheme probe | B2.1–B2.3 |
| D4 calibration (byte-identical, ≥ 2× headroom), PNG sizes | B2.9 (reproduce step), B2.11, B2.12 |
| §B3.1 dead members deleted (or A3b no-op), no hex table | B3.1 |
| §B3.2 soft silhouette, `"<name>-soft"`, one `silhouette`, glyph untouched | B3.2, B3.3 |
| §B3.3 starting values, shader changes, lab paste | B3.4, B3.5, B3.6 |
| §B3.4 unit tests, re-baseline, manual | B3.2, B3.3, B3.5, B3.7 |

## Lessons learned

Filled in while executing; promoted only at the milestone grooming pass.

- **Feature form that shipped (Task B1.3):**
- **Line-height probe (Task B1.6):**
- **Window fallback needed? (Task B2.5/B2.6):**
- **First reference PNG sizes (Task B2.11):**
- **Calibration (Task B2.12): smallest re-word fraction, resulting headroom:**
- **Stone lab picks (Task B3.6), date:**
