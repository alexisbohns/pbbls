#if DEBUG
import CoreGraphics
import Foundation
import ImageIO
import Observation
import UniformTypeIdentifiers
import os

struct LabLook: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    var name: String
    var createdAt: Date
    var params: FilterParams
    /// Vision's aesthetics score of this look's render; nil before iOS 18.
    var aesthetics: Double?

    private enum CodingKeys: String, CodingKey {
        case id, name, createdAt, params, aesthetics
    }

    /// `aesthetics` is written as an explicit null when absent, so every look in
    /// `looks.json` has the same keys for the analysis.
    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encode(createdAt, forKey: .createdAt)
        try container.encode(params, forKey: .params)
        try container.encode(aesthetics, forKey: .aesthetics)
    }

    /// Whole seconds: the narrowest precision every reader accepts, so a reload compares equal.
    static func timestamp(_ date: Date = .now) -> Date {
        Date(timeIntervalSince1970: date.timeIntervalSince1970.rounded(.down))
    }
}

struct LabPhoto: Codable, Identifiable, Equatable, Sendable {
    let id: UUID
    var file: String
    var width: Int
    var height: Int
    var looks: [LabLook]
}

/// `looks.json` (spec "The export").
struct LabDocument: Codable, Equatable, Sendable {
    var version = 1
    var photos: [LabPhoto] = []
}

/// `measures.json`, keyed by photo id.
struct LabMeasuresDocument: Codable, Equatable, Sendable {
    var version = 1
    var measures: [String: PhotoMeasures] = [:]
}

enum FilterLabError: Error {
    case writeFailed(String)
    case renderFailed(String)
    case zipFailed
}

/// The lab's one session on disk: `photos/<id>.jpg`, `looks.json`, `measures.json`.
/// Never leaves the device except through the export.
@MainActor
@Observable
final class FilterLabStore {
    private(set) var document = LabDocument()
    private(set) var measures = LabMeasuresDocument()
    let root: URL

    nonisolated private static let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-lab-store")

    nonisolated static var defaultRoot: URL {
        URL.documentsDirectory.appending(path: "FilterLab/current", directoryHint: .isDirectory)
    }

    init(root: URL = FilterLabStore.defaultRoot) {
        self.root = root
        document = Self.read(LabDocument.self, at: root.appending(path: "looks.json")) ?? LabDocument()
        measures = Self.read(LabMeasuresDocument.self, at: root.appending(path: "measures.json"))
            ?? LabMeasuresDocument()
    }

    func add(_ image: CGImage, measures photoMeasures: PhotoMeasures) throws -> LabPhoto {
        let id = UUID()
        let file = "photos/\(id.uuidString).jpg"
        do {
            try prepareRoot()
            try FileManager.default.createDirectory(at: root.appending(path: "photos"),
                                                    withIntermediateDirectories: true)
            try Self.writeJPEG(image, to: root.appending(path: file))
        } catch {
            Self.logger.error("could not add a photo: \(error.localizedDescription, privacy: .public)")
            throw error
        }
        let photo = LabPhoto(id: id, file: file, width: image.width, height: image.height, looks: [])
        document.photos.append(photo)
        measures.measures[id.uuidString] = photoMeasures
        try persist()
        return photo
    }

    func image(for photo: LabPhoto) -> CGImage? {
        Self.loadImage(at: root.appending(path: photo.file))
    }

    func looks(of photoID: UUID) -> [LabLook] {
        document.photos.first { $0.id == photoID }?.looks ?? []
    }

    /// Inserts the look, or replaces the one with the same id.
    func save(_ look: LabLook, in photoID: UUID) throws {
        guard let p = document.photos.firstIndex(where: { $0.id == photoID }) else { return }
        if let l = document.photos[p].looks.firstIndex(where: { $0.id == look.id }) {
            document.photos[p].looks[l] = look
        } else {
            document.photos[p].looks.append(look)
        }
        try persist()
    }

    func deleteLook(_ lookID: UUID, in photoID: UUID) throws {
        guard let p = document.photos.firstIndex(where: { $0.id == photoID }) else { return }
        document.photos[p].looks.removeAll { $0.id == lookID }
        try persist()
    }

