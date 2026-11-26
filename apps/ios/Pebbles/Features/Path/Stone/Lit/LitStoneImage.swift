import SwiftUI

/// A lit stone on screen (#1690): the cached picture, laid out in the outline's
/// aspect ratio and drawn unclipped, so the shadow falls outside the box (D6).
///
/// The scheme and Low Power Mode are read into the spec's `night` and `flat`,
/// so the picture redraws only when one of them, the spec or the box changes.
/// No timers and no animation: a still stone is a still image.
struct LitStoneImage: View {
    let spec: LitStoneSpec

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.displayScale) private var displayScale
    @State private var lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled

    init(spec: LitStoneSpec) {
        self.spec = spec
    }

    var body: some View {
        let outline = LitStoneGeometry.outlineBox(spec.size)
        Color.clear
            .aspectRatio(outline.width / outline.height, contentMode: .fit)
            .overlay(alignment: .topLeading) {
                GeometryReader { proxy in
                    picture(box: proxy.size)
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .NSProcessInfoPowerStateDidChange)
                .receive(on: DispatchQueue.main)) { _ in
                lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
            }
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private func picture(box: CGSize) -> some View {
        if box.width > 0, box.height > 0 {
            let live = LitStoneSpec(size: spec.size, polarity: spec.polarity, palette: spec.palette,
                                    glyph: spec.glyph, seed: spec.seed,
                                    night: colorScheme == .dark, flat: lowPower)
            let image = LitStoneCache.shared.image(live, box: box, scale: displayScale)
            let padding = LitStoneCache.padding(live, box: box, scale: displayScale)
            Image(decorative: image, scale: displayScale)
                .offset(x: -padding.leading, y: -padding.top)
        }
    }
}
