import SwiftUI

struct ProfileBanner: View {
    let displayName: String?
    let memberSince: Date?
    let glyphStrokes: [GlyphStroke]?

    var body: some View {
        VStack(spacing: 12) {
            glyph

            VStack(spacing: 2) {
                Text(displayName ?? "")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(Color.system.foreground)
                if let memberSince {
                    Text("Member since \(memberSince.formatted(.dateTime.month(.wide).year()))")
                        .font(.caption)
                        .foregroundStyle(Color.system.secondary)
                        .textCase(.uppercase)
                }
            }
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private var glyph: some View {
        if let strokes = glyphStrokes, !strokes.isEmpty {
            GlyphView(case: .profile, strokes: strokes, side: 96)
        } else {
            GlyphView(case: .carve, side: 96)
        }
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
