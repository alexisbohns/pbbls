import SwiftUI

/// Top-bar badge showing the pebble's privacy status: a rounded capsule with a
/// lock icon + the visibility label. Border, no fill — sits on top of the
/// navigation bar, paired visually with the native Edit button on the trailing
/// side.
struct PebblePrivacyBadge: View {
    let visibility: Visibility

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "lock.fill")
                .font(.caption)
                .accessibilityHidden(true)
            Text(visibility.label)
                .font(.caption)
                .fontWeight(.medium)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .overlay(
            Capsule()
                .strokeBorder(Color.pebblesBorder, lineWidth: 1)
        )
        .foregroundStyle(Color.pebblesForeground)
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("Privacy: \(String(localized: visibility.label))",
                                 comment: "Accessibility label for the pebble privacy badge"))
    }
}

#Preview {
    VStack(spacing: 12) {
        PebblePrivacyBadge(visibility: .private)
        PebblePrivacyBadge(visibility: .public)
    }
    .padding()
}
