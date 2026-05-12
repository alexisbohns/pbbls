import Foundation
import Testing
@testable import Pebbles

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

        enum Call: Equatable {
            case upload(snapId: UUID, userId: UUID)
            case deleteFilesBySnapId(snapId: UUID, userId: UUID)
            case deleteFilesByPrefix(prefix: String)
            case deletePebbleMedia(snapId: UUID)
        }

        var calls: [Call] = []

        // Stubs — set before invoking the method under test.
        var uploadResults: [Result<Void, Error>] = []
        var deletePebbleMediaResult: Result<String, Error> = .success("uid/sid")

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

        func deletePebbleMedia(snapId: UUID) async throws -> String {
            calls.append(.deletePebbleMedia(snapId: snapId))
            switch deletePebbleMediaResult {
            case .success(let path): return path
            case .failure(let error): throw error
            }
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
            .failure(StubError(tag: "second")),
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

    @Test("removeExisting: happy path calls RPC, fires storage cleanup, clears state")
    func removeExistingHappyPath() async throws {
        let repo = FakeRepo()
        let snapId = UUID()
        repo.deletePebbleMediaResult = .success("uid/sid")
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: snapId, storagePath: "uid/sid"),
            retryDelay: .milliseconds(1)
        )

        try await coordinator.removeExisting()

        #expect(coordinator.formSnap == nil)
        #expect(repo.calls == [
            .deletePebbleMedia(snapId: snapId),
            .deleteFilesByPrefix(prefix: "uid/sid"),
        ])
    }

    @Test("removeExisting: RPC failure throws and retains state")
    func removeExistingRPCFailureRetainsState() async throws {
        let repo = FakeRepo()
        let snapId = UUID()
        repo.deletePebbleMediaResult = .failure(StubError(tag: "rpc"))
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .existing(id: snapId, storagePath: "uid/sid"),
            retryDelay: .milliseconds(1)
        )

        await #expect(throws: StubError.self) {
            try await coordinator.removeExisting()
        }

        if case .existing(let id, _) = coordinator.formSnap {
            #expect(id == snapId)
        } else {
            Issue.record("expected formSnap to remain .existing")
        }
    }
}
