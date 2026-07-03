import SwiftUI

struct ProfileBanner: View {
    let displayName: String?
    let memberSince: Date?
    let glyphStrokes: [GlyphStroke]?

    var body: some View {
        GlyphBanner(
            strokes: glyphStrokes,
            title: displayName ?? "",
            titleFont: .largeTitleHand,
            subtitle: memberSince.map {
                .meta(String(localized: "Member since \($0.formatted(.dateTime.month(.wide).year()))"))
            }
        )
    }
}

#Preview("With glyph (placeholder strokes)") {
    ProfileBanner(displayName: "Alexis", memberSince: Date(), glyphStrokes: nil)
        .padding()
}

#Preview("With glyph") {
    ProfileBanner(
        displayName: "Alexis",
        memberSince: Date(),
        glyphStrokes: [GlyphStroke(d: "M40,40 L160,160", width: 6)]
    )
    .padding()
}
