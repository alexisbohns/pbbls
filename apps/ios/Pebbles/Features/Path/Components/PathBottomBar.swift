import SwiftUI

/// Bottom nav for the iOS Path screen. Replaces the system tab bar.
///
/// Glyph (left) and stat cluster (right) all push to ProfileView via
/// the `onProfile` callback. Karma uses the `sparkle` symbol; bounce
/// uses `circle.hexagongrid` (issue spec; implementer should verify
/// against Figma — `.fill` variant may apply).
struct PathBottomBar: View {
    let karma: Int?
    let bounce: Int?
    let onProfile: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private var numberColor: Color {
        colorScheme == .dark ? Color.pebblesAccent : Color.pebblesForeground
    }

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onProfile) {
                Image(systemName: "person.crop.circle")
                    .font(.title2)
                    .foregroundStyle(Color.pebblesAccent)
                    .frame(width: 40, height: 40)
            }
            .accessibilityLabel("Profile")

            Spacer(minLength: 0)

            Button(action: onProfile) {
                HStack(spacing: 16) {
                    stat(systemImage: "circle.hexagongrid", value: bounce, label: "bounce")
                    stat(systemImage: "sparkle",            value: karma,  label: "karma")
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Bounce \(bounce.map(String.init) ?? "—"), Karma \(karma.map(String.init) ?? "—")")
        }
        .padding(.horizontal, 16)
    }

    @ViewBuilder
    private func stat(systemImage: String, value: Int?, label: LocalizedStringKey) -> some View {
        HStack(spacing: 6) {
            Image(systemName: systemImage)
                .foregroundStyle(Color.pebblesAccent)
            VStack(alignment: .leading, spacing: 0) {
                Text(value.map { "\($0)" } ?? "—")
                    .font(.ysabeauSemibold(17))
                    .foregroundStyle(numberColor)
                Text(label)
                    .font(.caption)
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
    }
}
