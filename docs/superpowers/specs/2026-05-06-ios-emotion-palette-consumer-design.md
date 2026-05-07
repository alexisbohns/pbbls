# iOS emotion palette consumer — design

**Issue:** [#369](https://github.com/Bohns/pbbls/issues/369) — `[Feat] Adapt iOS pebble render to consume category palette`
**Date:** 2026-05-06
**Scope:** iOS client wiring for the palette infrastructure shipped in [#367](https://github.com/Bohns/pbbls/pull/367). No schema or web changes.
**Parent spec:** `docs/superpowers/specs/2026-05-06-emotion-categories-palettes-design.md`

## Goal

Replace every iOS read of `Emotion.color` with values from the four-color category palette stored in `public.emotion_categories` and exposed via `public.v_emotions_with_palette`. Apply the design-token contract the user defined for color roles:

- **Pebble stroke** — `primary_color` in light mode, `secondary_color` in dark mode.
- **Emotion meta pill** ("Accent" context) — `primary_color` background, `light_color` foreground, scheme-independent.
- `surface_color` is unused by this PR (reserved for the upcoming emotion-picker sibling issue).

## Acceptance criteria (from issue #369)

- Builds cleanly in Xcode (no `if #available`, Swift Testing only).
- All pebble surfaces render with palette colors.
- `grep -r "emotion.color\|emotion\.color" apps/ios/Pebbles` returns no matches.

## Architecture

A new `@Observable @MainActor` palette service caches the rows of `v_emotions_with_palette` for the session. Render surfaces look up by `emotion.id` and read role-named accessors (`accentBackground`, `stroke(for:)`) instead of raw hex. The legacy `Emotion.color` field is removed from the model.

```
RootView.task ──► EmotionPaletteService.load()
                         │
                         ▼
                  [UUID: EmotionWithPalette]   (in-memory cache)
                         ▲
                         │ palette(for: emotionId)
                         │
            ┌────────────┼─────────────────────┐
            │            │                     │
   PebbleReadView   PebbleReadBanner    EditPebbleSheet
   (pill)           (passes Color +     (preview stroke
                     hex to renders)     hex)
                         │
              ┌──────────┴──────────┐
              ▼                     ▼
   PebbleAnimatedRenderView   PebbleRenderView
   (SwiftUI Color)            (SVG hex injection)
```

## Data model

### New value types

```swift
struct EmotionPalette {
    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color

    // Hex retained for SVG-text injection (PebbleRenderView).
    let primaryHex: String
    let secondaryHex: String

    // Accent context — scheme-independent per design tokens.
    var accentBackground: Color { primary }
    var accentForeground: Color { light }

    // Pebble stroke — primary in light, secondary in dark.
    func stroke(for scheme: ColorScheme) -> Color {
        scheme == .dark ? secondary : primary
    }
    func strokeHex(for scheme: ColorScheme) -> String {
        scheme == .dark ? secondaryHex : primaryHex
    }
}

struct EmotionWithPalette: Decodable, Identifiable {
    let id: UUID
    let slug: String
    let name: String
    let categoryId: UUID
    let categorySlug: String
    let categoryName: String
    let palette: EmotionPalette
}
```

### `Emotion` model change

`apps/ios/Pebbles/Features/Path/Models/Emotion.swift` drops the `color` field:

```swift
struct Emotion: Identifiable, Decodable, Hashable {
    let id: UUID
    let slug: String
    let name: String
}
```

Every `select(...)` string that listed `color` drops it. This is the wire-level half of the acceptance grep — keeping the column in the SELECT but unused would be wasteful and confusing.

## The service

```swift
@Observable @MainActor
final class EmotionPaletteService {
    private(set) var byEmotionId: [UUID: EmotionWithPalette] = [:]
    private(set) var hasLoaded = false

    private let client: SupabaseClient
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "emotion-palette")

    init(client: SupabaseClient) { self.client = client }

    func load() async { /* select * from v_emotions_with_palette; populate map */ }
    func palette(for emotionId: UUID) -> EmotionPalette? { byEmotionId[emotionId]?.palette }
}
```

### Decoding strategy

`v_emotions_with_palette` columns are typed as `String?` in the generated `database.ts` because PostgREST always types view columns as nullable. The underlying invariants — `emotions.category_id NOT NULL` (shipped in #367) and the four palette `text NOT NULL` columns on `emotion_categories` — guarantee non-null values in practice.

The decoder rejects rows with any null field by logging and skipping. This is safer than force-unwrapping at access time: a stale view definition or unexpected DB state surfaces as "this emotion is missing from the cache" (which falls through to the fallback color) instead of a crash inside a render path.

### Lifecycle

- Instantiated in `PebblesApp` and injected via `.environment(palette)` from `RootView`.
- `palette.load()` is kicked off in a third `.task` block on `RootView`, in parallel with `supabase.start()` and the splash hold.
- Splash holds ≥ 2.5s (`RootView.minSplashSeconds`). The view fetch is one round-trip on a tiny payload (~38 rows × ~250B), so the cache is warm before any pebble surface is reachable in the common case.
- No retry on failure. Errors are logged. Subsequent `palette(for:)` calls return `nil` and consumers fall back. State recovers on next app launch.

## `Color(hex:)` extension — 6 or 8 digit dispatch

`apps/ios/Pebbles/Features/Path/Read/PebbleMetaPill.swift:101` currently parses 6-digit hex only. It grows to dispatch on string length:

```swift
extension Color {
    init?(hex: String) {
        var trimmed = hex.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard let value = UInt32(trimmed, radix: 16) else { return nil }
        switch trimmed.count {
        case 6:
            let r = Double((value >> 16) & 0xFF) / 255.0
            let g = Double((value >> 8) & 0xFF) / 255.0
            let b = Double(value & 0xFF) / 255.0
            self.init(red: r, green: g, blue: b)
        case 8:
            let r = Double((value >> 24) & 0xFF) / 255.0
            let g = Double((value >> 16) & 0xFF) / 255.0
            let b = Double((value >> 8) & 0xFF) / 255.0
            let a = Double(value & 0xFF) / 255.0
            self.init(red: r, green: g, blue: b, opacity: a)
        default:
            return nil
        }
    }
}
```

Order matches the `#RRGGBBAA` storage format used by the parent spec.

## Consumer changes

### `PebbleMetaPill.swift`

`Style.emotion` carries both colors instead of a single fill:

```swift
enum Style: Equatable {
    case emotion(background: Color, foreground: Color)
    case neutral
    case unset
}
```

`background` and `foreground` properties switch on the new payload. Consumers pass `palette.accentBackground` and `palette.accentForeground`.

### `PebbleReadView.swift:52`

Read the palette service from the environment, look up by `detail.emotion.id`, pass both colors into the pill:

```swift
@Environment(EmotionPaletteService.self) private var palettes

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

### `PebbleReadBanner.swift`

The `emotionColorHex: String` prop is replaced with `emotionId: UUID`. The banner reads the service + `colorScheme` and resolves stroke locally as **both** a `Color` (for the animated path) and a `String` hex (for the static SVGView fallback):

```swift
@Environment(EmotionPaletteService.self) private var palettes
@Environment(\.colorScheme) private var colorScheme

let palette = palettes.palette(for: emotionId)
PebbleAnimatedRenderView(
    svg: renderSvg,
    strokeColor: palette?.stroke(for: colorScheme) ?? Color.pebblesAccent,
    strokeColorHex: palette?.strokeHex(for: colorScheme) ?? Color.pebblesAccentHex,
    renderVersion: renderVersion
)
```

### `PebbleAnimatedRenderView.swift:64`

API change: the single `strokeColor: String` prop is split into two:

- `strokeColor: Color` — used directly by the SwiftUI `Shape.stroke(...)` call. Replaces the internal `Color(hex: strokeColor) ?? Color.pebblesAccent` resolution.
- `strokeColorHex: String` — passed through to the static `PebbleRenderView` fallback, which injects hex into raw SVG markup.

Two parallel props (rather than asking each consumer to derive one from the other) is the cleanest way to handle the SwiftUI/SVG-text impedance mismatch without dynamic-color trait gymnastics.

### `PebbleRenderView.swift`

Unchanged API. It injects hex into raw SVG markup; consumers pass the right hex for the current scheme.

### `EditPebbleSheet.swift:195`

The `self.strokeColor = detail.emotion.color` line becomes a lookup against the palette service:

```swift
@Environment(EmotionPaletteService.self) private var palettes
@Environment(\.colorScheme) private var colorScheme

// inside loadReferences():
self.strokeColor = palettes.palette(for: detail.emotion.id)?.strokeHex(for: colorScheme)
    ?? Color.pebblesAccentHex
```

`PebbleFormView` keeps its `strokeColor: String?` prop — the form is the boundary where hex-injection into SVGView text happens.

### `CreatePebbleSheet.swift` and `EditPebbleSheet.swift` SQL

Drop `color` from the `emotions` `select(...)` strings. The compose flow's strokeColor (returned from the compose RPC, not a column read) is unaffected.

## Fallback constants

A new static is added alongside `pebblesAccent`:

```swift
extension Color {
    // Light-mode hex of `pebblesAccent` (AccentColor asset).
    // Used as a hex-string fallback for SVG-text injection when the palette
    // cache is unwarm. Single value for both schemes — the fallback path is
    // rare and brief; perfect dark-mode parity is not worth a second constant.
    static let pebblesAccentHex: String = "#C07A7A"
}
```

Hex is taken from `AccentColor.colorset/Contents.json` (light variant). If `AccentColor` is ever retuned, this string drifts silently — accepted maintenance burden for a defensive fallback.

## App-wiring

`PebblesApp.swift`:

```swift
@State private var supabase = SupabaseService()
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
```

`RootView.swift` adds one `.task`:

```swift
.task { await palettes.load() }
```

## Testing

Per `apps/ios/CLAUDE.md` — Swift Testing only, no XCTest, no UI tests.

- `EmotionPaletteTests` — verify `Color(hex:)` parses 6- and 8-digit forms; `stroke(for:)` returns `secondary` in `.dark`; `strokeHex(for:)` produces the right hex.
- `EmotionPaletteServiceTests` — decode tests against synthetic JSON. Confirm rows with any null required field are dropped (not crashing). No network test; if a fake client is needed later, extract `SupabaseServicing` *then* per the project's YAGNI guideline.

No tests for SwiftUI consumers; visual verification + the acceptance grep cover them.

## Risks

- **Service must populate before user enters a deep-link to a pebble**. Mitigated by the splash hold (≥ 2.5s) and the trivial payload size. Fallback color is the existing `pebblesAccent`, so a miss is graceful.
- **Generated types stay nullable** even though Phase 2 NOT NULL has shipped. PostgREST views always type as nullable in `database.ts`. The decoder treats nulls as bad data — verified once at decode time, never at access.
- **Hardcoded `#7C5CFA` for `pebblesAccentHex`**. If `Color.pebblesAccent` is ever retuned, this string drifts silently. Mitigations considered: resolving via `Color.pebblesAccent.resolve(in:)` at runtime — but it requires an `EnvironmentValues` and adds runtime cost for a constant. Documented inline; future maintenance burden accepted.
- **`PebbleAnimatedRenderView` API change** is source-breaking for previews. Both `#Preview` blocks in the file need a one-liner update (Color literal in, hex out). No production callers outside the banner.

## Out of scope

- **Emotion picker grouped by category** (sibling issue). Service is shaped to support it (`EmotionWithPalette` exposes `categorySlug`, `categoryName`), but the picker view ships separately.
- **Replacing the ad-hoc `from("emotions").select()` calls** in Create/Edit with the service. Only the `color` column drops; the rest of the fetch stays.
- **Theming** (multiple palettes per category, user-selectable theme). Future-shape; not precluded.
- **Localization for category names**. Nothing in this PR renders a category name. The picker PR adds `Localizable.xcstrings` entries when it lands.
- **Dropping `emotions.color` from the DB**. Bound by shipped iOS clients; out of any near-term roadmap.
