import SwiftUI

/// Tap target that toggles the viewer's upvote on a backlog item.
/// Purely visual — the parent view owns the state and performs the write.
struct ReactionButton: View {
    let count: Int
    let isReacted: Bool
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            HStack(spacing: 4) {
                Image(systemName: isReacted ? "arrow.up.circle.fill" : "arrow.up.circle")
                Text("\(count)")
                    .font(.footnote.monospacedDigit())
            }
            .foregroundStyle(isReacted ? Color.pebblesAccent : Color.pebblesMutedForeground)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(isReacted ? "Remove upvote" : "Upvote")
        .accessibilityValue("\(count) upvotes")
    }
}

#Preview {
    VStack(spacing: 16) {
        ReactionButton(count: 3, isReacted: false, onToggle: {})
        ReactionButton(count: 4, isReacted: true, onToggle: {})
    }
    .padding()
}
