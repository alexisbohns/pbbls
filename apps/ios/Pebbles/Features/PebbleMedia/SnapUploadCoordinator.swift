import Foundation
import os

/// Single source of truth for the snap (photo) attached to an in-progress
/// pebble form. Owns `formSnap` (the form-layer state), `processedForRetry`
/// (re-encoded bytes kept around for a user-tapped retry), and every
/// transition: pick → upload → retry → cancel → remove.
///
/// Both `CreatePebbleSheet` and `EditPebbleSheet` hold one of these and
/// delegate. All Storage side-effects are explicitly awaited — there is no
/// sleep-based race in `cancelAndCleanup`.
@MainActor
@Observable
final class SnapUploadCoordinator {

    // MARK: - State (publicly readable, mutated only by methods on this type)

    private(set) var formSnap: FormSnap?

    /// Processed bytes kept around so a `.failed → user-tapped retry` doesn't
    /// re-load + re-encode the original. Cleared whenever `formSnap` is
    /// cleared.
    private(set) var processedForRetry: ProcessedImage?

    // MARK: - Derived state

    /// True while the pending snap is still uploading.
    var isUploading: Bool {
        if case .pending(let snap) = formSnap, snap.state == .uploading { return true }
        return false
    }

    /// True when the pending snap's last upload attempt failed.
    var hasFailed: Bool {
        if case .pending(let snap) = formSnap, snap.state == .failed { return true }
        return false
    }

    /// `true` while save should be blocked because the snap is mid-upload
    /// or in a failed state requiring user action.
    var isBlocking: Bool { isUploading || hasFailed }

    /// Returns the pending `AttachedSnap` only when it has fully uploaded —
    /// suitable for embedding in a save payload. Returns `nil` for `.none`,
    /// `.existing` (those are encoded directly from `formSnap`), or any
    /// in-flight/failed `.pending` state.
    func pendingSnapForPayload() -> AttachedSnap? {
        if case .pending(let snap) = formSnap, snap.state == .uploaded { return snap }
        return nil
    }

    // MARK: - Init

    private let repo: PebbleSnapRepositoryProtocol
    private let retryDelay: Duration
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-coordinator")

    init(
        repo: PebbleSnapRepositoryProtocol,
        initialSnap: FormSnap? = nil,
        retryDelay: Duration = .seconds(2)
    ) {
        self.repo = repo
        self.formSnap = initialSnap
        self.retryDelay = retryDelay
    }

    /// Set the initial snap *after* construction. Used by `EditPebbleSheet`
    /// which has to wait for `load()` to finish before it knows whether the
    /// pebble has an existing snap.
    func seedExisting(_ snap: FormSnap?) {
        self.formSnap = snap
    }

    // MARK: - Test seam

    #if DEBUG
    /// Test-only: seed `processedForRetry` directly so tests can exercise
    /// the upload path without driving the real `handlePicked` flow.
    func seedProcessedForRetry(_ processed: ProcessedImage) {
        self.processedForRetry = processed
    }
    #endif

    // MARK: - User-driven transitions

