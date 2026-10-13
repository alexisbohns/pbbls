import Foundation
import Testing
import UIKit
@testable import Pebbles

/// Recorded calls on `FakeRepo`. Top-level so the suite type stays at nesting
/// depth 1 (avoids swiftlint's nesting violation).
enum FakeRepoCall: Equatable {
    case upload(snapId: UUID, userId: UUID)
    case deleteFilesBySnapId(snapId: UUID, userId: UUID)
    case deleteFilesByPrefix(prefix: String)
}

@MainActor
@Suite("SnapUploadCoordinator")
struct SnapUploadCoordinatorTests {

    // MARK: - Fake repo

    /// Records every call and lets each method be stubbed. The live
    /// `deleteFiles(...)` methods don't throw (errors are logged internally),
    /// so the fake matches that contract — we assert call recording, not
    /// thrown errors, for those paths.
    @MainActor
    final class FakeRepo: PebbleSnapRepositoryProtocol {

        var calls: [FakeRepoCall] = []

        // Stubs — set before invoking the method under test.
        var uploadResults: [Result<Void, Error>] = []

        func uploadProcessed(_ processed: ProcessedImage, snapId: UUID, userId: UUID) async throws {
            calls.append(.upload(snapId: snapId, userId: userId))
            guard !uploadResults.isEmpty else { return }
            switch uploadResults.removeFirst() {
            case .success: return
            case .failure(let error): throw error
            }
        }

        func deleteFiles(snapId: UUID, userId: UUID) async {
            calls.append(.deleteFilesBySnapId(snapId: snapId, userId: userId))
        }

        func deleteFiles(storagePrefix: String) async {
            calls.append(.deleteFilesByPrefix(prefix: storagePrefix))
        }
    }

    // MARK: - Fixtures

    private func makeProcessed() -> ProcessedImage {
        ProcessedImage(original: Data([0x01]), thumb: Data([0x02]))
    }

