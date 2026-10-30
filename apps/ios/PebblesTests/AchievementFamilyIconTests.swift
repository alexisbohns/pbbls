import Testing
@testable import Pebbles

@Suite("Achievement family icons")
struct AchievementFamilyIconTests {
    @Test("every family draws a pebbles icon; the wave families and unknowns take the glow (spec D7)",
          arguments: [
            ("pebble_count", PbblsIcon.stack), ("emotion_first", .heart), ("domain_first", .pebbleGlowMd),
            ("first_collection", .collection), ("first_glyph", .pebbleDottedGlyph), ("first_soul", .people),
            ("first_moment", .pebbleFlat), ("first_pebble", .pebbleRound), ("first_five", .stackLit3),
            ("glyph_count", .pebbleDottedGlyph), ("glyph_sales", .pebbleGlowMd), ("never_heard_of", .pebbleGlowMd)
          ])
    func familySymbol(family: String, icon: PbblsIcon) throws {
        #expect(try AchievementsDecodingTests.record(family: family).familySymbol == icon)
    }
}
