import SwiftUI
import Testing
@testable import Pebbles

@Suite("Wobbly rectangle")
struct WobblyRectangleTests {
    @Test("the same seed draws the same path; another seed does not")
    func deterministic() {
        let rect = CGRect(x: 0, y: 0, width: 40, height: 50)
        let a = WobblyRectangle(seed: "4:5").path(in: rect)
        let b = WobblyRectangle(seed: "4:5").path(in: rect)
        let c = WobblyRectangle(seed: "3:4").path(in: rect)
        #expect(a.description == b.description)
        #expect(a.description != c.description)
    }

    @Test("the wobble stays within 5 % of the rect")
    func bounded() {
        let rect = CGRect(x: 0, y: 0, width: 100, height: 100)
        let bounds = WobblyRectangle(seed: "1:1").path(in: rect).boundingRect
        #expect(bounds.insetBy(dx: -5, dy: -5).contains(rect.insetBy(dx: 5, dy: 5)))
        #expect(rect.insetBy(dx: -5, dy: -5).contains(bounds))
    }

    @Test("the generator repeats for a seed")
    func generator() {
        var a = SeededGenerator(seed: "tick-12"), b = SeededGenerator(seed: "tick-12")
        #expect(a.next() == b.next())
        #expect(a.unit() == b.unit())
    }
}
