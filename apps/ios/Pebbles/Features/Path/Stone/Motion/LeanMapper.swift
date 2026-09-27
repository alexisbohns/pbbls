import Foundation

/// Turns relative device attitude into a lean. Pure: no CoreMotion here, so
/// it is tested with numbers. Angles are radians, already measured against
/// the reference pose (`StoneMotionSource` does that with `BodyTilt`).
///
/// The follow is exponential (τ 150 ms), so a new target never restarts a
/// clock. There is no release spring: the hand never lets go. Ported from the
/// Femfolk native card POC, with its device-run numbers.
struct LeanMapper {
    /// ±12° of wrist is the full lean: 25° felt like nothing was happening.
    static let maxAngle = 12.0 * .pi / 180
    static let maxLean = 1.0
    static let tau = 0.150
    /// A paused app hands back a huge dt on resume; clamped so the light does
    /// not teleport.
    static let maxFrame = 0.064

    private(set) var lean = Lean.zero

    /// Where the lean is asked to go, before the follow.
    func target(pitch: Double, roll: Double) -> Lean {
        Lean(x: Self.map(roll), y: Self.map(pitch))
    }

    /// One frame of follow toward the target. Returns and stores the new lean.
    mutating func step(pitch: Double, roll: Double, dt: TimeInterval) -> Lean {
        let there = target(pitch: pitch, roll: roll)
        let frame = min(max(dt, 0), Self.maxFrame)
        let k = 1 - exp(-frame / Self.tau)
        lean = Lean(x: lean.x + (there.x - lean.x) * k,
                    y: lean.y + (there.y - lean.y) * k)
        return lean
    }

    private static func map(_ angle: Double) -> Double {
        max(-maxLean, min(maxLean, angle / maxAngle * maxLean))
    }
}
