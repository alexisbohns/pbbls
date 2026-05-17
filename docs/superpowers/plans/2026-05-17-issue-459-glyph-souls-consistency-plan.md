# Issue #459 — Glyph and Souls Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify glyph and soul rendering across six iOS surfaces behind one `Glyph` component and one `SoulItem` component, with a new typography token set and a spacing scale as supporting foundations.

**Architecture:** New `Glyph` view wraps a chrome-stripped `GlyphThumbnail` and owns all border/state/icon-overlay logic. New `SoulItem` view replaces both `SoulGridCell` and `SoulSelectableCell`. `SoulPickerSheet` and `SoulsListView` are rewritten to render `LazyVGrid<SoulItem>`. `Font+Pebbles.swift` gains 8 typography tokens exposed through a `pebblesFont(_:)` view modifier; `Theme/Spacing.swift` defines a 6-step scale rooted on the 17pt body baseline.

**Tech Stack:** SwiftUI · iOS 17 · XcodeGen · SF Pro Rounded (system) · SF Compact Rounded (bundled TTFs) · Ysabeau SemiBold (already bundled).

**Spec:** `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md`

**Branch:** `quality/459-glyph-souls-consistency` (already created and checked out, contains the spec commit).

**Verification model:** This codebase has no unit tests for view code (per `apps/ios/CLAUDE.md`: "No UI tests for now"). Verification for each commit is:
1. `npm run generate --workspace=@pbbls/ios` (xcodegen) — regenerates `.xcodeproj` after source/resource changes.
2. `xcodebuild -workspace - -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build` — type-check + compile clean.
3. SwiftUI `#Preview` blocks updated/added per task — visual check in Xcode canvas.
4. For the final commit: launch in simulator and walk through the six surfaces (`SoulsListView`, `SoulPickerSheet`, `GlyphsListView`, `GlyphPickerSheet`, `ProfileBanner`, `SettingsSheet`) in both light and dark mode.

---

## File Map

### Create
- `apps/ios/Pebbles/Theme/Spacing.swift` — spacing scale (Task 1).
- `apps/ios/Pebbles/Resources/Fonts/SF-Compact-Rounded-Medium.otf` — bundled font (Task 2).
- `apps/ios/Pebbles/Resources/Fonts/SF-Compact-Rounded-Semibold.otf` — bundled font (Task 2).
- `apps/ios/Pebbles/Resources/Fonts/SF-Compact-Rounded-Bold.otf` — bundled font (Task 2).
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphView.swift` — `Glyph` component (Task 4).
- `apps/ios/Pebbles/Features/Shared/SoulItem.swift` — `SoulItem` component (Task 5).

### Modify
- `apps/ios/Pebbles/Theme/Font+Pebbles.swift` — add helpers, tokens, modifier (Task 2).
- `apps/ios/Pebbles/Resources/Info.plist` — register the three new TTFs in `UIAppFonts` (Task 2).
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift` — strip chrome (Task 3).
- `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` — migrate to `Glyph(.default)` (Task 4).
- `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` — migrate to `Glyph(.profile)` (Task 4).
- `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift` — migrate inner `GlyphRow` (Task 4).
- `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift` — migrate inner `GlyphRow` (Task 4).
- `apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift` — migrate to `Glyph(.profile)`, drop manual overlay (Task 4).
- `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift` — migrate to `Glyph(.default)` (Task 4).
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift` — migrate to `Glyph(.default)` (Task 4).
- `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift` — migrate to `Glyph(.selected/.default)` + `.carve` row (Task 4).
- `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift` — rewrite around `SoulItem` + section header (Task 5).
- `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` — rewrite around `SoulItem` (Task 5).
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — add `"All my souls"` (Task 5).

### Delete
- `apps/ios/Pebbles/Features/Profile/Lists/SoulGridCell.swift` (Task 5).
- `apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift` (Task 5).

### Untouched (intentional)
- `apps/ios/Pebbles/Features/Path/SoulPill.swift` — already passes `.clear`; keeps using `GlyphThumbnail` after the strip.
- `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift` — same.

---

## Task 1 — Add the spacing scale

**Commit message:** `feat(ios): add spacing scale`

**Files:**
- Create: `apps/ios/Pebbles/Theme/Spacing.swift`

- [ ] **Step 1.1 — Create `Spacing.swift`**

```swift
import CoreGraphics

/// Six-step spacing scale rooted on the 17pt iOS body baseline.
/// `lg` equals the body font size (17), `xxl` equals `lg * 2` (34).
/// Use these constants in place of literal pt values for paddings,
/// stack spacings, and corner radii so visual rhythm stays consistent
/// across screens.
enum Spacing {
    static let xs:  CGFloat = 3
    static let sm:  CGFloat = 10
    static let md:  CGFloat = 13
    static let lg:  CGFloat = 17    // root, == iOS body font size
    static let xl:  CGFloat = 22
    static let xxl: CGFloat = 34    // == lg * 2
}
```

- [ ] **Step 1.2 — Regenerate the Xcode project**

Run: `npm run generate --workspace=@pbbls/ios`
Expected: xcodegen exits 0; `apps/ios/Pebbles.xcodeproj` updated to include `Theme/Spacing.swift`.

- [ ] **Step 1.3 — Verify build**

Run from `apps/ios/`:
```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build -quiet
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 1.4 — Commit**

