import SwiftUI

/// The About Pebbles section (spec §1.5): the Spine's section header, then
/// one inset row per published guide, each a push to its page. The rows
/// carry the guide's mark, its title and its one-line summary.
struct GuidesList: View {
    /// nil until the first read lands; the header is static either way (spec §1.6).
    let guides: [Guide]?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SpineSectionHeader(String(localized: "home.about.title")) {}
            if let guides, !guides.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(Array(guides.enumerated()), id: \.element.id) { index, guide in
                        GuideRow(guide: guide)
                        if index < guides.count - 1 {
                            Rectangle().fill(Color.pebbles.rule).frame(height: 1)
                        }
                    }
                }
            }
        }
    }
}

private struct GuideRow: View {
    let guide: Guide

    var body: some View {
        let locale = SpineStrings.appLocale
        NavigationLink(value: HomeRoute.guide(guide.slug)) {
            HStack(alignment: .center, spacing: Spacing.md) {
                // The slot is kept for a guide without a mark, so the titles align.
                GuideIllustrationView(kind: guide.illustration, size: .mark)
                    .frame(width: GuideIllustrationView.markSide, height: GuideIllustrationView.markSide)
                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: guide.title(for: locale))
                        .pebblesFont(.headline)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text(verbatim: guide.summary(for: locale))
                        .spineType(.summary)
                        .italic()
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.pebbles.outline)
                    .accessibilityHidden(true)
            }
            .padding(.vertical, Spacing.sm)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}
