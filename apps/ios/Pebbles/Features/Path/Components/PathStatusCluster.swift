import SwiftUI

/// Karma + Ripples, leading on Path's FAB row (#903 §5, D4). Two independent
/// buttons (#442's split): #446 swaps `onRipple` for the Ripples explainer
/// sheet, and #444 restyles the badge rendered here.
struct PathStatusCluster: View {
    let karma: Int?
    let ripple: RippleSummary?
    let onKarma: () -> Void
    let onRipple: () -> Void

    var body: some View {
        HStack(spacing: 16) {
            Button(action: onKarma) {
                karmaStat
                    .frame(minHeight: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Karma \(karma.map(String.init) ?? "—")")
            .accessibilityHint("Opens You")

            Button(action: onRipple) {
                RippleBadge(level: ripple?.rippleLevel ?? 0,
                            activeToday: ripple?.activeToday ?? false)
            }
            .buttonStyle(.plain)
            .accessibilityHint("Opens You")
        }
    }

    /// The caption sits under the number, so it stays legible at
    /// accessibility sizes.
    private var karmaStat: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkle")
                .foregroundStyle(Color.pebbles.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text(karma.map { "\($0)" } ?? "—")
                    .font(.ysabeauSemibold(17))
                    .foregroundStyle(Color.pebbles.primary)
                Text("karma")
                    .font(.caption)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
    }
}

#Preview {
    PathStatusCluster(
        karma: 42,
        ripple: RippleSummary(rippleLevel: 3, pebbles28d: 10, activeToday: true),
        onKarma: {},
        onRipple: {}
    )
    .padding()
}
