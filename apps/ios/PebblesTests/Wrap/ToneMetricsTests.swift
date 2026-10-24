import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The tone figure's scale (#1226, D11). Android: `ToneFigure`'s constants.
@Suite("ToneMetrics")
@MainActor
struct ToneMetricsTests {
    @Test("a quiet month keeps the minimum scale at eight points a unit")
    func quiet() {
        let metrics = ToneMetrics(days: [ToneDay(up: 1, even: 0, down: 0)])
        #expect(metrics.maxUp == 3 && metrics.maxDown == 3)
        #expect(metrics.unit == CGFloat(8))
        #expect(metrics.height == CGFloat(14 + 24 + 24 + 30))
        #expect(metrics.axisY == CGFloat(38))
    }

    @Test("a heavy month shrinks the unit so no bar passes 64 points")
    func heavy() {
        let metrics = ToneMetrics(days: [ToneDay(up: 16, even: 0, down: 2)])
        #expect(metrics.unit == CGFloat(4))
        #expect(metrics.height == CGFloat(14 + 64 + 12 + 30))
        // The domain maps the whole frame at `unit` points a unit.
        #expect(abs((metrics.domain.upperBound - metrics.domain.lowerBound) * Double(metrics.unit)
            - Double(metrics.height)) < 1e-9)
    }

    @Test("October's tone: 31 days, its verdict, its first and last")
    func october() throws {
        let wrap = try MonthPayloadFixture.october()
        let tone = try #require(wrap.tone)
        #expect(tone.days.count == 31)
        #expect(tone.days[0] == ToneDay(up: 0, even: 0, down: 3))
        #expect(WrapCopy.toneLine(tone.verdict, first: tone.first, last: tone.last, locale: Vectors.en)
            == "Bright outweighed heavy two to one; it opened heavy and closed bright.")
    }
}
