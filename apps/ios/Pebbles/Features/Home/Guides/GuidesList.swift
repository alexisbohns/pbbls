import SwiftUI

/// The About Pebbles section (spec §1.5): the Spine's section header, then
/// one inset row per published guide, each a push to its page. The rows
/// carry the guide's mark, its title and its one-line summary.
struct GuidesList: View {
    let guides: [Guide]

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            SpineSectionHeader(String(localized: "home.about.title")) {}
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

private struct GuideRow: View {
    let guide: Guide

    var body: some View {
        let locale = SpineStrings.appLocale
        NavigationLink(value: HomeRoute.guide(guide.slug)) {
            HStack(alignment: .center, spacing: Spacing.md) {
                GuideIllustrationView(kind: guide.illustration, size: .mark)
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
            }
            .padding(.vertical, Spacing.sm)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
