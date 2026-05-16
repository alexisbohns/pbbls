import SwiftUI

struct ProfileBanner: View {
    let displayName: String?
    let memberSince: Date?
    let glyphStrokes: [GlyphStroke]?

    var body: some View {
        VStack(spacing: 12) {
            glyph
                .frame(width: 96, height: 96)

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
            GlyphThumbnail(strokes: strokes, side: 96)
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.08))
                .overlay {
                    Image(systemName: "scribble")
                        .font(.title)
                        .foregroundStyle(Color.system.secondary)
                }
        }
    }
}

#Preview("With glyph (placeholder strokes)") {
    ProfileBanner(displayName: "Alexis", memberSince: Date(), glyphStrokes: nil)
        .padding()
}
