import UIKit

/// The four tap flavors the record flow uses.
enum TapHaptic {
    /// Picking a tile, toggling a soul — the most common tap in the flow.
    case selection
    /// Step changed, forward or back.
    case advance
    /// The pebble published.
    case success
    /// A blocked advance or a failed publish.
    case warning
}

/// Thin wrapper over the UIKit feedback generators, used for every tap in the
/// record flow.
///
/// Deliberately not `HapticsService`: that type owns a `CHHapticEngine` and
/// bespoke waveform-derived patterns for karma and the glyph slider. UI taps
/// want the system generators instead — they respect the user's haptic
/// settings, need no warm engine on every step, and carry the texture users
/// already know from the rest of iOS.
///
/// Generators are cached rather than constructed per call: `prepare()` warms
/// the Taptic Engine, and a generator created and released for every tap never
/// gets the benefit.
@MainActor
enum TapHaptics {

    private static let selectionGenerator = UISelectionFeedbackGenerator()
    private static let impactGenerator = UIImpactFeedbackGenerator(style: .light)
    private static let notificationGenerator = UINotificationFeedbackGenerator()

    #if DEBUG
    /// Test-only tally. Lets a suite assert that an interaction produced
    /// feedback without reaching into UIKit or a mock protocol.
    private(set) static var playCount: Int = 0
    private(set) static var lastPlayed: TapHaptic?

    static func resetForTesting() {
        playCount = 0
        lastPlayed = nil
    }
    #endif

    /// Warms the Taptic Engine. Called when the flow appears so the first tap
    /// is as sharp as the tenth.
    static func prepare() {
        selectionGenerator.prepare()
        impactGenerator.prepare()
        notificationGenerator.prepare()
    }

    static func play(_ haptic: TapHaptic) {
        #if DEBUG
        playCount += 1
        lastPlayed = haptic
        #endif

        switch haptic {
        case .selection:
            selectionGenerator.selectionChanged()
            selectionGenerator.prepare()
        case .advance:
            impactGenerator.impactOccurred()
            impactGenerator.prepare()
        case .success:
            notificationGenerator.notificationOccurred(.success)
        case .warning:
            notificationGenerator.notificationOccurred(.warning)
        }
    }
}
