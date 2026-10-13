import Foundation
import ImageIO
import Testing
import UIKit
import UniformTypeIdentifiers
@testable import Pebbles

/// A pick between its tap and the end of its upload: it blocks saving, a
/// later pick supersedes it, and once the form is saving, committed or closed
/// it never lands on it.
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

    /// A JPEG carrying an EXIF `DateTimeOriginal`, for the capture-date seed.
    private func makeJPEG(dateTimeOriginal: String) -> Data {
        let image = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8)).image { context in
            UIColor.blue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
        let output = NSMutableData()
        guard let cgImage = image.cgImage,
              let destination = CGImageDestinationCreateWithData(output, UTType.jpeg.identifier as CFString, 1, nil)
        else { return Data() }
        let properties: [CFString: Any] = [
            kCGImagePropertyExifDictionary: [kCGImagePropertyExifDateTimeOriginal: dateTimeOriginal]
        ]
        CGImageDestinationAddImage(destination, cgImage, properties as CFDictionary)
        CGImageDestinationFinalize(destination)
        return output as Data
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

    @Test("an upload landing after its own snap was committed never deletes it")
    func commitDuringUploadKeepsCommittedSnap() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        repo.duringUpload = {
            coordinator.beginCommit()
            coordinator.markCommitted()
        }

        await coordinator.ingest(makeJPEG(), uti: "public.jpeg", userId: userId)

        guard case .upload(let snapId, _) = repo.calls.first else {
            Issue.record("expected an upload first, got \(repo.calls)"); return
        }
        guard case .pending(let kept) = coordinator.formSnap else {
            Issue.record("expected the committed snap to stay"); return
        }
        #expect(kept.id == snapId)
        #expect(!repo.calls.contains(.deleteFilesBySnapId(snapId: snapId, userId: userId)))
    }

    @Test("a pick running in a cancelled task still deletes its late upload, uncancelled")
    func cancelledPickCleanupRunsUncancelled() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        var pickWasCancelled = false
        repo.duringUpload = {
            pickWasCancelled = Task.isCancelled
            await coordinator.cancelAndCleanup(userId: userId)
        }

        let jpeg = makeJPEG()
        let pick = Task { await coordinator.ingest(jpeg, uti: "public.jpeg", userId: userId) }
        pick.cancel()
        await pick.value

        #expect(pickWasCancelled)
        guard case .upload(let snapId, _) = repo.calls.first else {
            Issue.record("expected an upload first, got \(repo.calls)"); return
        }
        #expect(repo.calls.last == .deleteFilesBySnapId(snapId: snapId, userId: userId))
        // The close's own delete ran inside the cancelled pick; the post-upload
        // one must not, or the live request would never be sent.
        let deletes = repo.calls.filter { $0 == .deleteFilesBySnapId(snapId: snapId, userId: userId) }.count
        #expect(deletes == 2)
        #expect(repo.deletesInCancelledTask == [snapId])
        #expect(coordinator.formSnap == nil)
    }

    @Test("a pick landing while a save commits is rejected and the saved snap is never deleted")
    func pickDuringCommitIsRejected() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let saved = AttachedSnap(id: UUID(), localThumb: Data([0xFF]), state: .uploaded)
        let coordinator = SnapUploadCoordinator(repo: repo, initialSnap: .pending(saved), retryDelay: .milliseconds(1))
        let gate = LoadGate()
        let jpeg = makeJPEG()

        let pick = Task {
            await coordinator.processPick(userId: userId) {
                await withCheckedContinuation { gate.continuation = $0 }
                return (jpeg, "public.jpeg")
            }
        }
        while gate.continuation == nil { await Task.yield() }

        coordinator.beginCommit()
        gate.continuation?.resume()
        await pick.value
        await coordinator.ingest(jpeg, uti: "public.jpeg", userId: userId)
        await coordinator.removePending(userId: userId)
        coordinator.markCommitted()

        guard case .pending(let kept) = coordinator.formSnap else {
            Issue.record("expected the saved snap to stay"); return
        }
        #expect(kept.id == saved.id)
        #expect(repo.calls.isEmpty)
    }

    @Test("abortCommit re-opens the form: Remove and picks work again")
    func abortCommitReopens() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let saved = AttachedSnap(id: UUID(), localThumb: Data([0xFF]), state: .uploaded)
        let coordinator = SnapUploadCoordinator(repo: repo, initialSnap: .pending(saved), retryDelay: .milliseconds(1))

        coordinator.beginCommit()
        await coordinator.removePending(userId: userId)
        #expect(coordinator.formSnap != nil)

        coordinator.abortCommit()
        await coordinator.removePending(userId: userId)
        #expect(coordinator.formSnap == nil)
        #expect(repo.calls == [.deleteFilesBySnapId(snapId: saved.id, userId: userId)])

        await coordinator.ingest(makeJPEG(), uti: "public.jpeg", userId: userId)
        guard case .pending(let picked) = coordinator.formSnap else {
            Issue.record("expected the new pick to attach"); return
        }
        #expect(picked.state == .uploaded)
    }

    @Test("a first pick that loads slower than a second never replaces it")
    func slowerFirstPickIsSuperseded() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        let gate = LoadGate()
        let dated = makeJPEG(dateTimeOriginal: "2026:08:14 17:32:05")
        #expect(ExifCaptureDate.from(dated) != nil)

        let first = Task {
            await coordinator.processPick(userId: userId) {
                await withCheckedContinuation { gate.continuation = $0 }
                return (dated, "public.jpeg")
            }
        }
        while gate.continuation == nil { await Task.yield() }

        let undated = makeJPEG()
        await coordinator.processPick(userId: userId) { (undated, "public.jpeg") }
        guard case .pending(let second) = coordinator.formSnap else {
            Issue.record("expected the second pick to attach"); return
        }

        gate.continuation?.resume()
        await first.value

        guard case .pending(let kept) = coordinator.formSnap else {
            Issue.record("expected the second pick to stay"); return
        }
        #expect(kept.id == second.id)
        #expect(repo.calls == [.upload(snapId: second.id, userId: userId)])
        // The superseded pick's capture date never reaches the form.
        #expect(coordinator.pickedCaptureDate == nil)
        #expect(!coordinator.isProcessingPick)
    }

    @Test("a pick replaced while uploading deletes its own files once they land")
    func replacedDuringUploadDeletesOwnFiles() async {
        let repo = SnapUploadCoordinatorTests.FakeRepo()
        let userId = UUID()
        let coordinator = SnapUploadCoordinator(repo: repo, retryDelay: .milliseconds(1))
        let jpeg = makeJPEG()
        repo.duringUpload = {
            repo.duringUpload = nil
            await coordinator.ingest(jpeg, uti: "public.jpeg", userId: userId)
        }

        await coordinator.ingest(jpeg, uti: "public.jpeg", userId: userId)

        let uploads = repo.calls.compactMap { if case .upload(let id, _) = $0 { id } else { nil } }
        guard uploads.count == 2, case .pending(let kept) = coordinator.formSnap else {
            Issue.record("expected two uploads and an attached snap, got \(repo.calls)"); return
        }
        let (replaced, latest) = (uploads[0], uploads[1])
        #expect(kept.id == latest)
        #expect(repo.calls.last == .deleteFilesBySnapId(snapId: replaced, userId: userId))
        #expect(!repo.calls.contains(.deleteFilesBySnapId(snapId: latest, userId: userId)))
        #expect(repo.deletesInCancelledTask.isEmpty)
    }
}
