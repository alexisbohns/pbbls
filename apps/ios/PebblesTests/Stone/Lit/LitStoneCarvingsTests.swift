import CoreGraphics
import Foundation
import SwiftUI
import Testing
@testable import Pebbles

/// The seeds split the way the web (`stone-carvings.test.ts`) and Android
/// (`StoneCarvingsTest`) split them. The expected `d` strings are the web
/// table's, in `LitStoneCarvingsWebTable.swift`.
@Suite("Lit stone carvings")
struct LitStoneCarvingsTests {

    private let veinCounts: [ValenceSizeGroup: [ValencePolarity: Int]] = [
        .small: [.lowlight: 0, .neutral: 0, .highlight: 0],
        .medium: [.lowlight: 1, .neutral: 1, .highlight: 2],
        .large: [.lowlight: 1, .neutral: 2, .highlight: 6],
    ]

    private func seed(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) throws -> StoneShapeParser.Parsed {
        try #require(StoneCarvingArt.parsedSeed(size: size, polarity: polarity), "\(size) \(polarity) seed")
    }

    @Test("the table covers the nine seeds, once each")
    func tableCoversNineSeeds() {
        #expect(WebCarvings.all.count == 9)
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                #expect(WebCarvings.all.filter { $0.size == size && $0.polarity == polarity }.count == 1)
            }
        }
    }

    @Test("every seed parses on its engine canvas")
    func everySeedParsesOnItsCanvas() throws {
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let parsed = try seed(size, polarity)
                let canvas = LitStoneGeometry.seedCanvas(size)
                #expect(parsed.viewBox.width == canvas.width, "\(size) \(polarity)")
                #expect(parsed.viewBox.height == canvas.height, "\(size) \(polarity)")
            }
        }
    }

    @Test("veins count 0-0-0, 1-1-2 and 1-2-6, lowlight to highlight")
    func veinCountsPerSeed() throws {
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let carving = LitStoneCarvings.carving(size, polarity)
                let expected = try #require(veinCounts[size]?[polarity])
                #expect(carving.veins.count == expected, "\(size) \(polarity)")
                for vein in carving.veins {
                    #expect(!vein.path.isEmpty, "\(size) \(polarity) vein is not empty")
                }
            }
        }
    }

    @Test("every seed but large highlight carries an even-odd fossil")
    func fossilPerSeed() {
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let fossil = LitStoneCarvings.carving(size, polarity).fossil
                if size == .large && polarity == .highlight {
                    #expect(fossil == nil, "\(size) \(polarity)")
                } else if let fossil {
                    #expect(fossil.evenOdd, "\(size) \(polarity) fill-rule")
                    #expect(!fossil.path.isEmpty, "\(size) \(polarity) fossil is not empty")
                } else {
                    Issue.record("\(size) \(polarity) has no fossil")
                }
            }
        }
    }

    @Test("large neutral's outline is the third stroked path, by extent not by position")
    func largeNeutralOutlineByExtent() throws {
        let parsed = try seed(.large, .neutral)
        let row = try #require(WebCarvings.all.first { $0.size == .large && $0.polarity == .neutral })
        // The two veins are the first two stroked paths, in document order,
        // and the outline (the third) is neither of them.
        #expect(parsed.veins.map(\.d) == row.veins.map(\.d))
        #expect(!parsed.veins.contains { $0.d == parsed.outline.d })
        #expect(parsed.fossil != nil)
    }

    @Test("each seed's veins and fossil are the web table's, string for string")
    func seedsMatchTheWebTable() throws {
        for row in WebCarvings.all {
            let parsed = try seed(row.size, row.polarity)
            let label = "\(row.size) \(row.polarity)"
            #expect(parsed.veins.map(\.d) == row.veins.map(\.d), "\(label) vein d")
            #expect(parsed.veins.map(\.width) == row.veins.map(\.width), "\(label) vein width")
            #expect(parsed.fossil?.d == row.fossil?.d, "\(label) fossil d")
            #expect(parsed.fossil?.usesEvenOddFill == row.fossil?.evenOdd, "\(label) fossil fill rule")
        }
    }

    @Test("the carving paths are the web's d strings, parsed")
    func carvingPathsAreTheWebStringsParsed() throws {
        for row in WebCarvings.all {
            let carving = LitStoneCarvings.carving(row.size, row.polarity)
            let label = "\(row.size) \(row.polarity)"
            #expect(carving.veins.count == row.veins.count, "\(label) veins")
            for (vein, expected) in zip(carving.veins, row.veins) {
                let reference = try #require(SVGPathParser.parse(expected.d), "\(label) web vein parses")
                #expect(!vein.path.isEmpty, "\(label) vein is not empty")
                #expect(vein.path.cgPath == reference, "\(label) vein path")
                #expect(vein.width == expected.width, "\(label) vein width")
            }
            #expect((carving.fossil == nil) == (row.fossil == nil), "\(label) fossil presence")
            if let fossil = carving.fossil, let expected = row.fossil {
                let reference = try #require(SVGPathParser.parse(expected.d), "\(label) web fossil parses")
                #expect(!fossil.path.isEmpty, "\(label) fossil is not empty")
                #expect(fossil.path.cgPath == reference, "\(label) fossil path")
                #expect(fossil.evenOdd == expected.evenOdd, "\(label) fossil fill rule")
            }
        }
    }

    @Test("fill-rule decides the fossil's even-odd flag; an outline-only seed has none")
    func syntheticSeedsSplit() throws {
        let svg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
          <path d="M 0 0 L 100 0 L 100 100 L 0 100 Z" fill="none" stroke="black" stroke-width="6"/>
          <path d="M 10 10 L 30 10 L 30 30 Z" fill="none" stroke="black" stroke-width="6"/>
          <path fill-rule="nonzero" d="M 40 40 L 60 40 L 60 60 Z" fill="black"/>
        </svg>
        """
        let parsed = try #require(StoneShapeParser.parse(svg))
        #expect(parsed.veins.map(\.d) == ["M 10 10 L 30 10 L 30 30 Z"])
        #expect(parsed.fossil == StoneShapeParser.Fill(d: "M 40 40 L 60 40 L 60 60 Z", usesEvenOddFill: false))

        let outlineOnly = try #require(StoneShapeParser.parse(
            #"<svg viewBox="0 0 100 100"><path d="M 0 0 L 100 0 L 100 100 L 0 100 Z" stroke="black" stroke-width="6"/></svg>"#
        ))
        #expect(outlineOnly.veins.isEmpty)
        #expect(outlineOnly.fossil == nil)
    }

    @Test("no viewBox or no stroked path parses to nil")
    func unparseableSeeds() {
        #expect(StoneShapeParser.parse("<svg/>") == nil)
        #expect(StoneShapeParser.parse(#"<svg viewBox="0 0 100 100"><path d="M 0 0 L 1 1" fill="black"/></svg>"#) == nil)
        #expect(StoneShapeParser.parse(#"<svg><path d="M 0 0 L 1 1" stroke="black"/></svg>"#) == nil)
        #expect(StoneShapeParser.parse("not xml") == nil)
    }

    @Test("the memo hands back the same carving on every call")
    func memoIsStable() {
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                let first = LitStoneCarvings.carving(size, polarity)
                let second = LitStoneCarvings.carving(size, polarity)
                #expect(first.veins.map(\.path) == second.veins.map(\.path))
                #expect(first.fossil?.path == second.fossil?.path)
            }
        }
    }
}
