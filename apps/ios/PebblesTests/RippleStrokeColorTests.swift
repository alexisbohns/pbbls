import Foundation
import Testing
@testable import Pebbles

@Suite("RippleStrokeTone resolver")
struct RippleStrokeColorTests {

    /// Truth table from issue #442. Row format: (level, active, expected-tones-for-strokes-1-through-6).
    /// `.default` means "outside the user's level".
    /// `.active`  means "within level AND user is active today".
    /// `.inactive` means "within level AND user is not active today".
    private struct Row {
        let level: Int
        let activeToday: Bool
        let expected: [RippleStrokeTone] // length 6
    }

    private let rows: [Row] = [
        Row(level: 0, activeToday: true, expected: [.default, .default, .default, .default, .default, .default]),
        Row(level: 0, activeToday: false, expected: [.default, .default, .default, .default, .default, .default]),
        Row(level: 1, activeToday: true, expected: [.active, .default, .default, .default, .default, .default]),
        Row(level: 1, activeToday: false, expected: [.inactive, .default, .default, .default, .default, .default]),
        Row(level: 2, activeToday: true, expected: [.active, .active, .default, .default, .default, .default]),
        Row(level: 2, activeToday: false, expected: [.inactive, .inactive, .default, .default, .default, .default]),
        Row(level: 3, activeToday: true, expected: [.active, .active, .active, .default, .default, .default]),
        Row(level: 3, activeToday: false, expected: [.inactive, .inactive, .inactive, .default, .default, .default]),
        Row(level: 4, activeToday: true, expected: [.active, .active, .active, .active, .default, .default]),
        Row(level: 4, activeToday: false, expected: [.inactive, .inactive, .inactive, .inactive, .default, .default]),
        Row(level: 5, activeToday: true, expected: [.active, .active, .active, .active, .active, .default]),
        Row(level: 5, activeToday: false, expected: [.inactive, .inactive, .inactive, .inactive, .inactive, .default]),
        Row(level: 6, activeToday: true, expected: [.active, .active, .active, .active, .active, .active]),
        Row(level: 6, activeToday: false, expected: [.inactive, .inactive, .inactive, .inactive, .inactive, .inactive])
    ]

    @Test("matches the issue #442 truth table for all 14 cases")
    func matchesTruthTable() {
        for row in rows {
            for strokeIndex in 1...6 {
                let got = rippleStrokeTone(strokeId: strokeIndex,
                                           level: row.level,
                                           activeToday: row.activeToday)
                let want = row.expected[strokeIndex - 1]
                #expect(got == want,
                        "level=\(row.level) active=\(row.activeToday) stroke=\(strokeIndex): got \(got), want \(want)")
            }
        }
    }
}
