import SwiftUI

/// Centered identity banner: a glyph above a title + optional subtitle. Shared by
/// the profile header (`ProfileBanner` — glyph, display name, "Member since …")
/// and the glyph swap drawer (glyph, name, creator handle). Falls back to the
/// `.carve` placeholder when there are no strokes.
struct GlyphBanner: View {
    let strokes: [GlyphStroke]?
    let title: String
    let subtitle: String?

    var body: some View {
        VStack(spacing: Spacing.xxl) {
            glyph

            VStack(spacing: Spacing.xs) {
                Text(title)
                    .pebblesFont(.title)
                    .foregroundStyle(Color.system.foreground)
                if let subtitle {
                    Text(subtitle)
                        .pebblesFont(.meta)
                        .foregroundStyle(Color.system.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity)
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
        title: "Molly",
        subtitle: "BY @community"
    )
    .padding()
}
