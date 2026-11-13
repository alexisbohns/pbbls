import SwiftUI

/// The Pebbles mark, wobbling. A 4 fps ping-pong across three jittered
/// variants of the outline (`LogoMark`), from the first frame — no draw-on.
///
/// How it behaves depends on `mode`:
/// - `.splash` wobbles until the app is ready, then emits `onSettled` and holds
///   the static mark. If the app is already ready on appear it settles at once:
///   there is no minimum duration. This is what `RootView` gates the app on.
/// - `.cover` wobbles until the parent removes it (a loader held over a later
///   load); it never settles itself.
///
/// Under Reduce Motion the mark is static; a splash still waits for readiness
/// before it settles.
struct WobblingLogoView: View {
    enum Mode { case splash, cover }

    /// Parent → "the app is ready; the splash may settle." Ignored in cover mode.
    let shouldSettle: Bool
    /// Fired once, the first time the splash is ready. The "ok to show the app"
    /// event the parent gates its transition on.
    var onSettled: () -> Void = {}
    var mode: Mode = .splash
    /// Logo ink colour. Tinted with the brand accent by default.
    var color: Color = Color.pebbles.primary

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var gate = LogoSettleGate()
    @State private var settled = false
    /// One increment per wobble tick; indexes `boilOrder`.
    @State private var frame = 0

    // Wobble: 4fps ping-pong (#555 §1/§3).
    private static let wobbleFPS: Double = 4
    private static let boilOrder = [0, 1, 2, 1]

    private var wobbles: Bool {
        guard !reduceMotion else { return false }
        return mode == .cover || !settled
    }

    var body: some View {
        Group {
            if let model = LogoMark.build() {
                logoBody(model)
            } else {
                Color.clear   // asset missing: render nothing, the gate still proceeds
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
        .onChange(of: shouldSettle, initial: true) { _, ready in
            guard mode == .splash, gate.shouldFire(ready: ready) else { return }
            settled = true
            onSettled()
        }
        .task(id: wobbles) {
            guard wobbles else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1.0 / Self.wobbleFPS))
                if Task.isCancelled { break }
                frame += 1
            }
        }
    }

    /// The mark to draw right now: a wobbled variant while wobbling, the exact
    /// outline otherwise.
    private func logoBody(_ model: LogoMark.Model) -> some View {
        let path = wobbles
            ? model.variants[Self.boilOrder[frame % Self.boilOrder.count]]
            : model.outline
        return WobbledPathShape(path: path, layerTransform: .identity, viewBox: model.viewBox)
            .fill(color, style: FillStyle(eoFill: true))
    }
}

#Preview("Splash (wobbling)") {
    WobblingLogoView(shouldSettle: false)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.pebbles.surface)
}

#Preview("Settled (static)") {
    WobblingLogoView(shouldSettle: true)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.pebbles.surface)
}

#Preview("Cover (wobbling)") {
    WobblingLogoView(shouldSettle: false, mode: .cover)
        .frame(width: 160, height: 160)
        .padding()
        .background(Color.pebbles.surface)
}
