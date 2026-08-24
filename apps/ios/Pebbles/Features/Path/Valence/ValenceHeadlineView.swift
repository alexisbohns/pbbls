import SwiftUI

/// The lockup under the fan naming the stone the user picked.
///
/// Three lines, typeset to carry the two axes without spelling them out: the
/// word grows with the size group and takes the polarity's colour, so
/// "Highlight" wears its stone's mesh while "Lowlight" reads as ink. Large
/// events get a prefix; small ones drop to lowercase.
struct ValenceHeadlineView: View {
    let valence: Valence

    var body: some View {
        VStack(spacing: Spacing.xs) {
            if let prefix = valence.headline.prefix {
                Text(prefix)
                    .pebblesFont(.cardHeadingEmphasized)
                    .foregroundStyle(Color.system.foreground)
            }

            Text(word)
                .pebblesFont(wordToken)
                .foregroundStyle(ValenceStoneStyle.headlineInk(for: valence.polarity))
                // Without this the hand font's last letter is clipped at the
                // text's advance width — see PebblesFont.inkOverhang.
                .padding(.horizontal, wordToken.inkOverhang)

            Text(valence.headline.span)
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.system.secondary)
        }
        .multilineTextAlignment(.center)
        .accessibilityElement(children: .combine)
    }

    /// Lowercased here rather than with `.textCase`: `pebblesFont` sets that
    /// environment value itself, and the token's `nil` wins over anything the
    /// call site layers on top of it.
    private var word: String {
        let word = String(localized: valence.headline.word)
        return valence.sizeGroup == .small ? word.lowercased() : word
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
