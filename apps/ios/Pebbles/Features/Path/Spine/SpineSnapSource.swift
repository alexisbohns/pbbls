import SwiftUI
import UIKit

/// Where a Spine card's photo comes from (#1130 §Testing). In the app, the snap
/// is signed and `AsyncImage` loads it; references and the lab hand decoded
/// `UIImage`s instead, because `AsyncImage` does not render under
/// `ImageRenderer`.
struct SpineSnapSource {
    /// A decoded photo for a storage path, at once; nil when this source has none in memory.
    var image: (String) -> UIImage?
    /// The signed original, for `AsyncImage`; nil when unsigned (the cache logs why).
    var url: (String) async -> URL?

    static let none = SpineSnapSource(image: { _ in nil }, url: { _ in nil })

    @MainActor
    static func signed(_ cache: SnapURLCache) -> SpineSnapSource {
        // `SnapURLCache.signedURLs` logs its own failure before it throws.
        SpineSnapSource(image: { _ in nil }, url: { path in try? await cache.signedURLs(storagePath: path).original })
    }

    /// Decoded images by storage path (references, the lab).
    static func images(_ byPath: [String: UIImage]) -> SpineSnapSource {
        SpineSnapSource(image: { byPath[$0] }, url: { _ in nil })
    }
}

private struct SpineSnapSourceKey: EnvironmentKey {
    static let defaultValue = SpineSnapSource.none
}

extension EnvironmentValues {
    var spineSnapSource: SpineSnapSource {
        get { self[SpineSnapSourceKey.self] }
        set { self[SpineSnapSourceKey.self] = newValue }
    }
}
