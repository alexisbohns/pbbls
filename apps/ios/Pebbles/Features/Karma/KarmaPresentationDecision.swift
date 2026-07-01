import Foundation

/// How a karma-earned event should be surfaced.
enum KarmaPresentation: Equatable, Sendable {
    case none
    case capsule
    case liveActivity
}

/// Pure routing decision. The real fork is "does this device have a Dynamic
/// Island?" — NOT "are Live Activities enabled?" — because iPhone 13/SE/14/
/// 14 Plus/16e have Live Activities but render them only on the Lock Screen,
/// which is invisible during a foreground earn.
///
/// A `.liveActivity` result is still best-effort: if `Activity.request` throws
/// at runtime, the caller falls back to `.capsule`.
func karmaPresentationDecision(
    amount: Int,
    hasDynamicIsland: Bool,
    activitiesEnabled: Bool
) -> KarmaPresentation {
    guard amount > 0 else { return .none }
    if hasDynamicIsland && activitiesEnabled { return .liveActivity }
    return .capsule
}
