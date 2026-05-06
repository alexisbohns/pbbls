# iOS Emotion Palette Consumer — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the iOS app to consume the four-color emotion palette from `public.v_emotions_with_palette`, removing every read of the legacy `Emotion.color` field. Pebble strokes use `primary` (light mode) / `secondary` (dark mode); the emotion meta pill uses `primary` background + `light` foreground.

**Architecture:** A new `@Observable @MainActor` `EmotionPaletteService` is instantiated in `PebblesApp`, injected via `.environment`, and its `load()` is called from a `RootView.task`. It caches a `[UUID: EmotionWithPalette]` map keyed by emotion id. Render surfaces (`PebbleReadView`, `PebbleReadBanner`, `PebbleAnimatedRenderView`, `EditPebbleSheet`) read the service via `@Environment(EmotionPaletteService.self)` and resolve colors through role-named accessors on the `EmotionPalette` value type. The `Emotion.color` field and every `select(...)` mention of `color` are removed. `Color(hex:)` is extended to dispatch on 6-digit (RGB) vs 8-digit (RGBA) input.

**Tech Stack:** SwiftUI, iOS 17+ (`@Observable`, `@Environment(Type.self)`), Supabase Swift SDK, `os.Logger`, Swift Testing (`@Suite`, `@Test`, `#expect`).

**Spec:** `docs/superpowers/specs/2026-05-06-ios-emotion-palette-consumer-design.md`

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift` | Create | Value type holding the four `Color`s + role-named accessors (`accentBackground`, `stroke(for:)`, etc.) |
| `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift` | Create | Decodable row of `v_emotions_with_palette` (id, slug, name, category_id/slug/name, palette) |
| `apps/ios/Pebbles/Services/EmotionPaletteService.swift` | Create | `@Observable @MainActor` service; loads view rows, exposes `palette(for:)` |
| `apps/ios/PebblesTests/EmotionPaletteTests.swift` | Create | Swift Testing: hex parsing (6/8 digit), `stroke(for:)`, `strokeHex(for:)` |
| `apps/ios/PebblesTests/EmotionWithPaletteDecodingTests.swift` | Create | Swift Testing: decoder accepts well-formed rows, rejects null fields |
| `apps/ios/Pebbles/Features/Path/Models/Emotion.swift` | Modify | Drop `color` field |
| `apps/ios/Pebbles/Theme/Color+Pebbles.swift` | Modify | Add `pebblesAccentHex` constant |
| `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift` | Modify | Extend `Color(hex:)` to handle 6 or 8 digits; change `Style.emotion` to carry `background` + `foreground` |
| `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift` | Modify | Read service from environment; pass both pill colors |
| `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` | Modify | Replace `emotionColorHex: String` prop with `emotionId: UUID`; resolve stroke via service + colorScheme |
| `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift` | Modify | Split `strokeColor: String` into `strokeColor: Color` + `strokeColorHex: String` |
| `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` | Modify | Remove `emotion(...).color` from select; compute `strokeColor` hex from palette service |
| `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` | Modify | Drop `color` from emotions select string |
| `apps/ios/Pebbles/PebblesApp.swift` | Modify | Instantiate `EmotionPaletteService` in `init`; inject via `.environment` |
| `apps/ios/Pebbles/RootView.swift` | Modify | Add `.task { await palettes.load() }` |

No file is modified just to "pass through" — only files whose behavior or signatures change.

---

## Task 1: Add `pebblesAccentHex` constant

**Files:**
- Modify: `apps/ios/Pebbles/Theme/Color+Pebbles.swift`

This is the single hex-string fallback used when the palette cache hasn't loaded yet (SVG-text injection needs hex). Done first so later tasks can reference it.

- [ ] **Step 1: Add the constant**

Open `apps/ios/Pebbles/Theme/Color+Pebbles.swift`. After the `pebblesAccentSoft` line, before `pebblesListRow`, add:

```swift
    /// Hex equivalent of `pebblesAccent` (light-mode value of `AccentColor`).
    /// Used as a fallback for SVG-text injection when the palette cache
    /// hasn't loaded yet. A single value covers both schemes — the fallback
    /// path is rare and brief, so dark-mode parity is not worth a second
    /// constant. If `AccentColor` is retuned, update here.
    static let pebblesAccentHex: String = "#C07A7A"
```

- [ ] **Step 2: Build the iOS workspace to confirm it compiles**

Run: `xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO -quiet 2>&1 | tail -10`

If `Pebbles.xcworkspace` doesn't exist, use `apps/ios/Pebbles.xcodeproj` and `-project`. If neither exists, skip this build check until Task 11 — `project.yml` may need regeneration first.

Expected: build succeeds (or fails for unrelated reasons; this single line cannot break compilation).

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Theme/Color+Pebbles.swift
git commit -m "feat(ui): add pebblesAccentHex fallback constant (#369)"
```

---

## Task 2: Extend `Color(hex:)` to handle 6 or 8 digits

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift` (lines 99-113)
- Test: `apps/ios/PebblesTests/EmotionPaletteTests.swift` (create — first part)

The 8-digit form is `#RRGGBBAA`. Done before the rest of the palette work because every downstream task that parses an 8-digit palette hex depends on it.

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/EmotionPaletteTests.swift`:

```swift
import Testing
import SwiftUI
@testable import Pebbles

