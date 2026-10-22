import CoreGraphics
import Foundation
import Observation

/// Where each Spine card and entry sits on screen, for stack E's flight
/// (#1130 §Frame registry, the canonical shape stack E consumes as written).
/// `RootView` creates it and injects it; the slots report global rects.
///
/// `frames` is unobserved: E reads it imperatively at open and close, and an
/// observed dictionary written on every scroll frame would invalidate every
/// reader. `hidden` is observed: that slot hides while E flies. A scrolled-out
/// slot forgets its frame, so E falls back to its fade.
@Observable @MainActor
final class CardFrameRegistry {
    @ObservationIgnored private(set) var frames: [UUID: CGRect] = [:]
    var hidden: UUID?

    func report(_ momentId: UUID, frame: CGRect) {
        frames[momentId] = frame
    }

    func forget(_ momentId: UUID) {
        frames[momentId] = nil
    }

    /// Forgets `momentId` only while it still holds `frame`, the one the leaving
    /// slot reported: a newer slot for the same moment keeps its live frame.
    func forget(_ momentId: UUID, ifAt frame: CGRect) {
        if frames[momentId] == frame { frames[momentId] = nil }
    }
}

/// A slot's last reported frame, held by reference so updating it re-renders nothing
/// (the card's, and `SpinePebble`'s for an entry); what `forget(_:ifAt:)` compares against.
final class ReportedFrame {
    var frame: CGRect?
}
