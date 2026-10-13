import Foundation
import Supabase

/// The two deletes (R8), with the client's half of each: `delete_moment`
/// hands back the snap prefixes and the client removes their files,
/// best-effort (the orphan sweep is the backstop).
@MainActor
enum MomentDeletion {
    /// Also forgets the moment's half-finished Complete flow (A3).
    static func deleteMoment(
        _ momentId: UUID,
        client: SupabaseClient,
        drafts: CompleteDraftStore? = nil
    ) async throws {
        let paths = try await MomentsService(client: client).deleteMoment(id: momentId)
        drafts?.clear(momentId: momentId)
        let repo = PebbleSnapRepository(client: client)
        for path in paths {
            await repo.deleteFiles(storagePrefix: path)
        }
    }

    static func deletePebble(_ pebbleId: UUID, client: SupabaseClient) async throws {
        try await MomentsService(client: client).deletePebble(id: pebbleId)
    }
}
