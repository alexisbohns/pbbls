import SwiftUI

/// The About Pebbles section (spec §1.5, #1382 D14): the section head, then one
/// link row per published guide, each a push to its page. No box, no dividers:
/// each row is a chip stone holding the guide's own mark, its title and its
/// one-line summary.
struct GuidesList: View {
    /// nil until the first read lands; the head is static either way (spec §1.6).
    let guides: [Guide]?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            PebblesSectionHead(String(localized: "home.about.title"))
            if let guides, !guides.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(guides) { guide in
                        GuideRow(guide: guide)
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
            PebblesLinkRow(title: guide.title(for: locale), line: guide.summary(for: locale),
                           chip: .view(AnyView(GuideIllustrationView(kind: guide.illustration, size: .mark))))
        }
        .buttonStyle(.plain)
    }
}
