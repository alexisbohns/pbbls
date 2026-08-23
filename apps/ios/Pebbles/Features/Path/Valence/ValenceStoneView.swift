import SwiftUI

/// One valence stone, drawn the way the Path draws a real pebble: the
/// wobbled silhouette for that (size × polarity), filled and stroked.
///
/// Goes straight to `WobbleRenderer.backdropArt` rather than through
/// `PebbleOutlineBackdropView`, which is fill-only by construction and whose
/// `SVGView` branch has no `CGPath` to stroke. The art is memoized inside
/// `WobbleRenderer`, so nine stones cost nine parses once per process — never
/// per frame.
///
/// Knows nothing about selection or placement: the picker owns both.
struct ValenceStoneView: View {
    let valence: Valence
    /// On-screen height. Width follows from the silhouette's aspect ratio.
    let height: CGFloat

    @Environment(\.colorScheme) private var colorScheme

    private var width: CGFloat {
        height * PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup)
    }

    /// Scales with the stone so a small stone never reads as heavier-lined
    /// than a large one. Floored so the small stones keep a visible edge.
    private var lineWidth: CGFloat {
        max(1.4, 2.5 * height / ValenceFanLayout.stoneHeight(for: .large))
    }

    var body: some View {
        let style = ValenceStoneStyle.style(for: valence.polarity, scheme: colorScheme)

        Group {
            if let art = WobbleRenderer.backdropArt(
                size: valence.sizeGroup, polarity: valence.polarity
            ) {
                let shape = WobbledBackdropShape(art: art)
                shape
                    .fill(style.fill, style: FillStyle(eoFill: art.usesEvenOddFill))
                    .overlay {
                        shape.stroke(style.stroke, lineWidth: lineWidth)
                    }
            } else {
                // Missing or unparseable outline asset — a setup bug, already
                // logged by WobbleRenderer. An empty cell makes it visible
                // without crashing every frame that lays this out.
                Color.clear
            }
        }
        .frame(width: width, height: height)
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
