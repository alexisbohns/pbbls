import SwiftUI

/// The soul grid: a `.create` tile followed by one `SoulItem` per soul.
///
/// Presentation only (D5) — it takes a list rather than fetching, so the sheet
/// can keep its own fetch (the form's cached list goes stale behind it) while
/// the record flow's souls step reads `ReferenceDataService.souls`, which is
/// already cached and already refreshed after Profile mutations.
///
/// Selection rule (issue #459): with nothing selected every row renders
/// `.default`; as soon as one soul is selected, selected rows render
/// `.selected` and every other renders `.unselected`. The `.create` tile is
/// unaffected by selection.
struct SoulPickerContent: View {
    let souls: [SoulWithGlyph]
    let selection: Set<UUID>
    let onToggle: (UUID) -> Void
    let onCreate: () -> Void

    private let columns = [GridItem(.adaptive(minimum: 96), spacing: Spacing.lg)]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            Text("All my souls")
                .pebblesFont(.cardHeading)
                .foregroundStyle(Color.system.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            LazyVGrid(columns: columns, spacing: Spacing.lg) {
                SoulItem(case: .create, soul: nil, count: nil, onTap: onCreate)
                ForEach(souls) { soul in
                    SoulItem(
                        case: itemCase(for: soul.id),
                        soul: soul,
                        count: soul.pebblesCount
                    ) {
                        onToggle(soul.id)
                    }
                }
            }

            if souls.isEmpty {
                Text("Add the first soul to tag this pebble with")
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.system.secondary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private func itemCase(for id: UUID) -> SoulItem.Case {
        if selection.isEmpty { return .default }
        return selection.contains(id) ? .selected : .unselected
    }
}
