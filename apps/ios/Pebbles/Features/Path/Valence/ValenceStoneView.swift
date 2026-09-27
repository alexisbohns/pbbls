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

    /// The rest light, or the sweep's light at `date`. Ends exactly at rest so
    /// the last animated frame and the still frame are the same picture.
    private func light(at date: Date) -> StoneLight {
        guard let sweepStart else { return .rest }
        let elapsed = date.timeIntervalSince(sweepStart)
        guard elapsed < Self.sweepDuration else {
            // Settle on the next run loop turn: mutating state inside a
            // TimelineView body is refused.
            DispatchQueue.main.async { self.sweepStart = nil }
            return .rest
        }
        let t = elapsed / Self.sweepDuration
        let eased = 0.5 - 0.5 * cos(t * .pi)           // ease in-out over the whole sweep
        let swing = sin(eased * .pi)                    // out and back: 0 → 1 → 0
        let restAngle = atan2(StoneLight.rest.direction.dy, StoneLight.rest.direction.dx)
        let angle = restAngle + swing * Self.sweepSwing
        let elevation = StoneLight.rest.elevationDegrees - swing * 20
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
