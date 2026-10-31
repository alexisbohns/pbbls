import SwiftUI

/// Presentational tile: a pebbles icon above a short label on a `primaryContainer`
/// rounded card: the glyph drawer's stat cards.
///
/// `muted` renders the icon + label in `outline` for placeholder values
/// (e.g. the "Soon" stats we don't source yet).
///
/// `backgroundColor` / `iconTint` / `labelColor` override the default chrome
/// colors — the pebble read page tints its tiles to the emotion palette (#605).
/// Each is `nil` by default, reproducing the accent-surface chrome elsewhere.
/// `muted` still wins for the icon/label so an empty placeholder reads muted
/// even on a tinted background.
struct SurfaceTile<Label: View>: View {
    let icon: PbblsIcon
    var muted: Bool = false
    var backgroundColor: Color? = nil
    var iconTint: Color? = nil
    var labelColor: Color? = nil
    @ViewBuilder let label: () -> Label

    var body: some View {
        VStack(spacing: Spacing.xs) {
            PbblsIconImage(
                icon, .tile,
                style: AnyShapeStyle(muted ? Color.pebbles.outline : (iconTint ?? Color.pebbles.onPrimaryContainer))
            )
                // Fixed icon slot so tiles stay equal height (Figma tile spec, 30×30).
                .frame(width: 30, height: 30)
            label()
                .pebblesFont(.callout)
                .foregroundStyle(muted ? Color.pebbles.outline : (labelColor ?? Color.pebbles.onPrimaryContainer))
                // Clamp to one line so tiles stay equal height even when a label
                // (e.g. multiple joined domains/collections) would otherwise wrap.
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, Spacing.md)
        .background(backgroundColor ?? Color.pebbles.primaryContainer)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.lg))
    }
}

#Preview {
    HStack(spacing: Spacing.sm) {
        SurfaceTile(icon: .calendarDate) { Text(verbatim: "Jul 2026") }
        SurfaceTile(icon: .chartDoubleGrow, muted: true) { Text(verbatim: "Soon") }
        SurfaceTile(icon: .people, muted: true) { Text(verbatim: "Soon") }
    }
    .padding()
}
