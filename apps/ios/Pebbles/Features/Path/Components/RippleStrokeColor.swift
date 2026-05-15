import SwiftUI

/// Logical color slot for a Ripple stroke. The view maps each tone
/// to a theme-aware `Color` asset.
enum RippleStrokeTone: Equatable {
    case `default`   // outside the user's current level
    case active      // within level, user created a pebble today
    case inactive    // within level, user has NOT created a pebble today
}

/// Pure mapping from `(strokeId, level, activeToday)` to a `RippleStrokeTone`.
/// Encodes the truth table from issue #442 verbatim:
///   - strokeId > level                      → .default
///   - strokeId <= level &&  activeToday     → .active
///   - strokeId <= level && !activeToday     → .inactive
func rippleStrokeTone(strokeId: Int, level: Int, activeToday: Bool) -> RippleStrokeTone {
    if strokeId > level { return .default }
    return activeToday ? .active : .inactive
}

extension RippleStrokeTone {
    /// Resolved theme-aware color for this tone.
    var color: Color {
        switch self {
        case .default:  return .rippleDefault
        case .active:   return .rippleActive
        case .inactive: return .rippleInactive
        }
    }
}
