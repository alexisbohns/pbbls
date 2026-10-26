import SwiftUI

/// The three Home shortcuts (spec D3): camera, quick moment, new pebble.
/// The order is fixed.
struct HomeShortcuts: View {
    let onCamera: () -> Void
    let onQuick: () -> Void
    let onPebble: () -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            tile("camera", "home.shortcut.camera", action: onCamera)
            tile("clock", "home.shortcut.quick", action: onQuick)
            tile("plus.circle", "home.shortcut.pebble", action: onPebble)
        }
    }

    private func tile(_ symbol: String, _ key: LocalizedStringKey, action: @escaping () -> Void) -> some View {
        Button {
            TapHaptics.play(.selection)
            action()
        } label: {
            VStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.title3)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Text(key)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, minHeight: 72)
            .background(Color.pebbles.surfaceContainer, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .strokeBorder(Color.pebbles.cardRule, lineWidth: 0.5)
            )
            .contentShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(key))
    }
}
