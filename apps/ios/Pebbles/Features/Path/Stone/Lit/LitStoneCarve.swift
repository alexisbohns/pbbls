import SwiftUI

extension LitStoneSpec {
    /// The same stone without its glyph: what is cached and shown under the live carve.
    var uncarved: LitStoneSpec {
        LitStoneSpec(size: size, polarity: polarity, palette: palette, glyph: nil, seed: seed, night: night, flat: flat)
    }
}

/// A live `Canvas` laid out exactly like `LitStoneImage` (#1691): the outline's aspect,
/// unclipped, its origin where the image's body sits, so a layer drawn in it lands on
/// the cached stone to the pixel. The scheme and Low Power Mode are read into the spec's
/// `night` and `flat`, as the image reads them, and the box is the one the cache paints.
/// `margin` is how far the layer's drawing reaches past the box on each side.
struct LitStoneLiveLayer: View {
    let spec: LitStoneSpec
    let margin: (LitStoneSpec, CGSize) -> EdgeInsets
    let draw: (LitStoneSpec, inout GraphicsContext, CGSize, CGFloat) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.displayScale) private var displayScale
    @State private var lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled

    var body: some View {
        let outline = LitStoneGeometry.outlineBox(spec.size)
        Color.clear
            .aspectRatio(outline.width / outline.height, contentMode: .fit)
            .overlay(alignment: .topLeading) {
                GeometryReader { proxy in
                    layer(box: proxy.size)
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .NSProcessInfoPowerStateDidChange)
                .receive(on: DispatchQueue.main)) { _ in
                lowPower = ProcessInfo.processInfo.isLowPowerModeEnabled
            }
            .accessibilityHidden(true)
    }

    @ViewBuilder
    private func layer(box raw: CGSize) -> some View {
        if raw.width > 0, raw.height > 0 {
            let live = LitStoneSpec(size: spec.size, polarity: spec.polarity, palette: spec.palette,
                                    glyph: spec.glyph, seed: spec.seed,
                                    night: colorScheme == .dark, flat: lowPower)
            // The box the cache paints, so the live layer and the image are the same size.
            let box = LitStoneCache.snapped(raw, scale: displayScale)
            let inset = margin(live, box)
            Canvas { context, _ in
                var context = context
                context.translateBy(x: inset.leading, y: inset.top)
                draw(live, &context, box, displayScale)
            }
            .frame(width: inset.leading + box.width + inset.trailing,
                   height: inset.top + box.height + inset.bottom)
            .offset(x: -inset.leading, y: -inset.top)
            .allowsHitTesting(false)
        }
    }
}

/// The stone's carve, drawn live (#1691): the lip trimmed to `lip` of the line, the ink to `ink`,
/// at `inkOpacity` of its own. Laid over `LitStoneImage(spec: spec.uncarved)`.
struct LitStoneCarve: View {
    let spec: LitStoneSpec
    let lip: Double
    let ink: Double
    let inkOpacity: Double

    init(spec: LitStoneSpec, lip: Double, ink: Double, inkOpacity: Double) {
        self.spec = spec
        self.lip = lip
        self.ink = ink
        self.inkOpacity = inkOpacity
    }

    var body: some View {
        LitStoneLiveLayer(
            spec: spec,
            margin: { live, box in LitStoneCache.padding(live, box: box) },
            draw: { live, context, box, scale in
                LitStonePainter.paintCarve(live, in: &context, box: box, scale: scale,
                                           lip: lip, ink: ink, inkOpacity: inkOpacity)
            })
    }
}
