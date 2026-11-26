import SwiftUI

/// One layer's paint, the web's `Paint`: `width` is a stroke width in the
/// layer's own units (nil for a fill), `offset` pushes a lip or a groove's far
/// wall, `glow` marks a blurred stroke under the vein.
struct LitPaint: Equatable {
    let color: Color
    let opacity: Double
    let width: CGFloat?
    let offset: CGVector
    let glow: Bool

    init(_ color: Color, _ opacity: Double, width: CGFloat? = nil,
         offset: CGVector = .zero, glow: Bool = false) {
        self.color = color
        self.opacity = opacity
        self.width = width
        self.offset = offset
        self.glow = glow
    }
}

/// One stop of the body's radial ramp.
struct LitToneStop: Equatable {
    let offset: Double
    let color: Color
}

/// The palette slots a recipe dot or facet paints with: the web's `SLOT_COLOR`
/// (`light`, `shaded`, `dark`, `white`) plus the two body tones.
enum LitSlot {
    case primary, secondary, light, shaded, dark, white
}

/// The lit stone's tone slots over a `StonePalette` (#1690): `stone-tones.ts`
/// transcribed through the Android port. The web declares them as CSS over
/// five inline variables (`--p --s --l --sh --d`) and lets the browser mix;
/// the painter here draws concrete colours, so each mix is computed per
/// palette. `StonePalette.primary / secondary / light / shaded / dark` are
/// those variables; `surface` is not read.
enum LitStoneTones {

    /// The body's radial ramp, lit from the upper left.
    static func bodyRamp(_ polarity: ValencePolarity, _ p: StonePalette) -> [LitToneStop] {
        switch polarity {
        case .highlight:
            return [
                LitToneStop(offset: 0, color: mixSrgb(p.secondary, 0.66, p.light)),
                LitToneStop(offset: 0.4, color: p.primary),
                LitToneStop(offset: 1, color: mixSrgb(p.shaded, 0.7, p.dark)),
            ]
        // Flatter than the mock's: a matte pebble has no hot spot.
        case .neutral:
            return [
                LitToneStop(offset: 0, color: mixSrgb(p.secondary, 0.72, p.light)),
                LitToneStop(offset: 0.5, color: p.secondary),
                LitToneStop(offset: 1, color: mixSrgb(p.primary, 0.72, p.secondary)),
            ]
        case .lowlight:
            return [
                LitToneStop(offset: 0, color: mixOklab(p.secondary, 0.31, p.shaded)),
                LitToneStop(offset: 0.5, color: mixOklab(p.shaded, 0.53, p.dark)),
                LitToneStop(offset: 1, color: p.dark),
            ]
        }
    }

    /// The glyph: a lit lip under the ink, both a constant 1.25 px line.
    static func glyphTones(_ polarity: ValencePolarity, _ p: StonePalette) -> (lip: LitPaint, ink: LitPaint) {
        switch polarity {
        case .highlight:
            return (LitPaint(p.light, 0.6), LitPaint(mixSrgb(p.shaded, 0.68, black), 0.85))
        case .neutral:
            return (LitPaint(p.light, 0.75), LitPaint(p.shaded, 0.9))
        case .lowlight:
            return (LitPaint(p.secondary, 0.5), LitPaint(black, 0.66))
        }
    }

    /// The seed's veins, by material: a gem's light seams, a river pebble's
    /// groove (a lit bed and its shadowed far wall), lava's glowing crack.
    static func veinTones(_ polarity: ValencePolarity, _ p: StonePalette) -> [LitPaint] {
        switch polarity {
        case .highlight:
            return [LitPaint(p.light, 0.3, width: 3)]
        case .neutral:
            return [
                LitPaint(p.light, 0.14, width: 9),
                LitPaint(p.shaded, 0.2, width: 2.4, offset: CGVector(dx: 1.5, dy: 3.5)),
            ]
        case .lowlight:
            return [
                LitPaint(p.primary, 0.5, width: 12, glow: true),
                LitPaint(mixSrgb(p.primary, 0.5, p.secondary), 0.9, width: 4.18),
            ]
        }
    }

    /// The seed's fossil: a ghost in the gem, raised in the pebble, sunk in lava.
    static func fossilTones(_ polarity: ValencePolarity, _ p: StonePalette) -> [LitPaint] {
        switch polarity {
        case .highlight:
            return [LitPaint(p.light, 0.18)]
        case .neutral:
            return [
                LitPaint(p.light, 0.35, offset: CGVector(dx: -1.5, dy: -2)),
                LitPaint(p.primary, 0.3),
            ]
        case .lowlight:
            return [LitPaint(p.dark, 0.5)]
        }
    }

    /// The inner shade along the edge (stroke width in px), blurred by `ao`.
    static func edgeShade(_ polarity: ValencePolarity, _ p: StonePalette) -> LitPaint {
        switch polarity {
        case .highlight: return LitPaint(p.dark, 0.5, width: 5)
        case .neutral:   return LitPaint(p.dark, 0.26, width: 6)
        case .lowlight:  return LitPaint(p.dark, 0.8, width: 6)
        }
    }

    /// How strongly a press glints: gem, pebble, lava. A matte pebble barely does.
    static func glintStrength(_ polarity: ValencePolarity) -> Double {
        switch polarity {
        case .highlight: return 1
        case .neutral:   return 0.3
        case .lowlight:  return 0.34
        }
    }

    /// The surface's grain, by material: lava harsh and pitted, the river
    /// pebble a faint tooth that keeps it matte, the gem polished clean.
    static func grainStrength(_ polarity: ValencePolarity) -> Double {
        switch polarity {
        case .highlight: return 0
        case .neutral:   return 0.14
        case .lowlight:  return 0.32
        }
    }

