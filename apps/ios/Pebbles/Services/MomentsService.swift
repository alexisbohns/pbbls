import Foundation
import Supabase

/// Every read and write the moments split introduced (spec §3–§4), in one
/// place: a Supabase name the split owns appears in this file and nowhere
/// else, so a grep finds every caller of the contract. Views construct it
/// from `SupabaseService.client`; it holds no state.
struct MomentsService {
    let client: SupabaseClient

    /// Explicit, so a dropped column fails loudly here rather than silently
    /// in a `*` (the web break, #1053).
    static let detailColumns =
        "id, user_id, name, description, happened_at, hidden_at, created_at, updated_at, snaps, pebble"

    // MARK: - Reads

    /// The caller's Path, newest `happened_at` first.
    func pathMoments() async throws -> [MomentSummary] {
        try await client.rpc("path_moments").execute().value
    }

    func moment(id: UUID) async throws -> MomentDetail {
        try await client
            .from("v_moments_full")
            .select(Self.detailColumns)
            .eq("id", value: id)
            .single()
            .execute()
            .value
    }

    /// Moments whose pebble carries this soul (R9). Two reads, no write:
    /// the link ids, intersected with the Path the caller already reads.
    func moments(taggedWithSoul soulId: UUID) async throws -> [MomentSummary] {
        async let links: [PebbleLink] = client
            .from("pebble_souls")
            .select("pebble_id")
            .eq("soul_id", value: soulId)
            .execute()
            .value
        async let all = pathMoments()
        return try await Self.moments(all, carrying: Set(links.map(\.pebbleId)))
    }

    func moments(inCollection collectionId: UUID) async throws -> [MomentSummary] {
        async let links: [PebbleLink] = client
            .from("collection_pebbles")
            .select("pebble_id")
            .eq("collection_id", value: collectionId)
            .execute()
            .value
        async let all = pathMoments()
        return try await Self.moments(all, carrying: Set(links.map(\.pebbleId)))
    }

    static func moments(_ moments: [MomentSummary], carrying pebbleIds: Set<UUID>) -> [MomentSummary] {
        moments.filter { moment in moment.pebble.map { pebbleIds.contains($0.id) } ?? false }
    }

    /// The pebble's derived glyph, or nil (none, unreadable, unknown id).
    func resolveGlyph(pebbleId: UUID) async throws -> PebbleGlyph? {
        let data = try await client
            .rpc("resolve_pebble_glyph", params: ["p_pebble_id": pebbleId.uuidString.lowercased()])
            .execute()
            .data
        return try Self.decodeOptionalGlyph(data)
    }

    static func decodeOptionalGlyph(_ data: Data) throws -> PebbleGlyph? {
        guard !data.isEmpty else { return nil }
        return try JSONDecoder().decode(PebbleGlyph?.self, from: data)
    }

    /// Sum of the caller's karma events for one pebble (A4). Read before and
    /// after a write; the difference is what the write earned.
    func karmaTotal(refId: UUID) async throws -> Int {
        let rows: [KarmaDeltaRow] = try await client
            .from("karma_events")
            .select("delta")
            .eq("ref_id", value: refId)
            .execute()
            .value
        return rows.reduce(0) { $0 + $1.delta }
    }

    // MARK: - Writes

    func saveMoment(_ payload: SaveMomentPayload) async throws -> UUID {
        try await client.rpc("save_moment", params: PayloadParams(payload: payload)).execute().value
    }

    func completePebble(_ payload: CompletePebblePayload) async throws -> UUID {
        try await client.rpc("complete_pebble", params: PayloadParams(payload: payload)).execute().value
    }

    /// The pebble only; the moment stays, as a blank stone.
    func deletePebble(id: UUID) async throws {
        try await client.rpc("delete_pebble", params: ["p_pebble_id": id.uuidString.lowercased()]).execute()
    }

    /// The moment, its pebbles and snap rows. Returns the snap storage
    /// prefixes whose files the client removes (spec §3).
    func deleteMoment(id: UUID) async throws -> [String] {
        let result: DeleteMomentResult = try await client
            .rpc("delete_moment", params: ["p_moment_id": id.uuidString.lowercased()])
            .execute()
            .value
        return result.storagePaths
    }
}

/// `{ "payload": … }`, the single jsonb argument of both writers.
struct PayloadParams<Payload: Encodable>: Encodable {
    let payload: Payload
}

struct DeleteMomentResult: Decodable, Equatable {
    let storagePaths: [String]

    private enum CodingKeys: String, CodingKey {
        case storagePaths = "storage_paths"
    }
}

private struct PebbleLink: Decodable {
    let pebbleId: UUID

    private enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}

private struct KarmaDeltaRow: Decodable {
    let delta: Int
}
