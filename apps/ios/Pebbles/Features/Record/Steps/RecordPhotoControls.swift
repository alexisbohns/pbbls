import SwiftUI

/// The photo step's fixed-slot controls (record-flow D3): the way past the
/// grid without a picture, and the camera — only where there is one (not on
/// a simulator). Owned by `CaptureFlowView`, which advances and presents.
struct RecordPhotoControls: View {
    let onJustWrite: () -> Void
    let onCamera: () -> Void

    var body: some View {
        HStack(spacing: Spacing.sm) {
            Button("Just write", action: onJustWrite)
                .buttonStyle(PebblesTonalButtonStyle())
            if CameraPicker.isAvailable {
                Button(action: onCamera) {
                    PbblsIconImage(.camera, size: 22, style: AnyShapeStyle(Color.pebbles.onPrimaryContainer))
                        .frame(width: 48, height: 48)
                        .background(Circle().fill(Color.pebbles.primaryContainer))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Take a photo")
            }
        }
    }
}

#Preview {
    RecordPhotoControls(onJustWrite: {}, onCamera: {})
        .padding()
        .background(Color.pebbles.surface)
}
