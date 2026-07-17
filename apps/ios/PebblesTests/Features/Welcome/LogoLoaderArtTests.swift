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
        // Each SVG path is kept separate (per-path masking, #598). The logo
        // has 1 outline, 10 creature strokes (12 minus the two eyes), and
        // 12 fossil+vein strokes (10 fossil lines + 2 veins).
        #expect(parsed.outline.count == 1)
        #expect(parsed.creatureStrokes.count == 10)
        #expect(parsed.fossilAndVeins.count == 12)
        #expect(parsed.outline.allSatisfy { !$0.isEmptyGroup })
        #expect(parsed.creatureStrokes.allSatisfy { !$0.isEmptyGroup })
        #expect(parsed.fossilAndVeins.allSatisfy { !$0.isEmptyGroup })
        // The two eyes are fills, kept combined.
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
            #expect(v.outline.count == 1)
            #expect(v.creature.count == 10)
            #expect(v.fossilVeins.count == 12)
            #expect(v.outline.allSatisfy { !$0.ink.isEmptyGroup })
            #expect(v.creature.allSatisfy { !$0.ink.isEmptyGroup })
            #expect(v.fossilVeins.allSatisfy { !$0.ink.isEmptyGroup })
            #expect(!v.eyes.isEmptyGroup)
        }
        // Seeds 3/4/5 must produce different geometry (boil, not a freeze).
        #expect(art.variants[0].outline[0].ink.boundingBoxOfPath
                != art.variants[1].outline[0].ink.boundingBoxOfPath)
    }
}
