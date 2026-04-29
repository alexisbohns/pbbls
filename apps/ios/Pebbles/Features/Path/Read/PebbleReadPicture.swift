import SwiftUI

/// Rounded full-width photo for the pebble read view. Uses the existing
/// `SnapImageView` for image loading and respects the imported file's
/// natural aspect ratio (no forced cropping).
struct PebbleReadPicture: View {
    let storagePath: String

    var body: some View {
        SnapImageView(storagePath: storagePath)
            .frame(maxWidth: .infinity)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .accessibilityLabel(Text("Pebble photo",
                                     comment: "Accessibility label for the photo attached to a pebble"))
    }
}
