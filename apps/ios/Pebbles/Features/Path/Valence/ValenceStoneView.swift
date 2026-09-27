import SwiftUI

/// One valence stone in the picker: the lit stone render (#974) in the brand
/// palette, with the maintainer's material, tone and look tables. Every stone
/// is lit; the picker itself says which one is chosen by scale and dimming,
/// so `isSelected` changes nothing here and is kept for the call site.
///
/// Both wobbled layers are memoized (`WobbleRenderer`, `StoneCarvingArt`), so
/// nine stones cost nine parses once per process, never per frame. Low Power
/// Mode draws the flat rendering: body tone and ink, no shader.
struct ValenceStoneView: View {
    let valence: Valence
    /// On-screen height of the whole stone, backdrop included.
    let height: CGFloat
    /// Kept for the picker's call site; selection is shown by the picker.
    var isSelected: Bool = false

    var body: some View {
        StoneView(
            valence: valence,
            palette: .brand,
            material: .starting(for: valence.polarity),
            tones: .starting(for: valence.polarity),
            light: .rest,
            height: height,
            isFlat: ProcessInfo.processInfo.isLowPowerModeEnabled
        )
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
