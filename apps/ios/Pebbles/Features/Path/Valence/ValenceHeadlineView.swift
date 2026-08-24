import SwiftUI

/// The lockup under the fan naming the stone the user picked.
///
/// Two lines, typeset to carry both axes without spelling them out: the word
/// grows with the size group and takes the polarity's colour, so "Highlight"
/// wears its stone's mesh while "Lowlight" reads as ink, and small events drop
/// to lowercase.
struct ValenceHeadlineView: View {
    let valence: Valence
    /// The roll's faded neighbour words are the word alone: the span belongs to
    /// whichever valence is actually centred.
    var showsSpan: Bool = true

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Text(word)
                .pebblesFont(wordToken)
                .foregroundStyle(ValenceStoneStyle.headlineInk(for: valence.polarity))
                .padding(.horizontal, wordToken.inkOverhang)

            if showsSpan {
                Text(valence.headline.span)
                    .pebblesFont(.cardHeading)
                    .foregroundStyle(Color.system.secondary)
            }
        }
        .multilineTextAlignment(.center)
        .accessibilityElement(children: .combine)
    }

    /// Lowercased here rather than with `.textCase`: `pebblesFont` sets that
    /// environment value itself, and the token's `nil` wins over anything the
    /// call site layers on top of it.
    ///
    /// Padded with a space on each side so the hand font's terminal flick has
    /// advance width to live in — see `PebblesFont.needsInkPadding`. A space is
    /// what survives: it is part of the line the glyphs are clipped to, where a
    /// frame's padding is not.
    private var word: String {
        let raw = String(localized: valence.headline.word)
        let cased = valence.sizeGroup == .small ? raw.lowercased() : raw
        return wordToken.needsInkPadding ? " \(cased) " : cased
    }

    private var wordToken: PebblesFont {
        switch valence.sizeGroup {
        case .small:  return .valenceWordSmall
        case .medium: return .valenceWordMedium
        case .large:  return .valenceWordLarge
        }
    }
}

#Preview("all nine") {
    ScrollView {
        VStack(spacing: 28) {
            ForEach(Valence.allCases) { valence in
                ValenceHeadlineView(valence: valence)
            }
        }
        .padding()
    }
}
