import Foundation
import Supabase
import UIKit
import os

/// Home's camera moment: the photo uploads, then one `save_moment` records a
/// nameless moment at the shutter time. Nothing is saved when the upload fails.
@Observable @MainActor
final class CameraMomentCoordinator {

    struct LandedMoment: Identifiable, Equatable {
        let momentId: UUID
        let shutter: Date
        let thumbnail: UIImage?
        var id: UUID { momentId }
    }

    private(set) var landed: LandedMoment?
    private(set) var error: String?
    private(set) var isSaving = false

    private let client: SupabaseClient
    private let userId: UUID
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.camera")

    init(client: SupabaseClient, userId: UUID) {
        self.client = client
        self.userId = userId
    }

    /// The payload: no name, the shutter floored to whole seconds.
    static func payload(shutter: Date, snaps: [SaveMomentPayload.Snap]) -> SaveMomentPayload {
        var payload = SaveMomentPayload()
        payload.happenedAt = Date(timeIntervalSince1970: shutter.timeIntervalSince1970.rounded(.down))
        payload.snaps = snaps.isEmpty ? nil : snaps
        return payload
    }

    func capture(_ data: Data, at shutter: Date) async {
        guard !isSaving else { return }
        isSaving = true
        error = nil
        defer { isSaving = false }

        let snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: client))
        await snaps.handleCaptured(data, userId: userId)

        // handleCaptured returns once the upload settled; the poll only covers
        // an upload still in flight (~30 s at most).
        var waited = 0
        while snaps.isUploading && waited < 300 {
            try? await Task.sleep(for: .milliseconds(100))
            waited += 1
        }

        guard case .pending(let snap) = snaps.formSnap, snap.state == .uploaded else {
            logger.error("camera upload did not finish; nothing saved")
            await snaps.cancelAndCleanup(userId: userId)
            error = String(localized: "home.camera.uploadFailed")
            return
        }

        snaps.beginCommit()
        let payload = Self.payload(
            shutter: shutter, snaps: SaveMomentPayload.snaps(for: snaps.formSnap, userId: userId))
        do {
            let id = try await MomentsService(client: client).saveMoment(payload)
            snaps.markCommitted()
            landed = LandedMoment(momentId: id, shutter: shutter, thumbnail: UIImage(data: snap.localThumb))
        } catch {
            logger.error("camera save_moment failed: \(error.localizedDescription, privacy: .private)")
            snaps.abortCommit()
            await snaps.cancelAndCleanup(userId: userId)
            self.error = String(localized: "home.quick.error")
        }
    }
}
