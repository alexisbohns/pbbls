import Foundation
import Testing
import UIKit
@testable import Pebbles

/// A pick between its tap and the end of its upload: it blocks saving, and
/// once the form closed or committed it never lands on it.
@MainActor
@Suite("SnapUploadCoordinator — pick in flight")
struct SnapUploadCoordinatorPickTests {

    /// Holds a pick's load open until the test releases it.
    @MainActor
    final class LoadGate {
        var continuation: CheckedContinuation<Void, Never>?
    }

    private func makeJPEG() -> Data {
        UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).jpegData(withCompressionQuality: 0.8) { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
    }

    @Test("a pick still loading blocks saving before formSnap changes")
    func pickInFlightBlocks() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        let gate = LoadGate()

        let pick = Task {
            await coordinator.processPick(userId: UUID()) {
                await withCheckedContinuation { gate.continuation = $0 }
                return nil
            }
        }
        while gate.continuation == nil { await Task.yield() }

        #expect(coordinator.isProcessingPick)
        #expect(coordinator.isUploading)
        #expect(coordinator.isBlocking)
        #expect(coordinator.formSnap == nil)

        gate.continuation?.resume()
        await pick.value

        #expect(!coordinator.isProcessingPick)
        #expect(!coordinator.isBlocking)
    }

    @Test("a pick landing after cancelAndCleanup attaches and uploads nothing")
    func pickAfterCloseIsDropped() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))

        await coordinator.cancelAndCleanup(userId: UUID())
        await coordinator.ingest(makeJPEG(), uti: "public.jpeg", userId: UUID())

        #expect(coordinator.formSnap == nil)
        #expect(coordinator.pickedCaptureDate == nil)
        #expect(repo.calls.isEmpty)
        #expect(!coordinator.isBlocking)
    }

    @Test("closing while a pick uploads deletes its files once the upload lands")
    func closeDuringUploadCleansUp() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        repo.duringUpload = { await coordinator.cancelAndCleanup(userId: userId) }

        await coordinator.ingest(makeJPEG(), uti: "public.jpeg", userId: userId)

        #expect(coordinator.formSnap == nil)
        guard case .upload(let snapId, _) = repo.calls.first else {
            Issue.record("expected an upload first, got \(repo.calls)"); return
        }
        // The last call is the post-upload delete of the snap's own files.
        #expect(repo.calls.last == .deleteFilesBySnapId(snapId: snapId, userId: userId))
    }

    @Test("a pick landing after markCommitted never touches the saved photo")
    func pickAfterCommitKeepsSavedPhoto() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let snapId = UUID()
        let original = AttachedSnap(id: snapId, localThumb: Data([0xFF]), state: .uploaded)
        let coordinator = SnapUploadCoordinator(
            repo: repo,
            initialSnap: .pending(original),
            retryDelay: .milliseconds(1)
        )

        coordinator.markCommitted()
        await coordinator.ingest(makeJPEG(), uti: "public.jpeg", userId: UUID())

        guard case .pending(let kept) = coordinator.formSnap else {
            Issue.record("expected the committed snap to stay"); return
        }
        #expect(kept.id == original.id)
        #expect(!repo.calls.contains { if case .deleteFilesBySnapId(snapId, _) = $0 { true } else { false } })
        #expect(!repo.calls.contains { if case .upload = $0 { true } else { false } })
    }

    @Test("committing while a pick uploads deletes that pick's files instead of attaching them")
    func commitDuringUploadKeepsSavedPhoto() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        repo.duringUpload = { coordinator.markCommitted() }

        await coordinator.ingest(makeJPEG(), uti: "public.jpeg", userId: userId)

        guard case .upload(let snapId, _) = repo.calls.first else {
            Issue.record("expected an upload first, got \(repo.calls)"); return
        }
        #expect(repo.calls.last == .deleteFilesBySnapId(snapId: snapId, userId: userId))
        #expect(coordinator.formSnap == nil)
    }
}