@Suite("Color(hex:)")
struct ColorHexTests {
    @Test("parses 6-digit hex with #")
    func parsesSixDigitWithHash() {
        let color = Color(hex: "#7B5E99")
        #expect(color != nil)
    }

    @Test("parses 6-digit hex without #")
    func parsesSixDigitNoHash() {
        let color = Color(hex: "7B5E99")
        #expect(color != nil)
    }

    @Test("parses 8-digit hex with alpha")
    func parsesEightDigitWithAlpha() {
        let color = Color(hex: "#7B5E991A")
        #expect(color != nil)
    }

    @Test("parses 8-digit fully opaque")
    func parsesEightDigitOpaque() {
        let color = Color(hex: "#7B5E99FF")
        #expect(color != nil)
    }

    @Test("rejects 5-digit input")
    func rejectsFiveDigit() {
        #expect(Color(hex: "#7B5E9") == nil)
    }

    @Test("rejects 7-digit input")
    func rejectsSevenDigit() {
        #expect(Color(hex: "#7B5E991") == nil)
    }

    @Test("rejects non-hex input")
    func rejectsNonHex() {
        #expect(Color(hex: "#ZZZZZZ") == nil)
    }

    @Test("trims surrounding whitespace")
    func trimsWhitespace() {
        let color = Color(hex: "  #7B5E99  ")
        #expect(color != nil)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `xcodebuild test -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/ColorHexTests 2>&1 | tail -25`

(If the workspace path is wrong, adjust to `-project apps/ios/Pebbles.xcodeproj`.)

Expected: the 8-digit and 5-digit/7-digit tests fail. The 6-digit tests pass against the existing implementation.

- [ ] **Step 3: Replace `Color(hex:)` body**

In `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift`, replace lines 99-113 (the existing `extension Color { init?(hex:) }`) with:

```swift
// MARK: - Hex color helper

/// Parses `#RRGGBB` and `#RRGGBBAA` strings. The 6-digit form is used by the
/// legacy `EmotionRef.color` column; the 8-digit form is used by the four
/// palette columns on `public.emotion_categories` (alpha lives in the last
/// byte). Returns `nil` for any other length — caller decides on a default.
extension Color {
    init?(hex: String) {
        var trimmed = hex.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard let value = UInt32(trimmed, radix: 16) else { return nil }
        switch trimmed.count {
        case 6:
            let red   = Double((value >> 16) & 0xFF) / 255.0
            let green = Double((value >> 8) & 0xFF) / 255.0
            let blue  = Double(value & 0xFF) / 255.0
            self.init(red: red, green: green, blue: blue)
        case 8:
            let red   = Double((value >> 24) & 0xFF) / 255.0
            let green = Double((value >> 16) & 0xFF) / 255.0
            let blue  = Double((value >> 8) & 0xFF) / 255.0
            let alpha = Double(value & 0xFF) / 255.0
            self.init(red: red, green: green, blue: blue, opacity: alpha)
        default:
            return nil
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `xcodebuild test -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/ColorHexTests 2>&1 | tail -25`

Expected: all 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift apps/ios/PebblesTests/EmotionPaletteTests.swift
git commit -m "feat(ui): extend Color(hex:) to parse 8-digit RRGGBBAA (#369)"
```

---

## Task 3: Create `EmotionPalette` value type

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift`
- Modify: `apps/ios/PebblesTests/EmotionPaletteTests.swift` (add tests for accessors)

- [ ] **Step 1: Write the failing tests**

Append to `apps/ios/PebblesTests/EmotionPaletteTests.swift` (inside the same file, after `ColorHexTests`):

```swift
@Suite("EmotionPalette")
struct EmotionPaletteTests {
    private static func makePalette() -> EmotionPalette? {
        EmotionPalette(
            primaryHex: "#7B5E99FF",
            secondaryHex: "#AE91CCFF",
            lightHex: "#F2EFF5FF",
            surfaceHex: "#7B5E991A"
        )
    }

    @Test("init succeeds with well-formed 8-digit hex")
    func initSucceeds() {
        #expect(Self.makePalette() != nil)
    }

    @Test("init returns nil when any hex is malformed")
    func initFailsOnBadHex() {
        let palette = EmotionPalette(
            primaryHex: "not-hex",
            secondaryHex: "#AE91CCFF",
            lightHex: "#F2EFF5FF",
            surfaceHex: "#7B5E991A"
        )
        #expect(palette == nil)
    }

    @Test("strokeHex returns primary in light mode")
    func strokeHexLight() {
        let palette = Self.makePalette()
        #expect(palette?.strokeHex(for: .light) == "#7B5E99FF")
    }

    @Test("strokeHex returns secondary in dark mode")
    func strokeHexDark() {
        let palette = Self.makePalette()
        #expect(palette?.strokeHex(for: .dark) == "#AE91CCFF")
    }

    @Test("primaryHex and secondaryHex are preserved verbatim")
    func hexPreserved() {
        let palette = Self.makePalette()
        #expect(palette?.primaryHex == "#7B5E99FF")
        #expect(palette?.secondaryHex == "#AE91CCFF")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `xcodebuild test -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/EmotionPaletteTests 2>&1 | tail -25`

Expected: build fails with "cannot find 'EmotionPalette' in scope".

- [ ] **Step 3: Create the value type**

Create `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift`:

```swift
import SwiftUI

/// The four colors of an emotion category palette, plus role-named accessors
/// that map to the design-token contract:
///
/// - **Accent context** (used by the emotion meta pill on the read view):
///   `accentBackground` = primary, `accentForeground` = light. Both
///   scheme-independent.
/// - **Pebble stroke**: primary in light mode, secondary in dark mode.
///   Exposed both as a SwiftUI `Color` (for SwiftUI shape strokes) and as a
///   raw hex `String` (for SVG-text injection in `PebbleRenderView`, which
///   replaces `currentColor` literally inside the SVG markup).
///
/// Initialized from the four 8-digit hex strings stored on
/// `public.emotion_categories`. Returns `nil` if any hex fails to parse —
/// callers treat the palette as unavailable and fall back to
/// `Color.pebblesAccent` / `Color.pebblesAccentHex`.
struct EmotionPalette: Equatable {
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color

    let primaryHex: String
    let secondaryHex: String
    let lightHex: String
    let surfaceHex: String

    init?(
        primaryHex: String,
        secondaryHex: String,
        lightHex: String,
        surfaceHex: String
    ) {
        guard
            let primary = Color(hex: primaryHex),
            let secondary = Color(hex: secondaryHex),
            let light = Color(hex: lightHex),
            let surface = Color(hex: surfaceHex)
        else {
            return nil
        }
        self.primary = primary
        self.secondary = secondary
        self.light = light
        self.surface = surface
        self.primaryHex = primaryHex
        self.secondaryHex = secondaryHex
        self.lightHex = lightHex
        self.surfaceHex = surfaceHex
    }

    var accentBackground: Color { primary }
    var accentForeground: Color { light }

    func stroke(for scheme: ColorScheme) -> Color {
        scheme == .dark ? secondary : primary
    }

    func strokeHex(for scheme: ColorScheme) -> String {
        scheme == .dark ? secondaryHex : primaryHex
    }
}
```

- [ ] **Step 4: Add the new file to the Xcode project**

If `project.yml`-driven xcodegen is in use (per `apps/ios/CLAUDE.md`), the file picks up automatically on the next `xcodegen generate`. Run from repo root:

```bash
npm run generate --workspace=@pbbls/ios
```

Expected: `xcodegen` regenerates `apps/ios/Pebbles.xcodeproj` with the new file included.

- [ ] **Step 5: Run tests to verify they pass**

Run: `xcodebuild test -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/EmotionPaletteTests 2>&1 | tail -25`

Expected: all 5 tests pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift \
        apps/ios/PebblesTests/EmotionPaletteTests.swift \
        apps/ios/project.yml apps/ios/Pebbles.xcodeproj 2>/dev/null
git commit -m "feat(ios): add EmotionPalette value type (#369)"
```

(Xcodeproj/yml may not have changed if xcodegen finds nothing new; `git add` will silently no-op for unchanged paths.)

---

## Task 4: Create `EmotionWithPalette` decoded row

**Files:**
- Create: `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift`
- Create: `apps/ios/PebblesTests/EmotionWithPaletteDecodingTests.swift`

`v_emotions_with_palette` columns are typed `String?` in the generated `database.ts`. The decoder rejects nulls and decodes the palette via `EmotionPalette.init?` to keep the strict contract at the boundary, not at access sites.

- [ ] **Step 1: Write the failing tests**

Create `apps/ios/PebblesTests/EmotionWithPaletteDecodingTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@Suite("EmotionWithPalette decoding")
struct EmotionWithPaletteDecodingTests {
    private func decode(_ json: String) throws -> EmotionWithPalette {
        let data = Data(json.utf8)
        return try JSONDecoder().decode(EmotionWithPalette.self, from: data)
    }

    private let validJson = """
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "slug": "anxiety",
      "name": "Anxiety",
      "color": "#7B5E99",
      "category_id": "22222222-2222-2222-2222-222222222222",
      "category_slug": "fear",
      "category_name": "Fear",
      "primary_color": "#7B5E99FF",
      "secondary_color": "#AE91CCFF",
      "light_color": "#F2EFF5FF",
      "surface_color": "#7B5E991A"
    }
    """

    @Test("decodes a well-formed row")
    func decodesValid() throws {
        let row = try decode(validJson)
        #expect(row.slug == "anxiety")
        #expect(row.categorySlug == "fear")
        #expect(row.palette.primaryHex == "#7B5E99FF")
        #expect(row.palette.strokeHex(for: .dark) == "#AE91CCFF")
    }

    @Test("rejects null id")
    func rejectsNullId() {
        let json = validJson.replacingOccurrences(
            of: "\"id\": \"11111111-1111-1111-1111-111111111111\"",
            with: "\"id\": null"
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }

    @Test("rejects null primary_color")
    func rejectsNullPrimaryColor() {
        let json = validJson.replacingOccurrences(
            of: "\"primary_color\": \"#7B5E99FF\"",
            with: "\"primary_color\": null"
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }

    @Test("rejects malformed primary_color hex")
    func rejectsMalformedHex() {
        let json = validJson.replacingOccurrences(
            of: "\"primary_color\": \"#7B5E99FF\"",
            with: "\"primary_color\": \"not-hex\""
        )
        #expect(throws: DecodingError.self) { try decode(json) }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `xcodebuild test -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/EmotionWithPaletteDecodingTests 2>&1 | tail -25`

Expected: build fails with "cannot find 'EmotionWithPalette' in scope".

- [ ] **Step 3: Create the row type**

Create `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift`:

```swift
import Foundation

/// A decoded row from `public.v_emotions_with_palette`.
///
/// PostgREST types every view column as nullable in `database.ts`, but the
/// underlying invariants — `emotions.category_id NOT NULL` (shipped in #367)
/// and the four palette `text NOT NULL` columns on `emotion_categories` —
/// guarantee non-null values in practice. This decoder enforces that
/// invariant at the boundary: rows with any null required field throw
/// `DecodingError`, which `EmotionPaletteService` logs and skips so the bad
/// row simply isn't cached. Access sites read non-optional values from the
/// cached `EmotionPalette` and never need to handle null.
struct EmotionWithPalette: Identifiable, Decodable {
    let id: UUID
    let slug: String
    let name: String
    let categoryId: UUID
    let categorySlug: String
    let categoryName: String
    let palette: EmotionPalette

    private enum CodingKeys: String, CodingKey {
        case id, slug, name
        case categoryId    = "category_id"
        case categorySlug  = "category_slug"
        case categoryName  = "category_name"
        case primaryColor  = "primary_color"
        case secondaryColor = "secondary_color"
        case lightColor    = "light_color"
        case surfaceColor  = "surface_color"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = try container.decode(UUID.self, forKey: .id)
        self.slug = try container.decode(String.self, forKey: .slug)
        self.name = try container.decode(String.self, forKey: .name)
        self.categoryId = try container.decode(UUID.self, forKey: .categoryId)
        self.categorySlug = try container.decode(String.self, forKey: .categorySlug)
        self.categoryName = try container.decode(String.self, forKey: .categoryName)

        let primary = try container.decode(String.self, forKey: .primaryColor)
        let secondary = try container.decode(String.self, forKey: .secondaryColor)
        let light = try container.decode(String.self, forKey: .lightColor)
        let surface = try container.decode(String.self, forKey: .surfaceColor)

        guard let palette = EmotionPalette(
            primaryHex: primary,
            secondaryHex: secondary,
            lightHex: light,
            surfaceHex: surface
        ) else {
            throw DecodingError.dataCorruptedError(
                forKey: .primaryColor,
                in: container,
                debugDescription: "Palette hex strings failed to parse"
            )
        }
        self.palette = palette
    }
}
```

- [ ] **Step 4: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `xcodebuild test -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' -only-testing:PebblesTests/EmotionWithPaletteDecodingTests 2>&1 | tail -25`

Expected: all 4 tests pass.

- [ ] **Step 6: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift \
        apps/ios/PebblesTests/EmotionWithPaletteDecodingTests.swift \
        apps/ios/project.yml apps/ios/Pebbles.xcodeproj 2>/dev/null
git commit -m "feat(ios): add EmotionWithPalette decoded row (#369)"
```

---

## Task 5: Create `EmotionPaletteService`

**Files:**
- Create: `apps/ios/Pebbles/Services/EmotionPaletteService.swift`

The service is `@Observable @MainActor` (matches `SupabaseService`). It loads `v_emotions_with_palette` once into a `[UUID: EmotionWithPalette]` map and exposes `palette(for:)`. No tests at this layer — fake-client extraction is YAGNI per `apps/ios/CLAUDE.md` until a test actually needs it. Decode behavior is already covered in Task 4.

- [ ] **Step 1: Create the service file**

Create `apps/ios/Pebbles/Services/EmotionPaletteService.swift`:

```swift
import Foundation
import Observation
import Supabase
import os

/// Caches the contents of `public.v_emotions_with_palette` for the session.
///
/// Loaded once from `RootView.task` while the splash holds (≥ 2.5s, see
/// `RootView.minSplashSeconds`). Render surfaces look up by `emotion.id`
/// via `palette(for:)`. A miss (cache not warm yet, or a bad row that the
/// decoder rejected) returns `nil` — callers fall back to
/// `Color.pebblesAccent` / `Color.pebblesAccentHex`. No retry on failure;
/// state recovers on next app launch.
@Observable
@MainActor
final class EmotionPaletteService {
    private(set) var byEmotionId: [UUID: EmotionWithPalette] = [:]
    private(set) var hasLoaded: Bool = false

    private let client: SupabaseClient
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "emotion-palette")

    init(client: SupabaseClient) {
        self.client = client
    }

    /// Fetch the view and populate the cache. Idempotent — safe to call
    /// more than once, though the splash-driven call site only fires once.
    func load() async {
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

    /// Look up the palette for an emotion id. Returns nil if the cache
    /// hasn't loaded yet or the row was rejected by the decoder.
    func palette(for emotionId: UUID) -> EmotionPalette? {
        byEmotionId[emotionId]?.palette
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project**

```bash
npm run generate --workspace=@pbbls/ios
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO -quiet 2>&1 | tail -15`

Expected: build succeeds. The service is not yet wired into the app, so no behavior change.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Services/EmotionPaletteService.swift \
        apps/ios/project.yml apps/ios/Pebbles.xcodeproj 2>/dev/null
git commit -m "feat(ios): add EmotionPaletteService (#369)"
```

---

## Task 6: Wire the service into the app

**Files:**
- Modify: `apps/ios/Pebbles/PebblesApp.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`

Service must be available in `@Environment` before any consumer reads it.

- [ ] **Step 1: Update `PebblesApp.swift`**

Replace the body of `PebblesApp` (replace the current `@State` declaration and `body`):

```swift
@main
struct PebblesApp: App {
    @State private var supabase: SupabaseService
    @State private var palettes: EmotionPaletteService

    init() {
        let supabase = SupabaseService()
        self._supabase = State(initialValue: supabase)
        self._palettes = State(initialValue: EmotionPaletteService(client: supabase.client))
        Self.configureSegmentedControlAppearance()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(supabase)
                .environment(palettes)
        }
    }

    /// Restyles the system segmented control to match the Pebbles design tokens.
    /// Applied globally because the app currently has only one segmented Picker
    /// (`PebblesAuthSwitcher`); if a second variant is added later, scope this
    /// via `appearance(whenContainedInInstancesOf:)`.
    private static func configureSegmentedControlAppearance() {
        let muted = UIColor(named: "Muted") ?? .systemGray5
        let mutedForeground = UIColor(named: "MutedForeground") ?? .systemGray

        let proxy = UISegmentedControl.appearance()
        proxy.backgroundColor = muted
        proxy.selectedSegmentTintColor = mutedForeground

        proxy.setTitleTextAttributes([
            .foregroundColor: UIColor.white,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .medium)
        ], for: .selected)

        proxy.setTitleTextAttributes([
            .foregroundColor: mutedForeground,
            .font: UIFont.systemFont(ofSize: UIFont.systemFontSize, weight: .regular)
        ], for: .normal)
    }
}
```

- [ ] **Step 2: Update `RootView.swift`**

Add a third `.task` block. After the existing line 76-79 block (`.task { try? await Task.sleep(...); minSplashDone = true }`), insert:

```swift
        .task { await palettes.load() }
```

And add the environment property at the top of the struct, after the existing `@Environment(SupabaseService.self) private var supabase` line:

```swift
    @Environment(EmotionPaletteService.self) private var palettes
```

Update the `#Preview` at the bottom to inject the new env:

```swift
#Preview {
    let supabase = SupabaseService()
    return RootView()
        .environment(supabase)
        .environment(EmotionPaletteService(client: supabase.client))
}
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO -quiet 2>&1 | tail -15`

Expected: build succeeds.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/RootView.swift
git commit -m "feat(ios): inject EmotionPaletteService and load on splash (#369)"
```

---

## Task 7: Update `PebbleMetaPill` to take both pill colors

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift`

The `Style.emotion(color:)` case becomes `Style.emotion(background:foreground:)`. Foreground was hardcoded to `.white`; now it's the palette's `light` color so dark-mode pills stay readable.

- [ ] **Step 1: Replace the `Style` enum and the `foreground`/`background` computed properties**

In `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift`:

Replace the `Style` enum (lines 21-25):

```swift
    enum Style: Equatable {
        case emotion(background: Color, foreground: Color)
        case neutral
        case unset
    }
```

Replace the `background` ViewBuilder (lines 67-75):

```swift
    @ViewBuilder
    private var background: some View {
        switch style {
        case .emotion(let background, _):
            Capsule().fill(background)
        case .neutral, .unset:
            Capsule().fill(Color.pebblesAccentSoft)
        }
    }
```

Replace the `foreground` computed property (lines 88-94):

```swift
    private var foreground: Color {
        switch style {
        case .emotion(_, let foreground): return foreground
        case .neutral: return Color.pebblesForeground
        case .unset:   return Color.pebblesMutedForeground
        }
    }
```

- [ ] **Step 2: Update the previews in this same file**

Replace lines 117-121 (first preview pill):

```swift
        PebbleMetaPill(
            icon: .system("heart.fill"),
            label: "Anxiety",
            style: .emotion(
                background: Color(red: 0.5, green: 0.4, blue: 0.95),
                foreground: .white
            )
        )
```

- [ ] **Step 3: Build to surface every caller that breaks**

Run: `xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | grep -E "error:|emotion\(color" | head -20`

Expected: errors at:
- `apps/ios/Pebbles/Features/Path/Read/PebblePillFlow.swift:91`
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift:52`

Both will be fixed in Tasks 8 and 9. For now, fix the unrelated `PebblePillFlow.swift:91` (a preview):

In `apps/ios/Pebbles/Features/Path/Read/PebblePillFlow.swift`, change line 91:

```swift
            style: .emotion(
                background: Color(red: 0.5, green: 0.4, blue: 0.95),
                foreground: .white
            )
```

- [ ] **Step 4: Build to confirm only the read-view call site is now red**

Run the same build command. Expected: only `PebbleReadView.swift:52` errors. That's intentional — Task 8 fixes it.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift \
        apps/ios/Pebbles/Features/Path/Read/PebblePillFlow.swift
git commit -m "feat(ui): pill emotion style takes background + foreground (#369)"
```

---

## Task 8: Wire `PebbleReadView` to the palette service

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`

- [ ] **Step 1: Add the env property**

In `apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift`, after line 10 (`let detail: PebbleDetail`), add:

```swift
    @Environment(EmotionPaletteService.self) private var palettes
```

- [ ] **Step 2: Replace the emotion pill construction**

Replace lines 49-53 (the existing emotion `PebbleMetaPill`):

```swift
            // Emotion — always present.
            let palette = palettes.palette(for: detail.emotion.id)
            PebbleMetaPill(
                icon: .system("heart.fill"),
                label: LocalizedStringResource(stringLiteral: detail.emotion.localizedName),
                style: .emotion(
                    background: palette?.accentBackground ?? Color.pebblesAccent,
                    foreground: palette?.accentForeground ?? .white
                )
            )
```

- [ ] **Step 3: Replace the banner call**

The banner currently takes `emotionColorHex: detail.emotion.color`. After Task 9 it will take `emotionId: detail.emotion.id`. Update line 19 in the same change:

```swift
                    emotionId: detail.emotion.id,
```

(Banner signature change happens in Task 9. The build will be red between this commit and the next — that's tracked in Step 4.)

- [ ] **Step 4: Verify only the banner-callsite mismatch remains**

Run: `xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | grep "error:" | head -10`

Expected: `PebbleReadBanner.swift` errors complaining about an `emotionId` argument that doesn't exist (or `emotionColorHex` that's missing). That's intentional — Task 9 closes the gap. Do **not** commit yet.

- [ ] **Step 5: (No commit yet — proceed to Task 9)**

The read-view + banner change is one atomic refactor. Hold off on the commit until Task 9 completes.

---

## Task 9: Update `PebbleReadBanner` and `PebbleAnimatedRenderView`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`

The banner replaces `emotionColorHex: String` with `emotionId: UUID` and resolves stroke colors via the service + colorScheme. The animated render's stroke API splits into a `Color` (for SwiftUI shapes) and a `String` hex (for the static fallback).

- [ ] **Step 1: Update `PebbleAnimatedRenderView` signature and body**

In `apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift`:

Replace lines 14-25 (struct head and stored props):

```swift
struct PebbleAnimatedRenderView: View {
    let svg: String
    /// Stroke for the SwiftUI shape paths used during the animation.
    let strokeColor: Color
    /// Hex equivalent of `strokeColor`, injected into raw SVG markup by the
    /// static `PebbleRenderView` fallback (Reduce Motion, unknown timings,
    /// or parse failure). Must match `strokeColor` for the current scheme.
    let strokeColorHex: String
    let renderVersion: String?
```

Replace line 32 (the static fallback call):

```swift
                PebbleRenderView(svg: svg, strokeColor: strokeColorHex)
```

Replace line 64 (the `stroke` computed property):

```swift
    private var stroke: Color { strokeColor }
```

Update the previews (lines 134-169):

```swift
#Preview("Animated · with fossil") {
    PebbleAnimatedRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
          <g id="layer:shape">
            <path d="M 20 120 C 20 60 60 20 120 20 C 180 20 220 60 220 120 C 220 180 180 220 120 220 C 60 220 20 180 20 120 Z" fill="none"/>
          </g>
          <g id="layer:fossil" opacity="0.3">
            <path d="M 60 60 L 180 180 M 60 180 L 180 60" fill="none"/>
          </g>
          <g id="layer:glyph" transform="translate(70, 70) scale(0.5)">
            <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none"/>
          </g>
        </svg>
        """,
        strokeColor: Color(red: 0.486, green: 0.361, blue: 0.980),
        strokeColorHex: "#7C5CFA",
        renderVersion: "0.1.0"
    )
    .frame(width: 200, height: 200)
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Static fallback (unknown version)") {
    PebbleAnimatedRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <g id="layer:shape"><path d="M 0 0 L 100 100" fill="none"/></g>
        </svg>
        """,
        strokeColor: Color(red: 0.486, green: 0.361, blue: 0.980),
        strokeColorHex: "#7C5CFA",
        renderVersion: "unknown"
    )
    .frame(width: 200, height: 200)
}
```

- [ ] **Step 2: Update `PebbleReadBanner` signature and body**

In `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`:

Replace line 21 (`let emotionColorHex: String`):

```swift
    let emotionId: UUID
```

Add envs after line 25 (after the existing `accessibilityReduceMotion`):

```swift
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme
```

Replace the `renderedPebble` ViewBuilder (lines 156-168):

```swift
    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            let palette = palettes.palette(for: emotionId)
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: palette?.stroke(for: colorScheme) ?? Color.pebblesAccent,
                strokeColorHex: palette?.strokeHex(for: colorScheme) ?? Color.pebblesAccentHex,
                renderVersion: renderVersion
            )
            .frame(height: pebbleHeight)
        } else {
            EmptyView()
        }
    }
```

Update all three previews at the bottom of the file. Replace the `emotionColorHex: "#7C5CFA"` line in each preview with:

```swift
        emotionId: UUID(),
```

(Each preview gets a fresh random UUID — the palette service has no rows in preview mode, so the fallback color renders. That's correct preview behavior.)

The previews will need an injected `EmotionPaletteService`. Wrap each preview's content in:

```swift
#Preview("Without photo · medium") {
    PebbleReadBanner(
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
    .background(Color.pebblesBackground)
    .environment(SupabaseService())
    .environment(EmotionPaletteService(client: SupabaseService().client))
}
```

Apply the same wrapper (`.environment(SupabaseService()).environment(EmotionPaletteService(client: SupabaseService().client))`) to all three previews. Banner field order in the preview call: keep alphabetical-by-existing-order, just swap the `emotionColorHex` line for `emotionId`.

- [ ] **Step 3: Build to confirm everything compiles**

Run: `xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | grep "error:" | head -10`

Expected: errors only at `EditPebbleSheet.swift:195` (`detail.emotion.color` no longer exists once Task 10 lands) — that's the next task. Read-view path should be clean.

If anything else errors, stop and read the error before continuing.

- [ ] **Step 4: Commit Tasks 8 + 9 together**

```bash
git add apps/ios/Pebbles/Features/Path/Read/PebbleReadView.swift \
        apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift \
        apps/ios/Pebbles/Features/Path/Render/PebbleAnimatedRenderView.swift
git commit -m "feat(ios): pebble read view consumes palette service (#369)"
```

---

## Task 10: Drop `Emotion.color` and update `EditPebbleSheet`

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/Emotion.swift`
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

This task closes the acceptance grep. After it, `grep -r "emotion.color\|emotion\.color" apps/ios/Pebbles` returns nothing.

- [ ] **Step 1: Drop `color` from the `Emotion` model**

Replace `apps/ios/Pebbles/Features/Path/Models/Emotion.swift` entirely:

```swift
import Foundation

struct Emotion: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
}
```

- [ ] **Step 2: Update `EditPebbleSheet` — env, select string, strokeColor source**

In `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`:

Add envs after line 20 (`@Environment(\.dismiss) private var dismiss`):

```swift
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme
```

In the `select` string for the detail query, find the line:

```swift
                    emotion:emotions(id, slug, name, color),
```

Change to:

```swift
                    emotion:emotions(id, slug, name),
```

Replace line 195 (`self.strokeColor = detail.emotion.color`):

```swift
            self.strokeColor = palettes.palette(for: detail.emotion.id)?
                .strokeHex(for: colorScheme) ?? Color.pebblesAccentHex
```

- [ ] **Step 3: Update `CreatePebbleSheet` — drop `color` from emotions select**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`, find line 219-224 (the `emotionsQuery`):

```swift
            async let emotionsQuery: [Emotion] = supabase.client
                .from("emotions")
                .select()
                .order("name")
                .execute()
                .value
```

`.select()` with no args fetches all columns — including `color`. Change to an explicit column list so the wire payload no longer carries `color`:

```swift
            async let emotionsQuery: [Emotion] = supabase.client
                .from("emotions")
                .select("id, slug, name")
                .order("name")
                .execute()
                .value
```

Apply the same change in `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` to the `async let emotionsQuery` block (lines 161-166):

```swift
            async let emotionsQuery: [Emotion] = supabase.client
                .from("emotions")
                .select("id, slug, name")
                .order("name")
                .execute()
                .value
```

- [ ] **Step 4: Build to confirm everything compiles**

Run: `xcodebuild -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -sdk iphonesimulator -configuration Debug build CODE_SIGNING_ALLOWED=NO 2>&1 | grep "error:" | head -20`

Expected: zero errors.

- [ ] **Step 5: Verify the acceptance grep is clean**

Run: `grep -rn "emotion.color\|emotion\.color" apps/ios/Pebbles 2>/dev/null`

Expected: no output.

If anything matches, fix it before committing. Common straggler: a comment or doc string referencing the old field.

- [ ] **Step 6: Run the full test suite**

Run: `xcodebuild test -workspace apps/ios/Pebbles.xcworkspace -scheme Pebbles -destination 'platform=iOS Simulator,name=iPhone 15' 2>&1 | tail -25`

Expected: all tests pass (`ColorHexTests`, `EmotionPaletteTests`, `EmotionWithPaletteDecodingTests`, plus the existing test files).

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/Emotion.swift \
        apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "feat(ios): drop Emotion.color, route stroke color via palette service (#369)"
```

---

## Task 11: Manual smoke verification

**Files:** none (simulator runs)

The previews and unit tests don't exercise the live Supabase fetch. Do one round of in-simulator verification before opening the PR.

- [ ] **Step 1: Run the app in the simulator**

Open the project in Xcode (`open apps/ios/Pebbles.xcworkspace`) and run on `iPhone 15` simulator.

- [ ] **Step 2: Sign in (if not already)**

Use a known account that has at least one pebble.

- [ ] **Step 3: Open a pebble's read view**

Tap a pebble in the Path timeline. Verify:
- Pebble stroke color matches the emotion's `primary_color` (light mode).
- Emotion meta pill background = `primary_color`, foreground = `light_color` (the pill text reads in the palette's light tone, not white).
- No flash of brand-purple fallback (cache should be warm by the time you reach the read sheet).

- [ ] **Step 4: Toggle dark mode**

`Cmd + Shift + A` (Xcode appearance toggle) or simulator Settings → Developer → Dark Appearance. Verify:
- Pebble stroke switches to `secondary_color`.
- Pill colors are scheme-independent (per design tokens — same primary bg, light fg).

- [ ] **Step 5: Open the edit sheet for the same pebble**

Tap the edit toolbar button. Verify the form preview's stroke matches the read view's stroke for the current scheme.

- [ ] **Step 6: Cold-launch via Reduce Motion enabled**

Simulator → Settings → Accessibility → Motion → Reduce Motion ON. Re-open a pebble. Verify the static `PebbleRenderView` fallback renders with the right stroke hex (not the brand-purple fallback).

- [ ] **Step 7: No commit needed for manual verification.**

---

## Task 12: Update Arkaik product map

**Files:**
- Modify: `docs/arkaik/bundle.json`

Per the project's `arkaik` skill: "Use this skill whenever you create, rename, move, or delete a view/screen, route, data model, or API endpoint." This PR adds a new service (`EmotionPaletteService`) and consumes a new view (`v_emotions_with_palette`). No new screens, but the data graph changes.

- [ ] **Step 1: Invoke the arkaik skill**

Use the `Skill` tool: `arkaik`. Describe the change — new iOS service that consumes `public.v_emotions_with_palette`, no new screens, replaces `Emotion.color` reads in pebble render and edit flows.

The skill will guide the bundle edit. Commit per its instructions.

---

## Task 13: Open the PR

**Files:** none

- [ ] **Step 1: Push the branch**

```bash
git push -u origin feat/369-ios-emotion-palette-consumer
```

- [ ] **Step 2: Open the PR**

PR title: `feat(ios): consume emotion category palette in pebble render (#369)`

PR body:

```markdown
Resolves #369.

Wires the iOS app to consume `public.v_emotions_with_palette` (shipped in
#367) and removes every read of the legacy `Emotion.color` field. Color
roles per the design tokens:

- Pebble stroke: `primary_color` in light mode, `secondary_color` in dark.
- Emotion meta pill: `primary_color` background, `light_color` foreground.

Key files:
- `apps/ios/Pebbles/Services/EmotionPaletteService.swift` — `@Observable`
  service loaded from `RootView.task`, caches view rows in memory.
- `apps/ios/Pebbles/Features/Path/Models/EmotionPalette.swift` — value type
  with role-named accessors (`accentBackground`, `stroke(for:)`, etc.).
- `apps/ios/Pebbles/Features/Path/Models/EmotionWithPalette.swift` — strict
  decoder (rejects null fields; PostgREST views are nullable on the wire).
- `apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift` — `Color(hex:)`
  extended to handle 6- or 8-digit input; `Style.emotion` carries both
  `background` and `foreground`.
- `Models/Emotion.swift` loses its `color` field; `EditPebbleSheet` /
  `CreatePebbleSheet` drop `color` from their `select(...)` strings.
- `PebbleAnimatedRenderView` API splits `strokeColor` into a `Color` (for
  SwiftUI shapes) and a hex `String` (for the static SVGView fallback).

Spec: `docs/superpowers/specs/2026-05-06-ios-emotion-palette-consumer-design.md`
Plan: `docs/superpowers/plans/2026-05-06-ios-emotion-palette-consumer.md`

## Test plan

- [x] Unit: `Color(hex:)` 6/8-digit parse cases (`ColorHexTests`).
- [x] Unit: `EmotionPalette` accessors (`EmotionPaletteTests`).
- [x] Unit: `EmotionWithPalette` decoder rejects null/malformed fields.
- [x] Manual: simulator verification on read sheet + edit sheet, light + dark, with Reduce Motion on/off.
- [x] `grep -r "emotion.color\|emotion\.color" apps/ios/Pebbles` is clean.
```

- [ ] **Step 3: Apply labels and milestone**

Per project memory: PR resolves an issue, propose inheriting the issue's labels and milestone (except `bug` → `fix`).

Issue #369 has labels `core`, `feat`, `ios`, `ui` and milestone `M30 · Emotions palettes`. Propose inheriting all four labels and the milestone.

Run: `gh pr view --json labels,milestone` after creation, then `gh pr edit <number> --add-label core,feat,ios,ui --milestone "M30 · Emotions palettes"` if needed.

Confirm with the user before applying.

---

## Self-Review

**Spec coverage check:**

| Spec section | Plan task |
|---|---|
| `EmotionPalette` value type | Task 3 |
| `EmotionWithPalette` row | Task 4 |
| `EmotionPaletteService` | Task 5 |
| `Color(hex:)` extension | Task 2 |
| `Emotion` model `color` removal | Task 10 |
| `PebbleMetaPill` `Style.emotion` change | Task 7 |
| `PebbleReadView` consumes service | Task 8 |
| `PebbleReadBanner` `emotionId` prop | Task 9 |
| `PebbleAnimatedRenderView` API split | Task 9 |
| `EditPebbleSheet:195` strokeColor source | Task 10 |
| `CreatePebbleSheet`/`EditPebbleSheet` SQL | Task 10 |
| `pebblesAccentHex` constant | Task 1 |
| App-wiring (PebblesApp + RootView) | Task 6 |
| Tests (palette + decoder) | Tasks 2, 3, 4 |
| Arkaik update | Task 12 |

All spec sections have a task. No gaps.

**Type consistency:**

- `EmotionPalette.init?` signature: `primaryHex/secondaryHex/lightHex/surfaceHex` — matches across Tasks 3, 4, and the test fixtures.
- `EmotionPaletteService.palette(for:)` returns `EmotionPalette?` — used consistently in Tasks 8, 9, 10.
- `EmotionWithPalette` field names (`categorySlug`, `categoryName`) — declared once in Task 4, never referenced elsewhere by name.
- `pebblesAccentHex` — added in Task 1, consumed in Tasks 9 and 10.
- `PebbleAnimatedRenderView`'s new params: `strokeColor: Color` + `strokeColorHex: String` — declared in Task 9, consumed only by `PebbleReadBanner` (also Task 9).

**Placeholder scan:** No "TBD", "TODO", "implement later", or vague handwaving. Every step shows actual code or an exact command.

**One known fragile spot:** the build commands assume `apps/ios/Pebbles.xcworkspace` exists. `xcodegen generate` produces the `.xcodeproj` (per `apps/ios/CLAUDE.md`); whether the workspace exists alongside depends on the project setup. Task 1 Step 2 falls back to `-project apps/ios/Pebbles.xcodeproj` and Tasks that follow inherit the same fallback if needed. Not a logic gap — just a real-world adaptation point.
