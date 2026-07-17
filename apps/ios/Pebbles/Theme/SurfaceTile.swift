import SwiftUI

/// Presentational tile: an SF Symbol above a short label on an `accent.surface`
/// rounded card. The shared visual for the profile shortcuts row
/// (`ProfileShortcutTile`) and the glyph drawer's stat cards.
///
/// `muted` renders the icon + label in `system.muted` for placeholder values
/// (e.g. the "Soon" stats we don't source yet).
///
/// `backgroundColor` / `iconTint` / `labelColor` override the default chrome
/// colors — the pebble read page tints its tiles to the emotion palette (#605).
/// Each is `nil` by default, reproducing the accent-surface chrome elsewhere.
/// `muted` still wins for the icon/label so an empty placeholder reads muted
/// even on a tinted background.
struct SurfaceTile<Label: View>: View {
    let systemImage: String
    var muted: Bool = false
    var backgroundColor: Color? = nil
    var iconTint: Color? = nil
    var labelColor: Color? = nil
    @ViewBuilder let label: () -> Label

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Image(systemName: systemImage)
                .pebblesIcon(.large)
                .foregroundStyle(muted ? Color.system.muted : (iconTint ?? Color.accent.primary))
                // Fixed icon slot so tiles stay equal height regardless of which
                // SF Symbol they carry (heart/tag/stack render at different
                // intrinsic heights). Matches the Figma tile spec (30×30).
                .frame(width: 30, height: 30)
            label()
                .pebblesFont(.callout)
                .foregroundStyle(muted ? Color.system.muted : (labelColor ?? Color.system.secondary))
                // Clamp to one line so tiles stay equal height even when a label
                // (e.g. multiple joined domains/collections) would otherwise wrap.
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.md)
        .background(backgroundColor ?? Color.accent.surface)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
    }
}

#Preview {
    HStack(spacing: Spacing.sm) {
        SurfaceTile(systemImage: "calendar") { Text(verbatim: "Jul 2026") }
        SurfaceTile(systemImage: "chart.bar.fill", muted: true) { Text(verbatim: "Soon") }
        SurfaceTile(systemImage: "person.2.fill", muted: true) { Text(verbatim: "Soon") }
    }
    .padding()
}
