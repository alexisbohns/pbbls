import SwiftUI

/// The print's one timeline (#1146 §The print, a port of Android's
/// `CompletionMotion`): a linear 0 to 1 over 1.6 s, mapped through
/// piecewise-linear phases, so every part reads the same clock and nothing
/// drifts. `print` runs over 0–0.35 (the card appears in the place), `stone`
/// over 0.25–0.6 (the dashed stone flies to the clearing; the lit stone presses
/// in at 0.45), `rise` over 0.6–1 (the page steps aside, the card rises home).
enum CompletionMotion {
    static let duration: TimeInterval = 1.6
    /// Reduced motion, or a place taller than the view: the page crossfades to the card.
    static let crossfade: TimeInterval = 0.25

    static let printEnd = 0.35
    static let stoneStart = 0.25
    static let stoneEnd = 0.6
    /// The verso stone presses in here, on the timeline.
    static let pressAt = 0.45
    /// `pressAt` in the stone phase: the dashed stone lands, the lit stone is in.
    static let landing = (pressAt - stoneStart) / (stoneEnd - stoneStart)
    static let pageScaleEnd = 0.97

    struct Progress: Equatable {
        let print: Double
        let stone: Double
        let rise: Double

        /// Printed and home: what a card that is not printing reads.
        static let rest = Progress(print: 1, stone: 1, rise: 1)
    }

    static func at(_ t: Double) -> Progress {
        Progress(print: print(t), stone: stone(t), rise: rise(t))
    }

    static func print(_ t: Double) -> Double { ramp(t, from: 0, to: printEnd) }
    static func stone(_ t: Double) -> Double { ramp(t, from: stoneStart, to: stoneEnd) }
    static func rise(_ t: Double) -> Double { ramp(t, from: stoneEnd, to: 1) }

    /// Within the stone phase: the flying outline's travel, its alpha, the lit stone's alpha.
    static func flight(_ stone: Double) -> Double { ramp(stone, from: 0, to: landing) }
    static func ghostAlpha(_ stone: Double) -> Double { 1 - ramp(stone, from: landing, to: landing + 0.2) }
    static func litStoneAlpha(_ stone: Double) -> Double { ramp(stone, from: landing - 0.1, to: landing) }

    /// Within the rise: the page fades as the card climbs.
    static func pageAlpha(_ rise: Double) -> Double { 1 - ramp(rise, from: 0, to: 0.6) }

    /// The timeline for `elapsed` seconds of a print (or a crossfade) `duration` long.
    static func timeline(elapsed: TimeInterval, duration: TimeInterval) -> Double {
        min(max(elapsed / duration, 0), 1)
    }

    /// Android's `FastOutSlowInEasing`, cubic-bezier(0.4, 0, 0.2, 1).
    static func ease(_ x: Double) -> Double {
        UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.4, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))
            .value(at: min(max(x, 0), 1))
    }

    static func ramp(_ x: Double, from start: Double, to end: Double) -> Double {
        min(max((x - start) / (end - start), 0), 1)
    }
}

/// The parts that print in turn across `CompletionMotion.print` (#1146 §The
/// print): the card itself first, then the lockup, the postmarks, the band and
/// the footer.
enum PrintPart: CaseIterable {
    case card, lockup, postmarks, band, footer

    private var window: (start: Double, end: Double) {
        switch self {
        case .card: return (0, 0.4)
        case .lockup: return (0.25, 0.55)
        case .postmarks: return (0.45, 0.75)
        case .band: return (0.6, 0.9)
        case .footer: return (0.7, 1)
        }
    }

    func alpha(_ print: Double) -> Double {
        CompletionMotion.ramp(print, from: window.start, to: window.end)
    }
}
