import SwiftUI

/// Presentational top-zone layout for the pebble page (issue #599).
///
/// - **With a snap:** the photo is fit inside a bounding box (max 230×200) at
///   its `BannerAspect` bucket, so wide ratios (16:9, 4:3) read big and portrait
///   (3:4) reads narrow-tall instead of every ratio sharing one width. It is
///   cover-cropped, rounded, tilted −4°, and centered so it breathes. The
///   petroglyph is centered *on* the snap's top-right corner — half overlapping
///   the photo, half poking past it — tilted +7°.
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
    private let snapTilt: Double = -4
    private let petroglyphTilt: Double = 7
    /// The snap is fit inside this bounding box, preserving its bucket ratio.
    /// A box (not a flat width cap) is what lets a 16:9 read as wide and a 3:4
    /// as narrow-tall — otherwise a short-wide 16:9 shares the portrait's width
    /// and looks tiny beside it (issue #599 follow-up feedback). Landscape
    /// buckets bind on width (230), portrait/square bind on height (200), so
    /// nothing spans the full content width — the rest is the breathing margin.
    private let snapMaxWidth: CGFloat = 230
    private let snapMaxHeight: CGFloat = 200
    /// Air above the composition, on top of the pebble's upward overhang. This
    /// is the visible gap between the nav zone and the pebble — the "more space
    /// on top" the design calls for.
    private let topGap: CGFloat = 32

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
            // failure). Only its placement modifiers change between states.
            //
            // With a snap, `.topTrailing` pins the box's top-right to the snap's
            // top-right corner; nudging out by half the box in each axis brings
            // the box CENTER onto that corner — half over the photo, half past
            // it, per the design. The +7° tilt pivots on that same center.
            // Without a snap the ZStack holds only the (centered, straight) box.
            petroglyph()
                .frame(width: petroglyphBox, height: petroglyphBox)
                .rotationEffect(.degrees(hasSnapSlot ? petroglyphTilt : 0))
                .offset(x: hasSnapSlot ? petroglyphBox / 2 : 0,
                        y: hasSnapSlot ? -petroglyphBox / 2 : 0)
        }
        // Center the whole composition in the content width so the snap floats
        // with equal margins instead of hugging the leading edge.
        .frame(maxWidth: .infinity, alignment: .center)
        // Reserve the pebble's upward overhang (half the box) plus a gap, so it
        // clears the nav zone and the page has real air at the top.
        .padding(.top, hasSnapSlot ? petroglyphBox / 2 + topGap : topGap)
    }

    @ViewBuilder
    private var snapArea: some View {
        // Bucket ratio is known only once the image decodes; until then the
        // placeholder holds a neutral square. The parent gates visibility on
        // load-complete, so this placeholder→image settling never shows.
        let aspect = snapImage.map {
            BannerAspect.nearest(to: $0.size.width / max($0.size.height, 1))
        }
        Color.system.muted
            .overlay {
                if let snapImage {
                    Image(uiImage: snapImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                }
            }
            .aspectRatio(aspect?.cgRatio ?? 1, contentMode: .fit)
            // Fit inside the bounding box — landscape binds on width, portrait
            // on height — so the photo breathes and ratios keep their character.
            .frame(maxWidth: snapMaxWidth, maxHeight: snapMaxHeight)
            .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
            .accessibilityHidden(true)
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
