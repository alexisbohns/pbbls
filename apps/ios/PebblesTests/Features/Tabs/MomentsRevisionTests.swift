import Testing
@testable import Pebbles

/// #903 D5: a moment changed on one tab tells the others.
@Suite("MomentsRevision")
@MainActor
struct MomentsRevisionTests {
    @Test("starts at zero with no origin, so nothing reads as foreign")
    func initial() {
        let revision = MomentsRevision()
        #expect(revision.value == 0)
        #expect(revision.lastOrigin == nil)
        #expect(!revision.changedOutside(.path))
    }

    @Test("a bump counts up and records where it came from")
    func bump() {
        let revision = MomentsRevision()
        revision.bump(from: .people)
        revision.bump(from: .collections)
        #expect(revision.value == 2)
        #expect(revision.lastOrigin == .collections)
    }

    @Test("Path ignores its own bumps and reacts to another tab's")
    func foreign() {
        let revision = MomentsRevision()
        revision.bump(from: .path)
        #expect(!revision.changedOutside(.path))
        revision.bump(from: .people)
        #expect(revision.changedOutside(.path))
    }
}
