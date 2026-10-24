import SwiftUI

/// A section's way to the rest of its list (#1226): the text and a forward
/// chevron (mirrored in right-to-left layouts), a 44 pt button.
struct MoreLink: View {
    let text: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 2) {
                Text(verbatim: text)
                    .spineType(.personsFold)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Image(systemName: "chevron.forward")
                    .imageScale(.small)
                    .spineType(.personsFold)
                    .foregroundStyle(Color.pebbles.outline)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: SpineLayout.minTarget)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
