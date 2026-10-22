import CoreGraphics
import Testing
@testable import Pebbles

/// #1146 §Band: the band's names wrap greedily and a line never starts or ends
/// on a knot (Android's `BandFlow`). §Footer: the turn drops under the privacy
/// once it would take more than half the width.
@Suite("Card flows")
struct CardFlowTests {
    @Test("names that fit share one line")
    func oneLine() {
        #expect(BandLines.lines(widths: [50, 60, 70], knotRun: 25, maxWidth: 300) == [[0, 1, 2]])
    }

    @Test("a name that would overrun starts the next line, with no knot before it")
    func wraps() {
        #expect(BandLines.lines(widths: [50, 60, 70], knotRun: 25, maxWidth: 150) == [[0, 1], [2]])
    }

    @Test("a name wider than the band still gets a line of its own")
    func tooWide() {
        #expect(BandLines.lines(widths: [400, 40], knotRun: 25, maxWidth: 300) == [[0], [1]])
    }

    @Test("no collections, no lines")
    func empty() {
        #expect(BandLines.lines(widths: [], knotRun: 25, maxWidth: 300).isEmpty)
    }

    @Test("the line widths add the knots between names only")
    func widths() {
        #expect(BandLines.width(of: [0, 1], widths: [50, 60, 70], knotRun: 25) == 135)
        #expect(BandLines.width(of: [2], widths: [50, 60, 70], knotRun: 25) == 70)
    }

    @Test("the turn sits beside the privacy up to half the width, then drops under it")
    func footer() {
        #expect(!FooterArrangement.stacked(turnWidth: 150, available: 300))
        #expect(FooterArrangement.stacked(turnWidth: 151, available: 300))
    }
}
