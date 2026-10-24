import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// The dial's arithmetic (#1226, D12). Android: `HoursLayout`.
@Suite("HoursLayout")
struct HoursLayoutTests {
    @Test("the wait runs clockwise round the day")
    func forward() {
        #expect(HoursLayout.forward(22.5, 1.0) == 2.5)
        #expect(HoursLayout.forward(8, 9.5) == 1.5)
        #expect(HoursLayout.forward(9, 9) == 0)
    }

    @Test("marks that would touch take the next lane out, earliest first; apart, they share the first")
    func lanes() {
        let lanes = HoursLayout.lanes([20.0, 20.1, 20.05, 8.0], sizes: [10, 10, 10, 10]) { _ in 10 }
        #expect(lanes == [0, 2, 1, 0])
    }

    @Test("Android's vector: close marks step out, across midnight too")
    func androidVector() {
        let lanes = HoursLayout.lanes([10.0, 10.2, 15.0, 23.9, 0.1], sizes: Array(repeating: 10, count: 5)) { _ in 20 }
        #expect(lanes == [0, 1, 0, 1, 0])
    }

    @Test("past the dial's lanes, marks share the last one: October's 24 one-sitting dots in four lanes")
    @MainActor
    func limit() throws {
        let wrap = try MonthPayloadFixture.october()
        let collected = try #require(wrap.hours).items.map(\.collected)
        let free = HoursLayout.lanes(collected, sizes: Array(repeating: 4.8, count: collected.count)) { _ in 18 }
        #expect(free.max() == collected.count - 1)
        let capped = HoursLayout.lanes(collected, sizes: Array(repeating: 4.8, count: collected.count),
                                       limit: 4) { _ in 18 }
        #expect(capped.max() == 3)
        #expect(capped.filter { $0 == 3 }.count == collected.count - 3)
    }

    @Test("lanes wrap round midnight")
    func midnight() {
        let lanes = HoursLayout.lanes([23.95, 0.05], sizes: [10, 10]) { _ in 10 }
        #expect(lanes == [1, 0])
    }
}
