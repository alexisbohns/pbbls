import SwiftUI

/// Which collection, if any. Single-select and optional: a tap commits and
/// advances, `Skip` says none. No inline creation (A1).
struct CompleteCollectionStep: View {
    let model: CompleteFlowModel

    @Environment(ReferenceDataService.self) private var refs

    var body: some View {
        if refs.collections.isEmpty {
            Text("You don't have any collections yet.")
                .pebblesFont(.callout)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.vertical, Spacing.xxl)
        } else {
            LazyVStack(spacing: Spacing.sm) {
                ForEach(refs.collections) { collection in
                    row(for: collection)
                }
            }
        }
    }

    @ViewBuilder
    private func row(for collection: PebbleCollection) -> some View {
        let isSelected = (collection.id == model.draft.collectionId)

        Button {
            model.select(collectionId: collection.id)
        } label: {
            HStack(spacing: Spacing.md) {
                Image(systemName: "square.stack")
                    .foregroundStyle(isSelected ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant)
                // Collection names are user-authored, so never localized.
                Text(verbatim: collection.name)
                    .pebblesFont(.body)
                    .foregroundStyle(isSelected ? Color.pebbles.primary : Color.pebbles.onSurface)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.pebbles.primary.opacity(0.12) : Color.pebbles.surfaceContainerHighest)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: collection.name))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
