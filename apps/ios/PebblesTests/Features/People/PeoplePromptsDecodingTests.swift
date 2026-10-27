import Foundation
import Testing
@testable import Pebbles

/// `people_prompts` decoded from the harness's verbatim payload, plus the
/// precision and null variants the other surfaces emit (standing rule).
@Suite("People prompts decoding")
struct PeoplePromptsDecodingTests {
    @Test func decodesTheHarnessPayloadVerbatim() throws {
        let prompts = try WrapDecoding.decode(PeoplePromptsPayload.self, from: PeoplePayloads.data("people-prompts")).model
        #expect(prompts.pebbles == 7 && prompts.souls == 1)
        #expect(prompts.prompts.count == 3)
        #expect(prompts.prompts[0].name == "outward bright b7f92491")
        #expect(prompts.prompts[1].positiveness == 0)
        #expect(prompts.prompts[0].happenedAt == ISO8601Flexible.parse("2026-10-06T09:00:00Z"))
        #expect(prompts.skipped == ["outward low b7f92491", "inward bright b7f92491"])
    }

    @Test func nullsAndFractionalSecondsAreTolerated() throws {
        let json = #"{"souls":0,"pebbles":1,"prompts":[{"name":null,"intensity":2,"moment_id":"a6b3a0f7-b5a8-48d6-a266-2fb32ec54055","pebble_id":"85a50229-9d81-474e-b6e4-61dbb6a58d45","emotion_id":null,"happened_at":"2026-10-06T09:00:00.123456+00:00","positiveness":1}],"skipped":[]}"#
        let prompts = try WrapDecoding.decode(PeoplePromptsPayload.self, from: Data(json.utf8)).model
        #expect(prompts.prompts.count == 1)
        #expect(prompts.prompts[0].name == nil && prompts.prompts[0].emotionId == nil)
    }

    @Test func aBadPromptRowIsDroppedNotFatal() throws {
        let json = #"{"souls":0,"pebbles":2,"prompts":[{"name":"x","intensity":"two","moment_id":"bad","pebble_id":"bad","emotion_id":null,"happened_at":"nope","positiveness":1},{"name":"ok","intensity":2,"moment_id":"a6b3a0f7-b5a8-48d6-a266-2fb32ec54055","pebble_id":"85a50229-9d81-474e-b6e4-61dbb6a58d45","emotion_id":null,"happened_at":"2026-10-06T09:00:00Z","positiveness":1}],"skipped":[]}"#
        let payload = try WrapDecoding.decode(PeoplePromptsPayload.self, from: Data(json.utf8))
        #expect(payload.model.prompts.map(\.name) == ["ok"])
        #expect(payload.dropped == 1)
    }
}
