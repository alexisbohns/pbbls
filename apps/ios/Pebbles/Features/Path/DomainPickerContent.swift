import SwiftUI

/// The domain picker: one row per domain carrying its glyph, localized name
/// and localized description. Mirrors the web `DomainSheet` row.
///
/// Single-select, presentation only. The record flow's domain step is the only
/// caller today — `PebbleFormView` keeps its menu `Picker`, which needs none of
/// this.
///
/// A domain with no default glyph (nil `strokes`) renders name and description
/// with the glyph slot left empty rather than substituting a placeholder mark:
/// an invented glyph would read as data.
struct DomainPickerContent: View {
    let domains: [Domain]
    let selected: UUID?
    let onSelect: (UUID) -> Void

    var body: some View {
        LazyVStack(spacing: Spacing.sm) {
            ForEach(domains) { domain in
                row(for: domain)
            }
        }
    }

    @ViewBuilder
    private func row(for domain: Domain) -> some View {
        let isSelected = (domain.id == selected)

        Button {
            onSelect(domain.id)
        } label: {
            HStack(spacing: Spacing.lg) {
                GlyphView(
                    case: isSelected ? .selected : .default,
                    strokes: domain.strokes,
                    side: 56
                )
                .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: domain.localizedName)
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(
                            isSelected ? Color.accent.primary : Color.system.foreground
                        )
                    // .subhead, not .meta: the `meta` token uppercases and
                    // letter-spaces, which is right for a label and wrong for a
                    // sentence-length description.
                    Text(verbatim: domain.localizedLabel)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 0)
            }
            .padding(.horizontal, Spacing.md)
            .padding(.vertical, Spacing.sm)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // Both strings are already resolved against the catalog, so they are
        // passed verbatim to avoid double-localization — same rule as
        // PebbleFormView's emotion row label.
        .accessibilityLabel(Text(verbatim: domain.localizedName))
        .accessibilityHint(Text(verbatim: domain.localizedLabel))
        .accessibilityAddTraits(isSelected ? [.isSelected] : [])
    }
}

#Preview {
    let strokes = [GlyphStroke(d: "M40 40 L 160 160 M160 40 L 40 160", width: 6)]
    let domains = [
        Domain(id: UUID(), slug: "health", name: "Health",
               label: "Your body, energy, and physical well-being",
               strokes: strokes, viewBox: "0 0 200 200"),
        Domain(id: UUID(), slug: "work", name: "Work",
               label: "Your job, career, and professional life",
               strokes: nil, viewBox: nil)
    ]
    return ScrollView {
        DomainPickerContent(domains: domains, selected: domains[0].id, onSelect: { _ in })
            .padding()
    }
    .background(Color.system.background)
}
