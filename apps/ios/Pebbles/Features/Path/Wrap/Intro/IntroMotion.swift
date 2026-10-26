import CoreGraphics
import Foundation

/// How a key lands in the month's intro (#1241 §Choreography), from its
/// linear progress 0…1. The durations are the beats'; the views apply the
/// curves. Starting values, tuned on the device.
enum IntroMotion {
    /// A SwiftUI part: fades in and rises `riseOffset`.
    static let rise: TimeInterval = 0.28
    /// A mark in a Canvas: grows from 0.4, overshooting once.
    static let pop: TimeInterval = 0.22
    /// A gem facet: its colour over the blank paper.
    static let light: TimeInterval = 0.18
    static let riseOffset: CGFloat = 6
    /// A pop is opaque after its first 120 ms.
    private static let popFade = 0.12 / pop
    /// Ease-out-back's constant: the pop's scale peaks near 1.08.
    private static let back = 2.0

    static func clamp(_ t: Double) -> Double {
        min(max(t, 0), 1)
    }

    /// Cubic ease-out.
    static func easeOut(_ t: Double) -> Double {
        let u = 1 - clamp(t)
        return 1 - u * u * u
    }

    /// Sine ease-in-out: the slow-fast-slow of the garland, the rings and the
    /// sweeps. Gentler than the cubic, whose middle would put a 31-day
    /// garland's ticks 17 ms apart, under the haptics' floor.
    static func easeInOut(_ t: Double) -> Double {
        (1 - cos(Double.pi * clamp(t))) / 2
    }

    /// When, 0…1, `easeInOut` reaches `y`.
    static func easeInOutInverse(_ y: Double) -> Double {
        acos(1 - 2 * clamp(y)) / Double.pi
    }

    static func popOpacity(_ t: Double) -> Double {
        clamp(t / popFade)
    }

    /// 0.4 to 1, overshooting once (ease-out-back).
    static func popScale(_ t: Double) -> Double {
        let u = clamp(t) - 1
        return 0.4 + 0.6 * (1 + (back + 1) * u * u * u + back * u * u)
    }

    /// When, 0…1 of its beat, a stamp meets the paper (#1241 D10): its tick's moment.
    static let stampImpact = 0.6
    /// A stamp's press past its size at impact.
    private static let stampSquash = 0.04

    /// A stamp's scale: from 1.25 down to its impact, a slight squash there, back to 1.
    static func stampScale(_ t: Double) -> Double {
        let t = clamp(t)
        if t < stampImpact {
            let u = t / stampImpact
            return 1.25 - (0.25 + stampSquash) * u * u
        }
        return 1 - stampSquash * (1 - easeOut((t - stampImpact) / (1 - stampImpact)))
    }

    /// A stamp's opacity: there by its impact.
    static func stampOpacity(_ t: Double) -> Double {
        clamp(t / stampImpact)
    }
}

/// The slow-fast-slow spacing of a run of beats (#1241): beat `i` of `n`
/// lands when the run's eased progress reaches `i / (n − 1)`.
enum IntroCurve {
    static func times(count: Int, duration: TimeInterval) -> [TimeInterval] {
        guard count > 1 else { return count == 1 ? [0] : [] }
        return (0..<count).map { duration * IntroMotion.easeInOutInverse(Double($0) / Double(count - 1)) }
    }
}
