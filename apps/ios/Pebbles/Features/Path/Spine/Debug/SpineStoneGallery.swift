#if DEBUG
import SwiftUI

/// The nine Spine stones (size × polarity) on the brand palette, then the blank
/// stone, at their phone widths with their rings and halo: the lab's gallery and
/// the `gallery` / `midPress` references. `pressable` makes each one a button.
struct SpineStoneGallery: View {
    var pressable = false
    var frozenAt: Double?
    /// The same stone with six seeds: the crust varies by pebble (`seeds` reference).
    var seeds = false

    var body: some View {
        VStack(spacing: 28) {
            if seeds {
                HStack(spacing: 20) {
                    ForEach(0..<6, id: \.self) { index in
                        SpineStoneCell(valence: .neutralLarge, isBlank: false, seed: StableHash.fnv1a("seed-\(index)"),
                                       pressable: pressable, frozenAt: frozenAt)
                    }
                }
            } else {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: 28) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            if let valence = Valence.allCases.first(where: {
                                $0.sizeGroup == size && $0.polarity == polarity
                            }) {
                                SpineStoneCell(valence: valence, isBlank: false, seed: StableHash.fnv1a("\(valence)"),
                                               pressable: pressable, frozenAt: frozenAt)
                            }
                        }
                    }
                }
                SpineStoneCell(valence: .neutralMedium, isBlank: true, seed: StableHash.fnv1a("blank"),
                               pressable: pressable, frozenAt: frozenAt)
            }
        }
        .padding(.vertical, 56)
        .frame(maxWidth: .infinity)
        .background(Color.pebbles.surface)
    }
}

private struct SpineStoneCell: View {
    let valence: Valence
    let isBlank: Bool
    let seed: UInt32
    let pressable: Bool
    let frozenAt: Double?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var press: StonePress?

    var body: some View {
        let stone = SpineStone(valence: valence, glyph: nil, palette: nil, isBlank: isBlank, seed: seed,
                               press: press, frozenAt: frozenAt)
            .frame(width: 80, height: 80)
        if pressable {
            Button {
                TapHaptics.play(.selection)
                press = .next(after: press, still: reduceMotion, now: .now)
            } label: {
                stone
            }
            .buttonStyle(.plain)
            .stonePressLifetime($press)
            .accessibilityLabel(Text(verbatim: isBlank ? "blank" : "\(valence)"))
        } else {
            stone
        }
    }
}
#endif
