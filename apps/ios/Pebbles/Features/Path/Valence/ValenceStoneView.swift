import SwiftUI

/// One valence stone in the picker: the lit stone render (#974) in the brand
/// palette, with the maintainer's material, tone and look tables. Every stone
/// is lit; the chosen one sits on a soft brand halo (the silhouette itself,
/// blown up and blurred), on top of the scale and dimming the picker applies.
///
/// Both wobbled layers are memoized (`WobbleRenderer`, `StoneCarvingArt`), so
/// nine stones cost nine parses once per process, never per frame. Low Power
/// Mode draws the flat rendering: body tone and ink, no shader.
struct ValenceStoneView: View {
    let valence: Valence
    /// On-screen height of the whole stone, backdrop included.
    let height: CGFloat
    /// Draws the halo behind the stone.
    var isSelected: Bool = false

    var body: some View {
        ZStack {
            if isSelected {
                halo
            }
            stone
        }
    }

    /// The stone's own silhouette in the brand's light tone, enlarged and
    /// blurred, so the chosen stone reads as lit from behind in both schemes.
    @ViewBuilder
    private var halo: some View {
        if let art = WobbleRenderer.backdropArt(size: valence.sizeGroup, polarity: valence.polarity) {
            WobbledBackdropShape(art: art)
                .fill(Color.accent.secondary.opacity(0.85), style: FillStyle(eoFill: art.usesEvenOddFill))
                .frame(width: height * PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup), height: height)
                .scaleEffect(1.22)
                .blur(radius: height * 0.12)
        }
    }

    private var stone: some View {
        StoneView(
            valence: valence,
            palette: .brand,
            material: .starting(for: valence.polarity),
            tones: .starting(for: valence.polarity),
            light: .rest,
            height: height,
            isFlat: ProcessInfo.processInfo.isLowPowerModeEnabled
        )
        .frame(width: height * PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup), height: height)
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
