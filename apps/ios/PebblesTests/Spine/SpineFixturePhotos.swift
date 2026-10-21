import Testing
import UIKit
@testable import Pebbles

/// The fixture week's five photos (the web's `public/sandbox/*.jpg`, ratios 1:1,
/// 3:2, 3:2, 16:9, 2:3), decoded for `SpineSnapSource` so the references draw
/// real photos: `AsyncImage` does not render under `ImageRenderer`. Read through
/// `#filePath`, as the reference PNGs are; the simulator shares the host disk.
enum SpineFixturePhotos {
    static let source: SpineSnapSource = {
        let folder = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("Fixtures")
        var images: [String: UIImage] = [:]
        for (path, photo) in SpineFixtureWeek.photos {
            if let image = UIImage(contentsOfFile: folder.appendingPathComponent("\(photo.file).jpg").path) {
                images[path] = image
            }
        }
        guard images.count == SpineFixtureWeek.photos.count else {
            Issue.record("missing a fixture photo in \(folder.path)")
            return .none
        }
        return .images(images)
    }()
}