```bash
git add apps/ios/Pebbles/Theme/Spacing.swift apps/ios/Pebbles.xcodeproj 2>/dev/null || true
git add apps/ios/Pebbles/Theme/Spacing.swift
git commit -m "feat(ios): add spacing scale" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```
(`Pebbles.xcodeproj` is git-ignored per `apps/ios/CLAUDE.md`; the `add` for it is a no-op and silently dropped — that's fine.)

---

## Task 2 — Typography tokens

**Commit message:** `feat(ios): introduce typography tokens`

**Files:**
- Create: `apps/ios/Pebbles/Resources/Fonts/SF-Compact-Rounded-Medium.otf`
- Create: `apps/ios/Pebbles/Resources/Fonts/SF-Compact-Rounded-Semibold.otf`
- Create: `apps/ios/Pebbles/Resources/Fonts/SF-Compact-Rounded-Bold.otf`
- Modify: `apps/ios/Pebbles/Resources/Info.plist:42-45` (extend `UIAppFonts`)
- Modify: `apps/ios/Pebbles/Theme/Font+Pebbles.swift` (full rewrite — extend with helpers + tokens + modifier)

- [ ] **Step 2.1 — Source the SF Compact Rounded TTFs**

Apple ships SF Compact in a DMG at https://developer.apple.com/fonts/ → "SF Compact" download. Mount the DMG, open the `.pkg`, and from the installed `/Library/Fonts/` (or the package's `Payload`) copy:
- `SF-Compact-Rounded-Medium.otf`
- `SF-Compact-Rounded-Semibold.otf`
- `SF-Compact-Rounded-Bold.otf`

Place them under `apps/ios/Pebbles/Resources/Fonts/` (create the `Fonts/` directory if it does not exist).

**Fallback if the TTFs cannot be obtained:** skip Step 2.1 and Step 2.3, and in Step 2.5 below replace the `sfCompactRounded` helper body with the `Font.system(size:weight:design:.rounded)` fallback documented in the spec's §1 risk note. The rest of the task proceeds unchanged.

- [ ] **Step 2.2 — Verify the files landed**

Run from repo root:
```bash
ls -lh apps/ios/Pebbles/Resources/Fonts/
```
Expected: three `.otf` files, each non-zero size.

- [ ] **Step 2.3 — Register fonts in `Info.plist`**

Edit `apps/ios/Pebbles/Resources/Info.plist` lines 42–45. Replace:

```xml
<key>UIAppFonts</key>
<array>
    <string>Ysabeau SemiBold.ttf</string>
</array>
```

With:

```xml
<key>UIAppFonts</key>
<array>
    <string>Ysabeau SemiBold.ttf</string>
    <string>Fonts/SF-Compact-Rounded-Medium.otf</string>
    <string>Fonts/SF-Compact-Rounded-Semibold.otf</string>
    <string>Fonts/SF-Compact-Rounded-Bold.otf</string>
</array>
```

- [ ] **Step 2.4 — Confirm font PostScript names**

Open one of the OTFs in Font Book (`open apps/ios/Pebbles/Resources/Fonts/SF-Compact-Rounded-Medium.otf`) and note the PostScript name shown in the info panel (e.g. `SFCompactRounded-Medium`). The three names must match what we'll request in `UIFont(name:)`. Apple's standard PostScript names for these weights are:
- `SFCompactRounded-Medium`
- `SFCompactRounded-Semibold`
- `SFCompactRounded-Bold`

If Font Book shows a different casing or spelling, use the exact PostScript name in Step 2.5 instead.

- [ ] **Step 2.5 — Replace `Font+Pebbles.swift`**

Full file replacement:

```swift
import SwiftUI
import UIKit

// MARK: - Token catalog

/// Typography tokens used across Pebbles iOS. Apply via `View.pebblesFont(_:)`
/// so that font + tracking + textCase are bundled at the call site.
enum PebblesFont {
    case body
    case bodyEmphasized
    case subhead
    case subheadEmphasized
    case headline
    case headlineEmphasized
    case callout
    case calloutEmphasized
    case meta
    case metaEmphasized
    case cardHeading
    case cardHeadingEmphasized
    case title
    case buttonLabel
}

// MARK: - View modifier

extension View {
    /// Apply a Pebbles typography token: sets `.font`, `.tracking`, and
    /// `.textCase` together so callers cannot forget one half of the pair
    /// (e.g. uppercase + letter-spacing on meta).
    func pebblesFont(_ token: PebblesFont) -> some View {
        modifier(PebblesFontModifier(token: token))
    }
}

private struct PebblesFontModifier: ViewModifier {
    let token: PebblesFont

    func body(content: Content) -> some View {
        content
            .font(token.font)
            .tracking(token.tracking)
            .textCase(token.isUppercase ? .uppercase : nil)
    }
}

// MARK: - Token → font / tracking / case mapping

private extension PebblesFont {
    var font: Font {
        switch self {
        case .body:                  return .sfProRounded(17, .regular)
        case .bodyEmphasized:        return .sfProRounded(17, .semibold)
        case .subhead:               return .sfProRounded(15, .regular)
        case .subheadEmphasized:     return .sfProRounded(15, .semibold)
        case .headline:              return .sfProRounded(17, .semibold)
        case .headlineEmphasized:    return .sfProRounded(17, .bold)
        case .callout:               return .sfProRounded(16, .medium)
        case .calloutEmphasized:     return .sfProRounded(16, .semibold)
        case .meta:                  return .sfCompactRounded(12, .medium)
        case .metaEmphasized:        return .sfCompactRounded(12, .bold)
        case .cardHeading:           return .sfCompactRounded(15, .semibold)
        case .cardHeadingEmphasized: return .sfCompactRounded(15, .bold)
        case .title:                 return .ysabeauSemibold(28)
        case .buttonLabel:           return .ysabeauSemibold(17)
        }
    }

    /// Tracking in points (the spec is in % of font size; converted here).
    var tracking: CGFloat {
        switch self {
        case .body, .bodyEmphasized, .headline, .headlineEmphasized,
             .buttonLabel:                                        return 0.34   // 2% of 17
        case .subhead, .subheadEmphasized:                        return 0.30   // 2% of 15
        case .callout, .calloutEmphasized:                        return 0.32   // 2% of 16
        case .meta, .metaEmphasized:                              return 1.20   // 10% of 12
        case .cardHeading, .cardHeadingEmphasized:                return 1.50   // 10% of 15
        case .title:                                              return -0.56  // -2% of 28
        }
    }

    var isUppercase: Bool {
        switch self {
        case .meta, .metaEmphasized, .cardHeading, .cardHeadingEmphasized:
            return true
        default:
            return false
        }
    }
}

// MARK: - Family helpers

extension Font {
    /// Ysabeau-SemiBold with OpenType proportional + lining figures
    /// (numbers align to cap height, proportional widths). Used everywhere
    /// Ysabeau renders mixed text + numbers so digits look right.
    ///
    /// Feature constants from `CoreText/SFNTLayoutTypes.h`:
    ///   - Number Spacing (type 6) → Proportional Numbers (selector 1)
    ///   - Number Case  (type 21) → Upper Case Numbers / lining (selector 1)
    static func ysabeauSemibold(_ size: CGFloat) -> Font {
        let descriptor = UIFontDescriptor(name: "Ysabeau-SemiBold", size: size)
            .addingAttributes([
                .featureSettings: [
                    [UIFontDescriptor.FeatureKey.type: 6,  UIFontDescriptor.FeatureKey.selector: 1],
                    [UIFontDescriptor.FeatureKey.type: 21, UIFontDescriptor.FeatureKey.selector: 1],
                ],
            ])
        return Font(UIFont(descriptor: descriptor, size: size))
    }

    /// SF Pro Rounded — system rounded design.
    fileprivate static func sfProRounded(_ size: CGFloat, _ weight: UIFont.Weight) -> Font {
        let base = UIFont.systemFont(ofSize: size, weight: weight)
        if let descriptor = base.fontDescriptor.withDesign(.rounded) {
            return Font(UIFont(descriptor: descriptor, size: size))
        }
        return Font(base)
    }

    /// SF Compact Rounded — bundled OTFs (see Resources/Fonts/).
    /// Falls back to SF Pro Rounded if the named font is missing (e.g. the
    /// OTFs were not bundled in a given build).
    fileprivate static func sfCompactRounded(_ size: CGFloat, _ weight: UIFont.Weight) -> Font {
        let name: String
        switch weight {
        case .medium:   name = "SFCompactRounded-Medium"
        case .semibold: name = "SFCompactRounded-Semibold"
        case .bold:     name = "SFCompactRounded-Bold"
        default:        name = "SFCompactRounded-Medium"
        }
        if let custom = UIFont(name: name, size: size) {
            return Font(custom)
        }
        return sfProRounded(size, weight)
    }
}
```

- [ ] **Step 2.6 — Regenerate project**

Run: `npm run generate --workspace=@pbbls/ios`
Expected: xcodegen exits 0; `Resources/Fonts/*.otf` are visible under the Pebbles target.

- [ ] **Step 2.7 — Build + token sanity check**

Run from `apps/ios/`:
```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build -quiet
```
Expected: `** BUILD SUCCEEDED **`.

Then, in `Font+Pebbles.swift` at the bottom of the file, temporarily add (will be removed in Step 2.8):

```swift
#Preview("Pebbles font tokens") {
    ScrollView {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("body — quick brown fox").pebblesFont(.body)
            Text("body emphasized — quick brown fox").pebblesFont(.bodyEmphasized)
            Text("subhead — quick brown fox").pebblesFont(.subhead)
            Text("subhead emphasized — quick brown fox").pebblesFont(.subheadEmphasized)
            Text("headline — quick brown fox").pebblesFont(.headline)
            Text("callout — quick brown fox").pebblesFont(.callout)
            Text("meta — quick brown fox").pebblesFont(.meta)
            Text("meta emphasized — quick brown fox").pebblesFont(.metaEmphasized)
            Text("cardHeading — quick brown fox").pebblesFont(.cardHeading)
            Text("title — quick brown fox").pebblesFont(.title)
            Text("buttonLabel — quick brown fox").pebblesFont(.buttonLabel)
        }
        .padding(Spacing.lg)
    }
}
```

Open the file in Xcode; the canvas should render two distinguishable rounded-font families. `meta` and `cardHeading` must render UPPERCASE with visible letter-spacing. If `meta`/`cardHeading` look identical to `subhead`, the SF Compact OTFs are not loading — re-check Step 2.4 (PostScript name) and Step 2.3 (`Info.plist` entry path).

- [ ] **Step 2.8 — Remove the temporary preview**

Delete the `#Preview("Pebbles font tokens")` block added in Step 2.7. The token catalog ships without a preview to avoid coupling the theme file to specimen content.

- [ ] **Step 2.9 — Commit**

```bash
git add apps/ios/Pebbles/Resources/Fonts/ apps/ios/Pebbles/Resources/Info.plist apps/ios/Pebbles/Theme/Font+Pebbles.swift
git commit -m "feat(ios): introduce typography tokens" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 3 — Strip `GlyphThumbnail` chrome

**Commit message:** `quality(ios): strip GlyphThumbnail chrome`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift`

**Note:** After this commit, the seven sites that today rely on `GlyphThumbnail`'s default grey background will render as bare strokes against the page. That's a deliberate, brief regression — Task 4 migrates them to `GlyphView(case:)` which restores the chrome. Do not stop here.

- [ ] **Step 3.1 — Rewrite `GlyphThumbnail.swift`**

Full file replacement:

```swift
import SwiftUI

/// Pure stroke canvas for a glyph. Renders each `GlyphStroke.d` via
/// `SVGPath.path(from:)` inside a 200x200 coordinate space, scaled to the
/// requested side length. No background, no clipping — chrome is the
/// caller's responsibility (see `Glyph` for the canonical wrapper).
///
/// Direct callers after the #459 refactor:
/// - `SoulPill` (path)         — explicitly chrome-less inside a pill
/// - `PebbleMetaPill` (path)   — explicitly chrome-less inside a pill
/// - `Glyph` (composition)     — owns 34-radius border + state colors
struct GlyphThumbnail: View {
    let strokes: [GlyphStroke]
    var side: CGFloat = 100
    var strokeColor: Color = .primary

    var body: some View {
        Canvas { ctx, size in
            let scale = size.width / 200.0
            for stroke in strokes {
                var path = SVGPath.path(from: stroke.d)
                path = path.applying(CGAffineTransform(scaleX: scale, y: scale))
                ctx.stroke(
                    path,
                    with: .color(strokeColor),
                    style: StrokeStyle(
                        lineWidth: stroke.width * scale,
                        lineCap: .round,
                        lineJoin: .round
                    )
                )
            }
        }
        .frame(width: side, height: side)
    }
}

#Preview {
    GlyphThumbnail(
        strokes: [
            GlyphStroke(d: "M30,30 L170,170", width: 6),
            GlyphStroke(d: "M170,30 L30,170", width: 6)
        ],
        side: 120,
        strokeColor: .primary
    )
    .padding()
}
```

- [ ] **Step 3.2 — Audit the two remaining direct callers**

These two files still pass `backgroundColor: .clear` to the old API. Strip that argument so they compile against the new signature.

In `apps/ios/Pebbles/Features/Path/SoulPill.swift` lines 17–22, replace:

```swift
GlyphThumbnail(
    strokes: glyph.strokes,
    side: 24,
    strokeColor: Color.accent.primary,
    backgroundColor: .clear
)
```

With:

```swift
GlyphThumbnail(
    strokes: glyph.strokes,
    side: 24,
    strokeColor: Color.accent.primary
)
```

In `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift` lines 59–64, replace:

```swift
GlyphThumbnail(
    strokes: glyph.strokes,
    side: 16,
    strokeColor: foreground,
    backgroundColor: .clear
)
```

With:

```swift
GlyphThumbnail(
    strokes: glyph.strokes,
    side: 16,
    strokeColor: foreground
)
```

- [ ] **Step 3.3 — Build (expect clean compile across the rest of the codebase)**

Run from `apps/ios/`:
```bash
xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build -quiet
```
Expected: `** BUILD SUCCEEDED **`. All other `GlyphThumbnail(...)` call sites still compile because they never passed `backgroundColor` (they used the default).

- [ ] **Step 3.4 — Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphThumbnail.swift apps/ios/Pebbles/Features/Path/SoulPill.swift apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift
git commit -m "quality(ios): strip GlyphThumbnail chrome" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 4 — Introduce `GlyphView` and migrate seven call sites

**Commit message:** `feat(ios): introduce GlyphView component`

**Naming note:** The new view is named `GlyphView` (not `Glyph`) to avoid a collision with the existing `Glyph` model struct at `Features/Glyph/Models/Glyph.swift`. Swift module qualification does not disambiguate types defined in the same module, so the rename is mandatory rather than optional.

**Files:**
- Create: `apps/ios/Pebbles/Features/Glyph/Views/GlyphView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift:223`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift:140`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift:135`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift:144`
- Modify: `apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift:9-15,35`
- Modify: `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift:91`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift:118-122`
- Modify: `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift:75-81,93-113`

- [ ] **Step 4.1 — Create `GlyphView.swift`**

Full file content:

```swift
import SwiftUI

/// Canonical glyph chrome component. Renders a 2XL-radius (34pt) frame
/// with state-driven border (continuous/dashed, weight, color) and either
/// the user's glyph strokes or an SF Symbol overlay (scribble for `.carve`,
/// plus for `.create`).
///
/// Named `GlyphView` (not `Glyph`) because the model type at
/// `Features/Glyph/Models/Glyph.swift` already owns the `Glyph` symbol
/// in this module.
///
/// Visual specification table is in
/// `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md` §2.
struct GlyphView: View {
    enum Case {
        case profile      // continuous 1pt system.muted; glyph in accent.primary
        case carve        // dashed 2pt system.muted; sf.scribble in system.secondary
        case create       // dashed 2pt system.muted; sf.plus in system.muted
        case selected     // continuous 2pt accent.primary; glyph in accent.primary
        case unselected   // continuous 1pt system.muted; glyph in system.muted
        case `default`    // continuous 1pt system.muted; glyph in system.secondary
    }

    let `case`: Case
    let strokes: [GlyphStroke]?
    var side: CGFloat = 96

    init(case: Case, strokes: [GlyphStroke]? = nil, side: CGFloat = 96) {
        self.case = `case`
        self.strokes = strokes
        self.side = side
    }

    var body: some View {
        ZStack {
            border
            content
        }
        .frame(width: side, height: side)
    }

    @ViewBuilder
    private var border: some View {
        let shape = RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous)
        switch `case` {
        case .selected:
            shape.stroke(Color.accent.primary, lineWidth: 2)
        case .carve, .create:
            shape.strokeBorder(
                Color.system.muted,
                style: StrokeStyle(lineWidth: 2, dash: [10, 10])
            )
        case .profile, .unselected, .default:
            shape.stroke(Color.system.muted, lineWidth: 1)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch `case` {
        case .carve:
            Image(systemName: "scribble")
                .font(.system(size: max(side * 0.4, 18), weight: .regular))
                .foregroundStyle(Color.system.secondary)
        case .create:
            Image(systemName: "plus")
                .font(.system(size: max(side * 0.4, 18), weight: .regular))
                .foregroundStyle(Color.system.muted)
        case .profile, .selected:
            GlyphThumbnail(strokes: strokes ?? [], side: side, strokeColor: Color.accent.primary)
        case .unselected:
            GlyphThumbnail(strokes: strokes ?? [], side: side, strokeColor: Color.system.muted)
        case .default:
            GlyphThumbnail(strokes: strokes ?? [], side: side, strokeColor: Color.system.secondary)
        }
    }
}

#Preview("All cases — light") {
    let strokes = [
        GlyphStroke(d: "M40,40 C80,20 120,20 160,40 S180,120 160,160 S80,180 40,160 S20,80 40,40", width: 6)
    ]
    return ScrollView {
        VStack(spacing: Spacing.lg) {
            HStack(spacing: Spacing.lg) {
                VStack { GlyphView(case: .profile,    strokes: strokes); Text(".profile") }
                VStack { GlyphView(case: .carve);                       Text(".carve") }
                VStack { GlyphView(case: .create);                      Text(".create") }
            }
            HStack(spacing: Spacing.lg) {
                VStack { GlyphView(case: .selected,   strokes: strokes); Text(".selected") }
                VStack { GlyphView(case: .unselected, strokes: strokes); Text(".unselected") }
                VStack { GlyphView(case: .default,    strokes: strokes); Text(".default") }
            }
        }
        .padding(Spacing.lg)
    }
}

#Preview("All cases — dark") {
    let strokes = [
        GlyphStroke(d: "M40,40 C80,20 120,20 160,40 S180,120 160,160 S80,180 40,160 S20,80 40,40", width: 6)
    ]
    return ScrollView {
        VStack(spacing: Spacing.lg) {
            HStack(spacing: Spacing.lg) {
                GlyphView(case: .profile,    strokes: strokes)
                GlyphView(case: .carve)
                GlyphView(case: .create)
            }
            HStack(spacing: Spacing.lg) {
                GlyphView(case: .selected,   strokes: strokes)
                GlyphView(case: .unselected, strokes: strokes)
                GlyphView(case: .default,    strokes: strokes)
            }
        }
        .padding(Spacing.lg)
    }
    .preferredColorScheme(.dark)
}
```

- [ ] **Step 4.2 — Regenerate + build to confirm `Glyph` compiles in isolation**

```bash
npm run generate --workspace=@pbbls/ios
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build -quiet
```
Expected: `** BUILD SUCCEEDED **`. Open `GlyphView.swift` in Xcode and confirm the two `#Preview` canvases render the six chrome cases. Note: `selected` / `unselected` / `default` all share the same strokes — the difference is color + border weight.

- [ ] **Step 4.3 — Migrate `PebbleFormView.swift`**

Open `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`, locate line 223. Replace:

```swift
GlyphThumbnail(strokes: glyph.strokes, side: 32)
    .accessibilityHidden(true)
```

With:

```swift
GlyphView(case: .default, strokes: glyph.strokes, side: 32)
    .accessibilityHidden(true)
```

- [ ] **Step 4.4 — Migrate `SettingsSheet.swift`**

Open `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift`, locate line 140. Replace:

```swift
GlyphThumbnail(strokes: strokes, side: 120)
```

With:

```swift
GlyphView(case: .profile, strokes: strokes, side: 120)
```

- [ ] **Step 4.5 — Migrate `EditSoulSheet.swift`**

Open `apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift`, locate line 135. Replace:

```swift
GlyphThumbnail(strokes: glyph.strokes, side: 32)
    .accessibilityHidden(true)
```

With:

```swift
GlyphView(case: .default, strokes: glyph.strokes, side: 32)
    .accessibilityHidden(true)
```

- [ ] **Step 4.6 — Migrate `CreateSoulSheet.swift`**

Open `apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift`, locate line 144. Replace:

```swift
GlyphThumbnail(strokes: glyph.strokes, side: 32)
    .accessibilityHidden(true)
```

With:

```swift
GlyphView(case: .default, strokes: glyph.strokes, side: 32)
    .accessibilityHidden(true)
```

- [ ] **Step 4.7 — Migrate `ProfileBanner.swift`**

Open `apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift`. Replace the entire `body` and `glyph` view-builder. Old:

```swift
var body: some View {
    VStack(spacing: 12) {
        glyph
            .frame(width: 96, height: 96)
            .overlay(
                RoundedRectangle(cornerRadius: 34)
                    .strokeBorder(Color.system.muted, lineWidth: 1)
            )

        VStack(spacing: 2) {
            Text(displayName ?? "")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Color.system.foreground)
            if let memberSince {
                Text("Member since \(memberSince.formatted(.dateTime.month(.wide).year()))")
                    .font(.caption)
                    .foregroundStyle(Color.system.secondary)
                    .textCase(.uppercase)
            }
        }
    }
    .frame(maxWidth: .infinity)
}

@ViewBuilder
private var glyph: some View {
    if let strokes = glyphStrokes, !strokes.isEmpty {
        GlyphThumbnail(strokes: strokes, side: 96)
    } else {
        RoundedRectangle(cornerRadius: 34)
            .fill(Color.clear)
            .overlay {
                Image(systemName: "scribble")
                    .font(.title)
                    .foregroundStyle(Color.system.secondary)
            }
    }
}
```

New:

```swift
var body: some View {
    VStack(spacing: 12) {
        glyph

        VStack(spacing: 2) {
            Text(displayName ?? "")
                .font(.title3.weight(.semibold))
                .foregroundStyle(Color.system.foreground)
            if let memberSince {
                Text("Member since \(memberSince.formatted(.dateTime.month(.wide).year()))")
                    .font(.caption)
                    .foregroundStyle(Color.system.secondary)
                    .textCase(.uppercase)
            }
        }
    }
    .frame(maxWidth: .infinity)
}

@ViewBuilder
private var glyph: some View {
    if let strokes = glyphStrokes, !strokes.isEmpty {
        GlyphView(case: .profile, strokes: strokes, side: 96)
    } else {
        GlyphView(case: .carve, side: 96)
    }
}
```

(The empty-strokes case becomes a `.carve` Glyph — dashed border + scribble icon — which is exactly the "prompt the user to draw a glyph" affordance the prior manual code was approximating.)

- [ ] **Step 4.8 — Migrate `SoulDetailView.swift`**

Open `apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift`, locate line 91. Replace:

```swift
GlyphThumbnail(strokes: soulWithGlyph.glyph.strokes, side: 56)
    .accessibilityHidden(true)
```

With:

```swift
GlyphView(case: .default, strokes: soulWithGlyph.glyph.strokes, side: 56)
    .accessibilityHidden(true)
```

- [ ] **Step 4.9 — Migrate `GlyphsListView.swift`**

Open `apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift`, locate the `thumbnail(for:)` helper at lines 116–130. Replace:

```swift
private func thumbnail(for glyph: Glyph) -> some View {
    VStack(spacing: 4) {
        GlyphThumbnail(
            strokes: glyph.strokes,
            side: 96,
            strokeColor: Color.accent.primary
        )
        if let name = glyph.name {
            Text(name)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}
```

With:

```swift
private func thumbnail(for glyph: Glyph) -> some View {
    VStack(spacing: 4) {
        GlyphView(case: .default, strokes: glyph.strokes, side: 96)
        if let name = glyph.name {
            Text(name)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
        }
    }
}
```

No naming collision concern: the new view is `GlyphView`, the model parameter is `glyph: Glyph` — distinct symbols.

- [ ] **Step 4.10 — Migrate `GlyphPickerSheet.swift`**

Open `apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift`. Two changes:

**Change A — grid cell at lines 75–81.** Replace:

```swift
GlyphThumbnail(
    strokes: glyph.strokes,
    side: 96,
    backgroundColor: glyph.id == currentGlyphId
        ? Color.accentColor.opacity(0.15)
        : Color.secondary.opacity(0.08)
)
```

With:

```swift
GlyphView(
    case: glyph.id == currentGlyphId ? .selected : .default,
    strokes: glyph.strokes,
    side: 96
)
```

**Change B — `carveNewRow` at lines 93–113.** Replace:

```swift
private var carveNewRow: some View {
    Button {
        showCarveSheet = true
    } label: {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 8)
                .strokeBorder(style: StrokeStyle(lineWidth: 1, dash: [4]))
                .frame(width: 48, height: 48)
                .overlay(Image(systemName: "plus"))
            Text("Carve new glyph")
                .font(.body)
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(.secondary)
        }
        .padding(12)
        .background(Color.secondary.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
    .buttonStyle(.plain)
}
```

With:

```swift
private var carveNewRow: some View {
    Button {
        showCarveSheet = true
    } label: {
        HStack(spacing: Spacing.sm) {
            GlyphView(case: .carve, side: 48)
            Text("Carve new glyph")
                .font(.body)
            Spacer()
            Image(systemName: "chevron.right")
                .foregroundStyle(.secondary)
        }
        .padding(Spacing.sm)
        .background(Color.secondary.opacity(0.06))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
    .buttonStyle(.plain)
}
```

- [ ] **Step 4.11 — Sanity grep for stale `Glyph(` view-call references**

Because the new view is `GlyphView` and the existing `Glyph` symbol is the model type, any leftover `Glyph(case:` call would be a typo — the model has no `case:` initializer parameter. Run:

```bash
grep -rn "\\bGlyph(case:" apps/ios/Pebbles/
```
Expected: zero results. If anything matches, it's a leftover from a Step 4.3–4.10 edit that wasn't switched to `GlyphView` — fix in place.

- [ ] **Step 4.12 — Regenerate + build**

```bash
npm run generate --workspace=@pbbls/ios
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build -quiet
```
Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4.13 — Visual smoke check (Xcode previews)**

Open each of these files in Xcode and verify the `#Preview` renders without runtime crash; specifically confirm the glyph thumbnail has a visible 34-radius rounded border in the new cases:
- `ProfileBanner.swift` — preview "With glyph (placeholder strokes)" now renders a `.carve` dashed border with scribble icon (since `glyphStrokes: nil`). Add a second preview with non-nil strokes to confirm `.profile` rendering:

  ```swift
  #Preview("With glyph") {
      ProfileBanner(
          displayName: "Alexis",
          memberSince: Date(),
          glyphStrokes: [GlyphStroke(d: "M40,40 L160,160", width: 6)]
      )
      .padding()
  }
  ```

- `GlyphsListView.swift`, `GlyphPickerSheet.swift` — confirm grid cells now have the 34-radius border with state-colored strokes.

- [ ] **Step 4.14 — Commit**

```bash
git add apps/ios/Pebbles/Features/Glyph/Views/GlyphView.swift \
        apps/ios/Pebbles/Features/Path/PebbleFormView.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/EditSoulSheet.swift \
        apps/ios/Pebbles/Features/Profile/Sheets/CreateSoulSheet.swift \
        apps/ios/Pebbles/Features/Profile/Components/ProfileBanner.swift \
        apps/ios/Pebbles/Features/Profile/Views/SoulDetailView.swift \
        apps/ios/Pebbles/Features/Glyph/Views/GlyphsListView.swift \
        apps/ios/Pebbles/Features/Glyph/Views/GlyphPickerSheet.swift
git commit -m "feat(ios): introduce GlyphView component" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 5 — `SoulItem` + rewrite `SoulPickerSheet` / `SoulsListView`

**Commit message:** `quality(ios): unify soul rendering with SoulItem`

**Files:**
- Create: `apps/ios/Pebbles/Features/Shared/SoulItem.swift`
- Modify: `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift` (rewrite body + remove `NewSoulTile`)
- Modify: `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift` (rewrite grid)
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings` (add `"All my souls"`)
- Delete: `apps/ios/Pebbles/Features/Profile/Lists/SoulGridCell.swift`
- Delete: `apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift`

- [ ] **Step 5.1 — Verify the `Shared/` directory exists**

Run: `ls apps/ios/Pebbles/Features/Shared/`
If the directory does not yet exist, `mkdir apps/ios/Pebbles/Features/Shared/`. (`Features/Shared/Ripples` already exists per the earlier directory listing, so the parent is present — but create the directory if missing.)

- [ ] **Step 5.2 — Create `SoulItem.swift`**

Full file content:

```swift
import SwiftUI

/// Single soul cell used in both `SoulsListView` (always `.default`) and
/// `SoulPickerSheet` (state-driven). Vertical stack: `GlyphView` on top,
/// name in subhead, optional `fossil.shell` + ripple/pebble count below.
///
/// Visual specification table is in
/// `docs/superpowers/specs/2026-05-17-issue-459-glyph-souls-consistency-design.md` §3.
struct SoulItem: View {
    enum Case { case selected, unselected, `default`, create }

    let `case`: Case
    let soul: SoulWithGlyph?
    let count: Int?
    var onTap: () -> Void = {}

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: Spacing.sm) {
                GlyphView(case: glyphCase, strokes: soul?.glyph.strokes, side: 96)

                VStack(spacing: Spacing.xs) {
                    Text(displayName)
                        .pebblesFont(nameToken)
                        .foregroundStyle(nameColor)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    if `case` != .create, let count {
                        HStack(spacing: Spacing.xs) {
                            Image(systemName: "fossil.shell")
                                .foregroundStyle(Color.accent.primary)
                            Text("\(count)")
                                .pebblesFont(.meta)
                                .foregroundStyle(Color.system.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(displayName)
        .accessibilityAddTraits(`case` == .selected ? [.isButton, .isSelected] : [.isButton])
    }

    private var glyphCase: GlyphView.Case {
        switch `case` {
        case .selected:   return .selected
        case .unselected: return .unselected
        case .default:    return .default
        case .create:     return .create
        }
    }

    private var displayName: String {
        switch `case` {
        case .create: return String(localized: "New soul")
        default:      return soul?.name ?? ""
        }
    }

    private var nameToken: PebblesFont {
        `case` == .selected ? .subheadEmphasized : .subhead
    }

    private var nameColor: Color {
        `case` == .selected ? Color.accent.primary : Color.system.secondary
    }
}

#Preview("All cases") {
    let sample = SoulWithGlyph(
        id: UUID(),
        name: "Molly",
        glyphId: SystemGlyph.default,
        glyph: Glyph(
            id: SystemGlyph.default,
            name: nil,
            strokes: [
                GlyphStroke(d: "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width: 6)
            ],
            viewBox: "0 0 200 200",
            userId: nil
        )
    )

    return ScrollView {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)],
            spacing: Spacing.lg
        ) {
            SoulItem(case: .selected,   soul: sample, count: 12)
            SoulItem(case: .unselected, soul: sample, count: 9)
            SoulItem(case: .default,    soul: sample, count: 3)
            SoulItem(case: .create,     soul: nil,   count: nil)
        }
        .padding(Spacing.lg)
    }
}
```

No naming ambiguity in the preview: `Glyph(...)` resolves to the model, `GlyphView(...)` (used inside `SoulItem.body`) resolves to the view.

- [ ] **Step 5.3 — Localize `"New soul"` and `"All my souls"`**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode. If `New soul` is not already a key (it might be — `CreateSoulSheet` may use it), add both keys:

| Key | en | fr |
|---|---|---|
| `New soul` | New soul | Nouvelle âme |
| `All my souls` | All my souls | Toutes mes âmes |

Confirm both rows show `Translated` state (not `New` or `Stale`) in both `en` and `fr` columns before continuing.

- [ ] **Step 5.4 — Rewrite `SoulPickerSheet.swift`**

Open `apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift`. Replace the entire file content with:

```swift
import SwiftUI
import os

/// Multi-select sheet for tagging a pebble with souls. Shown from
/// `SelectedSoulsRow` inside `PebbleFormView`. Loads its own souls via
/// `SupabaseService` so the form doesn't need to refetch when an inline
/// `+ New` insert happens.
///
/// Selection rule (see issue #459):
/// - If no soul is currently selected, all rows render as `.default`.
/// - As soon as one or more souls are selected, selected rows render as
///   `.selected` and every other soul renders as `.unselected`.
/// - The `.create` tile is always rendered the same; selection does not
///   affect it.
struct SoulPickerSheet: View {
    let currentSelection: [UUID]
    let onConfirm: ([UUID]) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var souls: [SoulWithGlyph] = []
    @State private var selection: Set<UUID> = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-form.souls")

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)]

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Choose souls")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") {
                            onConfirm(Array(selection))
                            dismiss()
                        }
                    }
                }
                .pebblesScreen()
                .task { await load() }
                .sheet(isPresented: $isPresentingCreate) {
                    CreateSoulSheet { inserted in
                        souls.append(inserted)
                        selection.insert(inserted.id)
                    }
                }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            ContentUnavailableView(
                "Couldn't load souls",
                systemImage: "exclamationmark.triangle",
                description: Text(loadError)
            )
        } else {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: Spacing.lg) {
                    Text("All my souls")
                        .pebblesFont(.cardHeading)
                        .foregroundStyle(Color.system.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    LazyVGrid(columns: columns, spacing: Spacing.lg) {
                        SoulItem(case: .create, soul: nil, count: nil) {
                            isPresentingCreate = true
                        }
                        ForEach(souls) { soul in
                            SoulItem(
                                case: itemCase(for: soul.id),
                                soul: soul,
                                count: nil
                            ) {
                                toggle(soul.id)
                            }
                        }
                    }

                    if souls.isEmpty {
                        Text("Add the first soul to tag this pebble with")
                            .pebblesFont(.callout)
                            .foregroundStyle(Color.system.secondary)
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(Spacing.lg)
            }
        }
    }

    private func itemCase(for id: UUID) -> SoulItem.Case {
        if selection.isEmpty { return .default }
        return selection.contains(id) ? .selected : .unselected
    }

    private func toggle(_ id: UUID) {
        if selection.contains(id) {
            selection.remove(id)
        } else {
            selection.insert(id)
        }
    }

    private func load() async {
        selection = Set(currentSelection)
        isLoading = true
        loadError = nil
        do {
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box)")
                .order("name", ascending: true)
                .execute()
                .value
            self.souls = result
        } catch {
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }
}
```

(The previously private `NewSoulTile` struct is gone — replaced by `SoulItem(case: .create, …)`.)

- [ ] **Step 5.5 — Rewrite `SoulsListView.swift`**

Open `apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift`. Replace the `content` view-builder (current lines 74–118) with the version below; leave everything else (`task { await load() }`, `confirmationDialog`, `alert`, `load`, `delete`) untouched.

Replace:

```swift
@ViewBuilder
private var content: some View {
    if isLoading {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    } else if let loadError {
        ContentUnavailableView(
            "Couldn't load souls",
            systemImage: "exclamationmark.triangle",
            description: Text(loadError)
        )
    } else if items.isEmpty {
        ContentUnavailableView(
            "No souls yet",
            systemImage: "person.2",
            description: Text("People and beings you tag on your pebbles will appear here.")
        )
    } else {
        ScrollView {
            LazyVGrid(columns: columns, spacing: 16) {
                ForEach(items) { item in
                    NavigationLink {
                        SoulDetailView(initial: item, onChanged: {
                            Task {
                                await load()
                                await refs.refreshSouls()
                            }
                        })
                    } label: {
                        SoulGridCell(soul: item)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button(role: .destructive) {
                            pendingDeletion = item
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .padding()
        }
    }
}
```

With:

```swift
@ViewBuilder
private var content: some View {
    if isLoading {
        ProgressView()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    } else if let loadError {
        ContentUnavailableView(
            "Couldn't load souls",
            systemImage: "exclamationmark.triangle",
            description: Text(loadError)
        )
    } else if items.isEmpty {
        ContentUnavailableView(
            "No souls yet",
            systemImage: "person.2",
            description: Text("People and beings you tag on your pebbles will appear here.")
        )
    } else {
        ScrollView {
            LazyVGrid(columns: columns, spacing: Spacing.lg) {
                ForEach(items) { item in
                    NavigationLink {
                        SoulDetailView(initial: item, onChanged: {
                            Task {
                                await load()
                                await refs.refreshSouls()
                            }
                        })
                    } label: {
                        SoulItem(case: .default, soul: item, count: nil)
                    }
                    .buttonStyle(.plain)
                    .contextMenu {
                        Button(role: .destructive) {
                            pendingDeletion = item
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
            .padding(Spacing.lg)
        }
    }
}
```

Also update the `columns` declaration at line 16 from:

```swift
private let columns = [GridItem(.adaptive(minimum: 96), spacing: 16)]
```

To:

```swift
private let columns = [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)]
```

- [ ] **Step 5.6 — Delete `SoulGridCell.swift` and `SoulSelectableCell.swift`**

```bash
rm apps/ios/Pebbles/Features/Profile/Lists/SoulGridCell.swift
rm apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift
```

- [ ] **Step 5.7 — Regenerate + build**

```bash
npm run generate --workspace=@pbbls/ios
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build -quiet
```
Expected: `** BUILD SUCCEEDED **`. If the build fails with "cannot find type 'SoulGridCell' / 'SoulSelectableCell'", grep for residual usages: `grep -rn "SoulGridCell\\|SoulSelectableCell" apps/ios/Pebbles/` should return zero hits.

- [ ] **Step 5.8 — Localization audit**

In Xcode, open `apps/ios/Pebbles/Resources/Localizable.xcstrings`. Confirm:
- Filter "State: New" → 0 results
- Filter "State: Stale" → 0 results
- New keys `"All my souls"` and (if added) `"New soul"` show translated values in both `en` and `fr`.

- [ ] **Step 5.9 — Full visual smoke test in the simulator**

Launch the app and walk through these surfaces in both light **and** dark mode (toggle via simulator → Features → Toggle Appearance):

1. Profile tab → top banner — glyph shows 34-radius continuous border, accent-primary strokes.
2. Profile tab → Settings sheet — same chrome at 120pt.
3. Profile tab → Glyphs list — each cell has 34-radius border, secondary strokes.
4. Profile tab → Glyphs list → toolbar "+" → (no change here, it's the carve sheet).
5. Profile tab → Souls list — each cell renders as `SoulItem(.default)`: 34-radius border with secondary strokes, name in subhead, no count row (until follow-up wires `count`).
6. New pebble → glyph row → glyph picker — currently-selected glyph has 2pt accent border; others 1pt muted; "Carve new glyph" row shows dashed border + scribble icon.
7. New pebble → souls row → soul picker — no souls selected: all `.default`. Tap one → that one becomes `.selected` (accent border, accent name in subhead-emphasized); all others flip to `.unselected` (muted strokes, secondary name). Section header "All my souls" visible at the top, uppercase + spaced.
8. Pebble detail / edit form — glyph row at 32pt now shows `.default` chrome.
9. Edit / Create soul sheets — glyph row at 32pt shows `.default` chrome.
10. Soul detail view — header glyph at 56pt shows `.default` chrome.

Spot-check any surface where the chrome looks wrong against the issue screenshots.

- [ ] **Step 5.10 — Commit**

```bash
git add apps/ios/Pebbles/Features/Shared/SoulItem.swift \
        apps/ios/Pebbles/Features/Path/SoulPickerSheet.swift \
        apps/ios/Pebbles/Features/Profile/Lists/SoulsListView.swift \
        apps/ios/Pebbles/Resources/Localizable.xcstrings
git rm apps/ios/Pebbles/Features/Profile/Lists/SoulGridCell.swift \
       apps/ios/Pebbles/Features/Profile/Lists/SoulSelectableCell.swift
git commit -m "quality(ios): unify soul rendering with SoulItem" -m "Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

## Task 6 — Push branch and open PR

- [ ] **Step 6.1 — Final repo-level checks**

```bash
git log --oneline main..HEAD
```
Expected: exactly 6 commits — 1 spec (`docs(ios): design for #459 …`) and 5 implementation commits in the order above.

```bash
git status
```
Expected: `nothing to commit, working tree clean`.

- [ ] **Step 6.2 — Push the branch**

```bash
git push -u origin quality/459-glyph-souls-consistency
```

- [ ] **Step 6.3 — Open the PR**

Issue labels inherited per repo convention (`bug` → `fix` mapping does not apply; issue has `quality`, `ios`, `ui` already):

```bash
gh pr create \
  --title "quality(ios): unify glyph and soul rendering" \
  --label quality --label ios --label ui \
  --milestone "M32 · iOS Quality" \
  --body "$(cat <<'EOF'
Resolves #459.

## Summary
- Adds a spacing scale rooted on the 17pt body baseline (`Theme/Spacing.swift`).
- Adds 8 typography tokens (Body, Subhead, Headline, Callout, Meta, CardHeading, Title, ButtonLabel) exposed via a `pebblesFont(_:)` view modifier. SF Compact Rounded OTFs bundled. Ysabeau Bold deferred (`title.emphasized` and `buttonLabel.emphasized` not needed by this PR).
- Strips `GlyphThumbnail` chrome — it is now a pure stroke canvas.
- Introduces `GlyphView` view component owning all chrome (continuous/dashed border, plus/scribble overlays, state colors) per #459 spec table. (Named `GlyphView` rather than `Glyph` to avoid a collision with the existing `Glyph` model struct.)
- Introduces `SoulItem` view component (vertical: GlyphView + name + optional fossil.shell + count) shared between `SoulsListView` (`.default`) and `SoulPickerSheet` (state-driven).
- `SoulPickerSheet` enforces the #459 selection rule: 0 selected → all `.default`; ≥1 selected → selected ones `.selected`, others `.unselected`. `.create` is invariant. Adds "All my souls" section header (cardHeading).
- Deletes `SoulGridCell` and `SoulSelectableCell`.
- Migrates the seven remaining `GlyphThumbnail` chrome-relying sites to `GlyphView(case: ...)`.

## Out of scope (follow-ups)
- Wiring per-soul ripple/pebble count into the data layer (`SoulItem.count` accepts `Int?`, currently passed as `nil`).
- "Frequently linked" section in `SoulPickerSheet` (deferred per issue).
- Ysabeau Bold registration for `title.emphasized` / `buttonLabel.emphasized` (deferred; neither needed here).
- Sweeping codebase to migrate every `.padding(16)` to the spacing scale.

## Test plan
- [ ] Profile glyph (banner + Settings) renders 34-radius continuous border with accent-primary strokes, light + dark.
- [ ] Glyphs list cells render 34-radius border + secondary strokes; "Carve new glyph" row shows dashed border + scribble.
- [ ] Glyph picker: currently-selected glyph shows 2pt accent border; others 1pt muted.
- [ ] Souls list cells render as `.default`.
- [ ] Soul picker: 0 selected → all `.default`; one selected → that one `.selected`, others `.unselected`. Section header "All my souls" visible.
- [ ] PebbleFormView, EditSoulSheet, CreateSoulSheet, SoulDetailView glyph rows render with the new `.default` chrome.
- [ ] `Localizable.xcstrings` has no `New` / `Stale` keys; "All my souls" and "New soul" translated in `en` and `fr`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Capture the PR URL from the output and report it back.

---

## Self-Review

**Spec coverage:**
- §1 (typography tokens) → Task 2 ✅
- §2 (Glyph component + GlyphThumbnail refactor + migration) → Tasks 3 & 4 ✅
- §3 (SoulItem + SoulPickerSheet rule + SoulsListView rewrite) → Task 5 ✅
- §4 (spacing scale) → Task 1 ✅
- Migration & PR plan (5 commits + branch name + PR title + labels + milestone) → Tasks 1–6 ✅
- Testing & verification (per-surface visual check, light + dark, lint/build, localization audit) → Step 5.9 + Step 5.8 ✅

**Placeholder scan:** No "TBD", "implement later", or vague "add appropriate handling" instructions remain. Every code step shows full code. The one user-action step (Step 2.1 — download SF Compact Rounded TTFs from Apple) is concretely sourced with a fallback path documented inline.

**Type consistency:**
- `Glyph` view's `Case` enum cases (`profile/carve/create/selected/unselected/default`) are spelled identically in Tasks 4.1 and 5.2's `glyphCase` mapping. ✅
- `SoulItem.Case` cases (`selected/unselected/default/create`) are spelled identically in Tasks 5.2, 5.4, and 5.5. ✅
- `PebblesFont` cases (`subhead/subheadEmphasized/meta/cardHeading/callout`) are spelled identically in Task 2.5 and consumed in Tasks 5.2 and 5.4. ✅
- The model/view `Glyph` collision is called out and resolved with `GlyphView` qualification in Step 4.9, Step 4.10, Step 4.11, and Step 5.2. Cross-checked: every Task-4 and Task-5 site that references the new view in a file where the model is in scope uses the qualified form.

**Known risk preserved from spec:** if SF Compact Rounded TTFs cannot be obtained, Step 2.1 documents the fallback (use `Font.system(design: .rounded)` for `meta` / `cardHeading`); the `sfCompactRounded` helper in Step 2.5 already falls back automatically via `UIFont(name:)` returning `nil`.

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-17-issue-459-glyph-souls-consistency-plan.md`.** Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
