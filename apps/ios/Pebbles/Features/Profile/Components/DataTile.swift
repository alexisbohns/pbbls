import SwiftUI

/// Single value/icon/label tile used inside the Profile Stats card.
/// vstack(xs) of:
///   - large counter number (counter.lg)
///   - hstack(xs) of icon (pebbles icon at tile, accent.primary) and label (subhead, system.secondary)
struct DataTile: View {
    let value: Int?
    let icon: PbblsIcon
    let label: LocalizedStringResource

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(value.map { "\($0)" } ?? "—")
                .pebblesFont(.counterLg)
                .foregroundStyle(Color.pebbles.onSurface)
                .monospacedDigit()
            HStack(spacing: Spacing.xs) {
                PbblsIconImage(icon, .tile, relativeTo: .subheadline, style: AnyShapeStyle(Color.pebbles.primary))
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    HStack {
        DataTile(value: 42, icon: .calendarDate, label: "Days")
        DataTile(value: 137, icon: .pebbleRound, label: "Pebbles")
        DataTile(value: 1200, icon: .karmaDot, label: "Karma")
    }
    .padding()
}
