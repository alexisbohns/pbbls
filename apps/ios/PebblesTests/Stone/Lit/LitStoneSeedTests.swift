import Foundation
import Testing
@testable import Pebbles

/// The picker's sample stones (#1690 D4): a fixed seed per valence and the
/// bundled sample glyph, so the fan never changes between launches.
@Suite("Lit stone sample")
struct LitStoneSeedTests {
    /// FNV-1a over UTF-16 of the string, computed independently of `StableHash`.
    @Test("the seed is the FNV-1a hash of sample-<valence>")
    func pinned() {
        #expect(LitStoneSeed.sample(.highlightMedium) == 614_695_697)
        #expect(LitStoneSeed.sample(.lowlightSmall) == 2_265_239_455)
    }

    @Test("the nine valences get nine different seeds")
    func distinct() {
        let seeds = Set(Valence.allCases.map(LitStoneSeed.sample))
        #expect(seeds.count == Valence.allCases.count)
    }

    @Test("the sample glyph decodes from stone-glyph.json, in a 200 square")
    func sampleGlyph() throws {
        let glyph = try #require(LitStoneSampleGlyph.glyph)
        #expect(!glyph.strokes.isEmpty)
        #expect(LitStoneGeometry.glyphSide(viewBox: glyph.viewBox) == 200)
        #expect(glyph == LitStoneSampleGlyph.glyph, "one decode, one identity, so the cache keys on it")
    }
}
