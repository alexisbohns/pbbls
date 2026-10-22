import SwiftUI

/// The verso's lit stone (#1146 §Verso), 150 pt wide when large, in its rings:
/// stack D's `SpineStone` layers at the verso's size, so the print can press it
/// in with the Spine's dip, ripple and glint (E3). Drawn at its own width, never
/// scaled up (the lit stone is a shader layer). The tilt stays off on the card.
struct VersoStone: View {
    let valence: Valence
    let glyph: PebbleGlyph?
    let palette: EmotionPalette?
    let seed: UInt32
    var press: StonePress?

    private var size: ValenceSizeGroup { valence.sizeGroup }

    var body: some View {
        TimelineView(.animation(minimumInterval: nil, paused: press == nil)) { context in
            layers(frame(at: context.date))
        }
    }

    private func frame(at date: Date) -> StoneMotion.Frame {
        guard let press else { return StoneMotion.Frame() }
        return StoneMotion.frame(at: press.elapsed(at: date), still: press.still, size: size)
    }

    private func layers(_ frame: StoneMotion.Frame) -> some View {
        let stone = CardLayout.versoStoneSize(size)
        // Fitted to the verso's size: the Spine's geometry is in its own points.
        let geometry = StoneRingGeometry.forStone(valence, in: stone)
        let reach = (StoneMotion.Wave.reach[size] ?? 5.6) * stone.width / SpineLayout.stoneWidth(size)
        return ZStack {
            if let geometry {
                StoneRings(geometry: geometry, stone: stone, scale: frame.rings, still: frame.stillRings)
                if !frame.waves.isEmpty {
                    StoneRipple(geometry: geometry, stone: stone, waves: frame.waves, reach: reach)
                }
            }
            PebbleStoneView(valence: valence, glyph: glyph, palette: palette, followsTilt: false, seed: seed)
                .frame(width: stone.width, height: stone.height)
                .scaleEffect(frame.dip)
            if let geometry {
                StoneGlint(outline: geometry.outline, stone: stone, frame: frame,
                           strength: StoneGlintInk.strength(valence.polarity))
                    .scaleEffect(frame.dip)
            }
        }
        .frame(width: stone.width, height: stone.height)
    }
}
