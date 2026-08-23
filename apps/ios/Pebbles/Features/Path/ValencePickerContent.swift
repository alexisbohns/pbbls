import SwiftUI

/// The valence grid: three `ValenceSizeGroup` sections, each holding the three
/// polarity options.
///
/// Presentation only (D5). Shared by `ValencePickerSheet`, which wraps it with
/// a Cancel toolbar and dismisses on pick, and the record flow's valence step,
/// which commits on tap and advances. Same grid, different commit semantics.
struct ValencePickerContent: View {
    let selected: Valence?
    let onSelect: (Valence) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            ForEach(ValenceSizeGroup.allCases) { group in
                section(for: group)
            }
        }
    }

    @ViewBuilder
    private func section(for group: ValenceSizeGroup) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(group.name)
                .font(.headline)
                .foregroundStyle(Color.system.secondary)

            Text(group.description)
                .font(.subheadline)
                .foregroundStyle(Color.system.secondary)

            HStack(spacing: 12) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    if let option = valence(in: group, polarity: polarity) {
                        optionButton(for: option, in: group)
                    }
                }
            }
        }
    }

    /// The single `Valence` case at a given (size, polarity) cell.
    /// Lookup uniqueness is guaranteed by `ValenceHelpersTests.lookupIsUnique`.
    private func valence(in group: ValenceSizeGroup, polarity: ValencePolarity) -> Valence? {
        Valence.allCases.first { $0.sizeGroup == group && $0.polarity == polarity }
    }

    @ViewBuilder
    private func optionButton(for option: Valence, in group: ValenceSizeGroup) -> some View {
        let isActive = (option == selected)

        Button {
            onSelect(option)
        } label: {
            VStack(spacing: 8) {
                Image(option.assetName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                    .foregroundStyle(isActive ? Color.system.background : Color.system.secondary)

                Text(option.shortLabel)
                    .font(.footnote)
                    .foregroundStyle(isActive ? Color.system.background : Color.system.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(isActive ? Color.accent.primary : Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(String(localized: group.name)), \(String(localized: option.shortLabel))"))
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
    }
}

#Preview("nothing selected") {
    ValencePickerContent(selected: nil, onSelect: { _ in }).padding()
}

#Preview("highlightMedium selected") {
    ValencePickerContent(selected: .highlightMedium, onSelect: { _ in }).padding()
}
