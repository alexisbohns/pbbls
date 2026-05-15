import SwiftUI

/// Shared row view for a pebble in a list. Used by `PathView`,
/// `SoulDetailView`, and `CollectionDetailView` so the thumbnail +
/// name + date treatment stays consistent across the app.
///
/// The row owns the long-press contextual menu so any new list that
/// uses `PebbleRow` automatically gets the Delete affordance. The
/// parent owns the destructive flow itself: confirmation dialog,
/// error alert, the `delete_pebble` RPC call, and the reload.
///
/// `pebble` must be loaded with `render_svg` and the
/// `emotion:emotions(id, slug, name)` join populated for the
/// thumbnail to render correctly. The stroke color is resolved from
/// `EmotionPaletteService` — primary in light mode, secondary in dark.
/// When `render_svg` is nil the row falls back to a neutral rounded
/// rectangle.
struct PebbleRow: View {
    let pebble: Pebble
    let onTap: () -> Void
    let onDelete: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 4) {
                    Text(pebble.name).font(.body)
                    Text(pebble.happenedAt, style: .date)
                        .font(.caption)
                        .foregroundStyle(.secondary)
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
        if let svg = pebble.renderSvg {
            PebbleRenderView(svg: svg, strokeColor: strokeHex)
                .frame(width: 40, height: 40)
        } else {
            RoundedRectangle(cornerRadius: 6)
                .fill(Color.secondary.opacity(0.15))
                .frame(width: 40, height: 40)
        }
    }

    private var strokeHex: String? {
        guard let emotionId = pebble.emotion?.id else { return nil }
        return palettes.palette(for: emotionId)?.strokeHex(for: colorScheme)
            ?? Color.pebblesAccentHex
    }
}

#Preview {
    let supabase = SupabaseService()
    return List {
        PebbleRow(
            pebble: Pebble(
                id: UUID(),
                name: "Sample pebble",
                happenedAt: Date(),
                createdAt: Date(),
                intensity: 1,
                renderSvg: nil,
                emotion: nil,
                firstSnapPath: nil
            ),
            onTap: {},
            onDelete: {}
        )
    }
    .environment(EmotionPaletteService(client: supabase.client))
}
