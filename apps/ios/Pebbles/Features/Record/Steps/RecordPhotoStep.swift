import PhotosUI
import SwiftUI

/// Step 0 — the picture the flow starts from (D2).
///
/// The library *is* the step: an inline `PhotosPicker` (iOS 17) renders the
/// system picker in place, so the user's own photos are the first thing on
/// screen instead of a tile standing in for them. It still runs out of
/// process — the app asks for no photo-library authorization and sees nothing
/// but the one image that gets tapped.
///
/// Auto-advances on pick (D3): `CaptureFlowView` moves to the moment page a
/// beat after the thumbnail lands, and the page shows the upload's state. The
/// controls above the grid ("Just write", the camera) are the scaffold's
/// fixed slot, owned by the flow. Changing your mind is "Change photo" on the
/// page, which comes back here — a second pick swaps the snap and the
/// coordinator takes the replaced one's bytes with it.
struct RecordPhotoStep: View {
    /// The picker's selection. Owned by `CaptureFlowView` so it survives
    /// stepping away and back, and so loading the bytes stays with the
    /// orchestration rather than the view.
    @Binding var selection: PhotosPickerItem?

    var body: some View {
        PhotosPicker(
            selection: $selection,
            matching: .images,
            preferredItemEncoding: .current,
            label: { Text("Add a photo") }
        )
        .photosPickerStyle(.inline)
        // The album switcher stays (Recents ▾ / Albums); search, the
        // selection bar and the staging area belong to the modal it is not.
        .photosPickerDisabledCapabilities([.search, .selectionActions, .stagingArea])
        .photosPickerAccessoryVisibility(.visible, edges: .top)
        .photosPickerAccessoryVisibility(.hidden, edges: .bottom)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .clipShape(RoundedRectangle(cornerRadius: Spacing.xxl, style: .continuous))
    }
}
