import SwiftUI

/// Capsule badge showing a collection's mode with emoji + label.
/// Renders nothing when mode is nil.
struct CollectionModeBadge: View {
    let mode: CollectionMode?

    var body: some View {
        if let mode {
            let (emoji, label) = Self.meta(for: mode)
            Label {
                Text(label)
            } icon: {
                Text(emoji)
            }
            .labelStyle(.titleAndIcon)
            .font(.caption)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .overlay(
                Capsule().stroke(.secondary.opacity(0.3), lineWidth: 1)
            )
            .accessibilityLabel("Mode: \(label)")
        } else {
            EmptyView()
        }
    }

    private static func meta(for mode: CollectionMode) -> (emoji: String, label: String) {
        switch mode {
        case .stack: return ("🎯", "Stack")
        case .pack:  return ("📦", "Pack")
        case .track: return ("🔄", "Track")
        }
    }
}

#Preview {
    VStack(spacing: 12) {
        CollectionModeBadge(mode: .stack)
        CollectionModeBadge(mode: .pack)
        CollectionModeBadge(mode: .track)
        CollectionModeBadge(mode: nil) // renders nothing
    }
    .padding()
}
