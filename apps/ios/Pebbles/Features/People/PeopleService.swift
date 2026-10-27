import Foundation
import os
import Supabase

/// The People tab's server read, behind a protocol so previews and tests use
/// fakes. Nothing here aggregates: the totals come from the RPC (spec D10).
protocol PeoplePrompting: Sendable {
    /// `people_prompts(p_limit)`: the totals and up to `limit` pebbles with nobody on them.
    func prompts(limit: Int) async throws -> PeoplePrompts
}

struct PeopleService: PeoplePrompting {
    let client: SupabaseClient

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "people.service")

    func prompts(limit: Int) async throws -> PeoplePrompts {
        let data = try await client.rpc("people_prompts", params: ["p_limit": limit]).execute().data
        let payload = try WrapDecoding.decode(PeoplePromptsPayload.self, from: data)
        if payload.dropped > 0 {
            Self.logger.warning("people_prompts: dropped \(payload.dropped) prompt(s) the client could not decode")
        }
        return payload.model
    }
}
