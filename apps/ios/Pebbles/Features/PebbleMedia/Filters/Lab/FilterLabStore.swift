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
        try FileManager.default.createDirectory(at: root.appending(path: "photos"), withIntermediateDirectories: true)
        try Self.writeJPEG(image, to: root.appending(path: file))
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
        try FileManager.default.removeItem(at: root.appending(path: photo.file))
        document.photos.removeAll { $0.id == photoID }
        measures.measures[photoID.uuidString] = nil
        try persist()
    }

    /// Deletes the whole session.
    func reset() throws {
        if FileManager.default.fileExists(atPath: root.path()) {
            try FileManager.default.removeItem(at: root)
        }
        document = LabDocument()
        measures = LabMeasuresDocument()
    }

    private func persist() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let encoder = Self.makeEncoder()
        try encoder.encode(document).write(to: root.appending(path: "looks.json"), options: .atomic)
        try encoder.encode(measures).write(to: root.appending(path: "measures.json"), options: .atomic)
    }

    nonisolated static func makeEncoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return encoder
    }

    nonisolated static func read<T: Decodable>(_ type: T.Type, at url: URL) -> T? {
        guard FileManager.default.fileExists(atPath: url.path()) else { return nil }
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            return try decoder.decode(type, from: Data(contentsOf: url))
        } catch {
            let name = url.lastPathComponent
            let reason = error.localizedDescription
            logger.error("lab file unreadable \(name, privacy: .public): \(reason, privacy: .public)")
            return nil
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
