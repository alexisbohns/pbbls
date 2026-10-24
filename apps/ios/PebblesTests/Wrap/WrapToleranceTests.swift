import Foundation
import Testing
@testable import Pebbles

/// The wraps' payloads grow additively across clients (#1226): what a newer
/// server adds must not break this client. Android: `WrapToleranceTest`.
@Suite("Path wrap decoding tolerance")
struct WrapToleranceTests {
    @Test("unknown tiles and keys are dropped")
    func unknownTiles() throws {
        let json = """
        {"week_start":"2026-10-05","tiles":["contents","sparkles","persons"],"persons":[],"glitter":{"a":1}}
        """
        let week = try WrapDecoding.decode(WeekWrapPayload.self, from: Data(json.utf8)).model
        #expect(week.tiles == [.contents, .persons])
    }

    @Test("an unknown verdict, explicit nulls and a null where a default exists all decode")
    func unknownVerdictAndNulls() throws {
        let json = """
        {"month":"2026-10-01","days_in_month":31,"first_weekday":3,"count":null,"sections":["weeks","tone","aurora"],
         "gem":{"main":null,"facets":{"kite_0":null,"kite_1":"joy"}},
         "tone":{"days":[[1,0,2]],"verdict":"luminous","first":null,"last":-1}}
        """
        let month = try WrapDecoding.decode(MonthWrapPayload.self, from: Data(json.utf8)).model
        #expect(month.pebbles == 0)
        #expect(month.sections == [.weeks, .tone])
        let gem = try #require(month.gem)
        #expect(gem.main == nil)
        #expect(gem.facets.keys.contains("kite_0"))
        #expect(gem.family(ofFacet: "kite_0") == nil)
        #expect(gem.family(ofFacet: "kite_1") == "joy")
        #expect(month.tone?.verdict == .unknown)
        #expect(month.tone?.first == nil)
        #expect(month.tone?.last == .lowlight)
        #expect(month.cover == nil)
    }

    @Test("an unknown strip kind is dropped")
    func unknownStripKind() throws {
        let json = #"{"items":[{"kind":"season","count":3},{"kind":"month","month":"2026-09-01","count":1}]}"#
        let strip = try WrapDecoding.decode(StripPayload.self, from: Data(json.utf8)).model
        #expect(strip.items == [.month(StripMonth(month: CalendarMonth(year: 2026, month: 9), pebbles: 1))])
    }

    @Test("a malformed day fails the page rather than guessing")
    func malformedDay() {
        let json = #"{"week_start":"2026-10-05T00:00:00Z","tiles":[]}"#
        #expect(throws: DecodingError.self) {
            try WrapDecoding.decode(WeekWrapPayload.self, from: Data(json.utf8))
        }
    }
}
