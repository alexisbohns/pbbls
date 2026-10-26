import Foundation
import Testing
@testable import Pebbles

@Suite("Camera moment") @MainActor
struct CameraMomentTests {
    @Test func payloadFloorsShutterAndHasNoName() {
        let payload = CameraMomentCoordinator.payload(
            shutter: Date(timeIntervalSince1970: 1_700_000_000.7), snaps: [])
        #expect(payload.happenedAt == Date(timeIntervalSince1970: 1_700_000_000))
        #expect(payload.name == nil)
        #expect(payload.snaps == nil)
    }

    @Test func payloadCarriesTheSnap() {
        let snap = SaveMomentPayload.Snap(id: UUID(), storagePath: "u/s", sortOrder: 0)
        let payload = CameraMomentCoordinator.payload(shutter: .now, snaps: [snap])
        #expect(payload.snaps == [snap])
    }
}
