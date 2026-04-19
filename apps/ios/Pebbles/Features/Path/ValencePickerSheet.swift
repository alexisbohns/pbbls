import SwiftUI

/// Sheet for picking a `Valence`, presented from `PebbleFormView`'s
/// "Valence" row. Three sections — one per `ValenceSizeGroup` — each
/// containing three options (lowlight / neutral / highlight). Tapping
/// an option writes back via `onSelected` and dismisses the sheet.
///
/// Pure UI: no Supabase calls, no async work. The nine options are
/// derived from `Valence.allCases` filtered by `(sizeGroup, polarity)`.
struct ValencePickerSheet: View {
    let currentValence: Valence?
    let onSelected: (Valence) -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    ForEach(ValenceSizeGroup.allCases) { group in
                        section(for: group)
                    }
                }
                .padding()
            }
            .navigationTitle("Choose a valence")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
            .pebblesScreen()
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private func section(for group: ValenceSizeGroup) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(group.name)
                .font(.headline)
                .foregroundStyle(Color.pebblesMutedForeground)

            Text(group.description)
                .font(.subheadline)
                .foregroundStyle(Color.pebblesMutedForeground)

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
        let isActive = (option == currentValence)

        Button {
            onSelected(option)
            dismiss()
        } label: {
            VStack(spacing: 8) {
                Image(option.assetName)
                    .renderingMode(.template)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 64, height: 64)
                    .foregroundStyle(isActive ? Color.pebblesBackground : Color.pebblesMutedForeground)

                Text(option.shortLabel)
                    .font(.footnote)
                    .foregroundStyle(isActive ? Color.pebblesBackground : Color.pebblesMutedForeground)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(isActive ? Color.pebblesAccent : Color.pebblesSurfaceAlt)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(group.name), \(option.shortLabel)")
        .accessibilityAddTraits(isActive ? [.isSelected] : [])
    }
}

#Preview("nothing selected") {
    Color.clear.sheet(isPresented: .constant(true)) {
        ValencePickerSheet(currentValence: nil, onSelected: { _ in })
    }
}

#Preview("highlightMedium selected") {
    Color.clear.sheet(isPresented: .constant(true)) {
        ValencePickerSheet(currentValence: .highlightMedium, onSelected: { _ in })
    }
}
