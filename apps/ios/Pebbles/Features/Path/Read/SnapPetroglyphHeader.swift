import SwiftUI

/// Presentational top-zone layout for the pebble page (issue #599).
///
/// - **With a snap:** the photo fills the content width at its `BannerAspect`
///   bucket (cover-cropped, rounded), tilted −4°, with the petroglyph
///   overlapping the top-right corner, tilted +7°.
/// - **Without a snap:** the petroglyph is centered as the page heading.
///
/// Pure layout — it loads nothing. `PebbleReadBanner` owns the async snap load
/// and the color selection. Edit mode will reuse this view later with a
/// placeholder snap state, so it stays view-mode-agnostic.
struct SnapPetroglyphHeader<Petroglyph: View>: View {
    /// Decoded snap bytes; nil until they load (or when there is no snap).
    let snapImage: UIImage?
    /// Whether a snap is expected for this pebble. Drives the layout from first
    /// paint (petroglyph top-right) independent of whether `snapImage` has
    /// decoded, so the petroglyph never jumps from center to corner.
    let hasSnapSlot: Bool
    @ViewBuilder var petroglyph: () -> Petroglyph

    private let cornerRadius: CGFloat = 24
    private let petroglyphBox: CGFloat = 120
    private let petroglyphInset: CGFloat = 16
    private let snapTilt: Double = -4
    private let petroglyphTilt: Double = 7

    var body: some View {
        if hasSnapSlot {
            withSnapLayout
        } else {
            // No snap → petroglyph centered as heading content, no tilt.
            petroglyph()
                .frame(width: petroglyphBox, height: petroglyphBox)
                .frame(maxWidth: .infinity)
        }
    }

    private var withSnapLayout: some View {
        ZStack(alignment: .topTrailing) {
            snapArea
                .rotationEffect(.degrees(snapTilt))

            petroglyph()
                .frame(width: petroglyphBox, height: petroglyphBox)
                .rotationEffect(.degrees(petroglyphTilt))
                .offset(x: petroglyphInset, y: -petroglyphInset)
        }
        // Reserve the inset so the petroglyph poking past the top-right corner
        // isn't clipped by the ZStack bounds / surrounding VStack spacing.
        .padding(.top, petroglyphInset)
        .padding(.trailing, petroglyphInset)
    }

    @ViewBuilder
    private var snapArea: some View {
        // Bucket ratio is known only once the image decodes; until then the
        // placeholder holds a neutral square so the petroglyph already sits
        // top-right. On decode the image cross-fades in and the container
        // settles to its bucket ratio.
        let aspect = snapImage.map {
            BannerAspect.nearest(to: $0.size.width / max($0.size.height, 1))
        }
        Color.system.muted
            .aspectRatio(aspect?.cgRatio ?? 1, contentMode: .fit)
            .frame(maxWidth: .infinity)
            .overlay {
                if let snapImage {
                    Image(uiImage: snapImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .transition(.opacity)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .accessibilityHidden(true)
            .animation(.easeOut(duration: 0.25), value: snapImage)
    }
}

#Preview("No snap · petroglyph centered") {
    SnapPetroglyphHeader(snapImage: nil, hasSnapSlot: false) {
        RoundedRectangle(cornerRadius: 24)
            .fill(Color.system.secondary)
    }
    .padding(.horizontal, 16)
    .background(Color.system.background)
}

#Preview("Snap slot · placeholder (no bytes)") {
    SnapPetroglyphHeader(snapImage: nil, hasSnapSlot: true) {
        RoundedRectangle(cornerRadius: 24)
            .fill(Color.system.secondary)
    }
    .padding(.horizontal, 16)
    .background(Color.system.background)
}
