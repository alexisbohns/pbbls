import Foundation
import Testing
@testable import Pebbles

/// #1146 D7: the scene keeps {momentId, landing face, printingFor}; a restore
/// decodes tolerantly, and a value it cannot read is no card, not a crash.
@Suite("HeldCardSnapshot")
struct HeldCardSnapshotTests {
    private let id = UUID(uuidString: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b") ?? UUID()

    @Test("a snapshot round-trips through the scene's data")
    func roundTrip() throws {
        let snapshot = HeldCardSnapshot(momentId: id, face: .verso, printingFor: id)
        let data = try #require(snapshot.encoded)
        #expect(HeldCardSnapshot.decoded(from: data) == snapshot)
    }

    @Test("an unknown face reopens on the recto; a missing print is none")
    func tolerant() throws {
        let data = Data(#"{"momentId": "0B7C3F5E-2D41-4A8E-9F6B-1C2D3E4F5A6B", "face": "edge"}"#.utf8)
        let snapshot = try #require(HeldCardSnapshot.decoded(from: data))
        #expect(snapshot.face == .recto)
        #expect(snapshot.printingFor == nil)
        #expect(snapshot.momentId == id)
    }

    @Test("data that is not a snapshot is no card")
    func garbage() {
        #expect(HeldCardSnapshot.decoded(from: Data("{}".utf8)) == nil)
        #expect(HeldCardSnapshot.decoded(from: Data("not json".utf8)) == nil)
    }

    @Test("a request is identified by its moment")
    func requestIdentity() {
        let request = HeldCardRequest(momentId: id, origin: .path)
        #expect(request.id == id)
        #expect(request.face == .recto && request.seed == nil && request.soulOrder.isEmpty)
    }
}
