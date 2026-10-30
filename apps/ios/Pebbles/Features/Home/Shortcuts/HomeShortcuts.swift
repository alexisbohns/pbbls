import SwiftUI

/// The three Home shortcuts (spec D3): camera, quick moment, new pebble.
/// The order is fixed.
struct HomeShortcuts: View {
    let onCamera: () -> Void
    let onQuick: () -> Void
    let onPebble: () -> Void
    var cameraDisabled = false

    var body: some View {
        HStack(spacing: Spacing.sm) {
            tile(.camera, "home.shortcut.camera", disabled: cameraDisabled, action: onCamera)
            tile(.pebbleFlat, "home.shortcut.quick", action: onQuick)
            tile(.plusCircle, "home.shortcut.pebble", action: onPebble)
        }
    }

    // Not SurfaceTile: D2 bans its primaryContainer default and the spec names its own geometry.
    private func tile(_ icon: PbblsIcon, _ key: LocalizedStringKey, disabled: Bool = false, action: @escaping () -> Void) -> some View {
        Button {
            TapHaptics.play(.selection)
            action()
        } label: {
            VStack(spacing: 6) {
                PbblsIconImage(icon, .tile, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
                    .frame(height: 28)
                Text(key)
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
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
        .disabled(disabled)
        .opacity(disabled ? 0.6 : 1)
        .accessibilityLabel(Text(key))
    }
}
