import SwiftUI

/// One valence stone in the picker: the vector stone (#1690) in the brand
/// palette, carved with the sample glyph and seeded by `LitStoneSeed.sample`, so
/// it is the same stone on every launch. Choosing one plays the Spine's press on
/// it once (D8): the dip and the glint, the rings and ripple left to the Spine.
/// The press is finite; once it ends nothing redraws, and the stone is a still
/// image again. Reduce Motion plays the still sheen instead of the sweep.
///
/// Low Power Mode draws the flat rendering (`LitStoneImage`).
struct ValenceStoneView: View {
    let valence: Valence
    /// On-screen height of the whole stone, backdrop included.
    let height: CGFloat
    /// Plays the press when it turns true.
    var isSelected: Bool = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.stoneSweeps) private var stoneSweeps
    /// The live press; nil when the stone is still.
    @State private var press: StonePress?

    private var size: ValenceSizeGroup { valence.sizeGroup }
    private var stone: CGSize {
        CGSize(width: height * PebbleOutlineGeometry.aspectRatio(for: size), height: height)
    }

    var body: some View {
        // One TimelineView whatever the press, so the lit stone keeps its identity
        // when a press starts and ends. It runs only while there is a press.
        TimelineView(.animation(minimumInterval: nil, paused: press == nil)) { context in
            layers(frame(at: context.date))
        }
        .frame(width: stone.width, height: stone.height)
        .stonePressLifetime($press)
        .onChange(of: isSelected, initial: true) { _, selected in
            guard selected, stoneSweeps else {
                press = nil
                return
            }
            press = StonePress.next(after: press, still: reduceMotion, now: .now)
        }
    }

    private func frame(at date: Date) -> StoneMotion.Frame {
        guard let press else { return StoneMotion.Frame() }
        return StoneMotion.frame(at: press.elapsed(at: date), still: press.still, size: size)
    }

    private func layers(_ frame: StoneMotion.Frame) -> some View {
        let geometry = StoneRingGeometry.forStone(valence, in: stone)
        return ZStack {
            // `LitStoneImage` reads the scheme and Low Power Mode itself; the spec's own are not used.
            LitStoneImage(spec: LitStoneSpec(size: size, polarity: valence.polarity, palette: .brand,
                                             glyph: LitStoneSampleGlyph.glyph, seed: LitStoneSeed.sample(valence),
                                             night: false, flat: false))
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

#Preview("the nine stones") {
    VStack(spacing: 24) {
        ForEach(ValenceSizeGroup.allCases) { group in
            HStack(spacing: 20) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    if let valence = Valence.allCases.first(
                        where: { $0.sizeGroup == group && $0.polarity == polarity }
                    ) {
                        ValenceStoneView(
                            valence: valence,
                            height: ValenceFanLayout.stoneHeight(for: group)
                        )
                    }
                }
            }
        }
    }
    .padding()
}
