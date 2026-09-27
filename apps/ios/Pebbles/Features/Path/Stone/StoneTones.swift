import Foundation
import SwiftUI

/// Which palette slot each colour role takes, and at what opacity. The palette
/// itself is chosen on the page; the tones only say "body is `dark`".
struct StoneTones: Equatable, Codable {
    struct Pick: Equatable, Codable {
        var slot: StonePalette.Slot
        var opacity: Double
    }

    var body: Pick
    var ink: Pick
    var lipLight: Pick
    var lipShadow: Pick

    /// The maintainer's picks on the phone (2026-09-27, second round). `dark`
    /// is the darkest tone and `shaded` the mid one.
    static func starting(for polarity: ValencePolarity) -> StoneTones {
        switch polarity {
        case .lowlight:
            return StoneTones(body: Pick(slot: .dark, opacity: 1), ink: Pick(slot: .dark, opacity: 1),
                              lipLight: Pick(slot: .secondary, opacity: 1), lipShadow: Pick(slot: .shaded, opacity: 1))
        case .neutral:
            return StoneTones(body: Pick(slot: .shaded, opacity: 1), ink: Pick(slot: .dark, opacity: 1),
                              lipLight: Pick(slot: .secondary, opacity: 1), lipShadow: Pick(slot: .secondary, opacity: 0.45))
        case .highlight:
            return StoneTones(body: Pick(slot: .primary, opacity: 1), ink: Pick(slot: .secondary, opacity: 1),
                              lipLight: Pick(slot: .light, opacity: 0.84), lipShadow: Pick(slot: .dark, opacity: 0.75))
        }
    }

    /// Straight (non-premultiplied) RGB for the shader.
    func rgb(_ pick: Pick, in palette: StonePalette, scheme: ColorScheme) -> SIMD3<Float> {
        palette.rgb(pick.slot, scheme: scheme)
    }

    func color(_ pick: Pick, in palette: StonePalette) -> Color {
        palette.color(pick.slot).opacity(pick.opacity)
    }
}
