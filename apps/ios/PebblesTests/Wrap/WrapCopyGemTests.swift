import Foundation
import Testing
@testable import Pebbles

/// The gem's words (#1226). Android: `WrapCopyTest`'s and `WrapCopyFrenchTest`'s
/// gem assertions, verbatim.
@Suite("WrapCopy gem")
@MainActor
struct WrapCopyGemTests {
    // swiftlint:disable:next type_name
    typealias V = Vectors

    @Test("the gem's lead reads the server's order and zeros")
    func english() {
        #expect(WrapCopy.gemLead(order: ["joy", "peace", "pride"], zero: ["shame"], level: false, locale: V.en)
            == "Joy first, then peace; nothing in shame.")
        #expect(WrapCopy.gemLead(order: ["joy", "peace"], zero: [], level: true, locale: V.en)
            == "Joy and peace level.")
        #expect(WrapCopy.gemLead(order: ["anger"], zero: (0..<6).map { "x\($0)" }, level: false, locale: V.en)
            == "Only anger; nothing in the other six.")
        #expect(WrapCopy.gemLead(order: ["joy", "fear"], zero: ["shame", "pride"], level: false, locale: V.en)
            == "Joy first, then fear; nothing in shame or pride.")
        #expect(WrapCopy.moreEmotions(11, locale: V.en) == "all eleven emotions")
        #expect(WrapCopy.moreEmotions(3, locale: V.en) == "the emotions, in detail")
        #expect(WrapCopy.gemPebbles(13, locale: V.en) == "pebbles")
    }

    @Test("the gem's lead reads in French")
    func french() {
        #expect(WrapCopy.gemLead(order: ["joie", "paix"], zero: ["honte"], level: false, locale: V.fr)
            == "Joie d'abord, puis paix, rien en honte.")
        #expect(WrapCopy.gemPebbles(1, locale: V.fr) == "galet")
    }

    @Test("the gem's words on the harness's October: the server's order, its shares, one sentence")
    func october() throws {
        let wrap = try MonthPayloadFixture.october()
        let gem = try #require(wrap.gem)
        let words = GemWords(gem: gem, lookups: MonthPayloadFixture.lookups(wrap), locale: V.en)
        #expect(words.lead == "Joy first, then peace; nothing in shame.")
        #expect(words.labels.map(\.slug) == ["joy", "peace", "pride", "anger", "sadness", "fear"])
        #expect(words.labels.map(\.percent) == [34, 20, 17, 12, 12, 5])
        #expect(words.description
            == "The emotions as a gem: joy 34%, peace 20%, pride 17%, anger 12%, sadness 12%, fear 5%.")
        #expect(WrapCopy.gemDescription([("joie", 34)], locale: V.fr)
            == "Les émotions en pierre taillée : joie 34\u{00A0}%.")
    }

    @Test("a level lead when the first two families weigh the same")
    func level() {
        let gem = Gem(worths: ["joy": 4, "peace": 4], shares: ["joy": 0.5, "peace": 0.5], main: "joy", rotation: 0,
                      facets: [:], lead: Lead(order: ["joy", "peace"], zero: []))
        let lookups = WrapLookups(family: { slug in SpineFixtureWeek.families.first { $0.slug == slug } })
        #expect(GemWords(gem: gem, lookups: lookups, locale: V.en).lead == "Joy and peace level.")
    }
}
