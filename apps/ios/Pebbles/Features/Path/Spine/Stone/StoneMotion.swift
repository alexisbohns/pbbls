import SwiftUI

/// The stone press (#1130 R12–R14), the web's `stone-motion.ts` table verbatim
/// (glint strengths from `stone-tones.ts`, in `StoneGlintInk`). WAAPI semantics,
/// the Android lesson: one easing over each element's whole run, linear between
/// keyframes in that eased progress. Times are seconds from the press. Nothing
/// runs unless a stone is pressed, and every element is finite.
enum StoneMotion {
    typealias Frames = [(at: Double, value: Double)]

    /// One animated element: a delay, a duration, one easing over the run.
    struct Element {
        let delay: Double
        let duration: Double
        let curve: UnitCurve

        var end: Double { delay + duration }

        /// The eased progress at `elapsed`: 0 until the start, 1 from the end.
        /// A zero-length element jumps at its delay instead of dividing by zero.
        func progress(at elapsed: Double) -> Double {
            guard duration > 0 else { return elapsed >= delay ? 1 : 0 }
            return curve.value(at: min(max((elapsed - delay) / duration, 0), 1))
        }

        func isRunning(at elapsed: Double) -> Bool { elapsed >= delay && elapsed < end }
    }

    struct WaveSample: Equatable {
        let scale: Double
        let alpha: Double
    }

    /// Every animated value at one instant of a press.
    struct Frame: Equatable {
        var dip = 1.0
        var rings = 1.0
        /// The sweep's travelled fraction while it runs; nil while parked off the stone.
        var sweep: Double?
        var sparkScale = 0.0
        var sparkRotation = 0.0
        var sparkAlpha = 0.0
        var waves: [WaveSample] = []
        /// Reduced motion (R13): the still sheen's opacity, and the rings in ink-2.
        var stillSheen = 0.0
        var stillRings = false
    }

    /// The value at `progress` through `frames`: linear between two keyframes,
    /// the end values outside them.
    static func piecewise(_ progress: Double, _ frames: Frames) -> Double {
        guard let first = frames.first, let last = frames.last else { return 0 }
        if progress <= first.at { return first.value }
        if progress >= last.at { return last.value }
        for index in 1..<frames.count where progress <= frames[index].at {
            let (t0, v0) = frames[index - 1]
            let (t1, v1) = frames[index]
            return t1 == t0 ? v1 : v0 + (v1 - v0) * (progress - t0) / (t1 - t0)
        }
        return last.value
    }

    private static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> UnitCurve {
        .bezier(startControlPoint: UnitPoint(x: x1, y: y1), endControlPoint: UnitPoint(x: x2, y: y2))
    }

    /// The stone dips under the finger and springs back: 1 → .9 at 26 % → 1.03 at 60 % → 1.
    enum Dip {
        static let element = Element(delay: 0, duration: 0.52, curve: bezier(0.3, 0.7, 0.2, 1))
        static let frames: Frames = [(0, 1), (0.26, 0.9), (0.6, 1.03), (1, 1)]
    }

    /// The raked rings swell once: 1 → 1.08 at 35 % → 1.
    enum Rings {
        static let element = Element(delay: 0.11, duration: 1.15, curve: bezier(0.2, 0.7, 0.2, 1))
        static let frames: Frames = [(0, 1), (0.35, 1.08), (1, 1)]
    }

    /// A sheen band crosses the stone from the light, over 1.6 stone widths.
    enum Sweep {
        static let element = Element(delay: 0.14, duration: 0.9, curve: bezier(0.37, 0, 0.63, 1))
        static let travel = 1.6
    }

    /// A four-point spark lands where the light is.
    enum Spark {
        static let element = Element(delay: 0.3, duration: 0.76, curve: bezier(0.3, 0.6, 0.3, 1))
        static let scaleFrames: Frames = [(0, 0), (0.35, 1), (1, 0)]
        static let rotationFrames: Frames = [(0, 0), (0.35, 40), (1, 90)]
        static let alphaFrames: Frames = [(0, 0), (0.35, 1), (1, 0)]
    }

    /// Three waves of the flat outline, k = 0, 1, 2: 1.15 at alpha 0 → 1.45 at .85
    /// (7 %) → `reach − 0.5k` stone widths at alpha 0.
    enum Wave {
        static let count = 3
        static let reach: [ValenceSizeGroup: Double] = [.small: 7.2, .medium: 5.6, .large: 4.4]

        static func element(_ k: Int) -> Element {
            Element(delay: 0.12 + 0.23 * Double(k), duration: 1.65 + 0.17 * Double(k),
                    curve: bezier(0.12, 0.55, 0.3, 1))
        }

        static func scaleFrames(_ size: ValenceSizeGroup, _ k: Int) -> Frames {
            [(0, 1.15), (0.07, 1.45), (1, (reach[size] ?? 5.6) - 0.5 * Double(k))]
        }

        static let alphaFrames: Frames = [(0, 0), (0.07, 0.85), (1, 0)]
    }

    /// Reduced motion: the rings darken and a still sheen at 0.9 holds this long.
    static let stillTouch = 0.9
    static let stillSheen = 0.9
    /// A pressed stone opens its pebble once the dip has landed (D8).
    static let openDelay = 0.36
    /// A contents row scrolls to its stone, then presses it after this, without opening.
    static let goPressDelay = 0.62

    /// When the last element ends: the third wave, or the still hold.
    static func total(still: Bool) -> Double {
        still ? stillTouch : Wave.element(Wave.count - 1).end
    }

    static func frame(at elapsed: Double, still: Bool, size: ValenceSizeGroup) -> Frame {
        var frame = Frame()
        if still {
            let holding = elapsed >= 0 && elapsed < stillTouch
            frame.stillSheen = holding ? stillSheen : 0
            frame.stillRings = holding
            return frame
        }
        frame.dip = piecewise(Dip.element.progress(at: elapsed), Dip.frames)
        frame.rings = piecewise(Rings.element.progress(at: elapsed), Rings.frames)
        frame.sweep = Sweep.element.isRunning(at: elapsed) ? Sweep.element.progress(at: elapsed) : nil
        let spark = Spark.element.progress(at: elapsed)
        frame.sparkScale = piecewise(spark, Spark.scaleFrames)
        frame.sparkRotation = piecewise(spark, Spark.rotationFrames)
        frame.sparkAlpha = piecewise(spark, Spark.alphaFrames)
        frame.waves = (0..<Wave.count).map { k in
            let progress = Wave.element(k).progress(at: elapsed)
            return WaveSample(scale: piecewise(progress, Wave.scaleFrames(size, k)),
                              alpha: piecewise(progress, Wave.alphaFrames))
        }
        return frame
    }
}
