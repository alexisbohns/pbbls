import Foundation
import os

/// Everything the app keeps about the signed-in user outside the view tree,
/// forgotten in one place when there is no session.
///
/// Pebbles is a journal, and phones get handed over and shared. Whatever
/// survives a sign-out is served to the next account on the same install:
/// the composer's crash snapshot would be offered as a restorable draft, the
/// souls picker would list someone else's people, and cached photo responses
/// would outlive the signed URLs that granted them.
///
/// `RootView` runs this on every resolved signed-out state: a sign-out, an
/// account deletion (which signs out once the server has erased the account),
/// and a cold start whose persisted session is gone. It never runs while auth
/// is still resolving, or a signed-in user's crash recovery would be wiped on
/// every launch.
@MainActor
struct SignedOutPurge {
    let snapshots: ComposerSnapshotStore
    let refs: ReferenceDataService
    let stats: PathStatsService
    let drafts: PebbleDraftsService
    let snapURLs: SnapURLCache
    /// `AsyncImage` loads through `URLSession.shared`, so snap photo bytes land
    /// in `URLCache.shared` (memory and disk).
    let urlCache: URLCache

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "signed-out-purge")

    func run() {
        snapshots.clear()
        refs.reset()
        stats.reset()
        drafts.reset()
        snapURLs.invalidateAll()
        urlCache.removeAllCachedResponses()
        Self.logger.info("cleared local account state")
    }
}
