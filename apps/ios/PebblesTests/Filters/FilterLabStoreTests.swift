#if DEBUG
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("FilterLabStore")
@MainActor
struct FilterLabStoreTests {
    typealias Img = FilterTestImages

    static func tempRoot() -> URL {
        FileManager.default.temporaryDirectory.appending(path: "filter-lab-tests/\(UUID().uuidString)")
    }

    static let measures = PhotoMeasures(
        luminance: [0.1, 0.3, 0.5, 0.7, 0.9], meanSaturation: 0.2, castRedGreen: 0, castYellowBlue: 0,
        faceCount: 1, scenes: [.init(identifier: "beach", confidence: 0.8)], hasDepth: false,
        aesthetics: nil, autoEnhance: [])

    @Test("a photo and a look survive a reload")
    func reload() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        let photo = try store.add(Img.ramp(size: 64), measures: Self.measures)
        var params = FilterParams.neutral
        params.exposure = 0.2
        let look = LabLook(id: UUID(), name: "warm", createdAt: LabLook.timestamp(), params: params, aesthetics: 0.1)
        try store.save(look, in: photo.id)

        let reloaded = FilterLabStore(root: root)
        #expect(reloaded.document == store.document)
        #expect(reloaded.measures.measures[photo.id.uuidString] == Self.measures)
        #expect(reloaded.image(for: photo)?.width == 64)
    }

    @Test("saving a look with a known id replaces it")
    func update() throws {
        let store = FilterLabStore(root: Self.tempRoot())
        let photo = try store.add(Img.ramp(size: 32), measures: Self.measures)
        var look = LabLook(id: UUID(), name: "a", createdAt: LabLook.timestamp(), params: .neutral, aesthetics: nil)
        try store.save(look, in: photo.id)
        look.name = "b"
        try store.save(look, in: photo.id)
        #expect(store.document.photos[0].looks.map(\.name) == ["b"])
    }

    @Test("deleting a photo removes its file and measures")
    func deletePhoto() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        let photo = try store.add(Img.ramp(size: 32), measures: Self.measures)
        try store.deletePhoto(photo.id)
        #expect(store.document.photos.isEmpty)
        #expect(store.measures.measures.isEmpty)
        #expect(!FileManager.default.fileExists(atPath: root.appending(path: photo.file).path(percentEncoded: false)))
    }

    @Test("the session root is kept out of backups")
    func excludedFromBackup() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        _ = try store.add(Img.ramp(size: 32), measures: Self.measures)
        let values = try root.resourceValues(forKeys: [.isExcludedFromBackupKey])
        #expect(values.isExcludedFromBackup == true)
    }

    @Test("a look without an aesthetics score writes an explicit null")
    func explicitNull() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        let photo = try store.add(Img.ramp(size: 32), measures: Self.measures)
        let look = LabLook(id: UUID(), name: "plain", createdAt: LabLook.timestamp(), params: .neutral, aesthetics: nil)
        try store.save(look, in: photo.id)
        let json = try String(contentsOf: root.appending(path: "looks.json"), encoding: .utf8)
        #expect(json.contains("\"aesthetics\" : null"))
    }

    @Test("deleting a photo whose file is already gone still succeeds")
    func deleteMissingFile() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        let photo = try store.add(Img.ramp(size: 32), measures: Self.measures)
        try FileManager.default.removeItem(at: root.appending(path: photo.file))
        try store.deletePhoto(photo.id)
        #expect(store.document.photos.isEmpty)
        #expect(FilterLabStore(root: root).document.photos.isEmpty)
    }

    @Test("an unreadable looks file is set aside, not overwritten")
    func unreadableSetAside() throws {
        let root = Self.tempRoot()
        let store = FilterLabStore(root: root)
        _ = try store.add(Img.ramp(size: 32), measures: Self.measures)
        let looksURL = root.appending(path: "looks.json")
        try Data("{ not json".utf8).write(to: looksURL)

        let reloaded = FilterLabStore(root: root)
        #expect(reloaded.document.photos.isEmpty)
        let bad = root.appending(path: "looks.json.bad")
        #expect(try String(contentsOf: bad, encoding: .utf8) == "{ not json")
        #expect(!FileManager.default.fileExists(atPath: looksURL.path(percentEncoded: false)))
    }
}
#endif
