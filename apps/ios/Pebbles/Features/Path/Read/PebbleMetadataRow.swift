import SwiftUI

/// Horizontal row used in the pebble read view: a boxed leading icon followed
/// by a label. Three style variants drive how the icon box is filled and
/// whether the row reads as "set", "unset" (dashed border, muted), or
/// "emotion" (filled with the emotion's color, white icon).
///
/// Reused by emotion, domain, collections, and each soul row.
struct PebbleMetadataRow: View {
    enum Icon {
        case system(String)
        case glyph(Glyph)
    }

    enum Style {
        case unset
        case set
        case emotion(color: Color)
    }

    let icon: Icon
    let label: LocalizedStringResource
    let style: Style

    var body: some View {
        HStack(spacing: 12) {
            iconBox
            Text(label)
                .font(.body)
                .foregroundStyle(labelForeground)
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
        .accessibilityValue(accessibilityValueText)
    }

    @ViewBuilder
    private var iconBox: some View {
        ZStack {
            backgroundShape
            iconContent
                .foregroundStyle(iconForeground)
                .frame(width: 18, height: 18)
        }
        .frame(width: 36, height: 36)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private var backgroundShape: some View {
        switch style {
        case .unset:
            RoundedRectangle(cornerRadius: 8)
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1, dash: [3])
                )
        case .set:
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.pebblesAccentSoft)
        case .emotion(let color):
            RoundedRectangle(cornerRadius: 8)
                .fill(color)
        }
    }

    @ViewBuilder
    private var iconContent: some View {
        switch icon {
        case .system(let name):
            Image(systemName: name)
                .resizable()
                .scaledToFit()
        case .glyph(let glyph):
            GlyphThumbnail(
                strokes: glyph.strokes,
                side: 18,
                strokeColor: iconForeground,
                backgroundColor: .clear
            )
        }
    }

    private var iconForeground: Color {
        switch style {
        case .unset:   return Color.pebblesMutedForeground
        case .set:     return Color.pebblesAccent
        case .emotion: return .white
        }
    }

    private var labelForeground: Color {
        switch style {
        case .unset:           return Color.pebblesMutedForeground
        case .set, .emotion:   return Color.pebblesForeground
        }
    }

    private var accessibilityValueText: Text {
        switch style {
        case .unset: return Text("Not set", comment: "Accessibility value for an unset pebble metadata row")
        case .set, .emotion: return Text("")
        }
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 16) {
        PebbleMetadataRow(
            icon: .system("heart.fill"),
            label: "Joy",
            style: .emotion(color: Color(red: 1.0, green: 0.8, blue: 0.0))
        )
        PebbleMetadataRow(
            icon: .system("leaf.fill"),
            label: "Family, Travel",
            style: .set
        )
        PebbleMetadataRow(
            icon: .system("sparkles"),
            label: "No domain",
            style: .unset
        )
        PebbleMetadataRow(
            icon: .glyph(Glyph(
                id: UUID(),
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 12)],
                viewBox: "0 0 200 200",
                userId: nil
            )),
            label: "Alex",
            style: .set
        )
    }
    .padding()
    .background(Color.pebblesBackground)
}
