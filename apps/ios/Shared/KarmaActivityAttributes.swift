import ActivityKit
import Foundation

/// Shared between the app and the widget extension (compiled into both). The
/// karma amount + reason live in the dynamic `ContentState`; there are no
/// static attributes. No App Group is needed — ActivityKit delivers
/// `ContentState` directly through request/update/end for this local,
/// non-push use case.
struct KarmaActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var amount: Int
        var reasonRawValue: String
    }
}
