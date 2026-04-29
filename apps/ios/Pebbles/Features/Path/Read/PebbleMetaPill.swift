import SwiftUI

/// Compact pill used in the pebble read view to show emotion, domain,
/// collections, and souls inline. Variants:
///
/// - `.emotion(color:)`: filled pill in the emotion's color, white icon and
///   label. Always used for the emotion pill.
/// - `.neutral`: muted surface fill with normal foreground. Used for set
///   domain/collections/souls.
/// - `.unset`: same neutral fill plus a 1pt dashed stroke and muted
///   foreground. Used when domain is missing.
///
/// Spacing/sizing constants match the spec at
/// `docs/superpowers/specs/2026-04-29-ios-pebble-read-view-polish-design.md`.
struct PebbleMetaPill: View {
    enum Icon {
        case system(String)
        case glyph(Glyph)
    }

    enum Style: Equatable {
        case emotion(color: Color)
        case neutral
        case unset
    }

    let icon: Icon
    let label: LocalizedStringResource
    let style: Style

    var body: some View {
        HStack(spacing: 6) {
            iconView
                .frame(width: 16, height: 16)
                .foregroundStyle(foreground)
                .accessibilityHidden(true)
            Text(label)
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundStyle(foreground)
                .lineLimit(1)
        }
        .padding(.horizontal, 12)
        .frame(height: 32)
        .background(background)
        .overlay(strokeOverlay)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var iconView: some View {
        switch icon {
        case .system(let name):
            Image(systemName: name)
                .resizable()
                .scaledToFit()
        case .glyph(let glyph):
            GlyphThumbnail(
                strokes: glyph.strokes,
                side: 16,
                strokeColor: foreground,
                backgroundColor: .clear
            )
        }
    }

    @ViewBuilder
    private var background: some View {
        switch style {
        case .emotion(let color):
            Capsule().fill(color)
        case .neutral, .unset:
            Capsule().fill(Color.pebblesAccentSoft)
        }
    }

    @ViewBuilder
    private var strokeOverlay: some View {
        if case .unset = style {
            Capsule()
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1, dash: [3])
                )
        }
    }

    private var foreground: Color {
        switch style {
        case .emotion: return .white
        case .neutral: return Color.pebblesForeground
        case .unset:   return Color.pebblesMutedForeground
        }
    }
}

// MARK: - Hex color helper

/// Parses `#RRGGBB` strings stored on `EmotionRef.color`. Falls back to
/// `nil` if the format is unexpected — caller decides on a default.
extension Color {
    init?(hex: String) {
        var trimmed = hex.trimmingCharacters(in: .whitespaces)
        if trimmed.hasPrefix("#") { trimmed.removeFirst() }
        guard trimmed.count == 6, let value = UInt32(trimmed, radix: 16) else {
            return nil
        }
        let red   = Double((value >> 16) & 0xFF) / 255.0
        let green = Double((value >> 8) & 0xFF) / 255.0
        let blue  = Double(value & 0xFF) / 255.0
        self.init(red: red, green: green, blue: blue)
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 12) {
        PebbleMetaPill(
            icon: .system("heart.fill"),
            label: "Anxiety",
            style: .emotion(color: Color(red: 0.5, green: 0.4, blue: 0.95))
        )
        PebbleMetaPill(
            icon: .system("square.grid.2x2"),
            label: "Family",
            style: .neutral
        )
        PebbleMetaPill(
            icon: .system("folder.fill"),
            label: "Writing, Books",
            style: .neutral
        )
        PebbleMetaPill(
            icon: .system("square.grid.2x2"),
            label: "No domain",
            style: .unset
        )
        PebbleMetaPill(
            icon: .glyph(Glyph(
                id: UUID(),
                name: nil,
                strokes: [GlyphStroke(d: "M30,30 L170,170", width: 12)],
                viewBox: "0 0 200 200",
                userId: nil
            )),
            label: "Thierry",
            style: .neutral
        )
    }
    .padding()
    .background(Color.pebblesBackground)
}
