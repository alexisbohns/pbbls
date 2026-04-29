import SwiftUI

/// Badge showing the pebble's privacy status. Two styles:
///
/// - `.capsule` (default): outlined capsule with lock + label, suitable for
///   inline placement.
/// - `.chip`: 36pt circular chip with translucent surface and a centered
///   lock icon, intended for the floating navigation-bar treatment in
///   `PebbleDetailSheet`.
struct PebblePrivacyBadge: View {
    enum Style {
        case capsule
        case chip
    }

    let visibility: Visibility
    var style: Style = .capsule

    var body: some View {
        switch style {
        case .capsule: capsuleBody
        case .chip:    chipBody
        }
    }

    private var capsuleBody: some View {
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

    private var chipBody: some View {
        Image(systemName: "lock.fill")
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(Color.pebblesForeground)
            .frame(width: 36, height: 36)
            .background(
                Circle().fill(Color.pebblesBackground.opacity(0.85))
            )
            .accessibilityLabel(Text("Privacy: \(String(localized: visibility.label))",
                                     comment: "Accessibility label for the pebble privacy chip"))
    }
}

#Preview {
    VStack(spacing: 16) {
        PebblePrivacyBadge(visibility: .private)
        PebblePrivacyBadge(visibility: .public)
        PebblePrivacyBadge(visibility: .private, style: .chip)
        PebblePrivacyBadge(visibility: .public, style: .chip)
    }
    .padding()
    .background(Color.pebblesBackground)
}
