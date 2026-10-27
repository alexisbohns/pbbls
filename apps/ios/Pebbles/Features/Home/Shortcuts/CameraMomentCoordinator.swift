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

    private let userId: UUID
    private let makeSnaps: () -> SnapUploadCoordinator
    private let save: (SaveMomentPayload) async throws -> UUID
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home.camera")

    /// `makeSnaps` yields a fresh upload coordinator per capture, so one
    /// capture's lifecycle never leaks into the next.
    init(userId: UUID,
         makeSnaps: @escaping () -> SnapUploadCoordinator,
         save: @escaping (SaveMomentPayload) async throws -> UUID) {
        self.userId = userId
        self.makeSnaps = makeSnaps
        self.save = save
    }

    convenience init(client: SupabaseClient, userId: UUID) {
        self.init(
            userId: userId,
            makeSnaps: { SnapUploadCoordinator(repo: PebbleSnapRepository(client: client)) },
            save: { try await MomentsService(client: client).saveMoment($0) })
    }

    /// Clears the landed moment once its toast is gone.
    func clearLanded() { landed = nil }

    /// The payload: no name, the shutter floored to whole seconds.
    static func payload(shutter: Date, snaps: [SaveMomentPayload.Snap]) -> SaveMomentPayload {
        var payload = SaveMomentPayload()
        payload.happenedAt = Date(timeIntervalSince1970: shutter.timeIntervalSince1970.rounded(.down))
        payload.snaps = snaps.isEmpty ? nil : snaps
        return payload
    }

    func capture(_ image: UIImage, at shutter: Date) async {
        guard !isSaving else { return }
        isSaving = true
        error = nil
        landed = nil
        defer { isSaving = false }

        // Off the main thread: a full-size JPEG encode is long enough to hitch the UI.
        let data = await Task.detached(priority: .userInitiated) { image.jpegData(compressionQuality: 0.9) }.value
        let snaps = makeSnaps()
        if let data { await snaps.handleCaptured(data, userId: userId) }

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
            let id = try await save(payload)
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
