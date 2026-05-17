import SwiftUI

/// Single value/icon/label tile used inside the Profile Stats card.
/// vstack(xs) of:
///   - large counter number (counter.lg)
///   - hstack(xs) of icon (icon.sm, accent.primary) and label (subhead, system.secondary)
struct DataTile: View {
    let value: Int?
    let icon: String
    let label: LocalizedStringResource

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xs) {
            Text(value.map { "\($0)" } ?? "—")
                .pebblesFont(.counterLg)
                .foregroundStyle(Color.system.foreground)
                .monospacedDigit()
            HStack(spacing: Spacing.xs) {
                Image(systemName: icon)
                    .pebblesIcon(.sm)
                    .foregroundStyle(Color.accent.primary)
                Text(label)
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

#Preview {
    HStack {
        DataTile(value: 42, icon: "calendar", label: "Days")
        DataTile(value: 137, icon: "fossil.shell", label: "Pebbles")
        DataTile(value: 1200, icon: "sparkles", label: "Karma")
    }
    .padding()
}
