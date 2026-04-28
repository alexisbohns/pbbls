# iOS — Single Pebble Read View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current "tap-to-edit" pebble surface with a designed read view, used for both path-list taps and the post-create reveal. Edit moves behind an Edit button inside the read view.

**Architecture:** Rewrite `PebbleDetailSheet` to render a new `PebbleReadView` (composed of small focused subviews under `Features/Path/Read/`). Reuse the existing `SoulWithGlyph` model so embedded soul-glyph data flows through `PebbleDetail`. Stacked sheet for edit. Collapse the two presentation states in `PebbleView` into one.

**Tech Stack:** SwiftUI (iOS 17+), Swift 5.9, Swift Testing, Supabase (PostgREST embeds), `xcodegen`, Tailwind-equivalent design tokens via asset catalog.

**Spec:** `docs/superpowers/specs/2026-04-28-ios-pebble-read-view-design.md`
**Issue:** [#329](https://github.com/) — `[Feat] Single pebble read view`

---

## File Structure

**New:**
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift` — body of the read view (ScrollView + sections); pure UI, takes a `PebbleDetail`.
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadHeader.swift` — render + Ysabeau title + uppercase tracked date label.
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadPicture.swift` — rounded full-width image that respects natural aspect ratio. Wraps `SnapImageView`.
- `apps/ios/Pebbles/Features/Path/Read/PebbleMetadataRow.swift` — boxed-icon + label row with three style variants (`.unset`, `.set`, `.emotion(color)`).
- `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift` — top-bar custom rounded lock+label badge.
- `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSoft.colorset/Contents.json` — light/dark "blush" accent-soft color.
- `apps/ios/PebblesTests/PebbleDetailSoulGlyphDecodingTests.swift` — Swift Testing for the new embed shape.

**Modified:**
- `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift` — extend SELECT, render `PebbleReadView`, add stacked Edit sheet, add `onPebbleUpdated` callback.
- `apps/ios/Pebbles/Features/Path/PathView.swift` — collapse `selectedPebbleId` + `presentedDetailPebbleId` into one; route both through `PebbleDetailSheet`.
- `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift` — change `souls` field from `[Soul]` to `[SoulWithGlyph]`; update inner `SoulWrapper`.
- `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` — extend its detail SELECT to include the embedded glyph (so its `PebbleDetail` decode keeps working).
- `apps/ios/Pebbles/Features/Path/Models/Visibility.swift` — convert `label` from `String` to `LocalizedStringResource`.
- `apps/ios/Pebbles/Theme/Color+Pebbles.swift` — add `Color.pebblesAccentSoft`.
- `apps/ios/project.yml` — register `Ysabeau SemiBold.ttf` resource.
- `apps/ios/Pebbles/Resources/Info.plist` — add `UIAppFonts`.
- `apps/ios/Pebbles/Resources/Localizable.xcstrings` — add `Private`, `Public`, `No domain`, `Pebble photo`, soul/edit accessibility strings.
- `docs/arkaik/bundle.json` — split detail vs edit nodes (via the project's `arkaik` skill).

---

## Task 0: Create feature branch

**Files:**
- (no files yet — branch only)

- [ ] **Step 1: Verify clean tree on main**

```bash
git status
```

Expected: `nothing to commit, working tree clean`. Current branch should be `main`. The spec doc commit (`docs(ios): spec for single pebble read view (#329)`) is already on `main` and stays there.

- [ ] **Step 2: Create the feature branch**

```bash
git checkout -b feat/329-pebble-read-view
```

Expected: `Switched to a new branch 'feat/329-pebble-read-view'`. All subsequent task commits land on this branch.

---

## Task 1: Register Ysabeau SemiBold font

**Files:**
- Modify: `apps/ios/project.yml`
- Modify: `apps/ios/Pebbles/Resources/Info.plist`
- Verify: `apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf` (already in repo)

- [ ] **Step 1: Verify the PostScript name of the font**

Run:

```bash
fc-scan --format '%{postscriptname}\n' "/Users/alexis/code/pbbls/apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf"
```

If `fc-scan` is not installed, use Python instead:

```bash
/usr/bin/python3 -c "from CoreText import CTFontManagerCreateFontDescriptorsFromURL; from Foundation import NSURL; url=NSURL.fileURLWithPath_('/Users/alexis/code/pbbls/apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf'); descs=CTFontManagerCreateFontDescriptorsFromURL(url); print(descs[0].objectForAttribute_('NSFontNameAttribute'))"
```

If neither works, open the file in macOS Font Book (`open "apps/ios/Pebbles/Resources/Ysabeau SemiBold.ttf"`) and read the PostScript name from the inspector.

Record the exact name (likely `Ysabeau-SemiBold`). It will be used in `Font.custom(...)` in Task 8 and in source-code comments.

- [ ] **Step 2: Add `UIAppFonts` to Info.plist**

Edit `apps/ios/Pebbles/Resources/Info.plist`. Insert directly before the closing `</dict>`:

```xml
	<key>UIAppFonts</key>
	<array>
		<string>Ysabeau SemiBold.ttf</string>
	</array>
```

`UIAppFonts` references the **file name**, not the PostScript name.

- [ ] **Step 3: Add the font as a resource in `project.yml`**

Edit `apps/ios/project.yml`. The `Pebbles` target currently has `sources: - path: Pebbles`. xcodegen treats `.ttf` files inside `sources` as build-copied resources by default *only* when they are recognized as resource extensions. To be explicit, change the target's sources block to include a `resources` declaration alongside:

```yaml
  Pebbles:
    type: application
    platform: iOS
    deploymentTarget: "17.0"
    sources:
      - path: Pebbles
        excludes:
          - "Resources/Ysabeau SemiBold.ttf"
    resources:
      - path: Pebbles/Resources/Ysabeau SemiBold.ttf
```

(Excluding from `sources` and re-adding under `resources` ensures the font is copied to the bundle even if xcodegen's auto-classification is uncertain.)

- [ ] **Step 4: Regenerate the Xcode project**

```bash
cd apps/ios && xcodegen generate
```

Expected: `Generated project successfully`. The Xcode project is git-ignored — generation only updates the local working copy.

- [ ] **Step 5: Build to confirm the font loads**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **`. (If the simulator name doesn't exist, list available simulators with `xcrun simctl list devices` and substitute one.)

- [ ] **Step 6: Commit**

```bash
git add apps/ios/project.yml apps/ios/Pebbles/Resources/Info.plist
git commit -m "feat(core): register Ysabeau SemiBold font in iOS app"
```

---

## Task 2: Add `Color.pebblesAccentSoft` token

**Files:**
- Create: `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSoft.colorset/Contents.json`
- Modify: `apps/ios/Pebbles/Theme/Color+Pebbles.swift`

- [ ] **Step 1: Create the color set**

Create the directory and file:

```bash
mkdir -p "apps/ios/Pebbles/Resources/Assets.xcassets/AccentSoft.colorset"
```

Create `apps/ios/Pebbles/Resources/Assets.xcassets/AccentSoft.colorset/Contents.json` with light/dark blush values (light: a pale blush ~#F5E6E2; dark: a desaturated muted version ~#3A2E2C):

```json
{
  "colors" : [
    {
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0xE2",
          "green" : "0xE6",
          "red" : "0xF5"
        }
      },
      "idiom" : "universal"
    },
    {
      "appearances" : [
        {
          "appearance" : "luminosity",
          "value" : "dark"
        }
      ],
      "color" : {
        "color-space" : "srgb",
        "components" : {
          "alpha" : "1.000",
          "blue" : "0x2C",
          "green" : "0x2E",
          "red" : "0x3A"
        }
      },
      "idiom" : "universal"
    }
  ],
  "info" : {
    "author" : "xcode",
    "version" : 1
  }
}
```

- [ ] **Step 2: Add the Swift accessor**

Edit `apps/ios/Pebbles/Theme/Color+Pebbles.swift`. Add this line under `pebblesAccent`:

```swift
    static let pebblesAccentSoft      = Color("AccentSoft")
```

The full `Color` extension top should now read:

```swift
extension Color {
    static let pebblesBackground      = Color("Background")
    static let pebblesForeground      = Color("Foreground")
    static let pebblesSurface         = Color("Surface")
    static let pebblesSurfaceAlt      = Color("SurfaceAlt")
    static let pebblesMuted           = Color("Muted")
    static let pebblesMutedForeground = Color("MutedForeground")
    static let pebblesBorder          = Color("Border")
    static let pebblesAccent          = Color("AccentColor")
    static let pebblesAccentSoft      = Color("AccentSoft")
    // ... pebblesListRow unchanged
```

- [ ] **Step 3: Build to confirm the asset resolves**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. A missing color asset would produce a runtime warning but build still succeeds; verify the JSON parses by running `/usr/bin/python3 -c "import json; json.load(open('apps/ios/Pebbles/Resources/Assets.xcassets/AccentSoft.colorset/Contents.json'))"` — must exit 0.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Resources/Assets.xcassets/AccentSoft.colorset/Contents.json apps/ios/Pebbles/Theme/Color+Pebbles.swift
git commit -m "feat(ui): add pebblesAccentSoft blush token"
```

---

## Task 3: Localize `Visibility.label`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Visibility.swift`
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings` (Xcode auto-extracts at build time, but we add `fr` translations manually)

- [ ] **Step 1: Convert `label` to `LocalizedStringResource`**

Replace `apps/ios/Pebbles/Features/Path/Models/Visibility.swift` body so `label` returns `LocalizedStringResource`:

```swift
import Foundation

enum Visibility: String, CaseIterable, Identifiable, Hashable, Decodable {
    case `private` = "private"
    case `public` = "public"

    var id: String { rawValue }

    var label: LocalizedStringResource {
        switch self {
        case .private: return "Private"
        case .public:  return "Public"
        }
    }
}
```

- [ ] **Step 2: Build (auto-extracts strings into `Localizable.xcstrings`)**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. The build pass extracts new keys (`Private`, `Public`) into `Localizable.xcstrings` because `SWIFT_EMIT_LOC_STRINGS=YES` is set in `project.yml`.

- [ ] **Step 3: Add the French translations**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode (`open apps/ios/Pebbles/Resources/Localizable.xcstrings`). For each new entry (`Private`, `Public`), fill in the `fr` column:

| Key | en | fr |
| --- | --- | --- |
| `Private` | `Private` | `Privé` |
| `Public` | `Public` | `Public` |

Verify both rows show state `Translated` (not `New` or `Stale`).

- [ ] **Step 4: Build once more to confirm xcstrings is clean**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **` with no `Stale` warnings.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Visibility.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(core): localize Visibility label as LocalizedStringResource"
```

---

## Task 4: Decode embedded soul glyph in `PebbleDetail`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`
- Create: `apps/ios/PebblesTests/PebbleDetailSoulGlyphDecodingTests.swift`

- [ ] **Step 1: Write the failing decoding test**

Create `apps/ios/PebblesTests/PebbleDetailSoulGlyphDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("PebbleDetail decoding with embedded soul glyph")
struct PebbleDetailSoulGlyphDecodingTests {

    private let pebbleId = UUID(uuidString: "22222222-2222-2222-2222-222222222222")!
    private let emotionId = UUID(uuidString: "33333333-3333-3333-3333-333333333333")!
    private let soulId = UUID(uuidString: "11111111-1111-1111-1111-111111111111")!
    private let glyphId = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!

    @Test("decodes a pebble whose soul includes a joined glyph")
    func decodesPebbleWithSoulGlyph() throws {
        let json = Data("""
        {
          "id": "\(pebbleId.uuidString)",
          "name": "Test pebble",
          "description": null,
          "happened_at": "2026-04-28T10:00:00Z",
          "intensity": 2,
          "positiveness": 1,
          "visibility": "private",
          "render_svg": null,
          "render_version": null,
          "glyph_id": null,
          "emotion": {
            "id": "\(emotionId.uuidString)",
            "slug": "joy",
            "name": "Joy",
            "color": "#FFCC00"
          },
          "pebble_domains": [],
          "pebble_souls": [
            {
              "soul": {
                "id": "\(soulId.uuidString)",
                "name": "Alex",
                "glyph_id": "\(glyphId.uuidString)",
                "glyphs": {
                  "id": "\(glyphId.uuidString)",
                  "name": null,
                  "strokes": [{"d": "M0,0 L10,10", "width": 6}],
                  "view_box": "0 0 200 200"
                }
              }
            }
          ],
          "collection_pebbles": [],
          "snaps": []
        }
        """.utf8)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let detail = try decoder.decode(PebbleDetail.self, from: json)

        #expect(detail.souls.count == 1)
        #expect(detail.souls[0].id == soulId)
        #expect(detail.souls[0].name == "Alex")
        #expect(detail.souls[0].glyph.id == glyphId)
        #expect(detail.souls[0].glyph.strokes.count == 1)
        #expect(detail.souls[0].glyph.strokes.first?.d == "M0,0 L10,10")
    }

    @Test("decodes a pebble with zero souls")
    func decodesPebbleWithoutSouls() throws {
        let json = Data("""
        {
          "id": "\(pebbleId.uuidString)",
          "name": "No souls",
          "description": null,
          "happened_at": "2026-04-28T10:00:00Z",
          "intensity": 2,
          "positiveness": 0,
          "visibility": "private",
          "render_svg": null,
          "render_version": null,
          "glyph_id": null,
          "emotion": {
            "id": "\(emotionId.uuidString)",
            "slug": "calm",
            "name": "Calm",
            "color": "#88AACC"
          },
          "pebble_domains": [],
          "pebble_souls": [],
          "collection_pebbles": [],
          "snaps": []
        }
        """.utf8)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let detail = try decoder.decode(PebbleDetail.self, from: json)

        #expect(detail.souls.isEmpty)
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleDetailSoulGlyphDecodingTests 2>&1 | tail -30
```

Expected: tests fail with a decoding error mentioning the missing `glyphs` key — `PebbleDetail.souls` is currently `[Soul]`, and `Soul` doesn't decode the `glyphs` nested object. The first test (`decodesPebbleWithSoulGlyph`) fails on the line `#expect(detail.souls[0].glyph.id == glyphId)` because `Soul` has no `glyph` property.

If both tests pass unexpectedly, the wrapper is silently dropping the joined glyph — confirm by adding `print(detail.souls[0])` and re-running.

- [ ] **Step 3: Update `PebbleDetail` to use `SoulWithGlyph`**

In `apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift`:

Change the stored field type:

```swift
    let souls: [SoulWithGlyph]
```

Change the inner wrapper to decode `SoulWithGlyph` instead of `Soul`:

```swift
    private struct SoulWrapper: Decodable { let soul: SoulWithGlyph }
```

The `init(from:)` line that maps the wrappers stays unchanged because it just calls `\.soul`:

```swift
        let soulWrappers = try container.decodeIfPresent([SoulWrapper].self, forKey: .pebbleSouls) ?? []
        self.souls = soulWrappers.map(\.soul)
```

No other lines in `PebbleDetail.swift` need to change.

- [ ] **Step 4: Run the test to confirm it passes**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/PebbleDetailSoulGlyphDecodingTests 2>&1 | tail -30
```

Expected: both tests pass.

- [ ] **Step 5: Run the full test suite to catch unintended breakage**

```bash
cd apps/ios && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -30
```

Expected: all tests pass. `PebbleDraft.init(from:)` still works because it reads `detail.souls.first?.id` — `SoulWithGlyph` has `id` just like `Soul` did.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDetail.swift apps/ios/PebblesTests/PebbleDetailSoulGlyphDecodingTests.swift
git commit -m "feat(core): decode joined soul glyph in PebbleDetail"
```

---

## Task 5: Extend SELECTs to embed the soul glyph

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

- [ ] **Step 1: Update the SELECT in `PebbleDetailSheet.load()`**

In `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`, find the existing select string:

```swift
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version,
                    emotion:emotions(id, slug, name, color),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id)),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
```

Replace it with the version that embeds the glyph and includes `glyph_id` (used by Edit):

```swift
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    emotion:emotions(id, slug, name, color),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id, glyphs(id, name, strokes, view_box))),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
```

(`glyph_id` was already in `EditPebbleSheet`'s select but missing from `PebbleDetailSheet`'s — adding it now is a free fix that aligns the two queries.)

- [ ] **Step 2: Update the SELECT in `EditPebbleSheet.load()`**

In `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`, find:

```swift
            async let detailQuery: PebbleDetail = supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    emotion:emotions(id, slug, name, color),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id)),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
```

Replace the `pebble_souls(...)` line with:

```swift
                    pebble_souls(soul:souls(id, name, glyph_id, glyphs(id, name, strokes, view_box))),
```

The rest of the SELECT stays unchanged.

- [ ] **Step 3: Build to confirm no regressions**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "feat(core): embed soul glyph in pebble detail SELECTs"
```

---

## Task 6: Build `PebbleMetadataRow`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebbleMetadataRow.swift`

iOS UI views are not unit-tested (per `apps/ios/CLAUDE.md`: "No UI tests for now"). Verification per task is: build succeeds + visual check via SwiftUI Preview.

- [ ] **Step 1: Create the file**

Create `apps/ios/Pebbles/Features/Path/Read/PebbleMetadataRow.swift`:

```swift
import SwiftUI

/// Horizontal row used in the pebble read view: a boxed leading icon followed
/// by a label. Three style variants drive how the icon box is filled and
/// whether the row reads as "set", "unset" (dashed border, muted), or
/// "emotion" (filled with the emotion's color, white icon).
///
/// Reused by emotion, domain, collections, and each soul row.
struct PebbleMetadataRow: View {
    enum Icon {
        case system(String)
        case glyph(Glyph)
    }

    enum Style {
        case unset
        case set
        case emotion(color: Color)
    }

    let icon: Icon
    let label: LocalizedStringResource
    let style: Style

    private var accessibilityValue: Text? {
        switch style {
        case .unset: return Text("Not set", comment: "Accessibility value for an unset pebble metadata row")
        case .set, .emotion: return nil
        }
    }

    var body: some View {
        HStack(spacing: 12) {
            iconBox
            Text(label)
                .font(.body)
                .foregroundStyle(labelForeground)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var iconBox: some View {
        ZStack {
            backgroundShape
            iconContent
                .foregroundStyle(iconForeground)
                .frame(width: 18, height: 18)
        }
        .frame(width: 36, height: 36)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var backgroundShape: some View {
        switch style {
        case .unset:
            RoundedRectangle(cornerRadius: 8)
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1, dash: [3])
                )
        case .set:
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.pebblesAccentSoft)
        case .emotion(let color):
            RoundedRectangle(cornerRadius: 8)
                .fill(color)
        }
    }

    @ViewBuilder
    private var iconContent: some View {
        switch icon {
        case .system(let name):
            Image(systemName: name)
                .resizable()
                .scaledToFit()
        case .glyph(let glyph):
            GlyphThumbnail(
                strokes: glyph.strokes,
                side: 18,
                strokeColor: iconForeground,
                backgroundColor: .clear
            )
        }
    }

    private var iconForeground: Color {
        switch style {
        case .unset:   return Color.pebblesMutedForeground
        case .set:     return Color.pebblesAccent
        case .emotion: return .white
        }
    }

    private var labelForeground: Color {
        switch style {
        case .unset:           return Color.pebblesMutedForeground
        case .set, .emotion:   return Color.pebblesForeground
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 16) {
        PebbleMetadataRow(
            icon: .system("heart.fill"),
            label: "Joy",
            style: .emotion(color: Color(red: 1.0, green: 0.8, blue: 0.0))
        )
        PebbleMetadataRow(
            icon: .system("leaf.fill"),
            label: "Family, Travel",
            style: .set
        )
        PebbleMetadataRow(
            icon: .system("sparkles"),
            label: "No domain",
            style: .unset
        )
        PebbleMetadataRow(
            icon: .glyph(Glyph(
                id: UUID(),
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 12)],
                viewBox: "0 0 200 200",
                userId: nil
            )),
            label: "Alex",
            style: .set
        )
    }
    .padding()
    .background(Color.pebblesBackground)
}
```

- [ ] **Step 2: Build and verify the preview compiles**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. Open the file in Xcode and check the canvas preview renders the four variants correctly:
- Joy row: orange-yellow filled icon box with white heart, "Joy" in primary text.
- Family/Travel row: blush filled box with accent-colored leaf, "Family, Travel" in primary text.
- No domain row: dashed empty box, "No domain" in muted text.
- Alex row: blush filled box with the diagonal-stroke glyph, "Alex" in primary text.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleMetadataRow.swift
git commit -m "feat(ui): add PebbleMetadataRow for pebble read view"
```

---

## Task 7: Build `PebblePrivacyBadge`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift`

- [ ] **Step 1: Create the file**

```swift
import SwiftUI

/// Top-bar badge showing the pebble's privacy status: a rounded capsule with a
/// lock icon + the visibility label. Border, no fill — sits on top of the
/// navigation bar, paired visually with the native Edit button on the trailing
/// side.
struct PebblePrivacyBadge: View {
    let visibility: Visibility

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "lock.fill")
                .font(.caption)
                .accessibilityHidden(true)
            Text(visibility.label)
                .font(.caption)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .overlay(
            Capsule()
                .strokeBorder(Color.pebblesBorder, lineWidth: 1)
        )
        .foregroundStyle(Color.pebblesForeground)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("Privacy: \(String(localized: visibility.label))",
                                 comment: "Accessibility label for the pebble privacy badge"))
    }
}

#Preview {
    VStack(spacing: 12) {
        PebblePrivacyBadge(visibility: .private)
        PebblePrivacyBadge(visibility: .public)
    }
    .padding()
}
```

- [ ] **Step 2: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. Visual: capsule with thin border and lock + "Private" / "Public" inside.

- [ ] **Step 3: Add the French translation for the new accessibility key**

Build emitted a new key (`Privacy: %@`). Open `Localizable.xcstrings` in Xcode and add the French translation:

| Key | en | fr |
| --- | --- | --- |
| `Privacy: %@` | `Privacy: %@` | `Confidentialité : %@` |

| Key | en | fr |
| --- | --- | --- |
| `Not set` | `Not set` | `Non défini` |

(Note: `Not set` was emitted by Task 6's `PebbleMetadataRow`. Add it now too if not already filled.)

Verify all entries show state `Translated`.

- [ ] **Step 4: Build once more after xcstrings edit**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebblePrivacyBadge.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ui): add PebblePrivacyBadge for pebble read view"
```

---

## Task 8: Build `PebbleReadHeader`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebbleReadHeader.swift`

- [ ] **Step 1: Create the file**

Replace `<PostScriptName>` below with the verified PostScript name from Task 1, Step 1 (likely `Ysabeau-SemiBold`):

```swift
import SwiftUI

/// Header section of the pebble read view: the rendered pebble shape, the
/// title in Ysabeau SemiBold, and a uppercased tracked date label below.
///
/// Font: Ysabeau SemiBold is registered via `UIAppFonts` in Info.plist
/// (file `Ysabeau SemiBold.ttf`, PostScript name "Ysabeau-SemiBold").
struct PebbleReadHeader: View {
    let detail: PebbleDetail

    var body: some View {
        VStack(spacing: 16) {
            if let svg = detail.renderSvg {
                PebbleRenderView(svg: svg, strokeColor: detail.emotion.color)
                    .frame(maxWidth: .infinity)
                    .frame(height: detail.valence.sizeGroup.renderHeight)
            }
            VStack(spacing: 8) {
                Text(detail.name)
                    .font(.custom("Ysabeau-SemiBold", size: 34))
                    .multilineTextAlignment(.center)
                    .foregroundStyle(Color.pebblesForeground)
                Text(formattedDate)
                    .font(.caption)
                    .tracking(1.2)
                    .textCase(.uppercase)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var formattedDate: String {
        // Locale-aware. Example en output: "MON, MAR 12, 2026 · 2:32 PM"
        // .textCase(.uppercase) handles the casing visually so we only need
        // a clean, locale-correct format here.
        let date = detail.happenedAt.formatted(
            .dateTime
                .weekday(.abbreviated)
                .month(.abbreviated)
                .day()
                .year()
        )
        let time = detail.happenedAt.formatted(.dateTime.hour().minute())
        return "\(date) · \(time)"
    }
}
```

- [ ] **Step 2: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. If the build emits a warning about `Ysabeau-SemiBold` not loading, the PostScript name is wrong — re-verify via Task 1's font inspection step.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadHeader.swift
git commit -m "feat(ui): add PebbleReadHeader with Ysabeau title"
```

---

## Task 9: Build `PebbleReadPicture`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebbleReadPicture.swift`

- [ ] **Step 1: Create the file**

```swift
import SwiftUI

/// Rounded full-width photo for the pebble read view. Uses the existing
/// `SnapImageView` for image loading and respects the imported file's
/// natural aspect ratio (no forced cropping).
struct PebbleReadPicture: View {
    let storagePath: String

    var body: some View {
        SnapImageView(storagePath: storagePath)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .accessibilityLabel(Text("Pebble photo",
                                     comment: "Accessibility label for the photo attached to a pebble"))
    }
}
```

- [ ] **Step 2: Verify `SnapImageView` does not force an aspect ratio**

Read `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift`. If `SnapImageView` already calls `.aspectRatio(contentMode: .fill)` or applies a fixed frame, **do not** wrap it again — the natural ratio comes from the underlying `Image` view's intrinsic size.

If `SnapImageView` already enforces a non-natural ratio in a way the read view shouldn't inherit (e.g. it always crops to a square thumbnail), open the file and verify before continuing. Expected current behavior is "fit-to-width with natural height" — most existing snap renderings show full snaps.

If the existing modifier is `.aspectRatio(contentMode: .fit)` or no modifier, the wrapper above is correct and natural ratio is preserved.

- [ ] **Step 3: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 4: Add the French translation for `Pebble photo`**

Open `Localizable.xcstrings` in Xcode:

| Key | en | fr |
| --- | --- | --- |
| `Pebble photo` | `Pebble photo` | `Photo du pebble` |

Verify state `Translated`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadPicture.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ui): add PebbleReadPicture component"
```

---

## Task 10: Build `PebbleReadView`

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`

- [ ] **Step 1: Create the file**

```swift
import SwiftUI

/// Body of the pebble read view. Pure UI — receives a fully-loaded
/// `PebbleDetail` and lays out the sections per spec
/// `docs/superpowers/specs/2026-04-28-ios-pebble-read-view-design.md`.
///
/// The `PebbleDetailSheet` wraps this view with the navigation bar (privacy
/// badge + edit button) and handles loading/error states.
struct PebbleReadView: View {
    let detail: PebbleDetail

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                PebbleReadHeader(detail: detail)
                    .padding(.top, 8)

                if let firstSnap = detail.snaps.first {
                    PebbleReadPicture(storagePath: firstSnap.storagePath)
                }

                metadataBlock

                if let description = detail.description, !description.isEmpty {
                    Text(description)
                        .font(.system(size: 17, weight: .regular, design: .serif))
                        .foregroundStyle(Color.pebblesForeground)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 32)
        }
        .background(Color.pebblesBackground)
    }

    @ViewBuilder
    private var metadataBlock: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Emotion — always rendered.
            PebbleMetadataRow(
                icon: .system("heart.fill"),
                label: LocalizedStringResource(stringLiteral: detail.emotion.localizedName),
                style: .emotion(color: Color(hex: detail.emotion.color) ?? Color.pebblesAccent)
            )

            // Domain — always rendered. Set if non-empty, otherwise dashed.
            if detail.domains.isEmpty {
                PebbleMetadataRow(
                    icon: .system("square.grid.2x2"),
                    label: "No domain",
                    style: .unset
                )
            } else {
                PebbleMetadataRow(
                    icon: .system("square.grid.2x2"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.domains.map(\.localizedName).joined(separator: ", ")
                    ),
                    style: .set
                )
            }

            // Collections — only when non-empty.
            if !detail.collections.isEmpty {
                PebbleMetadataRow(
                    icon: .system("folder.fill"),
                    label: LocalizedStringResource(
                        stringLiteral: detail.collections.map(\.name).joined(separator: ", ")
                    ),
                    style: .set
                )
            }

            // Souls — only when non-empty. One row per soul.
            ForEach(detail.souls) { soulWithGlyph in
                PebbleMetadataRow(
                    icon: .glyph(soulWithGlyph.glyph),
                    label: LocalizedStringResource(stringLiteral: soulWithGlyph.name),
                    style: .set
                )
            }
        }
    }
}

// MARK: - Hex color helper

/// Parses `#RRGGBB` strings stored on `EmotionRef.color`. Falls back to
/// `nil` if the format is unexpected — caller decides on a default.
private extension Color {
    init?(hex: String) {
        var trimmed = hex.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard trimmed.count == 6, let value = UInt32(trimmed, radix: 16) else {
            return nil
        }
        let r = Double((value >> 16) & 0xFF) / 255.0
        let g = Double((value >> 8) & 0xFF) / 255.0
        let b = Double(value & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
```

Note on `LocalizedStringResource(stringLiteral:)`: the metadata row's `label` parameter is `LocalizedStringResource`, but the data we render here (emotion name, comma-joined domains, comma-joined collections, soul name) is **already-resolved user/database content**, not a translation key. Using `stringLiteral:` wraps the runtime string without sending it through the catalog — correct behavior for dynamic content.

- [ ] **Step 2: Verify the `localizedName` properties exist on emotion and domain**

Confirm by reading `apps/ios/Pebbles/Features/Path/Models/Emotion+Localized.swift` and `Domain+Localized.swift`. They exist (used elsewhere) and provide `localizedName: String`. Also confirm `EmotionRef.localizedName` works the same way — read `PebbleDetail+Localized.swift`:

```bash
grep -n "localizedName" apps/ios/Pebbles/Features/Path/Models/PebbleDetail+Localized.swift apps/ios/Pebbles/Features/Path/Models/Emotion+Localized.swift apps/ios/Pebbles/Features/Path/Models/Domain+Localized.swift
```

Expected: `localizedName` is defined on both `EmotionRef` and `DomainRef`. If not on `EmotionRef`, add an extension to `PebbleDetail+Localized.swift` mirroring `Emotion+Localized.swift` (key by slug). Otherwise no change.

- [ ] **Step 3: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. If the build complains about `EmotionRef.localizedName` or `DomainRef.localizedName` missing, fix per Step 2 first.

- [ ] **Step 4: Add the French translation for `No domain`**

Open `Localizable.xcstrings` in Xcode:

| Key | en | fr |
| --- | --- | --- |
| `No domain` | `No domain` | `Aucun domaine` |

Verify state `Translated`.

- [ ] **Step 5: Build once more**

```bash
cd apps/ios && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(ui): add PebbleReadView assembling the pebble read sections"
```

---

## Task 11: Rewrite `PebbleDetailSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift`

- [ ] **Step 1: Replace the entire file**

Replace the file contents with:

```swift
import SwiftUI
import os

/// Read-view sheet for a single pebble. Loads the `PebbleDetail` from
/// Supabase and renders `PebbleReadView` with a privacy badge + Edit button
/// in the navigation bar. Tapping Edit stacks `EditPebbleSheet` on top; on
/// save the read view reloads in place and notifies the parent.
///
/// Used as the destination for both:
/// - Path-list tap of an existing pebble.
/// - Post-create reveal after `CreatePebbleSheet` completes.
struct PebbleDetailSheet: View {
    let pebbleId: UUID
    var onPebbleUpdated: (() -> Void)?

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var detail: PebbleDetail?
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingEdit = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-detail")

    var body: some View {
        NavigationStack {
            content
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        if let detail {
                            PebblePrivacyBadge(visibility: detail.visibility)
                        }
                    }
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Edit") { isPresentingEdit = true }
                            .disabled(detail == nil)
                    }
                }
                .pebblesScreen()
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingEdit) {
            EditPebbleSheet(pebbleId: pebbleId, onSaved: {
                Task { await load() }
                onPebbleUpdated?()
            })
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") { Task { await load() } }
            }
        } else if let detail {
            PebbleReadView(detail: detail)
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let loaded: PebbleDetail = try await supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    emotion:emotions(id, slug, name, color),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id, glyphs(id, name, strokes, view_box))),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value
            self.detail = loaded
            self.isLoading = false
        } catch {
            logger.error("pebble detail load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }
}

#Preview {
    PebbleDetailSheet(pebbleId: UUID())
        .environment(SupabaseService())
}
```

Key changes versus today:
- New optional `onPebbleUpdated` callback.
- Adds `isPresentingEdit` state and a stacked `EditPebbleSheet`.
- Toolbar now has the privacy badge (leading) and the Edit button (trailing). The previous "Done" button is removed — the sheet's swipe-to-dismiss replaces it. (Confirm in QA that swipe-down dismiss still works — sheets dismiss on swipe by default.)
- Replaces the inline ad-hoc body with `PebbleReadView`.
- Removes the unused `Couldn't load this pebble.` localizable extraction concerns by keeping that string in code (it was already a Swift string before).

