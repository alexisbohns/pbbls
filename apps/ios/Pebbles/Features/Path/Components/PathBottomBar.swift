import SwiftUI

/// Bottom nav for the iOS Path screen. Replaces the system tab bar.
///
/// Left: profile avatar (taps to ProfileView).
/// Right: karma stat (icon + number + caption) followed by the
/// Ripples badge. Karma and badge are independent tap targets so a
/// future Ripples explainer sheet can wire in without restructuring.
struct PathBottomBar: View {
    let karma: Int?
    let ripple: RippleSummary?
    let onProfile: () -> Void

    private var numberColor: Color {
        Color.pebbles.primary
    }

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onProfile) {
                Image(systemName: "person.crop.circle")
                    .font(.title2)
                    .foregroundStyle(Color.pebbles.primary)
                    .frame(width: 40, height: 40)
            }
            .accessibilityLabel("Profile")

            Spacer(minLength: 0)

            Button(action: onProfile) {
                karmaStat
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Karma \(karma.map(String.init) ?? "—")")

            Button(action: onProfile) {
                RippleBadge(level: ripple?.rippleLevel ?? 0,
                            activeToday: ripple?.activeToday ?? false)
            }
            .buttonStyle(.plain)
            .padding(.leading, 16)
        }
        .padding(.horizontal, 16)
    }

    private var karmaStat: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkle")
                .foregroundStyle(Color.pebbles.primary)
            VStack(alignment: .leading, spacing: 0) {
                Text(karma.map { "\($0)" } ?? "—")
                    .font(.ysabeauSemibold(17))
                    .foregroundStyle(numberColor)
                Text("karma")
                    .font(.caption)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }
        }
    }
}
