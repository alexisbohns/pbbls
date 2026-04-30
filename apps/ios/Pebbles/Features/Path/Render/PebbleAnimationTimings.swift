import Foundation

/// Phase timings for the pebble stroke-drawing animation. Keyed by
/// `pebbles.render_version` so a server engine bump can shift the curve
/// without breaking older pebbles.
///
/// All values are in seconds. Returning `nil` from `forVersion` instructs
/// the caller to render the static settled state with no animation.
enum PebbleAnimationTimings {

    struct Phase {
        let delay: Double
        let duration: Double
    }

    struct Timings {
        let glyph: Phase
        let shape: Phase
        let fossil: Phase
        let settle: Phase
    }

    /// Returns timings for the given render version, or `nil` if unknown.
    static func forVersion(_ version: String?) -> Timings? {
        guard let version else { return nil }
        switch version {
        case "0.1.0":
            return Timings(
                glyph:  Phase(delay: 0,    duration: 1.2),
                shape:  Phase(delay: 0.8,  duration: 0.8),
                fossil: Phase(delay: 1.0,  duration: 0.6),
                settle: Phase(delay: 1.4,  duration: 0.4)
            )
        default:
            return nil
        }
    }
}
