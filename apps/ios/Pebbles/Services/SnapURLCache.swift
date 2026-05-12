import Foundation
import Observation
import Supabase
import os

/// Per-session cache of signed Storage URLs keyed by `storage_path`.
/// Returns cached entries until they near expiry; coalesces concurrent
/// requests for the same path into a single round-trip.
///
/// Production wiring lives in `PebblesApp`, which constructs the cache
/// with a live `PebbleSnapRepository` as the provider. Sign-out
/// invalidation is wired in `RootView` via `.onChange(of: supabase.session)`.
@Observable
@MainActor
final class SnapURLCache {

    /// Effective TTL is `signedUrlTTL - safetyMargin`, so a URL handed out
    /// at hit time never expires mid-download.
    private static let signedUrlTTL: TimeInterval = 3600
    private static let safetyMargin: TimeInterval = 60

    private struct Entry {
        let urls: PebbleSnapRepository.SignedURLs
        let expiresAt: Date
    }

    private let provider: SignedURLProviding
    private let now: @Sendable () -> Date

    private var cache: [String: Entry] = [:]
    private var inflight: [String: Task<PebbleSnapRepository.SignedURLs, Error>] = [:]

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-url-cache")

    /// Production initializer. `PebblesApp` calls this with the live
    /// SupabaseClient.
    convenience init(client: SupabaseClient) {
        self.init(provider: PebbleSnapRepository(client: client), now: { Date() })
    }

    /// Test initializer. Allows injecting a fake provider and a virtual
    /// clock.
    init(provider: SignedURLProviding, now: @escaping @Sendable () -> Date) {
        self.provider = provider
        self.now = now
    }

    func signedURLs(storagePath: String) async throws
        -> PebbleSnapRepository.SignedURLs
    {
        if let entry = cache[storagePath], now() < entry.expiresAt {
            return entry.urls
        }
        if let task = inflight[storagePath] {
            return try await task.value
        }
        let task = Task<PebbleSnapRepository.SignedURLs, Error> { [provider] in
            try await provider.signedURLs(storagePrefix: storagePath)
        }
        inflight[storagePath] = task
        defer { inflight[storagePath] = nil }
        do {
            let urls = try await task.value
            cache[storagePath] = Entry(
                urls: urls,
                expiresAt: now() + Self.signedUrlTTL - Self.safetyMargin
            )
            return urls
        } catch {
            logger.error(
                "sign failed for \(storagePath, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
            throw error
        }
    }

    func invalidateAll() {
        cache.removeAll()
    }
}
