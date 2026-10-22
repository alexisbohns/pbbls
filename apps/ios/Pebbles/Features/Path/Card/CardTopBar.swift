import SwiftUI

/// The held card's own top bar (#1146 §Top bar), not a toolbar, so the chrome
/// can fade on its own (E4): ← Back, Share for a public pebble only (it shares
/// the pebble, whose id differs from the moment's), and ⋮. No privacy badge,
/// no title. Every control is a 44 pt target; all are disabled while loading
/// and deleting.
struct CardTopBar: View {
    let shareURL: URL?
    let menuItems: [PebblesMenuItem]
    let isDisabled: Bool
    let onBack: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            Button(action: onBack) {
                Image(systemName: "chevron.backward")
                    .frame(width: CardLayout.turnTarget, height: CardLayout.turnTarget)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel(Text("Back", comment: "Accessibility label for the held card's back button"))
            Spacer(minLength: 0)
            if let shareURL {
                ShareLink(item: shareURL) {
                    Image(systemName: "square.and.arrow.up")
                        .frame(width: CardLayout.turnTarget, height: CardLayout.turnTarget)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel(Text("Share", comment: "Accessibility label for the public pebble share button"))
                .disabled(isDisabled)
            }
            PebblesMenu(items: menuItems) {
                Image(systemName: "ellipsis.circle")
                    .frame(width: CardLayout.turnTarget, height: CardLayout.turnTarget)
                    .contentShape(Rectangle())
            }
            .disabled(isDisabled)
            .accessibilityLabel(Text("More", comment: "Accessibility label for the held card's menu button"))
        }
        .foregroundStyle(Color.pebbles.onSurface)
        .padding(.horizontal, 4)
    }
}
