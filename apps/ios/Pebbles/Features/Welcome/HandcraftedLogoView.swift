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
    /// One 0→1 reveal per creature stroke, each animated with its own delay so
    /// the creature is sketched line by line. (A single shared scalar can't
    /// stagger them: SwiftUI evaluates the body only at the animation endpoint,
    /// so every derived trim would animate together.)
    @State private var creatureProgress: [Double] = []
    @State private var fossilVeinProgress: Double = 0
    @State private var eyesIn = false
    /// Fresh @State mirror of `shouldSettle` — the running `.task` captures the
    /// view by value, so it can't observe input changes; onChange keeps this
    /// current for the boil loop to poll.
    @State private var wantsSettle = false
    /// Discrete boil frame counter — one increment per boil "tick". Drives the
    /// boil render and, via a count, the settle decision.
    @State private var boilFrame = 0

    private enum Phase { case drawing, boiling, settled }

    // Draw-on choreography (seconds). Slow, gentle hand-drawing. The creature
    // window is longer because its strokes draw one at a time. Tunable.
    private static let outline = (delay: 0.0, duration: 1.2)
    private static let creature = (delay: 1.0, duration: 2.2)
    private static let fossilVein = (delay: 3.0, duration: 1.3)
    private static var totalDrawDuration: Double { fossilVein.delay + fossilVein.duration }
    /// How long a single creature line takes to draw. Consecutive lines start
    /// `creature.duration` apart / (count-1), so they overlap slightly.
    private static let creatureStrokeDuration: Double = 0.55

    // Boil: 4fps ping-pong (#555 §1/§3).
    private static let boilFPS: Double = 4
    private static let boilOrder = [0, 1, 2, 1]
    /// Minimum number of boil ticks after the draw-on before the logo may
    /// settle — a count, not a duration. The logo keeps boiling past this
    /// until the app is ready, and only ever settles on a variant-0 frame so
    /// the transition to the static logo is seamless (no brutal cut). Tunable.
    private static let minBoilTicks = 4

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
            // Just keep the mirror fresh; the boil loop reacts to it. (@State is
            // read live from the running task; the `let` input can't be.)
            wantsSettle = newValue
        }
    }

    // MARK: - Orchestration

    private func run() async {
        wantsSettle = shouldSettle
        if model == nil { model = LogoLoaderArt.build() }
        guard model != nil else { onDrawComplete(); return }

        // Reduce Motion: static logo, never boils (#555 §3.1).
        if reduceMotion {
            revealAll()
            onDrawComplete()
            phase = .settled
            return
        }

        if startSettled {
            // Cover mode: skip the draw-on, reveal fully, then boil. The parent
            // removes this view when its work finishes, so it just boils.
            revealAll()
            onDrawComplete()
        } else {
            startDrawOn()
            try? await Task.sleep(for: .seconds(Self.totalDrawDuration))
            onDrawComplete()
        }

        await boilUntilReady()
    }

    /// Boil until enough ticks have played AND the app is ready, then settle —
    /// event/count-driven, not duration-driven. Settles only on a variant-0
    /// frame so the switch to the static logo is invisible. The cover
    /// (`shouldSettle` never true) simply boils until the parent removes it.
    private func boilUntilReady() async {
        phase = .boiling
        var ticks = 0
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(1.0 / Self.boilFPS))
            boilFrame += 1
            ticks += 1
            let onSettleFrame = Self.boilOrder[boilFrame % Self.boilOrder.count] == 0
            if ticks >= Self.minBoilTicks && wantsSettle && onSettleFrame {
                phase = .settled
                return
            }
        }
    }

    /// Fully reveal every group (no draw-on) — for Reduce Motion and cover mode.
    private func revealAll() {
        outlineProgress = 1
        creatureProgress = Array(repeating: 1, count: creatureCount)
        fossilVeinProgress = 1
        eyesIn = true
    }

    /// Number of creature strokes in the built art (0 until the model loads).
    private var creatureCount: Int { model?.variants.first?.creature.count ?? 0 }

    private func startDrawOn() {
        withAnimation(.easeInOut(duration: Self.outline.duration).delay(Self.outline.delay)) {
            outlineProgress = 1
        }

        // One animation per creature stroke, each with its own delay, so the
        // lines draw in sequence rather than all together.
        let count = creatureCount
        creatureProgress = Array(repeating: 0, count: count)
        let strokeDuration = Self.creatureStrokeDuration
        let stagger = count > 1 ? (Self.creature.duration - strokeDuration) / Double(count - 1) : 0
        for index in 0..<count {
            withAnimation(.easeInOut(duration: strokeDuration).delay(Self.creature.delay + Double(index) * stagger)) {
                creatureProgress[index] = 1
            }
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
                revealCreature(
                    variant.creature, progress: creatureProgress, maskWidth: maskWidth, viewBox: model.viewBox
                )
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

    /// Reveals the creature strokes one at a time: each stroke reads its own
    /// entry in `progress`, animated on its own delay in `startDrawOn`.
    @ViewBuilder
    private func revealCreature(
        _ arts: [WobbleArt], progress: [Double], maskWidth: CGFloat, viewBox: CGRect
    ) -> some View {
        ForEach(Array(arts.enumerated()), id: \.offset) { index, art in
            revealGroup(
                art,
                progress: index < progress.count ? progress[index] : 0,
                maskWidth: maskWidth,
                viewBox: viewBox
            )
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

    /// Boil: show the variant for the current boil tick. `boilFrame` advances
    /// in `boilUntilReady()`, so each increment is one discrete ping-pong swap
    /// (#555 §3 — no interpolation between frames).
    private func boilingBody(_ model: LogoLoaderModel) -> some View {
        let index = Self.boilOrder[boilFrame % Self.boilOrder.count]
        return filledLogo(model.variants[index], model: model)
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
