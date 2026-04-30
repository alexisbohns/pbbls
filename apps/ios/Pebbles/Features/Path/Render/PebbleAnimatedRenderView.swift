import SwiftUI
import os

/// Animated counterpart to `PebbleRenderView` used by the pebble read sheet.
///
/// On first appearance the composed SVG is parsed into a `PebbleSVGModel`.
/// If parsing fails, no timings are registered for `renderVersion`, or the
/// system has Reduce Motion enabled, the view falls back to the static
/// `PebbleRenderView` (SVGView). Otherwise it renders each parsed layer as
/// a `Shape` with `.trim(from: 0, to: progress)`, animating progress 0 → 1
/// per phase, then a brief scale pulse for the settle beat.
///
/// The animation replays each time the view appears.
struct PebbleAnimatedRenderView: View {
    let svg: String
    let strokeColor: String
    let renderVersion: String?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: PebbleSVGModel?
    @State private var glyphProgress: Double = 0
    @State private var shapeProgress: Double = 0
    @State private var fossilProgress: Double = 0
    @State private var settleScale: Double = 1

    var body: some View {
        Group {
            if let model, let timings = PebbleAnimationTimings.forVersion(renderVersion), !reduceMotion {
                animatedBody(model: model, timings: timings)
            } else {
                PebbleRenderView(svg: svg, strokeColor: strokeColor)
            }
        }
        .onAppear {
            if model == nil {
                model = PebbleSVGModel(svg: svg)
                if model == nil {
                    Logger(subsystem: "app.pbbls.ios", category: "pebble-render")
                        .info("PebbleAnimatedRenderView: parse failed; using SVGView fallback")
                }
            }
            startAnimation()
        }
        .onDisappear { resetProgress() }
    }

    // MARK: - Animated rendering

    @ViewBuilder
    private func animatedBody(model: PebbleSVGModel, timings: PebbleAnimationTimings.Timings) -> some View {
        ZStack {
            ForEach(Array(model.layers.enumerated()), id: \.offset) { _, layer in
                LayerShape(layer: layer, viewBox: model.viewBox)
                    .trim(from: 0, to: progress(for: layer.kind))
                    .stroke(stroke, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                    .opacity(layer.opacity)
            }
        }
        .scaleEffect(settleScale)
        .accessibilityHidden(true)
    }

    private var stroke: Color { Color(hex: strokeColor) ?? Color.pebblesAccent }

    private func progress(for kind: PebbleSVGModel.Layer.Kind) -> Double {
        switch kind {
        case .glyph:  return glyphProgress
        case .shape:  return shapeProgress
        case .fossil: return fossilProgress
        }
    }

    private func resetProgress() {
        glyphProgress = 0
        shapeProgress = 0
        fossilProgress = 0
        settleScale = 1
    }

    private func startAnimation() {
        resetProgress()
        guard let timings = PebbleAnimationTimings.forVersion(renderVersion), !reduceMotion else {
            return
        }
        withAnimation(.easeOut(duration: timings.glyph.duration).delay(timings.glyph.delay)) {
            glyphProgress = 1
        }
        withAnimation(.easeOut(duration: timings.shape.duration).delay(timings.shape.delay)) {
            shapeProgress = 1
        }
        withAnimation(.easeOut(duration: timings.fossil.duration).delay(timings.fossil.delay)) {
            fossilProgress = 1
        }
        // Settle pulse: 1.0 → 1.04 → 1.0 over the settle phase duration.
        let halfSettle = timings.settle.duration / 2
        withAnimation(.easeInOut(duration: halfSettle).delay(timings.settle.delay)) {
            settleScale = 1.04
        }
        withAnimation(.easeInOut(duration: halfSettle).delay(timings.settle.delay + halfSettle)) {
            settleScale = 1
        }
    }
}

// MARK: - Layer shape

private struct LayerShape: Shape {
    let layer: PebbleSVGModel.Layer
    let viewBox: CGRect

    func path(in rect: CGRect) -> Path {
        // Combine the layer's SVG-space transform with the viewBox→rect fit
        // so the resulting path draws at the right size and position inside
        // the Shape's drawing rect. Composition order (CG row-vector math):
        //   p' = p * layer.transform * scale * translate
        // ⇒ apply layer.transform first, then fit-scale, then center-offset.
        let scale = min(rect.width / viewBox.width, rect.height / viewBox.height)
        let scaledWidth = viewBox.width * scale
        let scaledHeight = viewBox.height * scale
        let dx = (rect.width - scaledWidth) / 2 - viewBox.minX * scale
        let dy = (rect.height - scaledHeight) / 2 - viewBox.minY * scale

        var transform = layer.transform
            .concatenating(CGAffineTransform(scaleX: scale, y: scale))
            .concatenating(CGAffineTransform(translationX: dx, y: dy))
        guard let transformed = layer.combinedPath.copy(using: &transform) else {
            return Path(layer.combinedPath)
        }
        return Path(transformed)
    }
}

#Preview("Animated · with fossil") {
    PebbleAnimatedRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 240" width="240" height="240">
          <g id="layer:shape">
            <path d="M 20 120 C 20 60 60 20 120 20 C 180 20 220 60 220 120 C 220 180 180 220 120 220 C 60 220 20 180 20 120 Z" fill="none"/>
          </g>
          <g id="layer:fossil" opacity="0.3">
            <path d="M 60 60 L 180 180 M 60 180 L 180 60" fill="none"/>
          </g>
          <g id="layer:glyph" transform="translate(70, 70) scale(0.5)">
            <path d="M 0 0 L 200 200 M 0 200 L 200 0" fill="none"/>
          </g>
        </svg>
        """,
        strokeColor: "#7C5CFA",
        renderVersion: "0.1.0"
    )
    .frame(width: 200, height: 200)
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Static fallback (unknown version)") {
    // No timings registered for this renderVersion → falls back to PebbleRenderView.
    PebbleAnimatedRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <g id="layer:shape"><path d="M 0 0 L 100 100" fill="none"/></g>
        </svg>
        """,
        strokeColor: "#7C5CFA",
        renderVersion: "unknown"
    )
    .frame(width: 200, height: 200)
}
