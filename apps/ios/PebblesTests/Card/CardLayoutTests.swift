import CoreGraphics
import Testing
@testable import Pebbles

/// #1146 §CardLayout: the held card's geometry, in points (Android's dp).
@Suite("CardLayout")
struct CardLayoutTests {
    @Test("the card is the page less 16 pt a side, at most 440 pt, never negative",
          arguments: [(393.0, 361.0), (375.0, 343.0), (472.0, 440.0), (1024.0, 440.0), (20.0, 0.0)])
    func width(available: Double, expected: Double) {
        #expect(CardLayout.cardWidth(available: CGFloat(available)) == CGFloat(expected))
    }

    @Test("card units are the width over 70, uncapped")
    func units() {
        #expect(CardLayout.u(cardWidth: 361) == CGFloat(361) / 70)
        #expect(CardLayout.u(cardWidth: 440) > 4.4)
    }

    @Test("a postmark is 72 pt, or the row's share when four do not fit")
    func postmark() {
        #expect(CardLayout.postmarkDiameter(count: 1, rowWidth: 300) == 72)
        #expect(CardLayout.postmarkDiameter(count: 4, rowWidth: 250) == CGFloat(250 - 18) / 4)
        #expect(CardLayout.postmarkDiameter(count: 0, rowWidth: 250) == 72)
    }

    @Test("the band's foot is the mat and its padding, in card units")
    func bandFoot() {
        #expect(abs(CardLayout.bandFoot(u: 5) - 19) < 1e-9)
    }

    @Test("the verso stone is 150 pt wide when large, the others in the Spine's proportions")
    func versoStone() {
        let large = CardLayout.versoStoneSize(.large)
        #expect(large.width == 150)
        let spine = SpineLayout.stoneSize(.large)
        #expect(abs(large.height / large.width - spine.height / spine.width) < 1e-9)
        let medium = CardLayout.versoStoneSize(.medium)
        #expect(abs(medium.width - 150 * SpineLayout.stoneWidth(.medium) / SpineLayout.stoneWidth(.large)) < 1e-9)
    }
}
