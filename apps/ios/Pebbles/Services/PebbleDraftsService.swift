import Foundation
import Observation
import Supabase
import os

/// A `pebble_drafts` row as the drafts list needs it.
///
/// `updated_at` is decoded from its **string** form through `ISO8601Flexible`
/// rather than left to whatever date strategy the ambient decoder happens to
/// carry (#649). Postgres returns microsecond precision, and a decoder expecting
/// a numeric or fixed-precision date fails the whole row — which would break the
/// drafts list outright, not just the timestamp.
struct PebbleDraftRecord: Identifiable, Decodable, Equatable {
    let id: UUID
    let payload: PebbleDraftPayload
    let updatedAt: Date

    enum CodingKeys: String, CodingKey {
        case id
        case payload
        case updatedAt = "updated_at"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(UUID.self, forKey: .id)
        payload = try container.decode(PebbleDraftPayload.self, forKey: .payload)
        let raw = try container.decode(String.self, forKey: .updatedAt)
        guard let parsed = ISO8601Flexible.parse(raw) else {
            throw DecodingError.dataCorruptedError(
                forKey: .updatedAt, in: container,
                debugDescription: "unparseable updated_at: \(raw)"
            )
        }
        updatedAt = parsed
    }

    /// Memberwise init, for tests and previews.
    init(id: UUID, payload: PebbleDraftPayload, updatedAt: Date) {
        self.id = id
        self.payload = payload
        self.updatedAt = updatedAt
    }
}

/// Server-side drafts (M47). Direct single-table CRUD on `pebble_drafts`,
/// owner-scoped by RLS — the sanctioned direct-client pattern, since nothing
/// here is multi-table and nothing emits karma.
///
/// Zero karma is structural rather than enforced: `create_pebble` is the
/// schema's only `pebble_created` emitter and a draft never reaches it.
///
/// Production wiring lives in `PebblesApp`, alongside `SnapURLCache`.
@Observable
@MainActor
final class PebbleDraftsService {

    private let client: SupabaseClient
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-drafts")

    /// Mirrors the count shown on the Path entry point. Refreshed by `list()`.
    private(set) var count: Int = 0

    init(client: SupabaseClient) {
        self.client = client
    }

    /// Most recently saved first — `updated_at` is trigger-maintained server-side,
    /// so this ordering does not depend on client clocks.
    func list() async throws -> [PebbleDraftRecord] {
        let rows: [PebbleDraftRecord] = try await client
            .from("pebble_drafts")
            .select("id, payload, updated_at")
            .order("updated_at", ascending: false)
            .execute()
            .value
        count = rows.count
        return rows
    }

    func load(id: UUID) async throws -> PebbleDraftRecord? {
        let rows: [PebbleDraftRecord] = try await client
            .from("pebble_drafts")
            .select("id, payload, updated_at")
            .eq("id", value: id)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    /// Upsert. `id` nil creates; passing one replaces that draft's payload
    /// wholesale — which is the point of the jsonb column, since a coalescing
    /// update could never clear a field the user emptied.
    ///
    /// `userId` is explicit because the RLS `with check` compares it to
    /// `auth.uid()`.
    @discardableResult
    func save(payload: PebbleDraftPayload, id: UUID?, userId: UUID) async throws -> UUID {
        let row = DraftUpsertPayload(id: id ?? UUID(), userId: userId, payload: payload)
        try await client
            .from("pebble_drafts")
            .upsert(row, onConflict: "id")
            .execute()
        return row.id
    }

    func delete(id: UUID) async throws {
        try await client
            .from("pebble_drafts")
            .delete()
            .eq("id", value: id)
            .execute()
    }

    /// Best-effort cleanup after a successful publish: the pebble exists, so a
    /// failure here must not surface as a publish failure. Worst case a stale
    /// draft is left in the list.
    func deleteIgnoringFailure(id: UUID) async {
        do {
            try await delete(id: id)
        } catch {
            logger.warning("draft cleanup after publish failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Refresh `count` without surfacing an error — it only drives an entry
    /// point's badge, and a missing badge beats an error state on the Path.
    ///
    /// Selects `id` alone rather than reusing `list()`: this runs on every Path
    /// appearance, and pulling every draft's full jsonb payload to render one
    /// number is waste on the app's home screen.
    func refreshCount() async {
        struct IdRow: Decodable { let id: UUID }
        do {
            let rows: [IdRow] = try await client
                .from("pebble_drafts")
                .select("id")
                .execute()
                .value
            count = rows.count
        } catch {
            logger.error("draft count refresh failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// `user_id` is explicitly encoded so the RLS `with check` sees it; `payload`
    /// rides along as the nested jsonb.
    private struct DraftUpsertPayload: Encodable {
        let id: UUID
        let userId: UUID
        let payload: PebbleDraftPayload

        enum CodingKeys: String, CodingKey {
            case id
            case userId = "user_id"
            case payload
        }
    }
}
