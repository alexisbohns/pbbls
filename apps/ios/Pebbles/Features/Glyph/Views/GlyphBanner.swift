import SwiftUI

/// Centered identity banner: a glyph above a title + optional subtitle. Shared by
/// the profile header (`ProfileBanner` — glyph, display name, "Member since …")
/// and the glyph swap drawer (glyph, name, creator handle). Falls back to the
/// `.carve` placeholder when there are no strokes.
struct GlyphBanner: View {
    /// A byline renders its name half in the handwritten face (a name is
    /// always hand — issue #515); meta is a plain small-caps line.
    enum Subtitle {
        case meta(String)
        case byline(name: String)
    }

    let strokes: [GlyphStroke]?
    let title: String
    /// Token for the title. Profile uses `.largeTitleHand`; the glyph swap
    /// drawer keeps the serif `.title` for the glyph name.
    var titleFont: PebblesFont = .title
    let subtitle: Subtitle?

    var body: some View {
        VStack(spacing: Spacing.xxl) {
            glyph

            VStack(spacing: Spacing.xs) {
                Text(title)
                    .pebblesFont(titleFont)
                    .foregroundStyle(Color.system.foreground)
                subtitleView
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var subtitleView: some View {
        switch subtitle {
        case .none:
            EmptyView()
        case .meta(let text):
            Text(text)
                .pebblesFont(.meta)
                .foregroundStyle(Color.system.secondary)
        case .byline(let name):
            HStack(alignment: .firstTextBaseline, spacing: Spacing.xs) {
                Text("BY")
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.system.secondary)
                Text(name)
                    .pebblesFont(.bodyLeadHand)
                    .foregroundStyle(Color.system.secondary)
            }
        }
    }

    @ViewBuilder
    private var glyph: some View {
        if let strokes, !strokes.isEmpty {
            GlyphView(case: .profile, strokes: strokes, side: 96)
        } else {
            GlyphView(case: .carve, side: 96)
        }
    }
}

#Preview {
    GlyphBanner(
        strokes: [GlyphStroke(d: "M40,40 L160,160", width: 6)],
        title: "Creature",
        subtitle: .byline(name: "Galadriel")
    )
    .padding()
}
