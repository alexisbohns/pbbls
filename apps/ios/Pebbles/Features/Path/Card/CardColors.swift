import SwiftUI

/// The held card's blends with a pebble's emotion palette (#1146 §Verso): the
/// palette is runtime data, so these are computed here, never colorsets. The
/// scheme-only tones are `Color.pebbles.*`; the slot and strength choices are
/// pure and tested.
enum CardColors {
    /// The mat's and the panel's keylines: the spot at 30 % and 28 %.
    static let matRuleAlpha = 0.30
    static let panelRuleAlpha = 0.28
    /// The photo-less slip's foot line: the spot at 22 %.
    static let slipFootAlpha = 0.22
    /// The band's hairlines and its turned-under folds, in the spot.
    static let bandHairlineAlpha = 0.35
    static let bandFoldAlpha = 0.5

    /// Postmark ink: the emotion's `dark` on paper, its `light` at Night.
    static func postmarkInkSlot(scheme: ColorScheme) -> StonePalette.Slot {
        scheme == .dark ? .light : .dark
    }

    /// The band's paper: the spot laid over the stock at 19 %, 24 % at Night.
    static func bandPaperK(scheme: ColorScheme) -> Double {
        scheme == .dark ? 0.24 : 0.19
    }

    /// `color` moved `k` of the way toward `other`, on the environment's
    /// resolved components (`Color.mix` is iOS 18; `resolve(in:)` is 17).
    static func mix(_ color: Color, toward other: Color, _ k: Double, in environment: EnvironmentValues) -> Color {
        let from = color.resolve(in: environment)
        let to = other.resolve(in: environment)
        let t = Float(min(max(k, 0), 1))
        func lerp(_ a: Float, _ b: Float) -> Float { a + (b - a) * t }
        return Color(Color.Resolved(colorSpace: .sRGBLinear, red: lerp(from.linearRed, to.linearRed),
                                    green: lerp(from.linearGreen, to.linearGreen),
                                    blue: lerp(from.linearBlue, to.linearBlue),
                                    opacity: lerp(from.opacity, to.opacity)))
    }

    /// The Highlight word's ramp, top to bottom (decision 2). Paper: the lit
    /// stone's own tones, its light lip, its body, its shadow lip. Night: `light`,
    /// `secondary` at 0.4, then `secondary` moved 70 % toward `primary`.
    static func highlightRamp(_ palette: StonePalette, scheme: ColorScheme,
                              in environment: EnvironmentValues) -> [Gradient.Stop] {
        if scheme == .dark {
            return [
                Gradient.Stop(color: palette.light, location: 0),
                Gradient.Stop(color: palette.secondary, location: 0.4),
                Gradient.Stop(color: mix(palette.secondary, toward: palette.primary, 0.7, in: environment), location: 1)
            ]
        }
        let tones = StoneTones.starting(for: .highlight)
        return [
            Gradient.Stop(color: tones.color(tones.lipLight, in: palette), location: 0),
            Gradient.Stop(color: tones.color(tones.body, in: palette), location: 0.4),
            Gradient.Stop(color: tones.color(tones.lipShadow, in: palette), location: 1)
        ]
    }
}
