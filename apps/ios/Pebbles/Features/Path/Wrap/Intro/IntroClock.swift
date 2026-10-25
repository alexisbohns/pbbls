import CoreGraphics
import Foundation
import Observation

/// What a month page needs to play its intro: the launch's ledger and the haptics (nil: silent).
struct IntroContext {
    let ledger: IntroLedger
    let haptics: IntroHapticPlaying?
}

/// Where a sequence sits in its page's viewport (#1241 §Lifecycle).
enum IntroViewport {
    enum Place: Equatable {
        case entered, between, out
    }

    /// A sequence enters once its top passes this share of the viewport's height.
    static let entryLine: CGFloat = 0.85

    static func place(_ frame: CGRect, viewport: CGFloat) -> Place {
        if frame.maxY <= 0 || frame.minY >= viewport { return .out }
        return frame.minY < viewport * entryLine ? .entered : .between
    }
}

/// Whether the intro plays at all (#1241 §Accessibility): not under Reduce
/// Motion, not while VoiceOver runs, where hidden and moving parts would fight
/// its focus.
enum IntroPolicy {
    static func plays(reduceMotion: Bool, voiceOver: Bool) -> Bool {
        !reduceMotion && !voiceOver
    }
}
