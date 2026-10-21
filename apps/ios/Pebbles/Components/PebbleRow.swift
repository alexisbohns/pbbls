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
                    MomentNameText(name: moment.name)
                        .font(.body)
                    Text(moment.happenedAt, style: .date)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
        .pebblesContextMenu([
            PebblesMenuItem(title: "Delete", systemImage: "trash", isDestructive: true, action: onDelete)
        ])
    }

    /// The lit stone (#974), at the rest light: the row never animates.
    private var thumbnail: some View {
        stone
            .frame(width: 40, height: 40)
    }

    /// The pebble's derived glyph carved in its stone, or the blank stone
    /// for an uncompleted moment (R4).
    @ViewBuilder
    private var stone: some View {
        if let pebble = moment.pebble {
            PebbleStoneView(valence: pebble.valence, glyph: pebble.glyph, palette: palette)
        } else {
            PebbleStoneView.blank()
        }
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