    func handlePicked(_ picked: PhotoPickerView.PickedItem, userId: UUID) async {
        Self.logger.notice("handlePicked: started uti=\(picked.uti, privacy: .public)")

        let data: Data
        do {
            data = try await Self.loadData(from: picked.itemProvider, uti: picked.uti)
            Self.logger.notice("handlePicked: loaded \(data.count, privacy: .public) bytes")
        } catch {
            Self.logger.error("picker data load failed: \(error.localizedDescription, privacy: .private)")
            return
        }

        let processed: ProcessedImage
        let uti = picked.uti
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(data, uti: uti)
            }.value
        } catch {
            Self.logger.error("image pipeline failed: \(String(describing: error), privacy: .public)")
            return
        }

        let snapId = UUID()
        formSnap = .pending(AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading))
        processedForRetry = processed

        await performUpload(processed: processed, snapId: snapId, userId: userId)
    }

    /// Re-run the upload after a user tap on the chip's retry button. Only
    /// valid when the current state is `.pending(.failed)` and we still hold
    /// `processedForRetry`. No-op otherwise.
    func retryCurrent(userId: UUID) async {
        guard case .pending(var snap) = formSnap, let processed = processedForRetry else { return }
        snap.state = .uploading
        formSnap = .pending(snap)
        await performUpload(processed: processed, snapId: snap.id, userId: userId)
    }

    /// Remove the current `.pending` snap and fire the compensating Storage
    /// delete. Safe to call when the snap is in any state. No-op when the
    /// current `formSnap` is not `.pending`.
    func removePending(userId: UUID) async {
        guard case .pending(let snap) = formSnap else { return }
        let snapId = snap.id
        formSnap = nil
        processedForRetry = nil
        await repo.deleteFiles(snapId: snapId, userId: userId)
    }

    /// Remove the current `.existing` snap. Calls `delete_pebble_media` and
    /// then fires fire-and-forget Storage cleanup. Throws on RPC failure so
    /// the sheet can surface a user-facing error. No-op (returns normally)
    /// when the current `formSnap` is not `.existing`.
    func removeExisting() async throws {
        guard case .existing(let id, _) = formSnap else { return }
        let storagePath = try await repo.deletePebbleMedia(snapId: id)
        // Fire-and-forget Storage cleanup. The live repo logs on failure.
        await repo.deleteFiles(storagePrefix: storagePath)
        formSnap = nil
    }

    // MARK: - Sheet lifecycle

    /// Called by the sheet's Cancel button before `dismiss()`. Captures the
    /// current pending snap id, clears state, and *awaits* the compensating
    /// Storage delete. Storage-delete failures are logged but do not throw —
    /// Cancel must not be un-cancellable.
    func cancelAndCleanup(userId: UUID) async {
        // Capture id before clearing so the delete still has the address.
        guard case .pending(let snap) = formSnap else {
            formSnap = nil
            processedForRetry = nil
            return
        }
        let snapId = snap.id
        formSnap = nil
        processedForRetry = nil
        await repo.deleteFiles(snapId: snapId, userId: userId)
    }

    /// Called by the sheet after a save (compose-pebble / compose-pebble-update)
    /// failure when a pending snap is attached. Awaits the compensating
    /// Storage delete. Does *not* clear `formSnap` — the sheet stays open
    /// and the user can retry. Use `cancelAndCleanup` if you also want to
    /// dismiss.
    func handleSaveFailure(userId: UUID) async {
        guard case .pending(let snap) = formSnap else { return }
        await repo.deleteFiles(snapId: snap.id, userId: userId)
        // Intentionally do not clear formSnap — caller (the sheet) keeps the
        // form open so the user can retry.
    }

    // MARK: - Private

    /// Single upload path used by both `handlePicked` (initial attempt) and
    /// `retryCurrent` (user-tapped retry). On first failure, sleeps for
    /// `retryDelay` and retries once. On second failure, transitions the
    /// snap to `.failed` and retains `processedForRetry`.
    private func performUpload(processed: ProcessedImage, snapId: UUID, userId: UUID) async {
        do {
            try await repo.uploadProcessed(processed, snapId: snapId, userId: userId)
            applyStateIfSnapMatches(snapId: snapId, newState: .uploaded)
        } catch {
            Self.logger.warning(
                "snap upload failed (first attempt): \(error.localizedDescription, privacy: .private)"
            )
            try? await Task.sleep(for: retryDelay)
            do {
                try await repo.uploadProcessed(processed, snapId: snapId, userId: userId)
                applyStateIfSnapMatches(snapId: snapId, newState: .uploaded)
            } catch {
                Self.logger.error(
                    "snap upload failed (retry): \(error.localizedDescription, privacy: .private)"
                )
                applyStateIfSnapMatches(snapId: snapId, newState: .failed)
            }
        }
    }

    /// Mutates `formSnap`'s `.pending` state only if the current snap id
    /// still matches. Guards against the user removing the snap mid-upload.
    private func applyStateIfSnapMatches(snapId: UUID, newState: AttachedSnap.UploadState) {
        guard case .pending(var snap) = formSnap, snap.id == snapId else { return }
        snap.state = newState
        formSnap = .pending(snap)
    }

    /// Loads the bytes for a `PHPicker` selection. Bridges the callback-based
    /// `loadDataRepresentation` API into async/await.
    private static func loadData(from provider: NSItemProvider, uti: String) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: uti) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: URLError(.cannotDecodeContentData))
                }
            }
        }
    }
}
