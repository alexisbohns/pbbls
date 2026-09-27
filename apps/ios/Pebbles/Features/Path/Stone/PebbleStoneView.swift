import SwiftUI

/// A real pebble as lit stone (#974), fitted into whatever frame it is given:
/// the seed's veins and fossil for its valence, its own glyph carved from its
/// composed SVG, coloured by its emotion's palette (the brand accent when the
/// palette is unknown). Used by the Path rows at the rest light, and by the
/// read page with `followsTilt`, where the light follows the phone in the
/// hand and sweeps once on appearance.
///
/// Nothing redraws by itself: the tilt publishes only when the phone moves,
/// the sweep is finite, and Low Power Mode draws the flat rendering.
struct PebbleStoneView: View {
    let valence: Valence
    let renderSvg: String?
    let palette: EmotionPalette?
    var followsTilt: Bool = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = StoneMotionSource()
    @State private var sweepStart: Date?

    private static let sweepDuration: TimeInterval = 1.6
    private static let sweepSwing: Double = .pi / 3

    private var stonePalette: StonePalette {
        palette.map(StonePalette.init(emotion:)) ?? .brand
    }

    var body: some View {
        GeometryReader { proxy in
            let aspect = PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup)
            let height = max(0, min(proxy.size.height, proxy.size.width / aspect))
            TimelineView(.animation(minimumInterval: 1 / 60, paused: sweepStart == nil)) { context in
                StoneView(
                    valence: valence,
                    palette: stonePalette,
                    material: .starting(for: valence.polarity),
                    tones: .starting(for: valence.polarity),
                    light: light(at: context.date),
                    height: height,
                    isFlat: ProcessInfo.processInfo.isLowPowerModeEnabled,
                    pebbleSvg: renderSvg
                )
            }
            .frame(width: proxy.size.width, height: proxy.size.height)
        }
        .aspectRatio(PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup), contentMode: .fit)
        .onAppear {
            guard followsTilt else { return }
            motion.start()
            if !reduceMotion { sweepStart = .now }
        }
        .onDisappear {
            motion.stop()
            sweepStart = nil
        }
    }

    private func light(at date: Date) -> StoneLight {
        let base = StoneLight.tilted(by: followsTilt ? motion.lean : .zero)
        guard let sweepStart else { return base }
        let elapsed = date.timeIntervalSince(sweepStart)
        guard elapsed < Self.sweepDuration else {
            DispatchQueue.main.async { self.sweepStart = nil }
            return base
        }
        let t = elapsed / Self.sweepDuration
        let eased = 0.5 - 0.5 * cos(t * .pi)
        let swing = sin(eased * .pi)
        let baseAngle = atan2(base.direction.dy, base.direction.dx)
        let angle = baseAngle + swing * Self.sweepSwing
        return StoneLight(direction: CGVector(dx: cos(angle), dy: sin(angle)),
                          elevationDegrees: base.elevationDegrees - swing * 20)
    }
}
