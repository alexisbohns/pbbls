import SwiftUI

/// The glint's light (#1130 R12): white in both schemes. It is the light that
/// falls on the stone, not a chrome colour, so it lives with the stone's other
/// literal tones (`StonePalette.blank`) in `Stone/`, which `ThemeLiteralsTests`
/// does not scan.
enum StoneGlintInk {
    static let light = Color.white
    /// The web `StoneSprite`'s gradient peaks.
    static let sheenPeak = 0.75
    static let sweepPeak = 0.72

    /// Gem 1, river 0.30, lava 0.34 (web `stone-tones.ts`).
    static func strength(_ polarity: ValencePolarity) -> Double {
        switch polarity {
        case .highlight: return 1
        case .neutral: return 0.3
        case .lowlight: return 0.34
        }
    }
}
