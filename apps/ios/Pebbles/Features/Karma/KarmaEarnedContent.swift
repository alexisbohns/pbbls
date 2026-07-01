import Foundation

/// One karma-earned event to celebrate. `Equatable`/`Sendable` so it can flow
/// into an `@Observable` capsule slot and across the ActivityKit boundary.
struct KarmaEarnedContent: Equatable, Sendable {
    let amount: Int
    let reason: KarmaReason
}
