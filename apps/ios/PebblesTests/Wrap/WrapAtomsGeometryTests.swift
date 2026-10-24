import CoreGraphics
import Testing
@testable import Pebbles

/// The wraps' ring and day-set geometry (#1226). Android: `RadialLayoutTest`,
/// `DaySetGeometryTest`.
@Suite("Wrap atoms geometry")
struct WrapAtomsGeometryTests {
    @Test("slot 0 sits half the gap past the start, clockwise")
    func ringGap() {
        let ring = RingGeometry(slots: 31, startDegrees: -90, gapSlots: 1.5)
        let step = 360 / 32.5
        #expect(abs(ring.degrees(0) - (-90 + 0.75 * step)) < 1e-9)
        #expect(abs(ring.degrees(30) - (-90 + 30.75 * step)) < 1e-9)
        // The last day stops short of the first by the gap and one step.
        #expect(abs(ring.degrees(30) - ring.degrees(0) + step - (360 - 1.5 * step)) < 1e-9)
    }

    @Test("counter-clockwise turns the other way")
    func counterClockwise() {
        let ring = RingGeometry(slots: 4, startDegrees: 0, clockwise: false)
        #expect(abs(ring.degrees(1) - (-90)) < 1e-9)
    }

    @Test("the dial puts noon on top and six in the morning on the left")
    func dial() {
        let dial = RingGeometry.continuous(24, top: 12)
        #expect(abs(dial.degrees(12) - (-90)) < 1e-9)
        #expect(abs(dial.degrees(6) - (-180)) < 1e-9)
        let left = dial.point(6, center: CGPoint(x: 50, y: 50), radius: 10)
        #expect(abs(left.x - 40) < 1e-9)
        #expect(abs(left.y - 50) < 1e-9)
    }

    @Test("a gem rotation is a fractional slot")
    func gemRotation() {
        let gem = RingGeometry(slots: 7)
        #expect(gem.degrees(3) == gem.degrees(1 + 2))
        #expect(abs(gem.degrees(0) - (-90)) < 1e-9)
    }

    @Test("a week row is the persons' 79 pt")
    func weekRow() {
        #expect(DaySetGeometry.size(length: 7, mode: .row) == CGSize(width: 79, height: 7))
    }

    @Test("a calendar is as tall as its weeks")
    func calendarRows() {
        // October 2026 starts on a Thursday: three leading days, 31 days, five weeks.
        #expect(DaySetGeometry.rows(length: 31, leading: 3) == 5)
        // February 2027 starts on a Monday: four weeks exactly.
        #expect(DaySetGeometry.rows(length: 28, leading: 0) == 4)
        #expect(DaySetGeometry.rows(length: 31, leading: 6) == 6)
        #expect(DaySetGeometry.size(length: 31, mode: .calendar(leading: 3)) == CGSize(width: 79, height: 55))
    }

    @Test("the seal sizes match Android's presets")
    func sealSizes() {
        let groups: [ValenceSizeGroup] = [.small, .medium, .large]
        #expect(groups.map(SealSize.contents.width) == [20, 24, 27])
        #expect(groups.map(SealSize.milestone.width) == [24, 28, 34])
        #expect(groups.map(SealSize.tiny.width) == [8.5, 10, 11.5])
        #expect(groups.map(SealSize.pip.width) == [8, 10.5, 13])
    }

    @Test("a proportion bar splits its room by weight, skipping empty shares, 2 pt apart")
    func proportionBar() {
        let rects = ProportionBar.layout([BarSegment(weight: 3, color: .clear), BarSegment(weight: 0, color: .clear),
                                          BarSegment(weight: 1, color: .clear)], width: 102, height: 6).map(\.0)
        #expect(rects == [CGRect(x: 0, y: 0, width: 75, height: 6), CGRect(x: 77, y: 0, width: 25, height: 6)])
        #expect(ProportionBar.layout([BarSegment(weight: 0, color: .clear)], width: 100, height: 6).isEmpty)
    }
}
