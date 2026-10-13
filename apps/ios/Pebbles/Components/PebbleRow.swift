import SwiftUI

/// Shared row view for a moment in a list. Used by `SoulDetailView` and
/// `CollectionDetailView` so the thumbnail +
/// name + date treatment stays consistent across the app.
///
/// The row owns the long-press contextual menu so any new list that
/// uses `PebbleRow` automatically gets the Delete affordance. The
/// parent owns the destructive flow itself: confirmation dialog,
/// error alert, the delete call, and the reload.
///
/// `moment` is a `path_moments` row. Colors are sourced from
/// `palette.pebbleFrameColors(forIntensity:)` and are scheme-independent.
struct PebbleRow: View {
    let moment: MomentSummary
    let onTap: () -> Void
    let onDelete: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                thumbnail
                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: MomentName.display(moment.name) ?? String(localized: "Untitled"))
                        .font(.body)
                    Text(moment.happenedAt, style: .date)
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

    /// The lit stone (#974), at the rest light: the row never animates.
    private var thumbnail: some View {
        PebbleStoneView(valence: moment.pebble?.valence ?? .neutralMedium, renderSvg: nil, palette: palette)
            .frame(width: 40, height: 40)
    }

    private var palette: EmotionPalette? {
        moment.pebble?.emotion.flatMap { palettes.palette(for: $0.id) }
    }

    private var frameColors: PebbleFrameColors? {
        palette?.pebbleFrameColors(forIntensity: moment.pebble?.intensity ?? 2)
    }
}

#Preview {
    let supabase = SupabaseService()
    return List {
        PebbleRow(
            moment: MomentSummary(
                id: UUID(),
                name: "Sample moment",
                happenedAt: Date(),
                createdAt: Date(),
                firstSnapPath: nil,
                pebble: nil
            ),
            onTap: {},
            onDelete: {}
        )
    }
    .environment(EmotionPaletteService(client: supabase.client))
}
