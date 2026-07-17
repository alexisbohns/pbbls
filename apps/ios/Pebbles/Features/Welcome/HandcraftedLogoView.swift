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
    /// Skip the draw-on and start fully revealed — used when the loader is
    /// re-shown to cover a later load (e.g. the authed home feed) after the
    /// draw-on has already played once, so it holds/boils without redrawing.
    var startSettled: Bool = false
    /// Logo ink colour. Tinted with the brand accent by default.
    var color: Color = .accent.primary

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model: LogoLoaderModel?
    @State private var phase: Phase = .drawing
    @State private var outlineProgress: Double = 0
    /// Linear 0→1 clock for the creature phase; each stroke derives its own
    /// staggered trim from it so the creature draws one line at a time.
    @State private var creatureClock: Double = 0
    @State private var fossilVeinProgress: Double = 0
    @State private var eyesIn = false
    /// Fresh @State mirror of `shouldSettle` — the running `.task` captures the
    /// view by value, so it can't observe input changes; onChange keeps this
    /// current for `settleIfReady()`.
    @State private var wantsSettle = false
    /// True once the guaranteed minimum boil has played.
    @State private var minBoilElapsed = false

    private enum Phase { case drawing, boiling, settled }

    // Draw-on choreography (seconds). Slow, gentle hand-drawing. The creature
    // window is longer because its strokes draw one at a time. Tunable.
    private static let outline = (delay: 0.0, duration: 1.2)
    private static let creature = (delay: 1.0, duration: 2.2)
    private static let fossilVein = (delay: 3.0, duration: 1.3)
    private static var totalDrawDuration: Double { fossilVein.delay + fossilVein.duration }
    /// Per-stroke overlap within the creature phase: 1.0 = strictly one-then-
    /// next; >1 lets the next line start before the previous finishes.
    private static let creatureStrokeOverlap: Double = 1.25

    // Boil: 4fps ping-pong (#555 §1/§3).
    private static let boilFPS: Double = 4
    private static let boilOrder = [0, 1, 2, 1]
    /// Minimum boil after the draw-on so it's always visibly alive before
    /// settling — never an instant cut to static (~5 frames at 4fps). Tunable.
    private static let minBoilSeconds: Double = 1.25

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
        .onChange(of: shouldSettle) { _, newValue in
            wantsSettle = newValue
            settleIfReady()
        }
    }

    // MARK: - Orchestration

    private func run() async {
        wantsSettle = shouldSettle
        if model == nil { model = LogoLoaderArt.build() }
        guard model != nil else { onDrawComplete(); return }

        if reduceMotion || startSettled {
            outlineProgress = 1; creatureClock = 1; fossilVeinProgress = 1; eyesIn = true
            minBoilElapsed = true
            onDrawComplete()
            // Reduce Motion never boils (#555 §3.1); startSettled boils until
            // the parent signals ready (or removes the view).
            phase = (reduceMotion || wantsSettle) ? .settled : .boiling
            return
        }

        startDrawOn()
        try? await Task.sleep(for: .seconds(Self.totalDrawDuration))
        onDrawComplete()
        phase = .boiling
        // Guarantee the boil is actually seen — never an instant cut to static,
        // even when the app was ready before the draw-on finished.
        try? await Task.sleep(for: .seconds(Self.minBoilSeconds))
        minBoilElapsed = true
        settleIfReady()
    }

    /// Settle to static only once the minimum boil has elapsed AND the app is
    /// ready. Driven by the min-boil timer and by `shouldSettle` changes.
    private func settleIfReady() {
        if phase == .boiling && minBoilElapsed && wantsSettle {
            phase = .settled
        }
    }

    private func startDrawOn() {
        withAnimation(.easeInOut(duration: Self.outline.duration).delay(Self.outline.delay)) {
            outlineProgress = 1
        }
        // Linear clock so each creature stroke gets an even slice of the phase.
        withAnimation(.linear(duration: Self.creature.duration).delay(Self.creature.delay)) {
            creatureClock = 1
        }
        withAnimation(.easeInOut(duration: 0.35).delay(Self.creature.delay + Self.creature.duration - 0.15)) {
            eyesIn = true    // eyes pop in at the tail of the creature phase
        }
        withAnimation(.easeInOut(duration: Self.fossilVein.duration).delay(Self.fossilVein.delay)) {
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
                revealPhase(variant.outline, progress: outlineProgress, maskWidth: maskWidth, viewBox: model.viewBox)
                revealCreature(variant.creature, clock: creatureClock, maskWidth: maskWidth, viewBox: model.viewBox)
                revealPhase(
                    variant.fossilVeins, progress: fossilVeinProgress, maskWidth: maskWidth, viewBox: model.viewBox
                )
                eyeShape(variant.eyes, viewBox: model.viewBox)
                    .opacity(eyesIn ? 1 : 0)
                    .scaleEffect(eyesIn ? 1 : 0.6)
            }
        }
    }

    /// Reveals every stroke in a phase against the shared phase progress. Each
    /// stroke is masked by its OWN centerline so a fat reveal mask can never
    /// expose a neighbour stroke's ink (#598 bleed fix).
    @ViewBuilder
    private func revealPhase(_ arts: [WobbleArt], progress: Double, maskWidth: CGFloat, viewBox: CGRect) -> some View {
        ForEach(Array(arts.enumerated()), id: \.offset) { _, art in
            revealGroup(art, progress: progress, maskWidth: maskWidth, viewBox: viewBox)
        }
    }

    /// Reveals the creature strokes one at a time: each stroke draws over its
    /// own staggered slice of the linear `clock`, so the creature is sketched
    /// line by line rather than all at once.
    @ViewBuilder
    private func revealCreature(_ arts: [WobbleArt], clock: Double, maskWidth: CGFloat, viewBox: CGRect) -> some View {
        ForEach(Array(arts.enumerated()), id: \.offset) { index, art in
            revealGroup(
                art,
                progress: Self.strokeProgress(clock: clock, index: index, count: arts.count),
                maskWidth: maskWidth,
                viewBox: viewBox
            )
        }
    }

    /// Stroke `index` of `count`'s local 0→1 reveal, derived from the phase
    /// clock: each stroke owns a `1/count` slot (widened by the overlap factor)
    /// and eases across it with a smoothstep.
    private static func strokeProgress(clock: Double, index: Int, count: Int) -> Double {
        guard count > 0 else { return clock }
        let step = 1.0 / Double(count)
        let start = Double(index) * step
        let span = step * creatureStrokeOverlap
        let local = min(1, max(0, (clock - start) / span))
        return local * local * (3 - 2 * local)   // smoothstep ease per stroke
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
            inkPhase(variant.outline, viewBox: model.viewBox)
            inkPhase(variant.creature, viewBox: model.viewBox)
            inkPhase(variant.fossilVeins, viewBox: model.viewBox)
            eyeShape(variant.eyes, viewBox: model.viewBox)
        }
    }

    @ViewBuilder
    private func inkPhase(_ arts: [WobbleArt], viewBox: CGRect) -> some View {
        ForEach(Array(arts.enumerated()), id: \.offset) { _, art in
            inkShape(art.ink, viewBox: viewBox)
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

#Preview("Cover (boil, no draw-on)") {
    HandcraftedLogoView(shouldSettle: false, startSettled: true)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.system.background)
}
