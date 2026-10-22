import UIKit

/// Decoded snap photos by storage path (#1160): a Spine card and the held card
/// it flies into draw the same photo, and the second must not start empty and
/// fetch it again. `SpineCardFace` reads it before any fetch and fills it when
/// a load succeeds; `SignedOutPurge` empties it.
@MainActor
final class SnapImageCache {
    static let shared = SnapImageCache()

    private let cache = NSCache<NSString, UIImage>()

    init(countLimit: Int = 80) {
        cache.countLimit = countLimit
    }

    func image(for path: String) -> UIImage? {
        cache.object(forKey: path as NSString)
    }

    func store(_ image: UIImage, for path: String) {
        cache.setObject(image, forKey: path as NSString)
    }

    func removeAll() {
        cache.removeAllObjects()
    }
}