    /// The palette slot a recipe dot or facet paints with.
    static func slotColor(_ slot: LitSlot, _ p: StonePalette) -> Color {
        switch slot {
        case .primary:   return p.primary
        case .secondary: return p.secondary
        case .light:     return p.light
        case .shaded:    return p.shaded
        case .dark:      return p.dark
        case .white:     return white
        }
    }

    /// A moment with no emotion yet: a blank river pebble in the grey ramp the
    /// web calls `BLANK_PALETTE` (the shame category's own). It is the same
    /// value as `StonePalette.blank`; the tests pin it to the web hexes.
    static let blankPalette = StonePalette.blank

    // MARK: - Night

    /// The web's Night filter on the stone as (brightness, saturation):
    /// `--spine-stone-filter: brightness(.92) saturate(.96)` for gem and river,
    /// `--spine-lava-filter: brightness(1.2) saturate(1.05)` for lava.
    static func nightAdjust(_ polarity: ValencePolarity) -> (brightness: Double, saturation: Double) {
        switch polarity {
        case .lowlight:           return (1.2, 1.05)
        case .highlight, .neutral: return (0.92, 0.96)
        }
    }

    /// The same filter as a row-major 4×5 colour matrix, Android's `nightMatrix`:
    /// `saturate × brightness` (CSS applies brightness first), where saturate
    /// is the Filter Effects matrix (luma 0.213 / 0.715 / 0.072) and brightness
    /// a uniform scale of r, g, b. No translation; alpha passes through.
    static func nightMatrix(_ polarity: ValencePolarity) -> [Double] {
        let (b, s) = nightAdjust(polarity)
        let lr = 0.213, lg = 0.715, lb = 0.072
        let sat: [[Double]] = [
            [lr + (1 - lr) * s, lg - lg * s, lb - lb * s],
            [lr - lr * s, lg + (1 - lg) * s, lb - lb * s],
            [lr - lr * s, lg - lg * s, lb + (1 - lb) * s],
        ]
        var out: [Double] = []
        for row in sat { out += [row[0] * b, row[1] * b, row[2] * b, 0, 0] }
        out += [0, 0, 0, 1, 0]
        return out
    }

    // MARK: - Mixing

    /// CSS `color-mix(in srgb, a pct, b)`: a per-channel linear interpolation
    /// of the sRGB-encoded values, opaque. `pct` is a fraction, 0...1.
    static func mixSrgb(_ a: Color, _ pct: Double, _ b: Color) -> Color {
        let w = min(max(pct, 0), 1)
        let ca = channels(a), cb = channels(b)
        return srgb(red: cb.r + (ca.r - cb.r) * w,
                    green: cb.g + (ca.g - cb.g) * w,
                    blue: cb.b + (ca.b - cb.b) * w)
    }

    /// CSS `color-mix(in oklab, a pct, b)`: sRGB to linear to OKLab (Björn
    /// Ottosson's matrices), interpolate, back to sRGB, clamped, opaque. The
    /// endpoints return the input itself, so a bare `var(--d)` stop stays
    /// exact rather than drifting a float through the round trip.
    static func mixOklab(_ a: Color, _ pct: Double, _ b: Color) -> Color {
        let w = min(max(pct, 0), 1)
        if w == 1 { return a }
        if w == 0 { return b }
        let la = toOklab(channels(a)), lb = toOklab(channels(b))
        let mixed = (lb.0 + (la.0 - lb.0) * w, lb.1 + (la.1 - lb.1) * w, lb.2 + (la.2 - lb.2) * w)
        return fromOklab(mixed)
    }

    // MARK: - Internals

    private static let black = srgb(0, 0, 0)
    private static let white = srgb(255, 255, 255)

    private static func srgb(_ r: Int, _ g: Int, _ b: Int, opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: opacity)
    }

    private static func srgb(red: Double, green: Double, blue: Double) -> Color {
        Color(.sRGB, red: red, green: green, blue: blue, opacity: 1)
    }

    /// Display-referred sRGB channels, resolved in an explicit light
    /// environment so an asset colour never reads the ambient scheme.
    private static func channels(_ color: Color) -> (r: Double, g: Double, b: Double) {
        var environment = EnvironmentValues()
        environment.colorScheme = .light
        let resolved = color.resolve(in: environment)
        return (Double(resolved.red), Double(resolved.green), Double(resolved.blue))
    }

    private static func srgbToLinear(_ c: Double) -> Double {
        c <= 0.04045 ? c / 12.92 : pow((c + 0.055) / 1.055, 2.4)
    }

    private static func linearToSrgb(_ c: Double) -> Double {
        let v = min(max(c, 0), 1)
        return v <= 0.0031308 ? v * 12.92 : 1.055 * pow(v, 1 / 2.4) - 0.055
    }

    private static func toOklab(_ c: (r: Double, g: Double, b: Double)) -> (Double, Double, Double) {
        let r = srgbToLinear(c.r), g = srgbToLinear(c.g), b = srgbToLinear(c.b)
        let l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        let m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        let s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return (
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    private static func fromOklab(_ lab: (Double, Double, Double)) -> Color {
        let (lightness, a, b) = lab
        let l = pow(lightness + 0.3963377774 * a + 0.2158037573 * b, 3)
        let m = pow(lightness - 0.1055613458 * a - 0.0638541728 * b, 3)
        let s = pow(lightness - 0.0894841775 * a - 1.2914855480 * b, 3)
        let r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        let g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        let bl = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return srgb(red: linearToSrgb(r), green: linearToSrgb(g), blue: linearToSrgb(bl))
    }
}
