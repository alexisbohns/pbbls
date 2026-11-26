#if DEBUG
import SwiftUI

/// The nine Spine stones (size × polarity) on the brand palette, then the blank
/// stone, at their phone widths with their rings and halo: the lab's gallery and
/// the `gallery` / `midPress` references. `pressable` makes each one a button.
///
/// `vector` puts the new vector stone (#1690, `LitStoneImage`) under each metal one,
/// same valence, same seed, brand palette, for a visual check before the switch-over.
/// Off for the references, so they do not move.
struct SpineStoneGallery: View {
    var pressable = false
    var frozenAt: Double?
    var vector = false
    /// The same stone with six seeds: the crust varies by pebble (`seeds` reference).
    var seeds = false

    var body: some View {
        VStack(spacing: 28) {
            if seeds {
                // Two rows of three: six 80 pt cells do not fit across 393 pt.
                ForEach(0..<2, id: \.self) { row in
                    HStack(spacing: 28) {
                        ForEach(0..<3, id: \.self) { column in
                            SpineStoneCell(valence: .neutralLarge, isBlank: false,
                                           seed: StableHash.fnv1a("seed-\(row * 3 + column)"),
                                           pressable: pressable, frozenAt: frozenAt)
                        }
                    }
                }
            } else {
                ForEach(ValenceSizeGroup.allCases) { size in
                    HStack(spacing: 28) {
                        ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                            if let valence = Valence.allCases.first(where: {
                                $0.sizeGroup == size && $0.polarity == polarity
                            }) {
                                let seed = StableHash.fnv1a("\(valence)")
                                VStack(spacing: 28) {
                                    SpineStoneCell(valence: valence, isBlank: false, seed: seed,
                                                   pressable: pressable, frozenAt: frozenAt)
                                    if vector {
                                        VectorStoneCell(valence: valence, isBlank: false, seed: seed)
                                    }
                                }
                            }
                        }
                    }
                }
                HStack(spacing: 28) {
                    SpineStoneCell(valence: .neutralMedium, isBlank: true, seed: StableHash.fnv1a("blank"),
                                   pressable: pressable, frozenAt: frozenAt)
                    if vector {
                        VectorStoneCell(valence: .neutralMedium, isBlank: true, seed: StableHash.fnv1a("blank"))
                    }
                }
            }
        }
        .padding(.vertical, 56)
        .frame(maxWidth: .infinity)
        .background(Color.pebbles.surface)
    }
}

/// The vector stone at the Spine's width for its size, in the same 80 pt cell as the metal one.
private struct VectorStoneCell: View {
    let valence: Valence
    let isBlank: Bool
    let seed: UInt32

    var body: some View {
        // `LitStoneImage` reads the scheme and Low Power Mode itself; the spec's own are not used.
        let spec = LitStoneSpec(size: valence.sizeGroup, polarity: valence.polarity,
                                palette: isBlank ? nil : .brand, glyph: nil, seed: seed,
                                night: false, flat: false)
        LitStoneImage(spec: spec)
            .frame(width: SpineLayout.stoneSize(valence.sizeGroup).width)
            .frame(width: 80, height: 80)
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
