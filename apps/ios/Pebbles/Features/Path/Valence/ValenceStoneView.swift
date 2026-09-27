import SwiftUI

/// One valence stone in the picker: the lit stone render (#974) in the brand
/// palette, with the maintainer's material, tone and look tables. Every stone
/// is lit. Choosing one makes the light sweep across it once (top-left to
/// top-right and back, dipping so the rim and the carving lips catch) and
/// settle at the rest light, on top of the scale and dimming the picker
/// applies. The sweep is finite: once it ends the stone stops redrawing.
///
/// Both wobbled layers are memoized (`WobbleRenderer`, `StoneCarvingArt`), so
/// nine stones cost nine parses once per process, never per frame. Low Power
/// Mode draws the flat rendering; Reduce Motion skips the sweep.
struct ValenceStoneView: View {
    let valence: Valence
    /// On-screen height of the whole stone, backdrop included.
    let height: CGFloat
    /// Starts the light sweep when it turns true.
    var isSelected: Bool = false
    /// The phone's lean, when this stone follows it (the chosen stone in the
    /// picker); nil keeps the rest light.
    var lean: Lean?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the current sweep started; nil when the stone is still.
    @State private var sweepStart: Date?

    private static let sweepDuration: TimeInterval = 1.6
    /// How far the light swings each way from the rest direction, in radians.
    private static let sweepSwing: Double = .pi / 3

    var body: some View {
        TimelineView(.animation(minimumInterval: 1 / 60, paused: sweepStart == nil)) { context in
            stone(light: light(at: context.date))
        }
        // `lean` is part of this view's inputs, so a published lean re-renders
        // the stone whether or not the timeline is running.
        .frame(width: height * PebbleOutlineGeometry.aspectRatio(for: valence.sizeGroup), height: height)
        .onChange(of: isSelected, initial: true) { _, selected in
            guard selected, !reduceMotion else {
                sweepStart = nil
                return
            }
            sweepStart = .now
        }
    }

    private func stone(light: StoneLight) -> some View {
        StoneView(
            valence: valence,
            palette: .brand,
            material: .starting(for: valence.polarity),
            tones: .starting(for: valence.polarity),
            light: light,
            height: height,
            isFlat: ProcessInfo.processInfo.isLowPowerModeEnabled
        )
    }

    /// The tilted (or rest) light, with the sweep's swing added while it runs.
    /// The sweep ends exactly on the tilted light, so the last animated frame
    /// and the still frame are the same picture.
    private func light(at date: Date) -> StoneLight {
        let base = StoneLight.tilted(by: lean ?? .zero)
        guard let sweepStart else { return base }
        let elapsed = date.timeIntervalSince(sweepStart)
        guard elapsed < Self.sweepDuration else {
            // Settle on the next run loop turn: mutating state inside a
            // TimelineView body is refused.
            DispatchQueue.main.async { self.sweepStart = nil }
            return base
        }
        let t = elapsed / Self.sweepDuration
        let eased = 0.5 - 0.5 * cos(t * .pi)           // ease in-out over the whole sweep
        let swing = sin(eased * .pi)                    // out and back: 0 → 1 → 0
        let baseAngle = atan2(base.direction.dy, base.direction.dx)
        let angle = baseAngle + swing * Self.sweepSwing
        let elevation = base.elevationDegrees - swing * 20
        return StoneLight(direction: CGVector(dx: cos(angle), dy: sin(angle)), elevationDegrees: elevation)
    }
}

#Preview("the nine stones") {
    VStack(spacing: 24) {
        ForEach(ValenceSizeGroup.allCases) { group in
            HStack(spacing: 20) {
                ForEach(ValencePolarity.allCases, id: \.self) { polarity in
                    if let valence = Valence.allCases.first(
                        where: { $0.sizeGroup == group && $0.polarity == polarity }
                    ) {
                        ValenceStoneView(
                            valence: valence,
                            height: ValenceFanLayout.stoneHeight(for: group)
                        )
                    }
                }
            }
        }
    }
    .padding()
}
