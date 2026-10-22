import CoreGraphics
import Testing
@testable import Pebbles

/// #1130 D12 and decision 2: of the realised marks (seams and pebble rows, each
/// with its day and its top in the page's space), the day of the last one at or
/// above 35 % of the viewport (Android ReadingDayTest).
@Suite("ReadingDay")
struct ReadingDayTests {
    typealias Mark = ReadingDay.Mark

    @Test("the last mark at or above the reading line wins")
    func line() {
        let marks = [Mark(day: 0, minY: -500), Mark(day: 0, minY: -100), Mark(day: 1, minY: 200),
                     Mark(day: 1, minY: 400)]
        #expect(ReadingDay.index(marks, viewportHeight: 1000) == 1)
        #expect(ReadingDay.index(marks, viewportHeight: 400) == 0)
    }

    @Test("a mark exactly on the line counts")
    func onTheLine() {
        #expect(ReadingDay.index([Mark(day: 3, minY: 350)], viewportHeight: 1000) == 3)
    }

    @Test("nothing has reached the line yet: nil, and the page reads its first day")
    func none() {
        #expect(ReadingDay.index([Mark(day: 0, minY: 600)], viewportHeight: 1000) == nil)
        #expect(ReadingDay.index([], viewportHeight: 1000) == nil)
    }

    @Test("the marks' order of arrival does not matter")
    func unordered() {
        let marks = [Mark(day: 2, minY: 300), Mark(day: 1, minY: -50), Mark(day: 4, minY: 800)]
        #expect(ReadingDay.index(marks, viewportHeight: 1000) == 2)
    }

    @Test("a long day whose seam has scrolled away is still read through its pebbles")
    func longDay() {
        #expect(ReadingDay.index([Mark(day: 3, minY: -900), Mark(day: 3, minY: 120)], viewportHeight: 800) == 3)
    }

    /// Android's `ReadingDayTest` verbatim: the line at 350 of a 1000 viewport.
    /// Android walks the list in order and stops at the first mark past the
    /// line; iOS takes the last mark at or above it. The same on ordered input.
    @Test("the android vectors", arguments: [
        ([(Int, CGFloat)](), nil),
        ([(0, 400), (1, 700)], nil),
        ([(0, -200), (1, 100), (2, 600)], 1),
        ([(1, -500), (2, -40)], 2),
        ([(2, 0), (3, 350), (4, 351)], 3),
        ([(1, -30), (1, 200), (2, 500)], 1)
    ] as [([(Int, CGFloat)], Int?)])
    func androidVectors(marks: [(Int, CGFloat)], expected: Int?) {
        let marks = marks.map { Mark(day: $0.0, minY: $0.1) }
        #expect(ReadingDay.index(marks, viewportHeight: 1000) == expected)
    }
}
