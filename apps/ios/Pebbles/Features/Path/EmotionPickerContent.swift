import SwiftUI

/// The emotion grid, grouped into categories ordered by the composer's current
/// valence via `EmotionCategoryOrdering.order(for:)`.
///
/// Presentation only (D5). `EmotionPickerSheet` stages the selection and
/// commits on Done; the record flow's emotion step commits on tap and advances.
/// Reporting the tapped id and letting the caller decide is what allows both.
struct EmotionPickerContent: View {
    let selected: UUID?
    let valence: Valence?
    let onSelect: (UUID) -> Void

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        LazyVStack(alignment: .leading, spacing: 24) {
            if groups.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 32)
            } else {
                ForEach(groups) { group in
                    section(for: group)
                }
            }
        }
    }

    // MARK: - sections

    private struct CategoryGroup: Identifiable {
        let category: EmotionCategory
        let emotions: [EmotionWithPalette]
        var id: UUID { category.id }
    }

    /// Categories in valence-derived order; emotions inside each category
    /// sorted by their localized name (locale-aware).
    private var groups: [CategoryGroup] {
        let allRows = Array(palettes.byEmotionId.values)
        guard !allRows.isEmpty else { return [] }

        // Build category index: slug -> EmotionCategory. First row per category wins.
        var categoryBySlug: [String: EmotionCategory] = [:]
        for row in allRows where categoryBySlug[row.categorySlug] == nil {
            categoryBySlug[row.categorySlug] = EmotionCategory(
                id: row.categoryId,
                slug: row.categorySlug,
                name: row.categoryName,
                palette: row.palette
            )
        }

        // Group emotions by category slug.
        let emotionsBySlug = Dictionary(grouping: allRows, by: { $0.categorySlug })

        let order = EmotionCategoryOrdering.order(for: valence)
        return order.compactMap { slug in
            guard let category = categoryBySlug[slug],
                  let rows = emotionsBySlug[slug], !rows.isEmpty else {
                return nil
            }
            let sorted = rows.sorted {
                $0.localizedName.localizedCompare($1.localizedName) == .orderedAscending
            }
            return CategoryGroup(category: category, emotions: sorted)
        }
    }

    @ViewBuilder
    private func section(for group: CategoryGroup) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            header(for: group.category)

            LazyVGrid(
                columns: [
                    GridItem(.flexible(), spacing: 12),
                    GridItem(.flexible(), spacing: 12)
                ],
                spacing: 12
            ) {
                ForEach(group.emotions) { row in
                    chip(for: row, in: group.category)
                }
            }
        }
    }

    @ViewBuilder
    private func header(for category: EmotionCategory) -> some View {
        HStack(spacing: 6) {
            Image(systemName: "waveform.path.ecg")
                .foregroundStyle(category.palette.primary)
            Text(category.localizedName)
                .font(.caption2)
                .fontWeight(.semibold)
                .tracking(1.5)
                .textCase(.uppercase)
                .foregroundStyle(Color.system.secondary)
        }
    }

    @ViewBuilder
    private func chip(for row: EmotionWithPalette, in category: EmotionCategory) -> some View {
        let isSelected = (row.id == selected)
        Button {
            onSelect(row.id)
        } label: {
            HStack(spacing: 8) {
                Text(row.emoji)
                Text(row.localizedName)
                    .font(.subheadline)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(isSelected ? category.palette.primary : category.palette.surface)
            .foregroundStyle(isSelected ? category.palette.light : Color.system.foreground)
            .clipShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(row.localizedName))
        .accessibilityValue(Text(category.localizedName))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}
