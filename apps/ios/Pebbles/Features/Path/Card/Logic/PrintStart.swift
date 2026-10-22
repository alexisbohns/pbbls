import CoreGraphics
import Foundation

/// How a completion's print starts (#1146 §The print, Android's
/// `CardCompletionState.play`): the print happens in the place, so it plays
/// only with the whole place on screen. A place on screen is printed into
/// where it stands; one cut by the view's edge or off it is scrolled into view
/// first; one taller than the view, or one not measured yet, crossfades, as
/// Reduce Motion does.
enum PrintStart: Equatable {
    case print, scrollThenPrint, crossfade

    static func decide(place: CGRect?, viewport: CGRect?, reduceMotion: Bool) -> PrintStart {
        guard !reduceMotion, let place, let viewport else { return .crossfade }
        if place.height > viewport.height + 1 { return .crossfade }
        let whole = place.minY >= viewport.minY - 1 && place.maxY <= viewport.maxY + 1
        return whole ? .print : .scrollThenPrint
    }

    /// D7: a relaunch that finds `printingFor` set for this moment lands the
    /// card at rest, verso up, rather than replaying the print.
    static func cutsShort(printingFor: UUID?, momentId: UUID, hasPebble: Bool) -> Bool {
        hasPebble && printingFor == momentId
    }
}
