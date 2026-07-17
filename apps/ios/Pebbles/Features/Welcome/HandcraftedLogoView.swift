import SwiftUI

/// The handcrafted logo loader. Plays a phased draw-on reveal, then boils
/// (#555) for at least `minBoilTicks` and until `shouldSettle` is true, then
/// holds static. Under Reduce Motion it renders static variant 0 and never
/// boils. The view does not dismiss itself — it emits `onSettled` once the
/// whole sequence is done, which is what `RootView` gates the app on.
struct HandcraftedLogoView: View {
    /// Parent → "app data is ready; the loader may settle once it has boiled
    /// enough." (Drives boil → settle; not the app transition itself.)
    let shouldSettle: Bool
    /// Fired once the loader has fully settled — i.e. drawn on AND boiled the
    /// minimum AND the app is ready. This is the "ok to show the app" event the
    /// parent gates its transition on (not the earlier draw-on completion).
    var onSettled: () -> Void = {}
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
    private static let minBoilTicks = 20

    var body: some View {
        Group {
            if let model {
                logoBody(model)
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
        guard model != nil else { onSettled(); return }   // no art: never block launch

        // Reduce Motion: static logo, no boil (#555 §3.1). Still wait for the
        // app to be ready before settling so nothing shows early.
        if reduceMotion {
            revealAll()
            phase = .settled
            await waitUntilReady()
            onSettled()
            return
        }

        // Cover mode (authed feed): skip the draw-on, reveal fully, boil until
        // the parent removes this view. It never settles itself.
        if startSettled {
            revealAll()
            phase = .boiling
            await boil(until: { _ in false })
            return
        }

        // Main loader: draw on, then boil until we've boiled at least
        // `minBoilTicks` AND the app is ready, landing on a variant-0 frame so
        // the settle is seamless. THEN emit `onSettled` — this is what gates the
        // app transition, so the full boil is always played.
        startDrawOn()
        try? await Task.sleep(for: .seconds(Self.totalDrawDuration))
        phase = .boiling
        await boil(until: { ticks in
            ticks >= Self.minBoilTicks
                && wantsSettle
                && Self.boilOrder[boilFrame % Self.boilOrder.count] == 0
        })
        phase = .settled
        onSettled()
    }

    /// Advance the discrete boil one tick at a time (one ping-pong swap each)
    /// until `stop` returns true or the task is cancelled.
    private func boil(until stop: (_ ticks: Int) -> Bool) async {
        var ticks = 0
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(1.0 / Self.boilFPS))
            boilFrame += 1
            ticks += 1
            if stop(ticks) { return }
        }
    }

    /// Poll until the app signals ready — the Reduce Motion path, which doesn't
    /// boil but must still hold until the data is loaded.
    private func waitUntilReady() async {
        while !Task.isCancelled && !wantsSettle {
            try? await Task.sleep(for: .seconds(0.1))
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

    /// Which variant's geometry is shown right now: the boil cycles variants,
    /// every other phase shows variant 0.
    private var currentVariantIndex: Int {
        phase == .boiling ? Self.boilOrder[boilFrame % Self.boilOrder.count] : 0
    }

    /// Single render path for EVERY phase — the view structure never changes,
    /// only the data (per-stroke `progress`, `variant` index). This is what
    /// keeps the draw-on → boil hand-off from flashing: at the boundary the
    /// progress is already all-1 and the variant is still 0, so nothing in the
    /// tree is torn down and rebuilt. Each stroke stays masked by its own
    /// centerline (trim = 1 fully reveals its ink) so there's no bleed.
    private func logoBody(_ model: LogoLoaderModel) -> some View {
        let variant = model.variants[currentVariantIndex]
        return GeometryReader { proxy in
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
