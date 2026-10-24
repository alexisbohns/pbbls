import Foundation
import os

/// Everything the app keeps about the signed-in user outside the view tree,
/// forgotten in one place when there is no session.
///
/// Pebbles is a journal, and phones get handed over and shared. Whatever
/// survives a sign-out is served to the next account on the same install:
/// a half-finished Complete flow would be offered back, the souls picker
/// would list someone else's people, and cached photo responses would outlive
/// the signed URLs that granted them.
///
/// `RootView` runs this on every resolved signed-out state: a sign-out, an
/// account deletion (which signs out once the server has erased the account),
/// and a cold start whose persisted session is gone. It never runs while auth
/// is still resolving, or a signed-in user's half-finished flows would be wiped on
/// every launch.
@MainActor
struct SignedOutPurge {
    /// Half-finished Complete flows, one file per moment (A3).
    let completeDrafts: CompleteDraftStore
    let refs: ReferenceDataService
    let stats: PathStatsService
    let snapURLs: SnapURLCache
    /// `AsyncImage` loads through `URLSession.shared`, so snap photo bytes land
    /// in `URLCache.shared` (memory and disk).
    let urlCache: URLCache
    /// The Path's wraps kept on the device (#1226).
    var wrapCache: WrapDiskCache = .standard

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "signed-out-purge")

    func run() {
        completeDrafts.clearAll()
        refs.reset()
        stats.reset()
        snapURLs.invalidateAll()
        urlCache.removeAllCachedResponses()
        SnapImageCache.shared.removeAll()
        wrapCache.clear()
        Self.logger.info("cleared local account state")
    }
}
