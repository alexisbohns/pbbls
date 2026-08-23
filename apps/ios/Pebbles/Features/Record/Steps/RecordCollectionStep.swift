import SwiftUI

/// Step 7 — which collection, if any. Single-select, so a tap commits and
/// advances; `Skip` is how the user says none (D3).
///
/// No inline creation: `CreateCollectionSheet` lives in Profile, and adding a
/// second creation entry point here is out of scope for the flow.
struct RecordCollectionStep: View {
    let model: RecordFlowModel

    @Environment(ReferenceDataService.self) private var refs

    var body: some View {
        if refs.collections.isEmpty {
            Text("You don't have any collections yet.")
                .pebblesFont(.callout)
                .foregroundStyle(Color.system.secondary)
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
                    .foregroundStyle(isSelected ? Color.accent.primary : Color.system.secondary)
                // Collection names are user-authored, so never localized.
                Text(verbatim: collection.name)
                    .pebblesFont(.body)
                    .foregroundStyle(isSelected ? Color.accent.primary : Color.system.foreground)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? Color.accent.primary.opacity(0.12) : Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: collection.name))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
