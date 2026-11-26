import CoreGraphics
import Foundation
import SwiftUI

/// The stone ceremony's haptic score (#1691): the events each act plays, as pure arithmetic
/// so the tests can assert them without hardware. `hapticsOn` is the user's preference; a
/// quiet valence plays nothing either way.
enum StoneCeremonyScore {
    /// Two events closer than this blur into one buzz.
    private static let floor = IntroHaptics.floor

    private static func event(_ at: Double, _ intensity: Float, _ sharpness: Float) -> IntroHapticEvent {
        IntroHapticEvent(at: at, tick: IntroTick(intensity: intensity, sharpness: sharpness))
    }

    // MARK: Act 1

    /// Act 1, from the first frame: the thud, its echo, the carve's ticks and the closing tap.
    static func act1(_ t: StoneCeremonyTimeline, carveLength: CGFloat, hapticsOn: Bool) -> [IntroHapticEvent] {
        guard hapticsOn, !t.quiet else { return [] }
        guard t.mode == .full else { return [event(t.contact, 0.50, 0.40)] }

        let sizeIndex = index(of: t.size)
        let thudIntensity = [Float(0.55), 0.75, 0.95][sizeIndex]
        let thudSharpness = polarityValue(t.polarity, lowlight: Float(0.25), neutral: 0.45, highlight: 0.80)
        var events = [
            event(t.contact, thudIntensity, thudSharpness),
            event(t.contact + 0.070, thudIntensity * 0.35, thudSharpness - 0.10)
        ]
        guard t.carveDuration > 0 else { return events }

        let closing = event(
            t.closure,
            polarityValue(t.polarity, lowlight: 0.60, neutral: 0.70, highlight: 0.80),
            polarityValue(t.polarity, lowlight: 0.35, neutral: 0.60, highlight: 0.80))
        // The thud, the echo and the closing tap stand; a tick too close to one of them, or to the
        // tick before it, is dropped.
        var last = events[1].at
        for tick in carveTicks(start: t.carveStart, duration: t.carveDuration, length: carveLength) {
            guard tick.at - last >= floor, closing.at - tick.at >= floor else { continue }
            events.append(tick)
            last = tick.at
        }
        events.append(closing)
        return events
    }

    /// The carve's ticks, before the floor: evenly spread along the stroke, so eased in time.
    static func carveTicks(start: Double, duration: Double, length: CGFloat) -> [IntroHapticEvent] {
        guard duration > 0 else { return [] }
        let length = Double(length)
        let count = max(8, min(40, Int((length / max(12, length / 36)).rounded())))
        return (0..<count).map { j in
            let p = (Double(j) + 0.5) / Double(count)
            return event(start + timeFraction(forProgress: p) * duration,
                         Float(0.18 + (0.30 - 0.18) * p), 0.85)
        }
    }

    // MARK: Act 2

    /// Act 2, from Continue: the tap on the button, and the stone landing on its day.
    static func act2(_ t: StoneCeremonyTimeline, hapticsOn: Bool) -> [IntroHapticEvent] {
        guard hapticsOn, !t.quiet else { return [] }
        let tap = event(0, 0.30, 0.45)
        guard t.mode == .full else { return [tap] }
        return [tap, event(t.landing, [Float(0.45), 0.55, 0.65][index(of: t.size)], 0.50)]
    }

    // MARK: Helpers

    private static func index(of size: ValenceSizeGroup) -> Int {
        switch size {
        case .small: return 0
        case .medium: return 1
        case .large: return 2
        }
    }

    private static func polarityValue(_ polarity: ValencePolarity, lowlight: Float, neutral: Float,
                                      highlight: Float) -> Float {
        switch polarity {
        case .lowlight: return lowlight
        case .neutral: return neutral
        case .highlight: return highlight
        }
    }

    /// The carve eases in and out, cubic-bezier (.37, 0, .63, 1), as the frames do.
    private static let inOut = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: 0.37, y: 0), endControlPoint: UnitPoint(x: 0.63, y: 1))

    /// The time fraction `x` at which the eased progress reaches `p`: the curve is monotonic, so bisect.
    private static func timeFraction(forProgress p: Double) -> Double {
        var low = 0.0
        var high = 1.0
        for _ in 0..<20 {
            let mid = (low + high) / 2
            if inOut.value(at: mid) < p { low = mid } else { high = mid }
        }
        return (low + high) / 2
    }
}
