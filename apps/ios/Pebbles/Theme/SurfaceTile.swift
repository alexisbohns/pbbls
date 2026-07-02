import SwiftUI

/// Presentational tile: an SF Symbol above a short label on an `accent.surface`
/// rounded card. The shared visual for the profile shortcuts row
/// (`ProfileShortcutTile`) and the glyph drawer's stat cards.
///
/// `muted` renders the icon + label in `system.muted` for placeholder values
/// (e.g. the "Soon" stats we don't source yet).
struct SurfaceTile<Label: View>: View {
    let systemImage: String
    var muted: Bool = false
    @ViewBuilder let label: () -> Label

    var body: some View {
        VStack(spacing: Spacing.xs) {
            Image(systemName: systemImage)
                .pebblesIcon(.large)
                .foregroundStyle(muted ? Color.system.muted : Color.accent.primary)
            label()
                .pebblesFont(.callout)
                .foregroundStyle(muted ? Color.system.muted : Color.system.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.md)
        .background(Color.accent.surface)
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
