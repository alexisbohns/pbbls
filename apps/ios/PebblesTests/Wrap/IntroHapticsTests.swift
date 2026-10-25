import Foundation
import Testing
@testable import Pebbles

/// #1241 D5: what the intro asks of the Taptic Engine.
@Suite("Intro haptics")
struct IntroHapticsTests {
    @Test("each ticking beat plays at its time; one closer than 25 ms to the last kept is dropped")
    func floor() {
        let beats = [
            IntroBeat(key: .row(1), at: 0.05, duration: 0.28, tick: .facet),
            IntroBeat(key: .header, at: 0, duration: 0.28, tick: .light),
            IntroBeat(key: .lead, at: 0.01, duration: 0.28),
            IntroBeat(key: .row(0), at: 0.02, duration: 0.28, tick: .light)
        ]
        #expect(IntroHaptics.events(for: beats) == [IntroHapticEvent(at: 0, tick: .light),
                                                    IntroHapticEvent(at: 0.05, tick: .facet)])
    }

    @Test("a 31-day garland keeps its name, every day and its leaf")
    func garland() {
        let beats = IntroScore.cover(days: [], daysInMonth: 31, numbered: [], souls: 0, lines: [])
        #expect(IntroHaptics.events(for: beats).count == 33)
    }
}