- [ ] **Step 2: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`. If any localized string keys are emitted (e.g. `Edit` already existed; `Retry` and `Couldn't load this pebble.` likely already exist from before), Xcode will not flag duplicates.

- [ ] **Step 3: Verify French translations cover any new emitted strings**

```bash
/usr/bin/python3 -c "
import json
data = json.load(open('apps/ios/Pebbles/Resources/Localizable.xcstrings'))
strings = data.get('strings', {})
for key in ['Edit', 'Retry', \"Couldn't load this pebble.\"]:
    s = strings.get(key)
    if not s:
        print(f'MISSING: {key}')
    else:
        fr = s.get('localizations', {}).get('fr', {})
        if not fr:
            print(f'NO FR: {key}')
        else:
            print(f'OK:     {key}')
"
```

Expected: `OK:` for each. If any show `MISSING` or `NO FR`, open `Localizable.xcstrings` in Xcode and fill the `fr` column.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PebbleDetailSheet.swift apps/ios/Pebbles/Resources/Localizable.xcstrings
git commit -m "feat(core): rebuild PebbleDetailSheet around PebbleReadView with stacked edit"
```

---

## Task 12: Update `PathView` wiring

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/PathView.swift`

- [ ] **Step 1: Collapse the two presentation states**

In `apps/ios/Pebbles/Features/Path/PathView.swift`:

