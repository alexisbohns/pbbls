import SwiftUI

/// The Spine's stone (#1130 D1, R2–R3, R12–R14), bottom to top: the halo and
/// the rings, the ripple (mounted only while a press is live), the existing lit
/// stone at rest light, then the glint. It lays out at the stone's phone size;
/// the halo, the rings and the ripple overflow it unclipped (D4).
///
/// The press is a value (`StonePress`) the caller owns as `@State` and clears
/// with `.stonePressLifetime`. The timeline runs only while there is one.
struct SpineStone: View {
    let valence: Valence
    let glyph: PebbleGlyph?
    let palette: EmotionPalette?
    let isBlank: Bool
    let seed: UInt32
    var press: StonePress?
    /// References and the lab: one instant of a press, without a timeline.
    var frozenAt: Double?
    /// How strongly the halo, the rings and the ripple draw, 0…1: the Spine's at 1, quieter elsewhere (a month's
    /// milestones, #1241 D16).
    var rings: Double = 1

    init(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, isBlank: Bool, seed: UInt32,
         press: StonePress? = nil, frozenAt: Double? = nil, rings: Double = 1) {
        self.valence = valence
        self.glyph = glyph
        self.palette = palette
        self.isBlank = isBlank
        self.seed = seed
        self.press = press
        self.frozenAt = frozenAt
        self.rings = rings
    }

    /// A moment's stone: its pebble's valence, glyph and seed, or the blank stone.
    init(moment: MomentSummary, palette: EmotionPalette?, press: StonePress? = nil, frozenAt: Double? = nil,
         rings: Double = 1) {
        self.init(valence: moment.pebble?.valence ?? .neutralMedium, glyph: moment.pebble?.glyph, palette: palette,
                  isBlank: moment.pebble == nil, seed: StableHash.seed(moment.pebble?.id ?? moment.id),
                  press: press, frozenAt: frozenAt, rings: rings)
    }

    private var size: ValenceSizeGroup { valence.sizeGroup }

    var body: some View {
        // One TimelineView whatever the press, so the lit stone keeps its identity
        // (and its tilt state) when a press starts and ends.
        TimelineView(.animation(minimumInterval: nil, paused: press == nil || frozenAt != nil)) { context in
            layers(frame(at: context.date))
        }
    }

    private func frame(at date: Date) -> StoneMotion.Frame {
        if let frozenAt { return StoneMotion.frame(at: frozenAt, still: false, size: size) }
        guard let press else { return StoneMotion.Frame() }
        return StoneMotion.frame(at: press.elapsed(at: date), still: press.still, size: size)
    }

    private func layers(_ frame: StoneMotion.Frame) -> some View {
        let stone = SpineLayout.stoneSize(size)
        let geometry = StoneRingGeometry.forStone(valence)
        return ZStack {
            if let geometry {
                StoneRings(geometry: geometry, stone: stone, scale: frame.rings, still: frame.stillRings)
                    .opacity(rings)
                if !frame.waves.isEmpty {
                    StoneRipple(geometry: geometry, stone: stone, waves: frame.waves,
                                reach: StoneMotion.Wave.reach[size] ?? 5.6)
                        .opacity(rings)
                }
            }
            litStone
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

    @ViewBuilder
    private var litStone: some View {
        if isBlank {
            PebbleStoneView.blank(seed: seed)
        } else {
            PebbleStoneView(valence: valence, glyph: glyph, palette: palette, seed: seed)
        }
    }
}
