import UIKit

/// Decoded snap photos by storage path (#1160): a Spine card and the held card
/// it flies into draw the same photo, and the second must not start empty and
/// fetch it again. `SpineCardFace` reads it before any fetch and fills it when
/// a load succeeds; `SignedOutPurge` empties it.
@MainActor
final class SnapImageCache {
    static let shared = SnapImageCache()

    private let cache = NSCache<NSString, UIImage>()

    /// Originals are at most 1024 px an edge (3–4 MB decoded), so the count
    /// alone would let the cache reach hundreds of megabytes before NSCache
    /// reacts; the cost limit keeps it near a hundred.
    init(countLimit: Int = 80, totalCostLimit: Int = 96 * 1024 * 1024) {
        cache.countLimit = countLimit
        cache.totalCostLimit = totalCostLimit
    }

    func image(for path: String) -> UIImage? {
        cache.object(forKey: path as NSString)
    }

    func store(_ image: UIImage, for path: String) {
        let cost = image.cgImage.map { $0.bytesPerRow * $0.height } ?? 0
        cache.setObject(image, forKey: path as NSString, cost: cost)
    }

    func removeAll() {
        cache.removeAllObjects()
    }
}
