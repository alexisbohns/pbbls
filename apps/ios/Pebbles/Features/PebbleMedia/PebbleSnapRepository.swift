import Foundation
import Supabase
import os

/// Storage operations for `pebbles-media`. Stateless except for the injected
/// `SupabaseClient`. Errors propagate; callers decide whether to retry, surface
/// to the user, or fire-and-forget.
@MainActor
struct PebbleSnapRepository {

    private static let bucketId = "pebbles-media"
    private static let signedUrlTTL: Int = 3600    // 1 h
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-repo")

    let client: SupabaseClient

    /// Upload original + thumb in parallel. Returns when both succeed; throws
    /// if either fails. The two `Data` blobs come from `ImagePipeline.process`.
    func uploadProcessed(
        _ processed: ProcessedImage,
        snapId: UUID,
        userId: UUID
    ) async throws {
        let originalPath = "\(userId.uuidString)/\(snapId.uuidString)/original.jpg"
        let thumbPath    = "\(userId.uuidString)/\(snapId.uuidString)/thumb.jpg"
        let bucket = client.storage.from(Self.bucketId)
        let options = FileOptions(contentType: "image/jpeg")

        async let original: Void = {
            _ = try await bucket.upload(originalPath, data: processed.original, options: options)
        }()
        async let thumb: Void = {
            _ = try await bucket.upload(thumbPath, data: processed.thumb, options: options)
        }()
        _ = try await (original, thumb)
    }

    /// Best-effort cleanup of a snap's Storage files. Logs failures but does
    /// not throw — the orphan-sweep follow-up will catch any residue.
    func deleteFiles(snapId: UUID, userId: UUID) async {
        let originalPath = "\(userId.uuidString)/\(snapId.uuidString)/original.jpg"
        let thumbPath    = "\(userId.uuidString)/\(snapId.uuidString)/thumb.jpg"
        do {
            _ = try await client.storage.from(Self.bucketId)
                .remove(paths: [originalPath, thumbPath])
        } catch {
            Self.logger.error(
                "snap delete failed for \(snapId.uuidString, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
        }
    }

    /// One round-trip for both URLs of a snap. Caller is responsible for
    /// caching by `snapId` until expiry.
    struct SignedURLs {
        let original: URL
        let thumb: URL
    }

    func signedURLs(snapId: UUID, userId: UUID) async throws -> SignedURLs {
        try await signedURLs(storagePrefix: "\(userId.uuidString)/\(snapId.uuidString)")
    }

    /// Read-path overload: caller already has the `storage_path` string from
    /// `public.snaps.storage_path` and doesn't need to know the snap/user IDs.
    func signedURLs(storagePrefix prefix: String) async throws -> SignedURLs {
        let originalPath = "\(prefix)/original.jpg"
        let thumbPath    = "\(prefix)/thumb.jpg"
        let signed = try await client.storage.from(Self.bucketId)
            .createSignedURLs(paths: [originalPath, thumbPath], expiresIn: Self.signedUrlTTL)
        guard signed.count == 2 else { throw URLError(.badServerResponse) }
        return SignedURLs(original: signed[0], thumb: signed[1])
    }
}
