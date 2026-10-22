import Foundation
import Testing
@testable import Pebbles

/// #1146 D7: one host for every caller; the scene keeps the card's moment and
/// landing face; a restore reopens it at rest on that face, owned by no list
/// (decision 3); sign-out forgets it.
@Suite("HeldCardHostModel")
@MainActor
struct HeldCardHostModelTests {
    private let id = UUID(uuidString: "0b7c3f5e-2d41-4a8e-9f6b-1c2d3e4f5a6b") ?? UUID()

    @Test("opening holds the request and snapshots its face")
    func open() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, face: .verso, origin: .path))
        #expect(host.request?.momentId == id)
        #expect(host.snapshot == HeldCardSnapshot(momentId: id, face: .verso))
    }

    @Test("a landing face is written to the snapshot")
    func land() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.land(.verso)
        #expect(host.snapshot?.face == .verso)
    }

    @Test("closing forgets the card")
    func close() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.close()
        #expect(host.request == nil && host.snapshot == nil)
    }

    @Test("a restore reopens the card on its saved face, owned by no list")
    func restore() {
        let host = HeldCardHostModel()
        host.restore(HeldCardSnapshot(momentId: id, face: .verso))
        #expect(host.request?.momentId == id)
        #expect(host.request?.face == .verso)
        #expect(host.request?.origin == .you)
        #expect(host.request?.seed == nil)
    }

    @Test("a restore never replaces a card already open")
    func restoreOverOpen() {
        let host = HeldCardHostModel()
        let other = UUID()
        host.open(HeldCardRequest(momentId: other, origin: .path))
        host.restore(HeldCardSnapshot(momentId: id, face: .verso))
        #expect(host.request?.momentId == other)
    }

    @Test("sign-out resets it")
    func reset() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: id, origin: .path))
        host.reset()
        #expect(host.request == nil && host.snapshot == nil)
    }
}
