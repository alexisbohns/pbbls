import SwiftUI
import os

/// Static (non-animated) counterpart to `PebbleAnimatedRenderView`, used by
/// `PathPebbleRow`.
///
/// Traces each parsed `PebbleSVGModel` layer at a uniform stroke width equal to
/// the outline's authored weight (`PebbleStroke.lineWidth`), so the glyph reads
/// the same weight as the outline — matching the settled detail view and fixing
/// issue #509. Falls back to `PebbleRenderView` (SVGView) when the SVG cannot be
/// parsed into a model.
struct PebbleStaticRenderView: View {
    let svg: String
    /// Stroke for the traced layer paths.
    let strokeColor: Color
    /// Hex equivalent injected into the raw SVG for the SVGView fallback.
    let strokeColorHex: String

    @State private var model: PebbleSVGModel?
    @State private var wobbleArt: WobblePebbleArt?
    @State private var parseAttempted = false

    var body: some View {
        Group {
            if let model {
                GeometryReader { proxy in
                    if let wobbleArt {
                        // Wobble experiment (#555): geometry-true filled ink —
                        // thickness is baked into the outline, not stroked.
                        ZStack {
                            ForEach(Array(model.layers.enumerated()), id: \.offset) { index, layer in
                                WobbledPathShape(
                                    path: wobbleArt.layers[index].ink,
                                    layerTransform: layer.transform,
                                    viewBox: model.viewBox
                                )
                                .fill(strokeColor)
                                .opacity(layer.opacity)
                            }
                        }
                    } else {
                        let lineWidth = PebbleStroke.lineWidth(viewBox: model.viewBox, frame: proxy.size)
                        ZStack {
                            ForEach(Array(model.layers.enumerated()), id: \.offset) { _, layer in
                                LayerShape(layer: layer, viewBox: model.viewBox)
                                    .stroke(
                                        strokeColor,
                                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, lineJoin: .round)
                                    )
                                    .opacity(layer.opacity)
                            }
                        }
                    }
                }
            } else {
                PebbleRenderView(svg: svg, strokeColor: strokeColorHex)
            }
        }
        .accessibilityHidden(true)
        .onAppear {
            guard !parseAttempted else { return }
            parseAttempted = true
            model = PebbleSVGModel(svg: svg)
            if model == nil {
                Logger(subsystem: "app.pbbls.ios", category: "pebble-render")
                    .info("PebbleStaticRenderView: parse failed; using SVGView fallback")
            }
            if WobbleFlags.isEnabled, let model {
                wobbleArt = WobbleRenderer.pebbleArt(svg: svg, model: model)
            }
        }
    }
}

#Preview("Static · glyph matches outline weight") {
    PebbleStaticRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 260 260" width="260" height="260">
          <g id="layer:shape">
            <path d="M 20 130 C 20 70 70 20 130 20 C 190 20 240 70 240 130 C 240 190 190 240 130 240 C 70 240 20 190 20 130 Z" fill="none" stroke="currentColor"/>
          </g>
          <g id="layer:glyph" transform="translate(78, 78) scale(0.52)">
            <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none" stroke="currentColor"/>
          </g>
        </svg>
        """,
        strokeColor: Color(red: 0.486, green: 0.361, blue: 0.980),
        strokeColorHex: "#7C5CFA"
    )
    .frame(width: 96, height: 96)
    .padding()
}
