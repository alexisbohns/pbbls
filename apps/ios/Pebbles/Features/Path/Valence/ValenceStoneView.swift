import SwiftUI

/// One valence stone, composed the way the Path and the read sheet compose a
/// real pebble: a soft-filled silhouette behind, the artwork inked inside it.
///
/// The backdrop is the wobbled `Outlines/<size>-<polarity>.svg` shape, filled
/// and never stroked. The artwork on top is the wobbled `ValenceArt/valence-*`
/// ink (the pebble's own outline plus its creature and fossil), tinted and
/// scaled down by `PebbleOutlineGeometry.pebbleScale` so the backdrop frames it
/// with the same ~12% margin a real stone gets — instead of the backdrop's edge
/// and the artwork's edge landing on top of each other.
///
/// Both halves are wobbled by the same renderer a real pebble goes through, and
/// both are memoized, so nine stones cost nine parses once per process — never
/// per frame.
///
/// Knows nothing about selection or placement: the picker owns both.
struct ValenceStoneView: View {
    let valence: Valence
    /// On-screen height of the whole stone, backdrop included.
    let height: CGFloat
    /// Inverts the wash and the ink — see `ValenceStoneStyle`.
    var isSelected: Bool = false

    @Environment(\.colorScheme) private var colorScheme

    private var size: ValenceSizeGroup { valence.sizeGroup }

    private var width: CGFloat {
        height * PebbleOutlineGeometry.aspectRatio(for: size)
    }

    var body: some View {
        let style = ValenceStoneStyle.style(
            for: valence.polarity, scheme: colorScheme, isSelected: isSelected
        )

        ZStack {
            backdrop(style)
            artwork(style)
                .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: size))
        }
        .frame(width: width, height: height)
    }

    @ViewBuilder
    private func backdrop(_ style: ValenceStoneStyle) -> some View {
        if let art = WobbleRenderer.backdropArt(size: size, polarity: valence.polarity) {
            WobbledBackdropShape(art: art)
                .fill(style.backdrop, style: FillStyle(eoFill: art.usesEvenOddFill))
        } else {
            // Missing or unparseable outline asset — a setup bug, already
            // logged by WobbleRenderer. Losing the wash still leaves the
            // artwork readable, which is the better half to keep.
            Color.clear
        }
    }

    /// The leaky wobbled ink, aspect-fitted into the artwork's own viewBox the
    /// same way `PebbleAnimatedRenderView` fits a real pebble's static body.
    @ViewBuilder
    private func artwork(_ style: ValenceStoneStyle) -> some View {
        if let art = ValenceArt.art(for: valence) {
            ZStack {
                WobbledPathShape(path: art.ink, layerTransform: .identity, viewBox: art.viewBox)
                    .fill(style.ink)
                ForEach(Array(art.regions.enumerated()), id: \.offset) { _, region in
                    WobbledPathShape(
                        path: region.path, layerTransform: .identity, viewBox: art.viewBox
                    )
                    .fill(style.ink, style: FillStyle(eoFill: region.usesEvenOddFill))
                }
            }
            .aspectRatio(art.viewBox.width / art.viewBox.height, contentMode: .fit)
        } else {
            // Missing or unparseable artwork — logged by ValenceArt. The
            // backdrop wash alone still reads as a stone of the right size and
            // colour, so the picker stays usable.
            Color.clear
        }
    }
}

#Preview("the nine stones") {
    VStack(spacing: 24) {
        ForEach(ValenceSizeGroup.allCases) { group in
            HStack(spacing: 20) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    if let valence = Valence.allCases.first(
                        where: { $0.sizeGroup == group && $0.polarity == polarity }
                    ) {
                        ValenceStoneView(
                            valence: valence,
                            height: ValenceFanLayout.stoneHeight(for: group)
                        )
                    }
                }
            }
        }
    }
    .padding()
}
