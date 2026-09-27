import Testing
import CoreGraphics
import Foundation
@testable import Pebbles

@Suite("StoneShapeParser")
struct StoneShapeParserTests {

    /// Real counts from `docs/seeds/shape-seeds/*.svg`, walked stroked-path
    /// by stroked-path (outline first, everything after it a vein). They vary
    /// by polarity within a size, not just by size — the plan's 0/2/3-by-size
    /// table didn't hold, so this is keyed by the full valence instead.
    private static let veinsByValence: [Valence: Int] = [
        .lowlightSmall: 0, .neutralSmall: 0, .highlightSmall: 0,
        .lowlightMedium: 1, .neutralMedium: 1, .highlightMedium: 2,
        .lowlightLarge: 1, .neutralLarge: 2, .highlightLarge: 6,
    ]

    /// `large-highlight.svg` carries seven stroked paths (an outline plus six
    /// veins) and no filled path at all, so its fossil is legitimately nil.
    /// Every other seed has exactly one filled path.
    private static let valencesWithoutFossil: Set<Valence> = [.highlightLarge]

    @Test("splits every engine seed into one outline, N veins and a fossil where the seed has one", arguments: Valence.allCases)
    func splitsSeed(valence: Valence) throws {
        let name = "stone-lab-shape-\(valence.sizeGroup.rawValue)-\(valence.polarity.rawValue)"
        let url = try #require(Bundle.main.url(forResource: name, withExtension: "svg"))
        let svg = try String(contentsOf: url, encoding: .utf8)
        let parsed = try #require(StoneShapeParser.parse(svg))
        #expect(parsed.veins.count == Self.veinsByValence[valence])
        #expect(parsed.outline.width == 6)
        if Self.valencesWithoutFossil.contains(valence) {
            #expect(parsed.fossil == nil)
        } else {
            #expect(parsed.fossil != nil)
        }
    }

    @Test("the outline is the stroked path with the largest extent, veins keep document order")
    func outlineIsFirst() throws {
        let svg = """
        <svg viewBox="0 0 10 10"><path d="M2 2L3 3" stroke="black" stroke-width="6"/>\
        <path d="M0 0L9 9" stroke="black" stroke-width="6"/>\
        <path d="M4 4L5 5" stroke="black" stroke-width="6"/>\
        <path fill-rule="evenodd" d="M5 5L6 6Z" fill="black"/></svg>
        """
        let parsed = try #require(StoneShapeParser.parse(svg))
        #expect(parsed.outline.d == "M0 0L9 9")
        #expect(parsed.veins.map(\.d) == ["M2 2L3 3", "M4 4L5 5"])
        #expect(parsed.fossil?.d == "M5 5L6 6Z")
        #expect(parsed.fossil?.usesEvenOddFill == true)
        #expect(parsed.viewBox == CGRect(x: 0, y: 0, width: 10, height: 10))
    }

    @Test("large-neutral's outline is its third stroked path and spans the canvas")
    func largeNeutralOutline() throws {
        let url = try #require(Bundle.main.url(forResource: "stone-lab-shape-large-neutral", withExtension: "svg"))
        let parsed = try #require(StoneShapeParser.parse(try String(contentsOf: url, encoding: .utf8)))
        #expect(parsed.outline.d.hasSuffix("Z"))
        #expect(parsed.veins.count == 2)
        #expect(parsed.veins.allSatisfy { !$0.d.hasSuffix("Z") })
    }

    @Test("returns nil without a viewBox or without a stroked path")
    func rejectsMalformed() {
        #expect(StoneShapeParser.parse("<svg><path d=\"M0 0\" stroke=\"black\"/></svg>") == nil)
        #expect(StoneShapeParser.parse("<svg viewBox=\"0 0 1 1\"><path d=\"M0 0\" fill=\"black\"/></svg>") == nil)
    }
}
