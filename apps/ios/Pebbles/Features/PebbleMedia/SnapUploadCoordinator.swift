import CoreGraphics
import Foundation
import SwiftUI
import os

/// Single source of truth for the snap (photo) attached to an in-progress
/// moment form. Owns `formSnap` (the form-layer state), `processedForRetry`
/// (re-encoded bytes kept around for a user-tapped retry), and every
/// transition: pick → upload → retry → cancel → remove.
///
/// `CaptureFlowView` and `EditMomentSheet` each hold one of these and
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

    /// Ingests between their start and the end of their upload. During the
    /// re-encode `formSnap` is still nil or the previous photo, so a form that
    /// read only `formSnap` would save or discard around the new one. The
    /// load and the crop editor come before the coordinator hears of a photo:
    /// the flows cover them with a `CropPickGate`.
    private var picksInFlight = 0

    /// Bumped by every ingest when it starts. An ingest applies only while it
    /// is still the latest one, so a first that re-encodes slower than a
    /// second never lands over it.
    private var pickGeneration = 0

    /// Where the form stands. While its save is in flight (`beginCommit`),
    /// once that save committed (`markCommitted`) or once the form closed
    /// (`cancelAndCleanup`), a pick that lands late must leave `formSnap`
    /// alone and take its own uploaded files with it.
    private var lifecycle: Lifecycle = .open

    private enum Lifecycle { case open, committing, committed, closed }

    /// The pending snap a save sent. No late-upload cleanup ever deletes it.
    private var committedSnapId: UUID?

    // MARK: - Derived state

    /// True from an ingest's start until its upload finishes.
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

    /// Set the initial snap *after* construction, for a form that learns its
    /// saved photo only once it has loaded.
    func seedExisting(_ snap: FormSnap?) {
        self.formSnap = snap
    }

    // MARK: - Test seam

    #if DEBUG
    /// Test-only: seed `processedForRetry` directly so tests can exercise
    /// the upload path without driving a real pick.
    func seedProcessedForRetry(_ processed: ProcessedImage) {
        self.processedForRetry = processed
    }
    #endif

    // MARK: - User-driven transitions

    /// A photo the camera just took (Home's camera moment). The bytes are in
    /// hand, so they take `ingest`, the same path a library pick ends on.
    func handleCaptured(_ data: Data, userId: UUID) async {
        let uti = ImageFormatValidator.uti(of: data) ?? "public.jpeg"
        Self.logger.notice("handleCaptured: \(data.count, privacy: .public) bytes uti=\(uti, privacy: .public)")
        guard ImageFormatValidator.isSupported(uti) else {
            Self.logger.warning("unsupported captured format: \(uti, privacy: .public)")
            return
        }
        await ingest(data, uti: uti, userId: userId)
    }

    /// A pick whose bytes are already in hand; it supersedes any earlier one.
    /// `uti` has already passed `ImageFormatValidator`.
    func ingest(_ data: Data, uti: String, userId: UUID) async {
        await ingest(data, uti: uti, userId: userId, generation: nextPickGeneration())
    }

    private func nextPickGeneration() -> Int {
        pickGeneration += 1
        return pickGeneration
    }

    /// Shared pick path: capture date → re-encode → replace → upload.
    private func ingest(_ data: Data, uti: String, userId: UUID, generation: Int) async {
        picksInFlight += 1
        defer { picksInFlight -= 1 }
        guard accepts(generation) else { return }

        // Read the capture date before the pipeline strips metadata (D7); the
        // form only sees it once this pick is accepted.
        let captureDate = ExifCaptureDate.from(data)

        let processed: ProcessedImage
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(data, uti: uti)
            }.value
        } catch {
            Self.logger.error("image pipeline failed: \(String(describing: error), privacy: .public)")
            return
        }

        await attach(processed, captureDate: captureDate, userId: userId, generation: generation)
    }

    /// The crop editor's Done (spec "Pipeline"): the image is already upright
    /// and cropped, and its date was read from the original bytes beforehand.
    func ingest(cropped image: CGImage, captureDate: Date?, userId: UUID) async {
        let generation = nextPickGeneration()
        picksInFlight += 1
        defer { picksInFlight -= 1 }
        guard accepts(generation) else { return }
        let processed: ProcessedImage
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(image)
            }.value
        } catch {
            Self.logger.error("cropped pipeline failed: \(String(describing: error), privacy: .public)")
            return
        }
        await attach(processed, captureDate: captureDate, userId: userId, generation: generation)
    }

    /// Attach → replace → upload, shared by every ingest once its bytes are processed.
    private func attach(_ processed: ProcessedImage, captureDate: Date?, userId: UUID, generation: Int) async {
        // The form closed or is saving, or a later pick superseded this one,
        // while it was being read: nothing is attached, replaced or deleted.
        guard accepts(generation) else { return }

        // A saved photo replaced by a new pick: its row goes with the next
        // save's whole-set `snaps`, its files after that save (R6).
        if case .existing(_, let storagePath) = formSnap {
            detachedStoragePaths.append(storagePath)
        }

        // The inline picker makes "actually, that one" a single tap, so the
        // snap being replaced has to take its Storage bytes with it. An upload
        // still in flight for that id is left to finish: the id guard in
        // `applyStateIfSnapMatches` keeps its result off the new snap, and its
        // own pick deletes the files once they land.
        if case .pending(let replaced) = formSnap {
            Self.logger.notice("replacing pending snap \(replaced.id.uuidString, privacy: .public)")
            Task { await self.repo.deleteFiles(snapId: replaced.id, userId: userId) }
        }

        let snapId = UUID()
        formSnap = .pending(AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading))
        processedForRetry = processed
        pickedCaptureDate = captureDate

        await performUpload(processed: processed, snapId: snapId, userId: userId)

        // Removed, replaced, superseded or closed while uploading: the form no
        // longer holds these files, so they go. A delete issued before the
        // upload landed may have found nothing yet. The snap a save sent is
        // never touched.
        if pendingSnapId != snapId, snapId != committedSnapId {
            Self.logger.notice("late upload: deleting orphaned \(snapId.uuidString, privacy: .public)")
            await deleteFilesUncancelled(snapId: snapId, userId: userId)
        }
    }

    /// False once the form is saving, committed or closed, or when a later
    /// pick superseded this one.
    private func accepts(_ generation: Int) -> Bool {
        if lifecycle != .open {
            Self.logger.notice("pick ignored: form is \(String(describing: self.lifecycle), privacy: .public)")
            return false
        }
        if generation != pickGeneration {
            Self.logger.notice("pick ignored: superseded by a later pick")
            return false
        }
        return true
    }

    /// The id of the `.pending` snap the form holds, if any.
    private var pendingSnapId: UUID? {
        if case .pending(let snap) = formSnap { return snap.id }
        return nil
    }

    /// True while a save is in flight or done: the photo it sent can be
    /// neither removed nor detached.
    /// True once a save succeeded (the sheet's `onDisappear` skips cleanup).
    var isCommitted: Bool { lifecycle == .committed }

    private var isCommitLocked: Bool { lifecycle == .committing || lifecycle == .committed }

    /// A cleanup delete that must run even when its caller's task was
    /// cancelled: the unstructured task keeps the main actor but not the
    /// cancellation, which would make the Storage request throw before it is
    /// ever sent and orphan the files.
    private func deleteFilesUncancelled(snapId: UUID, userId: UUID) async {
        await Task { await self.repo.deleteFiles(snapId: snapId, userId: userId) }.value
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
        guard !isCommitLocked, case .pending(let snap) = formSnap else { return }
        let snapId = snap.id
        formSnap = nil
        processedForRetry = nil
        pickedCaptureDate = nil
        guard snapId != committedSnapId else { return }
        await repo.deleteFiles(snapId: snapId, userId: userId)
    }

    /// Storage prefixes of saved photos the user removed or replaced in this
    /// form. Their rows go when the save sends the whole `snaps` set (R6);
    /// their files go in `purgeDetached(_:)`, after that save succeeds.
    private(set) var detachedStoragePaths: [String] = []

    /// Remove the current `.existing` photo from the form. No RPC: the next
    /// `save_moment` sends `snaps: []` and the server enforces "a name or a
    /// photo" in that same write. No-op unless the current snap is `.existing`,
    /// and while a save is in flight or done.
    func detachExisting() {
        guard !isCommitLocked, case .existing(_, let storagePath) = formSnap else { return }
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
        // A save that started owns the photo: never delete a committed snap.
        guard !isCommitLocked else { return }
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
        guard snapId != committedSnapId else { return }
        await repo.deleteFiles(snapId: snapId, userId: userId)
    }

    /// Called by the form right before its save RPC. Until `markCommitted`
    /// or `abortCommit`, new picks are rejected and the attached photo can be
    /// neither removed nor replaced, so the save sends exactly what it read.
    func beginCommit() {
        guard lifecycle == .open else { return }
        lifecycle = .committing
        committedSnapId = pendingSnapId
    }

    /// Called by the form after its save succeeded: the attached photo now
    /// belongs to a saved moment, so a pick that lands late must neither
    /// replace it nor delete its files.
    func markCommitted() {
        if lifecycle != .committing { committedSnapId = pendingSnapId }
        lifecycle = .committed
    }

    /// Called by the form when its save failed: the form is open again and
    /// keeps its photo for a retry (A2).
    func abortCommit() {
        guard lifecycle == .committing else { return }
        lifecycle = .open
        committedSnapId = nil
    }

    // MARK: - Private

    /// Single upload path used by both `attach` (initial attempt) and
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
}