    /// Build a coordinator pre-seeded with a `.pending(.uploaded)` snap.
    /// Useful for tests that start mid-flow (retry, remove, cancel).
    private func makeCoordinatorWithUploadedPending(
        repo: FakeRepo,
        snapId: UUID = UUID()
    ) -> (SnapUploadCoordinator, AttachedSnap) {
        let snap = AttachedSnap(id: snapId, localThumb: Data([0xFF]), state: .uploaded)
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .pending(snap),
            retryDelay: .milliseconds(1)
        )
        return (coordinator, snap)
    }

    private struct StubError: Error, Equatable { let tag: String }

    // MARK: - Tests

    // Note: `handlePicked` exercises the full `loadData → ImagePipeline.process →
    // performUpload` path. Driving it from a unit test would require a real
    // `NSItemProvider` (and would re-encode a JPEG in-process), which isn't worth
    // the test infrastructure for what is mostly glue. The internal `performUpload`
    // path is exercised by `retryWithinUploadSucceeds`, `bothAttemptsFailEndsInFailedState`,
    // and `manualRetrySucceeds` below. The end-to-end picker integration is covered
    // by the manual smoke test in plan Task 10.

    @Test("first upload fails, second attempt succeeds → .uploaded")
    func retryWithinUploadSucceeds() async throws {
        let repo = FakeRepo()
        repo.uploadResults = [.failure(StubError(tag: "first")), .success(())]

        let snapId = UUID()
        let userId = UUID()
        let snap = AttachedSnap(id: snapId, localThumb: Data([0xFF]), state: .uploading)
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .pending(snap),
            retryDelay: .milliseconds(1)
        )
        coordinator.seedProcessedForRetry(makeProcessed())
        await coordinator.retryCurrent(userId: userId)

        guard case .pending(let final) = coordinator.formSnap else {
            Issue.record("expected .pending"); return
        }
        #expect(final.state == .uploaded)
        #expect(repo.calls.filter { if case .upload = $0 { return true }; return false }.count == 2)
    }

    @Test("both upload attempts fail → .failed, processedForRetry retained")
    func bothAttemptsFailEndsInFailedState() async throws {
        let repo = FakeRepo()
        repo.uploadResults = [
            .failure(StubError(tag: "first")),
            .failure(StubError(tag: "second"))
        ]
        let snap = AttachedSnap(id: UUID(), localThumb: Data([0xFF]), state: .uploading)
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .pending(snap),
            retryDelay: .milliseconds(1)
        )
        coordinator.seedProcessedForRetry(makeProcessed())
        await coordinator.retryCurrent(userId: UUID())

        guard case .pending(let final) = coordinator.formSnap else {
            Issue.record("expected .pending"); return
        }
        #expect(final.state == .failed)
        #expect(coordinator.processedForRetry != nil)
    }

    @Test("manual retry from .failed → .uploading → .uploaded")
    func manualRetrySucceeds() async throws {
        let repo = FakeRepo()
        repo.uploadResults = [.success(())]
        let snap = AttachedSnap(id: UUID(), localThumb: Data([0xFF]), state: .failed)
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .pending(snap),
            retryDelay: .milliseconds(1)
        )
        coordinator.seedProcessedForRetry(makeProcessed())
        await coordinator.retryCurrent(userId: UUID())

        guard case .pending(let final) = coordinator.formSnap else {
            Issue.record("expected .pending"); return
        }
        #expect(final.state == .uploaded)
    }

    @Test("removePending clears state and fires compensating delete")
    func removePendingFiresCleanup() async throws {
        let repo = FakeRepo()
        let snapId = UUID()
        let userId = UUID()
        let (coordinator, _) = makeCoordinatorWithUploadedPending(repo: repo, snapId: snapId)
        coordinator.seedProcessedForRetry(makeProcessed())

        await coordinator.removePending(userId: userId)

        #expect(coordinator.formSnap == nil)
        #expect(coordinator.processedForRetry == nil)
        #expect(repo.calls == [.deleteFilesBySnapId(snapId: snapId, userId: userId)])
    }

    @Test("cancelAndCleanup awaits the Storage delete before returning")
    func cancelAndCleanupAwaitsDelete() async throws {
        let repo = FakeRepo()
        let snapId = UUID()
        let userId = UUID()
        let (coordinator, _) = makeCoordinatorWithUploadedPending(repo: repo, snapId: snapId)

        await coordinator.cancelAndCleanup(userId: userId)

        #expect(coordinator.formSnap == nil)
        #expect(repo.calls == [.deleteFilesBySnapId(snapId: snapId, userId: userId)])
    }

    @Test("cancelAndCleanup with no pending snap is a no-op")
    func cancelAndCleanupNoOpWhenEmpty() async throws {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))

        await coordinator.cancelAndCleanup(userId: UUID())

        #expect(coordinator.formSnap == nil)
        #expect(repo.calls.isEmpty)
    }

    // Note: the spec lists "cancelAndCleanup with delete failure → returns, state
    // cleared" as a case. That property is enforced at the type level — the
    // protocol's `deleteFiles(snapId:userId:)` returns `async` (not `async throws`),
    // so the coordinator structurally cannot fail-fast on a Storage error. The
    // live repo logs internally; the fake records the call. No additional test
    // needed beyond `cancelAndCleanupAwaitsDelete`.

    @Test("handleSaveFailure fires compensating delete but keeps formSnap")
    func handleSaveFailureFiresCleanupAndKeepsState() async throws {
        let repo = FakeRepo()
        let snapId = UUID()
        let userId = UUID()
        let (coordinator, original) = makeCoordinatorWithUploadedPending(repo: repo, snapId: snapId)

        await coordinator.handleSaveFailure(userId: userId)

        #expect(repo.calls == [.deleteFilesBySnapId(snapId: snapId, userId: userId)])
        // Caller decides whether to clear state; coordinator preserves it.
        if case .pending(let snap) = coordinator.formSnap {
            #expect(snap.id == original.id)
        } else {
            Issue.record("expected formSnap to be retained")
        }
    }

    @Test("detachExisting: clears the photo, calls nothing, remembers its files")
    func detachExistingIsLocal() {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: UUID(), storagePath: "uid/sid"),
            retryDelay: .milliseconds(1)
        )

        coordinator.detachExisting()

        #expect(coordinator.formSnap == nil)
        #expect(coordinator.detachedStoragePaths == ["uid/sid"])
        #expect(repo.calls.isEmpty)
    }

    @Test("purgeDetached: deletes the detached files once, after a save")
    func purgeDetachedDeletesFiles() async {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: UUID(), storagePath: "uid/sid"),
            retryDelay: .milliseconds(1)
        )
        coordinator.detachExisting()

        await coordinator.purgeDetached()
        await coordinator.purgeDetached()

        #expect(repo.calls == [.deleteFilesByPrefix(prefix: "uid/sid")])
        #expect(coordinator.detachedStoragePaths.isEmpty)
    }

    @Test("detachExisting is a no-op without an existing photo")
    func detachWithoutExisting() {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(repo: repo, initialSnap: nil, retryDelay: .milliseconds(1))
        coordinator.detachExisting()
        #expect(coordinator.detachedStoragePaths.isEmpty)
    }

    @Test("replacing a saved photo with a new pick remembers the old files, deletes nothing yet")
    func replacingExistingRemembersFiles() async throws {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: UUID(), storagePath: "uid/old"),
            retryDelay: .milliseconds(1)
        )
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8))
        let jpeg = renderer.jpegData(withCompressionQuality: 0.8) { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }

        await coordinator.ingest(jpeg, uti: "public.jpeg", userId: UUID())

        #expect(coordinator.detachedStoragePaths == ["uid/old"])
        #expect(!repo.calls.contains(.deleteFilesByPrefix(prefix: "uid/old")))
    }

    @Test("cancelAndCleanup forgets detached photos: a cancelled form keeps its saved photo")
    func cancelForgetsDetached() async {
        let repo = FakeRepo()
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: UUID(), storagePath: "uid/sid"),
            retryDelay: .milliseconds(1)
        )
        coordinator.detachExisting()

        await coordinator.cancelAndCleanup(userId: UUID())

        #expect(coordinator.detachedStoragePaths.isEmpty)
        #expect(repo.calls.isEmpty)
    }
}
