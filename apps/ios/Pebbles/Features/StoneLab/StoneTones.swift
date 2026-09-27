#if DEBUG
import Foundation
import SwiftUI

/// Which palette slot each colour role takes, and at what opacity. The palette
/// itself is chosen on the page; the tones only say "body is `dark`".
struct StoneTones: Equatable {
    struct Pick: Equatable {
        var slot: StoneLabPalette.Slot
        var opacity: Double
    }

    var body: Pick
    var ink: Pick
    var lipLight: Pick
    var lipShadow: Pick

    static func starting(for polarity: ValencePolarity) -> StoneTones {
        switch polarity {
        case .lowlight:
            return StoneTones(body: Pick(slot: .dark, opacity: 1), ink: Pick(slot: .shaded, opacity: 1),
                              lipLight: Pick(slot: .secondary, opacity: 0.35), lipShadow: Pick(slot: .shaded, opacity: 1))
        case .neutral:
            return StoneTones(body: Pick(slot: .secondary, opacity: 1), ink: Pick(slot: .dark, opacity: 1),
                              lipLight: Pick(slot: .light, opacity: 1), lipShadow: Pick(slot: .dark, opacity: 0.45))
        case .highlight:
            return StoneTones(body: Pick(slot: .primary, opacity: 1), ink: Pick(slot: .dark, opacity: 1),
                              lipLight: Pick(slot: .light, opacity: 1), lipShadow: Pick(slot: .dark, opacity: 1))
        }
    }

    /// Straight (non-premultiplied) RGB for the shader.
    func rgb(_ pick: Pick, in palette: StoneLabPalette) -> SIMD3<Float> { palette.rgb(pick.slot) }

    func color(_ pick: Pick, in palette: StoneLabPalette) -> Color {
        palette.color(pick.slot).opacity(pick.opacity)
    }
}
#endif
