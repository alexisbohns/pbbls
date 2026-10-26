import Foundation
import UIKit
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

@Suite("Camera moment coordinator") @MainActor
struct CameraMomentCoordinatorTests {
    private struct Boom: Error {}

    private func image() -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).image { ctx in
            UIColor.red.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
    }

    @Test func failedUploadLandsNothingAndSavesNothing() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        repo.uploadResults = [.failure(Boom()), .failure(Boom())]
        var saved = 0
        let coordinator = CameraMomentCoordinator(
            userId: UUID(),
            makeSnaps: { SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1)) },
            save: { _ in saved += 1; return UUID() })

        await coordinator.capture(image(), at: .now)

        #expect(coordinator.landed == nil)
        #expect(coordinator.error == String(localized: "home.camera.uploadFailed"))
        #expect(saved == 0)
        #expect(!coordinator.isSaving)
    }
}
