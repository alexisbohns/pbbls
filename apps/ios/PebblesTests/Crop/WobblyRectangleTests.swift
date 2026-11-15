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

    @Test("rounded corners keep it deterministic, inside the rect and different from the square one")
    func rounded() {
        let rect = CGRect(x: 0, y: 0, width: 34, height: 46)
        let a = WobblyRectangle(seed: "3:4", cornerRadius: 14).path(in: rect)
        let b = WobblyRectangle(seed: "3:4", cornerRadius: 14).path(in: rect)
        let square = WobblyRectangle(seed: "3:4").path(in: rect)
        #expect(a.description == b.description)
        #expect(a.description != square.description)
        // The corners are cut, so the outline's box stays within the square one's.
        #expect(rect.insetBy(dx: -2, dy: -2).contains(a.boundingRect))
        // The point at the top-left corner of the rect is outside a 14 pt rounding.
        #expect(!a.contains(CGPoint(x: 1, y: 1)))
        #expect(a.contains(CGPoint(x: 17, y: 23)))
    }

    @Test("a radius past half the short side is clamped")
    func clamped() {
        let rect = CGRect(x: 0, y: 0, width: 34, height: 46)
        let a = WobblyRectangle(seed: "3:4", cornerRadius: 400).path(in: rect)
        let b = WobblyRectangle(seed: "3:4", cornerRadius: 17).path(in: rect)
        #expect(a.description == b.description)
    }

    @Test("the generator repeats for a seed")
    func generator() {
        var a = SeededGenerator(seed: "tick-12"), b = SeededGenerator(seed: "tick-12")
        #expect(a.next() == b.next())
        #expect(a.unit() == b.unit())
    }
}
