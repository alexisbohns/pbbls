import Testing
@testable import Pebbles

@Suite("Achievement family icons")
struct AchievementFamilyIconTests {
    @Test("every family draws a pebbles icon; the wave families, the six firsts, the completions and unknowns take the glow (spec D7, Android #1349)",
          arguments: [
            ("pebble_count", PbblsGlyph.stack), ("emotion_first", .heart), ("domain_first", .pebbleGlowMd),
            ("first_collection", .collection), ("first_glyph", .pebbleGlyph), ("first_soul", .people),
            ("first_moment", .pebbleFlat), ("first_pebble", .pebbleRound), ("first_five", .stack),
            ("glyph_count", .pebbleGlyph), ("glyph_sales", .pebbleGlowMd), ("first_stone", .pebbleGlowMd),
            ("set_complete", .pebbleGlowMd), ("never_heard_of", .pebbleGlowMd)
          ])
    func familySymbol(family: String, icon: PbblsGlyph) throws {
        #expect(try AchievementsDecodingTests.record(family: family).familySymbol == icon)
    }

    @Test("the dotted line and the last step are forms of the family symbol, not twins")
    func familyForm() throws {
        #expect(try AchievementsDecodingTests.record(family: "first_five").familyForm == .init(.progress(3)))
        #expect(try AchievementsDecodingTests.record(family: "first_glyph").familyForm == .dotted)
        #expect(try AchievementsDecodingTests.record(family: "glyph_count").familyForm == .dotted)
        #expect(try AchievementsDecodingTests.record(family: "first_moment").familyForm == .regular)
    }
}
