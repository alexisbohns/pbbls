import SwiftUI

/// The bar that follows a camera moment: the photo, when, and "Name it".
struct MomentToast: View {
    let landed: CameraMomentCoordinator.LandedMoment
    let onName: () -> Void
    let onDismiss: () -> Void

    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver

    var body: some View {
        HStack(spacing: Spacing.sm) {
            thumbnail
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: landed.shutter.formatted(.dateTime.hour().minute().locale(SpineStrings.appLocale)))
                    .pebblesFont(.meta)
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                Text("home.toast.saved")
                    .pebblesFont(.callout)
                    .foregroundStyle(Color.pebbles.onSurface)
            }
            Spacer(minLength: 0)
            Button { onName() } label: { Text("home.toast.name") }
                .pebblesFont(.callout)
                .foregroundStyle(Color.pebbles.primary)
        }
        .padding(Spacing.sm)
        .background {
            ZStack {
                Rectangle().fill(.ultraThinMaterial)
                Color.pebbles.surfaceContainer.opacity(0.85)
            }
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .strokeBorder(Color.pebbles.cardRule, lineWidth: 0.5)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAction(named: Text("home.toast.name")) { onName() }
        .accessibilityAction(named: Text("Close")) { onDismiss() }
        .accessibilityAction(.escape) { onDismiss() }
        .task(id: landed.id) {
            // Under VoiceOver the toast stays until it is used or closed.
            guard !voiceOver else { return }
            try? await Task.sleep(for: .seconds(6))
            if !Task.isCancelled { onDismiss() }
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let image = landed.thumbnail {
            Image(uiImage: image).resizable().scaledToFill()
        } else {
            ZStack {
                Color.pebbles.surfaceContainerHigh
                PbblsIconImage(.cameraFocus, .tile, style: AnyShapeStyle(Color.pebbles.onSurfaceVariant))
            }
        }
    }
}