Remove the `presentedDetailPebbleId` state line:

```swift
    @State private var presentedDetailPebbleId: UUID?
```

(the line after `@State private var selectedPebbleId: UUID?`).

- [ ] **Step 2: Replace the two sheet modifiers with one**

Find the existing block:

```swift
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                presentedDetailPebbleId = newPebbleId
                Task { await load() }
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            EditPebbleSheet(pebbleId: id, onSaved: {
                Task { await load() }
            })
        }
        .sheet(item: $presentedDetailPebbleId) { id in
            PebbleDetailSheet(pebbleId: id)
        }
```

Replace it with:

```swift
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                selectedPebbleId = newPebbleId
                Task { await load() }
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            PebbleDetailSheet(pebbleId: id, onPebbleUpdated: {
                Task { await load() }
            })
        }
```

After the change, both path-list tap (`selectedPebbleId = pebble.id` inside `PebbleRow`'s `onTap`) and the post-create flow write to `selectedPebbleId` and present the same `PebbleDetailSheet`.

- [ ] **Step 3: Verify no references to the removed state remain**

```bash
grep -n "presentedDetailPebbleId" apps/ios/Pebbles/Features/Path/PathView.swift
```

Expected: no output. If any line still references the old state, remove it.

- [ ] **Step 4: Build**

```bash
cd apps/ios && xcodegen generate && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/PathView.swift
git commit -m "feat(core): route both path tap and post-create reveal through PebbleDetailSheet"
```

---

## Task 13: Update Arkaik product map

**Files:**
- Modify: `docs/arkaik/bundle.json` (via the `arkaik` skill)

- [ ] **Step 1: Invoke the `arkaik` skill**

In Claude Code, call `Skill arkaik` with the change description:

> Pebbles iOS — split the previously-merged "pebble detail" surface from "edit pebble". Now: path-list tap and post-create reveal both open the read view (`PebbleDetailSheet` rendering `PebbleReadView`); from there, an Edit button stacks `EditPebbleSheet`. Reflect this as two distinct view nodes with an edge from detail → edit.

The skill walks through schema, surgical update patterns, and runs the validation script. Follow its instructions exactly.

- [ ] **Step 2: Run the bundle validation script**

The arkaik skill includes a validation script. After applying its surgical edit, run it (the skill tells you the exact path/command). Expected: validation passes with no errors.

- [ ] **Step 3: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(core): update arkaik map for split pebble detail/edit surfaces"
```

---

## Task 14: Pre-PR localization sweep

**Files:**
- Verify: `apps/ios/Pebbles/Resources/Localizable.xcstrings`

- [ ] **Step 1: Build clean to ensure all keys are extracted**

```bash
cd apps/ios && xcodebuild clean -scheme Pebbles && xcodebuild -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' build 2>&1 | tail -10
```

Expected: `** BUILD SUCCEEDED **`.

- [ ] **Step 2: Open `Localizable.xcstrings` in Xcode**

```bash
open apps/ios/Pebbles/Resources/Localizable.xcstrings
```

Confirm:
- No row is in state `New` or `Stale`.
- Every row has both `en` and `fr` filled.

The new keys this PR introduces are: `Private`, `Public`, `No domain`, `Pebble photo`, `Privacy: %@`, `Not set`. Existing keys touched: `Edit` (already existed; verify still translated).

- [ ] **Step 3: If anything is missing, fill it**

For any `New` row: select state `Translated`. For any missing `fr` value, fill it. Standard pebbles French style: short, no en-dash decorations.

- [ ] **Step 4: Commit any xcstrings fixes**

```bash
git status
git add apps/ios/Pebbles/Resources/Localizable.xcstrings 2>/dev/null
git diff --cached --quiet || git commit -m "chore(core): finalize fr translations for pebble read view"
```

(If there's nothing to commit, the diff-quiet check skips the commit.)

---

## Task 15: Final verification

**Files:** none (verification only)

- [ ] **Step 1: Run full build + tests**

```bash
cd apps/ios && xcodegen generate && xcodebuild test -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -30
```

Expected: `** TEST SUCCEEDED **`. All tests pass, including the new `PebbleDetailSoulGlyphDecodingTests`.

- [ ] **Step 2: Run repo-level lint and build per CLAUDE.md**

```bash
cd /Users/alexis/code/pbbls && npm run lint
```

Expected: lint passes for all workspaces (including `@pbbls/ios` if it has a lint script).

```bash
cd /Users/alexis/code/pbbls && npm run build
```

Expected: build passes.

- [ ] **Step 3: Manual QA on simulator**

Run the app on the iPhone 15 simulator. Sign in. Verify each scenario below.

A. **Path list tap → read view opens:**
- Tap an existing pebble in the path list.
- The `PebbleDetailSheet` opens with `PebbleReadView`.
- Top bar: privacy badge (leading), Edit button (trailing).
- Header: pebble render, title in Ysabeau SemiBold (large, prominent), uppercase tracked date label below.
- Sections render per spec depending on which fields are populated.

B. **Edit transition (stacked sheets):**
- From the read view, tap Edit.
- `EditPebbleSheet` slides up on top of the read view.
- Make a change, tap Save.
- Edit dismisses; the read view (still open behind it) refreshes with the updated content.
- Path list also refreshes (verify by going back).

C. **Post-create reveal:**
- Tap "Record a pebble" in the path list, fill in the form, save.
- The new read view appears with the freshly-created pebble (NOT the edit form).
- Same layout as path-tap.

D. **Empty / unset states:**
- Find or create a pebble with: no description, no picture, no soul, no collection. Verify those sections do **not** render.
- Find or create a pebble with no domain. Verify the domain row renders with the dashed-empty style and "No domain" label.
- Verify emotion is always rendered with the emotion-color filled box.

E. **Multi-value rows:**
- A pebble with multiple domains: rendered as a single row, comma-separated.
- A pebble with multiple collections: same — single row, comma-separated.
- A pebble with multiple souls: rendered as one row per soul, each with the soul's glyph in the icon box.

F. **Theming:**
- Toggle dark mode in simulator (`⌘ + ⇧ + A`). Verify all colors adapt — background, blush accent-soft, dashed border.

G. **Localization:**
- Switch the simulator language to French (`Settings → General → Language & Region → iPhone Language → Français`). Restart the app.
- Verify "Privé" / "Public", "Aucun domaine", and the date label all localize.

- [ ] **Step 4: Push the branch and open the PR**

```bash
git push -u origin feat/329-pebble-read-view
```

Open the PR using `gh`:

```bash
gh pr create --title "feat(core): single pebble read view (#329)" --body "$(cat <<'EOF'
Resolves #329

## Summary
- Rebuilds `PebbleDetailSheet` around a new `PebbleReadView` (`Features/Path/Read/`): rendered pebble + Ysabeau title + uppercase date label, optional photo, metadata rows (emotion, domain, collections, souls), description in serif. Path-list tap and post-create reveal both route through this single read view.
- Adds an Edit button in the read view that stacks `EditPebbleSheet`; saving refreshes the read view in place.
- Decodes the embedded soul glyph through `PebbleDetail` (reusing `SoulWithGlyph`), with a Swift Testing decoder test.
- Registers Ysabeau SemiBold via `project.yml` + `UIAppFonts`. Adds `Color.pebblesAccentSoft` token for the "blush" set-state fill. Localizes `Visibility.label`.

## Test plan
- [ ] Path-list tap opens the read view (not the edit form).
- [ ] Post-create reveal opens the read view with the same layout.
- [ ] Edit button stacks `EditPebbleSheet`; save refreshes the read view; cancel returns unchanged.
- [ ] Pebbles with no description / picture / soul / collection: optional sections hidden.
- [ ] Pebble with no domain: dashed "No domain" row.
- [ ] Multi-domain / multi-collection: single comma-listed row each. Multi-soul: one row per soul.
- [ ] Light + dark theme.
- [ ] French locale: Privé / Public, Aucun domaine, date label.
- [ ] `npm run build` and `npm run lint` pass.
- [ ] `Localizable.xcstrings` has no `New`/`Stale` rows; every entry filled in `en` and `fr`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

Per project conventions (and user memory), labels and milestone must be applied. The issue is labeled `feat`, `ios`, `ui` and on milestone `M25 · Improved core UX`. **Ask the user** to confirm inheriting these on the PR before applying:

```bash
# After user confirmation:
gh pr edit --add-label "feat,ios,ui" --milestone "M25 · Improved core UX"
```

- [ ] **Step 5: Capture the PR URL**

`gh pr create` returns the PR URL on success. Record it for the user.

---

## Self-Review Notes

(Performed at the end of plan-writing; logged for transparency.)

- **Spec coverage:** every section of the spec maps to a task — fonts (Task 1), tokens (Task 2), Visibility localization (Task 3), data model (Task 4), SELECTs (Task 5), components (Tasks 6–10), `PebbleDetailSheet` rewrite (Task 11), `PathView` wiring (Task 12), arkaik (Task 13), localization sweep (Task 14), verification (Task 15). The "out of scope" items (photo viewer, delete) are explicitly not in any task.
- **Placeholder scan:** the only placeholder is `<PostScriptName>` in Task 8, which is filled by the verified value from Task 1's lookup step. Every other code block contains complete, runnable code.
- **Type consistency:**
  - `PebbleMetadataRow`'s `Icon`, `Style`, `label: LocalizedStringResource` signatures are consistent across Tasks 6, 8, 9, 10.
  - `PebbleDetail.souls` is `[SoulWithGlyph]` after Task 4; consumers in `PebbleDraft.init(from:)` already use `.first?.id` which works for both types.
  - `onPebbleUpdated` callback is consistent in Tasks 11 (definition) and 12 (caller).
- **Decomposition:** five small UI files in `Features/Path/Read/` keep `PebbleReadView` readable; `PebbleDetailSheet` stays focused on data loading + navigation; `PebbleMetadataRow` is the only piece with non-trivial styling logic and earns its extraction by being reused four times.
