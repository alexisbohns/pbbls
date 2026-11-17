#if DEBUG
import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// Serialised: every export works in the one `FilterLabExport` folder and wipes it first.
@Suite("FilterLabExport", .serialized)
@MainActor
struct FilterLabExportTests {
    typealias Img = FilterTestImages

    private static let exportDate = ISO8601DateFormatter().date(from: "2026-11-17T12:00:00Z") ?? .now

    private struct Session {
        let store: FilterLabStore
        let photo: LabPhoto
        let look: LabLook
    }

    private func session() throws -> Session {
        let store = FilterLabStore(root: FilterLabStoreTests.tempRoot())
        let photo = try store.add(Img.ramp(size: 64), measures: FilterLabStoreTests.measures)
        var params = FilterParams.neutral
        params.grain = 0.5
        let look = LabLook(id: UUID(), name: "grainy", createdAt: LabLook.timestamp(), params: params, aesthetics: nil)
        try store.save(look, in: photo.id)
        return Session(store: store, photo: photo, look: look)
    }

    @Test("the staged folder holds the inputs, both documents and one render per look")
    func staged() throws {
        let s = try session()
        let staging = try FilterLabExport.stage(document: s.store.document, measures: s.store.measures,
                                                root: s.store.root, date: Self.exportDate)
        for path in ["looks.json", "measures.json", s.photo.file,
                     "renders/\(s.photo.id.uuidString)/\(s.look.id.uuidString).jpg"] {
            let file = staging.appending(path: path)
            let exists = FileManager.default.fileExists(atPath: file.path(percentEncoded: false))
            #expect(exists, "missing \(path)")
        }
    }

    @Test("the zip is the only thing left, and names every render")
    func zipped() throws {
        let s = try session()
        let zip = try FilterLabExport.makeZip(document: s.store.document, measures: s.store.measures,
                                              root: s.store.root, date: Self.exportDate)
        #expect(zip.lastPathComponent == "filter-lab-2026-11-17.zip")
        #expect(zip.deletingLastPathComponent() == FilterLabExport.workDirectory)

        let bytes = try Data(contentsOf: zip)
        #expect(!bytes.isEmpty)
        // Central-directory names are stored uncompressed.
        let name = Data("renders/\(s.photo.id.uuidString)/\(s.look.id.uuidString).jpg".utf8)
        #expect(bytes.range(of: name) != nil, "the zip does not name the render")

        let work = FilterLabExport.workDirectory.path(percentEncoded: false)
        let leftovers = try FileManager.default.contentsOfDirectory(atPath: work)
        #expect(leftovers == ["filter-lab-2026-11-17.zip"])
    }

    @Test("a grey photo still exports")
    func greyPhoto() throws {
        let store = FilterLabStore(root: FilterLabStoreTests.tempRoot())
        let photo = try store.add(Img.ramp(size: 64), measures: FilterLabStoreTests.measures)
        // Replace the stored jpg with a one-channel picture.
        let grey = try #require(CGContext(data: nil, width: 32, height: 32, bitsPerComponent: 8, bytesPerRow: 32,
                                          space: CGColorSpaceCreateDeviceGray(),
                                          bitmapInfo: CGImageAlphaInfo.none.rawValue)?.makeImage())
        try FilterLabStore.writeJPEG(grey, to: store.root.appending(path: photo.file))
        let look = LabLook(id: UUID(), name: "any", createdAt: LabLook.timestamp(), params: .neutral, aesthetics: nil)
        try store.save(look, in: photo.id)
        let staging = try FilterLabExport.stage(document: store.document, measures: store.measures,
                                                root: store.root, date: Self.exportDate)
        let render = staging.appending(path: "renders/\(photo.id.uuidString)/\(look.id.uuidString).jpg")
        #expect(FileManager.default.fileExists(atPath: render.path(percentEncoded: false)))
    }
}
#endif
