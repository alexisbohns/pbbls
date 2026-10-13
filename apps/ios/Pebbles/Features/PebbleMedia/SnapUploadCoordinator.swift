import Foundation
import PhotosUI
import SwiftUI
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

    /// Capture date read from the picked photo's EXIF, before `ImagePipeline`
    /// strips it (D7). The record flow seeds its `when` step from this; nil
    /// means the step falls back to now. Cleared alongside `processedForRetry`.
    private(set) var pickedCaptureDate: Date?

    /// Picks between the tap and the end of their `ingest` (load, re-encode,
    /// upload). During the load and re-encode — seconds for an iCloud or HEIC
    /// original — `formSnap` is still nil or the previous photo, so a form
    /// that read only `formSnap` would save or discard around the new one.
    private var picksInFlight = 0

    /// Where the form stands. Once it is closed (`cancelAndCleanup`) or its
    /// save committed (`markCommitted`), a pick that lands late must leave
    /// `formSnap` alone and take its own uploaded files with it.
    private var lifecycle: Lifecycle = .open

    private enum Lifecycle { case open, closed, committed }

    // MARK: - Derived state

    /// True from a pick's tap until its upload finishes.
    var isProcessingPick: Bool { picksInFlight > 0 }

    /// True while the photo is not ready to save: a pick is still being
    /// read or re-encoded, or the pending snap is uploading. Every form shows
    /// "Photo is still uploading." for it, which is what the user sees either way.
    var isUploading: Bool {
        if isProcessingPick { return true }
        if case .pending(let snap) = formSnap, snap.state == .uploading { return true }
        return false
    }

    /// True when the pending snap's last upload attempt failed.
    var hasFailed: Bool {
        if case .pending(let snap) = formSnap, snap.state == .failed { return true }
        return false
    }

    /// `true` while save should be blocked because a pick is processing, the
    /// snap is mid-upload, or it failed and needs user action.
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

    /// A pick from the sheet-presented `PHPicker` (`CreatePebbleSheet`,
    /// `EditPebbleSheet`), which names the representation it hands over.
    func handlePicked(_ picked: PhotoPickerView.PickedItem, userId: UUID) async {
        Self.logger.notice("handlePicked: started uti=\(picked.uti, privacy: .public)")

        await processPick(userId: userId) {
            do {
                let data = try await Self.loadData(from: picked.itemProvider, uti: picked.uti)
                Self.logger.notice("handlePicked: loaded \(data.count, privacy: .public) bytes")
                return (data, picked.uti)
            } catch {
                Self.logger.error("picker data load failed: \(error.localizedDescription, privacy: .private)")
                return nil
            }
        }
    }

    /// A pick from the record flow's inline `PhotosPicker`. A
    /// `PhotosPickerItem` carries no chosen representation, so the bytes come
    /// first and the format is read back off them.
    func handlePicked(_ item: PhotosPickerItem, userId: UUID) async {
        let advertised = item.supportedContentTypes.map(\.identifier)
        Self.logger.notice("handlePicked(item): started types=\(advertised, privacy: .public)")

        await processPick(userId: userId) {
            let data: Data
            do {
                guard let loaded = try await item.loadTransferable(type: Data.self) else {
                    Self.logger.warning("picker item carried no data representation")
                    return nil
                }
                data = loaded
                Self.logger.notice("handlePicked(item): loaded \(data.count, privacy: .public) bytes")
            } catch {
                Self.logger.error("picker item load failed: \(error.localizedDescription, privacy: .private)")
                return nil
            }

            // Sniffed first; the advertised list is only a fallback for bytes
            // ImageIO cannot identify, and it still has to pass the same gate.
            let candidate = ImageFormatValidator.uti(of: data) ?? advertised.first ?? ""
            guard ImageFormatValidator.isSupported(candidate) else {
                Self.logger.warning("unsupported picked format: \(candidate, privacy: .public)")
                return nil
            }
            return (data, candidate)
        }
    }

    /// Both pickers' path: counts the pick as in flight from the tap, before
    /// its bytes arrive, then hands them to `ingest`. `load` returns nil when
    /// the pick cannot be used (already logged).
    func processPick(userId: UUID, load: () async -> (data: Data, uti: String)?) async {
        picksInFlight += 1
        defer { picksInFlight -= 1 }
        guard let loaded = await load() else { return }
        await ingest(loaded.data, uti: loaded.uti, userId: userId)
    }

    /// Shared pick path: capture date → re-encode → replace → upload.
    /// `uti` has already passed `ImageFormatValidator`.
    func ingest(_ data: Data, uti: String, userId: UUID) async {
        picksInFlight += 1
        defer { picksInFlight -= 1 }
        guard isAcceptingPicks else { return }

        // Read the capture date before the pipeline strips metadata (D7).
        pickedCaptureDate = ExifCaptureDate.from(data)

        let processed: ProcessedImage
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(data, uti: uti)
            }.value
        } catch {
            Self.logger.error("image pipeline failed: \(String(describing: error), privacy: .public)")
            return
        }

        // The form closed, saved, or dropped this pick while it was being
        // read: nothing is attached, replaced or deleted.
        guard isAcceptingPicks else { return }

        // A saved photo replaced by a new pick: its row goes with the next
        // save's whole-set `snaps`, its files after that save (R6).
        if case .existing(_, let storagePath) = formSnap {
            detachedStoragePaths.append(storagePath)
        }

        // The inline picker makes "actually, that one" a single tap, so the
        // snap being replaced has to take its Storage bytes with it. An upload
        // still in flight for that id is left to finish — a narrow window, and
        // the id guard in `applyStateIfSnapMatches` keeps its result off the
        // new snap.
        if case .pending(let replaced) = formSnap {
            Self.logger.notice("replacing pending snap \(replaced.id.uuidString, privacy: .public)")
            Task { await self.repo.deleteFiles(snapId: replaced.id, userId: userId) }
        }

        let snapId = UUID()
        formSnap = .pending(AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading))
        processedForRetry = processed

        await performUpload(processed: processed, snapId: snapId, userId: userId)

        // Closed or committed while uploading: these files belong to no saved
        // moment, so they go, and the form never shows them again.
        if lifecycle != .open {
            let state = String(describing: lifecycle)
            let id = snapId.uuidString
            Self.logger.notice("late upload (\(state, privacy: .public)): deleting \(id, privacy: .public)")
            if case .pending(let snap) = formSnap, snap.id == snapId {
                formSnap = nil
                processedForRetry = nil
            }
            await repo.deleteFiles(snapId: snapId, userId: userId)
        }
    }

    /// False once the form closed or committed, or when the pick's task was
    /// cancelled (the view drops it on Discard).
    private var isAcceptingPicks: Bool {
        if lifecycle != .open {
            Self.logger.notice("pick ignored: form is \(String(describing: self.lifecycle), privacy: .public)")
            return false
        }
        if Task.isCancelled {
            Self.logger.notice("pick ignored: its task was cancelled")
            return false
        }
        return true
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
        pickedCaptureDate = nil
        await repo.deleteFiles(snapId: snapId, userId: userId)
    }

    /// Storage prefixes of saved photos the user removed or replaced in this
    /// form. Their rows go when the save sends the whole `snaps` set (R6);
    /// their files go in `purgeDetached(_:)`, after that save succeeds.
    private(set) var detachedStoragePaths: [String] = []

    /// Remove the current `.existing` photo from the form. No RPC: the next
    /// `save_moment` sends `snaps: []` and the server enforces "a name or a
    /// photo" in that same write. No-op unless the current snap is `.existing`.
    func detachExisting() {
        guard case .existing(_, let storagePath) = formSnap else { return }
        detachedStoragePaths.append(storagePath)
        formSnap = nil
    }

    /// Best-effort file cleanup for the detached photos a save sent (the paths
    /// snapshotted when its payload was built). Removes exactly those paths from
    /// the list, so a photo detached while the save was in flight survives for
    /// the next one. Call only after the save that dropped their rows succeeded;
    /// the orphan sweep is the backstop.
    func purgeDetached(_ paths: [String]) async {
        detachedStoragePaths.removeAll { paths.contains($0) }
        for path in paths {
            await repo.deleteFiles(storagePrefix: path)
        }
    }

    // MARK: - Sheet lifecycle

    /// Called by the sheet's Cancel button before `dismiss()`. Captures the
    /// current pending snap id, clears state, and *awaits* the compensating
    /// Storage delete. Storage-delete failures are logged but do not throw —
    /// Cancel must not be un-cancellable.
    func cancelAndCleanup(userId: UUID) async {
        // A pick still loading must not land on a closed form.
        lifecycle = .closed
        // A cancelled form keeps its saved photo.
        detachedStoragePaths = []
        // Capture id before clearing so the delete still has the address.
        guard case .pending(let snap) = formSnap else {
            formSnap = nil
            processedForRetry = nil
            pickedCaptureDate = nil
            return
        }
        let snapId = snap.id
        formSnap = nil
        processedForRetry = nil
        pickedCaptureDate = nil
        await repo.deleteFiles(snapId: snapId, userId: userId)
    }

    /// Called by the form after its save succeeded: the attached photo now
    /// belongs to a saved moment, so a pick that lands late must neither
    /// replace it nor delete its files.
    func markCommitted() {
        lifecycle = .committed
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
