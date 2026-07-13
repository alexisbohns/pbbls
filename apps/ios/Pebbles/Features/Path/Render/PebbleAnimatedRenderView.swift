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
    /// Stroke for the SwiftUI shape paths used during the animation.
    let strokeColor: Color
    /// Hex equivalent of `strokeColor`, injected into raw SVG markup by the
    /// static `PebbleRenderView` fallback (Reduce Motion, unknown timings,
    /// or parse failure). Must match `strokeColor` for the current scheme.
    let strokeColorHex: String
    let fillHex: String
    let fillOpacity: Double
    let size: ValenceSizeGroup
    let polarity: ValencePolarity
    let renderVersion: String?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: PebbleSVGModel?
    @State private var wobbleArt: WobblePebbleArt?
    @State private var glyphProgress: Double = 0
    @State private var shapeProgress: Double = 0
    @State private var fossilProgress: Double = 0
    @State private var settleScale: Double = 1
    @State private var backdropIn: Bool = false
    @State private var pebbleIn: Bool = false

    var body: some View {
        ZStack {
            PebbleOutlineBackdropView(size: size, polarity: polarity, fillHex: fillHex, fillOpacity: fillOpacity)
                .scaleEffect(reduceMotion ? 1 : (backdropIn ? 1 : 0.6))
                .opacity(reduceMotion ? 1 : (backdropIn ? 1 : 0))

            pebbleLayer
                .scaleEffect(PebbleOutlineGeometry.pebbleScale(for: size))
                .opacity(reduceMotion ? 1 : (pebbleIn ? 1 : 0))
        }
        .aspectRatio(PebbleOutlineGeometry.aspectRatio(for: size), contentMode: .fit)
        .onAppear {
            if model == nil {
                model = PebbleSVGModel(svg: svg)
                if model == nil {
                    Logger(subsystem: "app.pbbls.ios", category: "pebble-render")
                        .info("PebbleAnimatedRenderView: parse failed; using SVGView fallback")
                }
            }
            if WobbleFlags.isEnabled, wobbleArt == nil, let model {
                wobbleArt = WobbleRenderer.pebbleArt(svg: svg, model: model)
            }
            startEntryAnimation()
            startAnimation()
        }
        .onDisappear {
            resetProgress()
            backdropIn = false
            pebbleIn = false
        }
    }

    @ViewBuilder
    private var pebbleLayer: some View {
        if let model, let timings = PebbleAnimationTimings.forVersion(renderVersion), !reduceMotion {
            animatedBody(model: model, timings: timings)
        } else if let model, let wobbleArt {
            // Wobble experiment (#555) under Reduce Motion or unknown timings:
            // static wobbled ink — no reveal, no timers.
            staticWobbledBody(model: model, art: wobbleArt)
        } else {
            PebbleRenderView(svg: svg, strokeColor: strokeColorHex)
        }
    }

    private func startEntryAnimation() {
        guard !reduceMotion else {
            backdropIn = true
            pebbleIn = true
            return
        }
        withAnimation(.spring(response: 0.42, dampingFraction: 0.7)) {
            backdropIn = true
        }
        withAnimation(.easeOut(duration: 0.25).delay(0.18)) {
            pebbleIn = true
        }
    }

    // MARK: - Animated rendering

    @ViewBuilder
    private func animatedBody(model: PebbleSVGModel, timings: PebbleAnimationTimings.Timings) -> some View {
        Group {
            if let wobbleArt {
                // Wobble experiment (#555): the leaky filled ink cannot be
                // trim-stroked, so the draw-on becomes a fat trimmed mask
                // stroking along the wobbled centerline — same progress
                // states and per-layer timings as the classic path below.
                GeometryReader { proxy in
                    let maskWidth = WobbleMask.lineWidth(viewBox: model.viewBox, frame: proxy.size)
                    ZStack {
                        ForEach(Array(model.layers.enumerated()), id: \.offset) { index, layer in
                            WobbledPathShape(
                                path: wobbleArt.layers[index].ink,
                                layerTransform: layer.transform,
                                viewBox: model.viewBox
                            )
                            .fill(stroke)
                            .mask {
                                WobbledPathShape(
                                    path: wobbleArt.layers[index].centerline,
                                    layerTransform: layer.transform,
                                    viewBox: model.viewBox
                                )
                                .trim(from: 0, to: progress(for: layer.kind))
                                .stroke(
                                    Color.white,
                                    style: StrokeStyle(lineWidth: maskWidth, lineCap: .round, lineJoin: .round)
                                )
                            }
                            .opacity(layer.opacity)
                        }
                    }
                }
            } else {
                ZStack {
                    ForEach(Array(model.layers.enumerated()), id: \.offset) { _, layer in
                        LayerShape(layer: layer, viewBox: model.viewBox)
                            .trim(from: 0, to: progress(for: layer.kind))
                            .stroke(stroke, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                            .opacity(layer.opacity)
                    }
                }
            }
        }
        .scaleEffect(settleScale)
        .accessibilityHidden(true)
    }

    @ViewBuilder
    private func staticWobbledBody(model: PebbleSVGModel, art: WobblePebbleArt) -> some View {
        ZStack {
            ForEach(Array(model.layers.enumerated()), id: \.offset) { index, layer in
                WobbledPathShape(
                    path: art.layers[index].ink,
                    layerTransform: layer.transform,
                    viewBox: model.viewBox
                )
                .fill(stroke)
                .opacity(layer.opacity)
            }
        }
        .accessibilityHidden(true)
    }

    private var stroke: Color { strokeColor }

    private func progress(for kind: PebbleSVGModel.Layer.Kind) -> Double {
        switch kind {
        case .glyph:  return glyphProgress
        case .shape:  return shapeProgress
        case .fossil: return fossilProgress
        }
    }

    /// Resets only the stroke-trim progress. The entry-animation flags
    /// (`backdropIn` / `pebbleIn`) are deliberately NOT reset here:
    /// `startAnimation()` calls this after `startEntryAnimation()` has
    /// already set them, so clearing them here would blank the view.
    /// They are reset in `.onDisappear` instead.
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
        strokeColor: Color(red: 0.486, green: 0.361, blue: 0.980),
        strokeColorHex: "#7C5CFA",
        fillHex: "#7C5CFA",
        fillOpacity: 1,
        size: .medium,
        polarity: .neutral,
        renderVersion: "0.1.0"
    )
    .frame(width: 200, height: 200)
    .padding()
    .background(Color.system.background)
}

#Preview("Static fallback (unknown version)") {
    // No timings registered for this renderVersion → falls back to PebbleRenderView.
    PebbleAnimatedRenderView(
        svg: """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <g id="layer:shape"><path d="M 0 0 L 100 100" fill="none"/></g>
        </svg>
        """,
        strokeColor: Color(red: 0.486, green: 0.361, blue: 0.980),
        strokeColorHex: "#7C5CFA",
        fillHex: "#7C5CFA",
        fillOpacity: 1,
        size: .medium,
        polarity: .neutral,
        renderVersion: "unknown"
    )
    .frame(width: 200, height: 200)
}
