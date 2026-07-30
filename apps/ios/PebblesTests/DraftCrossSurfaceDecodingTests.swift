import Foundation
import Testing
@testable import Pebbles

/// A draft written by ANOTHER surface must decode here. Web writes
/// `new Date().toISOString()`, which carries milliseconds; Postgres returns
/// `timestamptz` with microseconds. iOS-only round-trip tests cannot catch a
/// formatter that rejects either, so these use real foreign payloads verbatim.
@Suite("Draft cross-surface decoding")
struct DraftCrossSurfaceDecodingTests {

    @Test("a web-written happened_at (milliseconds) parses")
    func webMillisecondsParse() throws {
        // Exactly what `new Date().toISOString()` produces.
        let json = #"{"name":"From web","happened_at":"2026-07-30T01:23:45.123Z"}"#
        let decoded = try JSONDecoder().decode(PebbleDraftPayload.self, from: Data(json.utf8))
        #expect(decoded.happenedAt != nil, "a web draft's timestamp must survive")
    }

    @Test("a Postgres-style happened_at (microseconds + offset) parses")
    func postgresMicrosecondsParse() throws {
        let json = #"{"name":"x","happened_at":"2026-07-30T01:23:45.123456+00:00"}"#
        let decoded = try JSONDecoder().decode(PebbleDraftPayload.self, from: Data(json.utf8))
        #expect(decoded.happenedAt != nil)
    }

    @Test("a whole-second happened_at still parses")
    func wholeSecondsParse() throws {
        let json = #"{"name":"x","happened_at":"2026-07-30T01:23:45Z"}"#
        let decoded = try JSONDecoder().decode(PebbleDraftPayload.self, from: Data(json.utf8))
        #expect(decoded.happenedAt != nil)
    }

    @Test("a pebble_drafts row decodes with a fractional-second updated_at")
    func draftRecordDecodes() throws {
        // The shape PostgREST returns for `select id, payload, updated_at`.
        let json = """
        {"id":"3F2504E0-4F89-11D3-9A0C-0305E82C3301",
         "payload":{"name":"Quick capture"},
         "updated_at":"2026-07-30T01:23:45.123456+00:00"}
        """
        let record = try JSONDecoder().decode(PebbleDraftRecord.self, from: Data(json.utf8))
        #expect(record.payload.name == "Quick capture")
    }
}
