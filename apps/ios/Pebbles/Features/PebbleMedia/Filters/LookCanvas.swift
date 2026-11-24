import SwiftUI

/// The canvas on Filter and Adjust: the crop with the look, no gestures but
/// touch-and-hold for the photo without it (spec 2026-11-23 "Layout").
struct LookCanvas: View {
    @Bindable var model: PhotoLookModel

    var body: some View {
        Group {
            if let image = model.showsOriginal ? model.base : (model.preview ?? model.base) {
                Image(decorative: image, scale: 1)
                    .resizable()
                    .scaledToFit()
                    .padding(Spacing.lg)
                    .onLongPressGesture(minimumDuration: 0, maximumDistance: 60, perform: {},
                                        onPressingChanged: { model.showsOriginal = $0 })
                    .accessibilityElement()
                    .accessibilityLabel(Text("Photo with the look"))
                    .accessibilityAction(named: Text("Show original")) { model.showsOriginal.toggle() }
            } else {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
