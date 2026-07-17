import SwiftUI

/// Presentational top-zone layout for the pebble page (issue #599).
///
/// - **With a snap:** the photo sits at ~half the screen width — centered, so it
///   breathes with generous side margins rather than spanning edge-to-edge — at
///   its `BannerAspect` bucket (cover-cropped, rounded), tilted −4°, with the
///   petroglyph perched on and poking past its top-right corner, tilted +7°.
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
    /// Snap width cap. The design (issue #599) keeps the photo at roughly half
    /// the screen so it reads as a framed keepsake with breathing room, not a
    /// full-bleed banner; the rest of the width is the centering margin on
    /// either side. A fixed cap (rather than a screen fraction) keeps the photo
    /// from ballooning on the largest phones while still breathing on the
    /// smallest — ~51% on a 390pt screen, ~47% on a 430pt Pro Max.
    private let snapMaxWidth: CGFloat = 200

    var body: some View {
        ZStack(alignment: .topTrailing) {
            if hasSnapSlot {
                snapArea
                    .rotationEffect(.degrees(snapTilt))
            }

            // The petroglyph lives at a STABLE structural position (always the
            // second element of this ZStack, never inside an if/else branch) so
            // its identity — and `PebbleAnimatedRenderView`'s entry animation —
            // is preserved when `hasSnapSlot` flips (e.g. a settled snap-load
            // failure). Only its placement modifiers change between states:
            // top-right + tilted with a snap, centered + straight without.
            //
            // With a snap the ZStack sizes to the (half-width) snap and aligns
            // the petroglyph to its top-trailing corner; the +16/−16 offset then
            // pokes it out past that corner. Without a snap the ZStack holds only
            // the petroglyph.
            petroglyph()
                .frame(width: petroglyphBox, height: petroglyphBox)
                .rotationEffect(.degrees(hasSnapSlot ? petroglyphTilt : 0))
                .offset(x: hasSnapSlot ? petroglyphInset : 0,
                        y: hasSnapSlot ? -petroglyphInset : 0)
        }
        // Center the whole composition in the content width so the half-width
        // snap floats with equal margins instead of hugging the leading edge.
        .frame(maxWidth: .infinity, alignment: .center)
        // Headroom for the petroglyph's upward poke so it clears the nav zone.
        .padding(.top, hasSnapSlot ? petroglyphInset : 0)
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
            .overlay {
                if let snapImage {
                    Image(uiImage: snapImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .transition(.opacity)
                }
            }
            .aspectRatio(aspect?.cgRatio ?? 1, contentMode: .fit)
            // Cap at ~half the screen width, not the full content width — the
            // source of the "let the picture breathe" correction. Height follows
            // the bucket ratio above.
            .frame(maxWidth: snapMaxWidth)
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
