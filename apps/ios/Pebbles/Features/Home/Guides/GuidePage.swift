import SwiftUI

/// One About Pebbles reading, pushed from Home (spec §1.5): the title in the
/// large bar, then the summary, the picture and the body's blocks.
struct GuidePage: View {
    let guide: Guide

    var body: some View {
        ScrollView {
            GuidePageContent(guide: guide)
                .padding(.top, Spacing.sm)
                .padding(.bottom, MonthLayout.pageBottom)
        }
        .pebblesScreen()
        .navigationTitle(guide.title(for: SpineStrings.appLocale))
        .navigationBarTitleDisplayMode(.large)
    }
}

/// The page's body alone, so the reference pins it without a navigation bar.
struct GuidePageContent: View {
    let guide: Guide

    var body: some View {
        let locale = SpineStrings.appLocale
        VStack(alignment: .leading, spacing: Spacing.md) {
            Text(verbatim: guide.summary(for: locale))
                .spineType(.summary)
                .italic()
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            if guide.illustration != nil {
                GuideIllustrationView(kind: guide.illustration, size: .page)
            }
            MarkdownBlocksView(markdown: guide.body(for: locale))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.lg)
    }
}