    func deletePhoto(_ photoID: UUID) throws {
        guard let photo = document.photos.first(where: { $0.id == photoID }) else { return }
        // The documents go first: a crash in between leaves an orphan jpg, never a
        // photo the session lists but cannot show.
        document.photos.removeAll { $0.id == photoID }
        measures.measures[photoID.uuidString] = nil
        try persist()
        do {
            try FileManager.default.removeItem(at: root.appending(path: photo.file))
        } catch CocoaError.fileNoSuchFile {
            Self.logger.debug("photo file already gone: \(photo.file, privacy: .public)")
        } catch {
            let reason = error.localizedDescription
            Self.logger.error("could not remove \(photo.file, privacy: .public): \(reason, privacy: .public)")
            throw error
        }
    }

    /// Deletes the whole session.
    func reset() throws {
        if FileManager.default.fileExists(atPath: root.path(percentEncoded: false)) {
            do {
                try FileManager.default.removeItem(at: root)
            } catch {
                Self.logger.error("could not reset the session: \(error.localizedDescription, privacy: .public)")
                throw error
            }
        }
        document = LabDocument()
        measures = LabMeasuresDocument()
    }

    /// Creates the root and keeps it out of iCloud and device backups: the photos
    /// are the maintainer's own and must not leave the device except by the export.
    private func prepareRoot() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        var url = root
        do {
            try url.setResourceValues(values)
        } catch {
            Self.logger.error("could not exclude the lab from backups: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Measures first: a photo with measures and no look is harmless, the reverse is a lookup miss.
    private func persist() throws {
        do {
            try prepareRoot()
            let encoder = Self.makeEncoder()
            try encoder.encode(measures).write(to: root.appending(path: "measures.json"), options: .atomic)
            try encoder.encode(document).write(to: root.appending(path: "looks.json"), options: .atomic)
        } catch {
            Self.logger.error("could not save the session: \(error.localizedDescription, privacy: .public)")
            throw error
        }
    }

    nonisolated static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }

    nonisolated static func read<T: Decodable>(_ type: T.Type, at url: URL) -> T? {
        guard FileManager.default.fileExists(atPath: url.path(percentEncoded: false)) else { return nil }
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode(type, from: Data(contentsOf: url))
        } catch {
            let name = url.lastPathComponent
            let reason = error.localizedDescription
            logger.error("lab file unreadable \(name, privacy: .public): \(reason, privacy: .public)")
            setAside(url)
            return nil
        }
    }

    /// Moves an unreadable file to `<name>.bad`, so the next save cannot silently destroy the old session.
    nonisolated private static func setAside(_ url: URL) {
        let fm = FileManager.default
        let bad = url.appendingPathExtension("bad")
        do {
            if fm.fileExists(atPath: bad.path(percentEncoded: false)) { try fm.removeItem(at: bad) }
            try fm.moveItem(at: url, to: bad)
        } catch {
            let name = url.lastPathComponent
            let reason = error.localizedDescription
            logger.error("could not set \(name, privacy: .public) aside: \(reason, privacy: .public)")
        }
    }

    nonisolated static func loadImage(at url: URL) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            logger.error("lab photo unreadable: \(url.lastPathComponent, privacy: .public)")
            return nil
        }
        return image
    }

    nonisolated static func writeJPEG(_ image: CGImage, to url: URL, quality: CGFloat = 0.92) throws {
        guard let destination = CGImageDestinationCreateWithURL(url as CFURL, UTType.jpeg.identifier as CFString,
                                                                1, nil) else {
            logger.error("jpeg destination unavailable for \(url.lastPathComponent, privacy: .public)")
            throw FilterLabError.writeFailed(url.lastPathComponent)
        }
        CGImageDestinationAddImage(destination, image,
                                   [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            logger.error("jpeg write failed for \(url.lastPathComponent, privacy: .public)")
            throw FilterLabError.writeFailed(url.lastPathComponent)
        }
    }
}
#endif
