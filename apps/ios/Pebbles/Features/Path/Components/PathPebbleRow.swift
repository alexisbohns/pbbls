import SwiftUI

/// Path-specific pebble row. Renders the row using the row's
/// emotion-category palette per spec
/// `docs/superpowers/specs/2026-05-09-ios-week-groups-emotion-rows-design.md`:
///
/// - Thumbnail: 56×56, RoundedRectangle radius 12, fill `palette.surface`,
///   glyph stroked in `palette.secondaryHex` (both schemes).
/// - Name: Ysabeau-SemiBold 17, foreground `palette.primary` in light /
///   `palette.light` in dark.
/// - Date+time: uppercased, tracked `.caption`, foreground = name color
///   at 50% opacity. Built from two `Date.FormatStyle` calls joined by a
///   literal middle-dot separator.
///
/// `PebbleRow` (in `Components/PebbleRow.swift`) is the canonical row used
/// by `SoulDetailView` and `CollectionDetailView`; do not generalize this
/// row — keep them separate so a Path tweak cannot regress the other two.
struct PathPebbleRow: View {
    let pebble: Pebble
    let onTap: () -> Void
    let onDelete: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme

    private static let thumbnailSize: CGFloat = 56
    private static let glyphInset: CGFloat = 8

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name)
                        .font(.custom("Ysabeau-SemiBold", size: 17))
                        .foregroundStyle(nameColor)
                    Text(formattedDateTime)
                        .font(.caption)
                        .tracking(1.0)
                        .textCase(.uppercase)
                        .foregroundStyle(nameColor.opacity(0.5))
                }
            }
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button(role: .destructive, action: onDelete) {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(thumbnailFill)
            if let svg = pebble.renderSvg {
                PebbleRenderView(svg: svg, strokeColor: glyphStrokeHex)
                    .padding(Self.glyphInset)
            }
        }
        .frame(width: Self.thumbnailSize, height: Self.thumbnailSize)
    }

    private var palette: EmotionPalette? {
        guard let emotionId = pebble.emotion?.id else { return nil }
        return palettes.palette(for: emotionId)
    }

    private var thumbnailFill: Color {
        palette?.surface ?? Color.pebblesAccent.opacity(0.15)
    }

    private var glyphStrokeHex: String? {
        // 6-digit hex — same trim rule as `EmotionPalette.strokeHex(for:)`,
        // because `PebbleRenderView` injects the value as text into the
        // raw SVG markup and SVGView does not parse the 8-digit form
        // reliably.
        guard let palette else { return Color.pebblesAccentHex }
        let hex = palette.secondaryHex
        return hex.count == 9 ? String(hex.prefix(7)) : hex
    }

    private var nameColor: Color {
        guard let palette else { return Color.pebblesForeground }
        return colorScheme == .dark ? palette.light : palette.primary
    }

    private var formattedDateTime: String {
        let date = pebble.happenedAt.formatted(
            .dateTime.weekday(.wide).day().month(.wide)
        )
        let time = pebble.happenedAt.formatted(.dateTime.hour().minute())
        return "\(date) · \(time)"
    }
}

#Preview {
    let supabase = SupabaseService()
    return List {
        Section {
            PathPebbleRow(
                pebble: Pebble(
                    id: UUID(),
                    name: "Sample pebble",
                    happenedAt: Date(),
                    renderSvg: nil,
                    emotion: nil
                ),
                onTap: {},
                onDelete: {}
            )
            .listRowBackground(Color.pebblesListRow)
        }
    }
    .environment(EmotionPaletteService(client: supabase.client))
}
