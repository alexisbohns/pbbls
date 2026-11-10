import Foundation

/// A file of this repository, read in place from the simulator as `PbblsIconTests` reads the manifest.
enum RepoFile {
    static let root = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Glyphs
        .deletingLastPathComponent() // Theme
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // ios
        .deletingLastPathComponent() // apps
        .deletingLastPathComponent() // the repository

    static func url(_ path: String) -> URL {
        root.appendingPathComponent(path)
    }
}
