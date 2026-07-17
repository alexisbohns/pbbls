import Testing
import Foundation
import CoreGraphics
@testable import Pebbles

@Suite
struct LogoLoaderArtTests {

    @Test("Loader SVG is bundled")
    func assetBundled() throws {
        let url = Bundle.main.url(forResource: "pbbls-logo-loader", withExtension: "svg")
        #expect(url != nil, "missing pbbls-logo-loader.svg")
    }
}

extension LogoLoaderArtTests {
    @Test("Parsed groups split by id prefix and render mode")
    func parsedGroups() throws {
        let parsed = try #require(LogoLoaderArt.parseGroups())
        // viewBox is the issue's 251-box.
        #expect(parsed.viewBox == CGRect(x: 0, y: 0, width: 251, height: 251))
        // Every group's combined stroke path is non-empty (`isEmptyGroup` is
        // the internal CGPath helper defined in LogoLoaderArt.swift).
        #expect(!parsed.outline.isEmptyGroup)
        #expect(!parsed.creatureStrokes.isEmptyGroup)
        #expect(!parsed.fossilAndVeins.isEmptyGroup)
        // The two eyes are fills.
        #expect(!parsed.eyeFills.isEmptyGroup)
    }
}

extension LogoLoaderArtTests {
    @Test("Builds three distinct boil variants")
    func boilVariants() throws {
        let art = try #require(LogoLoaderArt.build())
        #expect(art.variants.count == 3)
        // swiftlint:disable:next identifier_name
        for v in art.variants {
            #expect(!v.outline.ink.isEmptyGroup)
            #expect(!v.creature.ink.isEmptyGroup)
            #expect(!v.fossilVeins.ink.isEmptyGroup)
            #expect(!v.eyes.isEmptyGroup)
        }
        // Seeds 3/4/5 must produce different geometry (boil, not a freeze).
        #expect(art.variants[0].outline.ink.boundingBoxOfPath
                != art.variants[1].outline.ink.boundingBoxOfPath)
    }
}
