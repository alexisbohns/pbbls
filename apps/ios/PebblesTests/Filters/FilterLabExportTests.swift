#if DEBUG
import Foundation
import Testing
@testable import Pebbles

@Suite("FilterLabExport")
@MainActor
struct FilterLabExportTests {
    typealias Img = FilterTestImages

    @Test("the zip holds the inputs, both documents and one render per look")
    func bundle() throws {
        let store = FilterLabStore(root: FilterLabStoreTests.tempRoot())
        let photo = try store.add(Img.ramp(size: 64), measures: FilterLabStoreTests.measures)
        var params = FilterParams.neutral
        params.grain = 0.5
        let look = LabLook(id: UUID(), name: "grainy", createdAt: LabLook.timestamp(), params: params, aesthetics: nil)
        try store.save(look, in: photo.id)

        let date = try #require(ISO8601DateFormatter().date(from: "2026-11-17T12:00:00Z"))
        let zip = try FilterLabExport.makeZip(document: store.document, measures: store.measures,
                                              root: store.root, date: date)
        #expect(zip.lastPathComponent == "filter-lab-2026-11-17.zip")
        let size = try #require(try FileManager.default.attributesOfItem(atPath: zip.path())[.size] as? Int)
        #expect(size > 0)

        let staging = zip.deletingPathExtension()
        for path in ["looks.json", "measures.json", photo.file,
                     "renders/\(photo.id.uuidString)/\(look.id.uuidString).jpg"] {
            #expect(FileManager.default.fileExists(atPath: staging.appending(path: path).path()), "missing \(path)")
        }
    }
}
#endif
