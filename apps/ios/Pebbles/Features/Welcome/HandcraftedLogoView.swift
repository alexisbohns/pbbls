import SwiftUI

/// The handcrafted logo loader. Plays a phased draw-on reveal, then boils
/// (#555) until `shouldSettle` flips true, then holds static. Under Reduce
/// Motion it renders static variant 0 immediately and never boils.
///
/// The view does not dismiss itself — `RootView` gates the launch on
/// `onDrawComplete` + real readiness. `shouldSettle` only tells the logo to
/// stop boiling.
struct HandcraftedLogoView: View {
    /// Parent → "app is ready; stop boiling and settle to static."
    let shouldSettle: Bool
    /// Fired once the draw-on finishes (immediately under Reduce Motion).
    var onDrawComplete: () -> Void = {}
    /// Logo ink colour. The artwork is authored black; default adapts to scheme.
    var color: Color = .system.foreground

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: LogoLoaderModel?
    @State private var phase: Phase = .drawing
    @State private var outlineProgress: Double = 0
    @State private var creatureProgress: Double = 0
    @State private var fossilVeinProgress: Double = 0
    @State private var eyesIn = false

    private enum Phase { case drawing, boiling, settled }

    // Draw-on choreography (seconds). Tunable in the simulator.
    private static let outline = (delay: 0.0, duration: 0.55)
    private static let creature = (delay: 0.45, duration: 0.70)
    private static let fossilVein = (delay: 1.00, duration: 0.55)
    private static var totalDrawDuration: Double { fossilVein.delay + fossilVein.duration }

    // Boil: 4fps ping-pong (#555 §1/§3).
    private static let boilFPS: Double = 4
    private static let boilOrder = [0, 1, 2, 1]

    var body: some View {
        Group {
            if let model {
                switch phase {
                case .drawing: drawingBody(model)
                case .boiling: boilingBody(model)
                case .settled: filledLogo(model.variants[0], model: model)
                }
            } else {
                Color.clear   // asset missing: render nothing, gate still proceeds
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
        .task { await run() }
        .onChange(of: shouldSettle) { _, settle in
            if settle && phase == .boiling { phase = .settled }
        }
    }

    // MARK: - Orchestration

    private func run() async {
        if model == nil { model = LogoLoaderArt.build() }
        guard model != nil else { onDrawComplete(); return }

        if reduceMotion {
            outlineProgress = 1; creatureProgress = 1; fossilVeinProgress = 1; eyesIn = true
            phase = .settled                 // static; never boil (#555 §3.1)
            onDrawComplete()
            return
        }

        startDrawOn()
        try? await Task.sleep(for: .seconds(Self.totalDrawDuration))
        onDrawComplete()
        // Draw finished: boil unless the app is already ready.
        phase = shouldSettle ? .settled : .boiling
    }

    private func startDrawOn() {
        withAnimation(.easeOut(duration: Self.outline.duration).delay(Self.outline.delay)) {
            outlineProgress = 1
        }
        withAnimation(.easeOut(duration: Self.creature.duration).delay(Self.creature.delay)) {
            creatureProgress = 1
        }
        withAnimation(.easeOut(duration: 0.25).delay(Self.creature.delay + Self.creature.duration - 0.1)) {
            eyesIn = true    // eyes pop in at the tail of the creature phase
        }
        withAnimation(.easeOut(duration: Self.fossilVein.duration).delay(Self.fossilVein.delay)) {
            fossilVeinProgress = 1
        }
    }

    // MARK: - Rendering

    /// Draw-on: variant 0, each stroke group's ink revealed by a trimmed
    /// centerline mask (mirrors PebbleAnimatedRenderView.animatedBody).
    @ViewBuilder
    private func drawingBody(_ model: LogoLoaderModel) -> some View {
        let variant = model.variants[0]
        GeometryReader { proxy in
            let maskWidth = WobbleMask.lineWidth(viewBox: model.viewBox, frame: proxy.size)
            ZStack {
                revealGroup(variant.outline, progress: outlineProgress, maskWidth: maskWidth, viewBox: model.viewBox)
                revealGroup(variant.creature, progress: creatureProgress, maskWidth: maskWidth, viewBox: model.viewBox)
                revealGroup(
                    variant.fossilVeins, progress: fossilVeinProgress, maskWidth: maskWidth, viewBox: model.viewBox
                )
                eyeShape(variant.eyes, viewBox: model.viewBox)
                    .opacity(eyesIn ? 1 : 0)
                    .scaleEffect(eyesIn ? 1 : 0.6)
            }
        }
    }

    @ViewBuilder
    private func revealGroup(_ art: WobbleArt, progress: Double, maskWidth: CGFloat, viewBox: CGRect) -> some View {
        WobbledPathShape(path: art.ink, layerTransform: .identity, viewBox: viewBox)
            .fill(color)
            .mask {
                WobbledPathShape(path: art.centerline, layerTransform: .identity, viewBox: viewBox)
                    .trim(from: 0, to: progress)
                    .stroke(Color.white, style: StrokeStyle(lineWidth: maskWidth, lineCap: .round, lineJoin: .round))
            }
    }

    /// Boil: cycle the three variant inks on a wall-clock timer (#555 §3).
    private func boilingBody(_ model: LogoLoaderModel) -> some View {
        TimelineView(.periodic(from: .now, by: 1.0 / Self.boilFPS)) { context in
            let tick = Int(context.date.timeIntervalSinceReferenceDate * Self.boilFPS)
            let index = Self.boilOrder[tick % Self.boilOrder.count]
            filledLogo(model.variants[index], model: model)
        }
    }

    /// Static fully-revealed logo for one variant.
    @ViewBuilder
    private func filledLogo(_ variant: LogoLoaderVariant, model: LogoLoaderModel) -> some View {
        ZStack {
            inkShape(variant.outline.ink, viewBox: model.viewBox)
            inkShape(variant.creature.ink, viewBox: model.viewBox)
            inkShape(variant.fossilVeins.ink, viewBox: model.viewBox)
            eyeShape(variant.eyes, viewBox: model.viewBox)
        }
    }

    private func inkShape(_ path: CGPath, viewBox: CGRect) -> some View {
        WobbledPathShape(path: path, layerTransform: .identity, viewBox: viewBox).fill(color)
    }

    private func eyeShape(_ path: CGPath, viewBox: CGRect) -> some View {
        WobbledPathShape(path: path, layerTransform: .identity, viewBox: viewBox).fill(color)
    }
}

#Preview("Draw-on then boil") {
    HandcraftedLogoView(shouldSettle: false)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.system.background)
}

#Preview("Settled (static)") {
    HandcraftedLogoView(shouldSettle: true)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.system.background)
}
