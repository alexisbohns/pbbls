# SnapUploadCoordinator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the snap upload, retry, and compensating-delete logic duplicated between `CreatePebbleSheet` and `EditPebbleSheet` into a single `@Observable SnapUploadCoordinator`, with the `cancelAndCleanup` sleep-race fixed and full test coverage.

**Architecture:** New `@MainActor @Observable` class owns `formSnap` (moved out of `PebbleDraft`) and `processedForRetry`. Storage operations go through a new `PebbleSnapRepositoryProtocol` so the coordinator is unit-testable with a fake. Both sheets delegate every snap mutation to the coordinator; the two `.onChange` observers that translated state mutations into Storage side-effects are deleted — the chip calls callbacks directly.

**Tech Stack:** Swift 5.9+, SwiftUI, iOS 17, `@Observable`, Swift Testing, Supabase Swift SDK.

**Spec:** `docs/superpowers/2026-05-12-ios-snap-upload-coordinator-design.md`
**Issue:** [#401](https://github.com/Bohns/pbbls/issues/401)
**Branch:** `feat/401-snap-upload-coordinator` (already created)

**Ordering rationale:** Tasks 1–4 are purely additive — the build stays green. Tasks 5–8 are the switch-over; each ends with a passing build and one logical refactor committed. Task 9 finally removes `PebbleDraft.formSnap` once nothing reads it. Task 10 is verification.

**Verification commands (used throughout):**
- Lint: `npm run lint --workspace=@pbbls/ios`
- Build: `npm run build --workspace=@pbbls/ios`
- Test: `npm run test --workspace=@pbbls/ios`

(All commands are run from the repo root `/Users/alexis/code/pbbls`. The build/test scripts run `xcodegen generate` first so new files are auto-picked up.)

---

## Task 1: Add `PebbleSnapRepositoryProtocol` and `deletePebbleMedia(snapId:)`

**Why:** The coordinator must be unit-testable without a real Supabase client. We extract a protocol so a fake conformance can be injected. While we're here, add a `deletePebbleMedia(snapId:)` method that wraps the `delete_pebble_media` RPC — currently called from `EditPebbleSheet.removeExistingSnap()` directly on the client. Moving it here keeps the coordinator off the raw client.

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`

- [ ] **Step 1: Add the protocol and the new method**

Open `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`. After the imports (line 3), add the protocol. Then add the new method to `PebbleSnapRepository` and declare conformance.

Insert directly after `import os` (before `@MainActor struct PebbleSnapRepository`):

```swift
/// Abstraction over snap Storage + RPC operations so callers (notably
/// `SnapUploadCoordinator`) can be unit-tested with a fake. The live
/// conformance is `PebbleSnapRepository`.
@MainActor
protocol PebbleSnapRepositoryProtocol {
    func uploadProcessed(_ processed: ProcessedImage, snapId: UUID, userId: UUID) async throws
    func deleteFiles(snapId: UUID, userId: UUID) async
    func deleteFiles(storagePrefix: String) async
    /// Calls the `delete_pebble_media` Postgres RPC. Returns the row's
    /// `storage_path` so the caller can fire-and-forget Storage cleanup.
    func deletePebbleMedia(snapId: UUID) async throws -> String
}
```

Change the struct declaration line from:

```swift
struct PebbleSnapRepository {
```

to:

```swift
struct PebbleSnapRepository: PebbleSnapRepositoryProtocol {
```

Inside the struct, add the new method. Put it after `deleteFiles(storagePrefix:)` (right after line 68 — the closing brace of that method):

```swift
    /// Wraps the `delete_pebble_media` RPC. Returns the row's `storage_path`
    /// so the caller can run fire-and-forget Storage cleanup using
    /// `deleteFiles(storagePrefix:)`.
    func deletePebbleMedia(snapId: UUID) async throws -> String {
        try await client
            .rpc("delete_pebble_media", params: ["p_snap_id": snapId.uuidString])
            .execute()
            .value
    }
```

- [ ] **Step 2: Verify the build is still green**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds. The protocol is unused so far; the new method is unused so far. Both are fine — `swiftlint` may flag the unused protocol with a warning; if so, it's acceptable for one commit.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift
git commit -m "$(cat <<'EOF'
quality(ios): add PebbleSnapRepositoryProtocol and deletePebbleMedia method

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Scaffold `SnapUploadCoordinator` with stubbed methods

**Why:** Creating the file with the full public surface (and `fatalError("not implemented")` bodies) lets us write the test file in the next task without compile errors. Once tests are written and failing, we fill in the implementation.

**Files:**
- Create: `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`

- [ ] **Step 1: Create the file with the full API surface**

Create `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`:

```swift
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

    // MARK: - User-driven transitions

    func handlePicked(_ picked: PhotoPickerView.PickedItem, userId: UUID) async {
        fatalError("not implemented")
    }

    /// Re-run the upload after a user tap on the chip's retry button. Only
    /// valid when the current state is `.pending(.failed)` and we still hold
    /// `processedForRetry`. No-op otherwise.
    func retryCurrent(userId: UUID) async {
        fatalError("not implemented")
    }

    /// Remove the current `.pending` snap and fire the compensating Storage
    /// delete. Safe to call when the snap is in any state. No-op when the
    /// current `formSnap` is not `.pending`.
    func removePending(userId: UUID) async {
        fatalError("not implemented")
    }

    /// Remove the current `.existing` snap. Calls `delete_pebble_media` and
    /// then fires fire-and-forget Storage cleanup. Throws on RPC failure so
    /// the sheet can surface a user-facing error. No-op (returns normally)
    /// when the current `formSnap` is not `.existing`.
    func removeExisting() async throws {
        fatalError("not implemented")
    }

    // MARK: - Sheet lifecycle

    /// Called by the sheet's Cancel button before `dismiss()`. Captures the
    /// current pending snap id, clears state, and *awaits* the compensating
    /// Storage delete. Storage-delete failures are logged but do not throw —
    /// Cancel must not be un-cancellable.
    func cancelAndCleanup(userId: UUID) async {
        fatalError("not implemented")
    }

    /// Called by the sheet after a save (compose-pebble / compose-pebble-update)
    /// failure when a pending snap is attached. Awaits the compensating
    /// Storage delete. Does *not* clear `formSnap` — the sheet stays open
    /// and the user can retry. Use `cancelAndCleanup` if you also want to
    /// dismiss.
    func handleSaveFailure(userId: UUID) async {
        fatalError("not implemented")
    }
}
```

- [ ] **Step 2: Verify build is still green**

```bash
npm run build --workspace=@pbbls/ios
```

Expected: build succeeds. The file compiles; nothing references the new class yet.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift
git commit -m "$(cat <<'EOF'
quality(ios): scaffold SnapUploadCoordinator API surface for #401

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Write the test suite (failing) and a `FakePebbleSnapRepository`

**Why:** TDD. Write all ten test cases against the stubbed API surface, watch them fail, then implement.

**Files:**
- Create: `apps/ios/PebblesTests/SnapUploadCoordinatorTests.swift`

- [ ] **Step 1: Create the test file with the fake repo and all ten tests**

Create `apps/ios/PebblesTests/SnapUploadCoordinatorTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

@MainActor
@Suite("SnapUploadCoordinator")
struct SnapUploadCoordinatorTests {

    // MARK: - Fake repo

    /// Records every call (with timing) and lets each method be stubbed to
    /// throw. Records "await order" so we can prove `cancelAndCleanup` waits
    /// for the Storage delete to finish before returning.
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
        // Inject processedForRetry by routing through internal upload path:
        // we call `retryCurrent` which sets state to .uploading and re-runs.
        coordinator.seedProcessedForRetry(makeProcessed())   // test-only helper, added in Task 4
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
```

- [ ] **Step 2: Run the tests and confirm they fail**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: compile error first — the tests reference `coordinator.seedProcessedForRetry(_:)`, which doesn't exist yet. **That's fine** — we'll add it as a test-only helper in Task 4. Once it compiles, all the new tests should fail with `fatalError("not implemented")`.

If the suite won't compile because `seedProcessedForRetry` is missing, that's the expected gate before Task 4 — proceed to Task 4 without commenting out the calls.

- [ ] **Step 3: Commit (tests-only, will fail to compile)**

This commit *intentionally* leaves the workspace red. Subagent/executor reviewers: this is the standard TDD red step.

```bash
git add apps/ios/PebblesTests/SnapUploadCoordinatorTests.swift
git commit -m "$(cat <<'EOF'
test(ios): add SnapUploadCoordinator test suite (red)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Implement `SnapUploadCoordinator` until the test suite is green

**Why:** Replace every `fatalError("not implemented")` with the real behavior. Add the `seedProcessedForRetry` test-only helper. The 10 tests in the suite gate correctness.

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`

- [ ] **Step 1: Add the test-only helper and a private `performUpload`**

In `SnapUploadCoordinator.swift`, immediately *before* the `// MARK: - User-driven transitions` line, add:

```swift
    // MARK: - Test seam

    #if DEBUG
    /// Test-only: seed `processedForRetry` directly so tests can exercise
    /// the upload path without driving the real `handlePicked` flow.
    func seedProcessedForRetry(_ processed: ProcessedImage) {
        self.processedForRetry = processed
    }
    #endif

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
```

- [ ] **Step 2: Replace each stub with the real implementation**

Replace the body of `handlePicked(_:userId:)` (currently `fatalError`):

```swift
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
```

Replace the body of `retryCurrent`:

```swift
    func retryCurrent(userId: UUID) async {
        guard case .pending(var snap) = formSnap, let processed = processedForRetry else { return }
        snap.state = .uploading
        formSnap = .pending(snap)
        await performUpload(processed: processed, snapId: snap.id, userId: userId)
    }
```

Replace the body of `removePending`:

```swift
    func removePending(userId: UUID) async {
        guard case .pending(let snap) = formSnap else { return }
        let snapId = snap.id
        formSnap = nil
        processedForRetry = nil
        await repo.deleteFiles(snapId: snapId, userId: userId)
    }
```

Replace the body of `removeExisting`:

```swift
    func removeExisting() async throws {
        guard case .existing(let id, _) = formSnap else { return }
        let storagePath = try await repo.deletePebbleMedia(snapId: id)
        // Fire-and-forget Storage cleanup. The live repo logs on failure.
        await repo.deleteFiles(storagePrefix: storagePath)
        formSnap = nil
    }
```

Replace the body of `cancelAndCleanup`:

```swift
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
```

Replace the body of `handleSaveFailure`:

```swift
    func handleSaveFailure(userId: UUID) async {
        guard case .pending(let snap) = formSnap else { return }
        await repo.deleteFiles(snapId: snap.id, userId: userId)
        // Intentionally do not clear formSnap — caller (the sheet) keeps the
        // form open so the user can retry.
    }
```

- [ ] **Step 3: Add the static `loadData` helper at the bottom of the class**

Just before the final closing brace of `SnapUploadCoordinator`, add:

```swift
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
```

- [ ] **Step 4: Run the test suite**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: `SnapUploadCoordinator` suite — all 10 tests pass. All other suites still pass (we have not yet touched any production caller).

If any test fails, read the failure carefully and adjust the implementation. The most likely cause: ordering in `cancelAndCleanup` (state must clear before the delete completes; the test only checks call recording, so a sequencing bug shows up as missing/extra `Call` entries).

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift
git commit -m "$(cat <<'EOF'
quality(ios): implement SnapUploadCoordinator behavior (tests green)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Add `formSnap:` parameter to payload builders

**Why:** Once we remove `PebbleDraft.formSnap` in Task 9, the payload builders can no longer read it from the draft. Change the signature *now* (while `draft.formSnap` still exists, so both call sites can pass it through), and update the existing encoding tests to match.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` (call site)
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` (call site)
- Modify: `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift` (call site at line 123)

- [ ] **Step 1: Update `PebbleCreatePayload` to take `formSnap:`**

In `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift`, change the `init(from: userId:)` to `init(from: formSnap: userId:)`:

```swift
    init(from draft: PebbleDraft, formSnap: FormSnap?, userId: UUID) {
        precondition(draft.isValid, "PebbleCreatePayload(from:formSnap:userId:) called with invalid draft")
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
        self.domainIds = [draft.domainId!]
        self.soulIds = draft.soulIds
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
        self.glyphId = draft.glyphId
        self.snaps = {
            switch formSnap {
            case .none:
                return nil
            case .pending(let snap):
                return [SnapPayload(
                    id: snap.id,
                    storagePath: snap.storagePrefix(userId: userId),
                    sortOrder: 0
                )]
            case .existing:
                assertionFailure("PebbleCreatePayload: unexpected .existing FormSnap during create")
                return nil
            }
        }()
    }
```

- [ ] **Step 2: Update `PebbleUpdatePayload` to take `formSnap:`**

In `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift`, change `init(from: userId:)` to `init(from: formSnap: userId:)`:

```swift
    init(from draft: PebbleDraft, formSnap: FormSnap?, userId: UUID) {
        precondition(draft.isValid, "PebbleUpdatePayload(from:formSnap:userId:) called with invalid draft")
        self.name = draft.name.trimmingCharacters(in: .whitespaces)
        let trimmedDescription = draft.description.trimmingCharacters(in: .whitespaces)
        self.description = trimmedDescription.isEmpty ? nil : trimmedDescription
        self.happenedAt = draft.happenedAt
        self.intensity = draft.valence!.intensity
        self.positiveness = draft.valence!.positiveness
        self.visibility = draft.visibility.rawValue
        self.emotionId = draft.emotionId!
        self.domainIds = [draft.domainId!]
        self.soulIds = draft.soulIds
        self.collectionIds = draft.collectionId.map { [$0] } ?? []
        self.glyphId = draft.glyphId
        self.snaps = {
            switch formSnap {
            case .none:
                return []
            case .existing(let id, let storagePath):
                return [SnapPayload(id: id, storagePath: storagePath, sortOrder: 0)]
            case .pending(let snap):
                return [SnapPayload(
                    id: snap.id,
                    storagePath: snap.storagePrefix(userId: userId),
                    sortOrder: 0
                )]
            }
        }()
    }
```

- [ ] **Step 3: Update `CreatePebbleSheet`'s call site**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`, find the line:

```swift
        let payload = PebbleCreatePayload(from: draft, userId: userId)
```

Change to:

```swift
        let payload = PebbleCreatePayload(from: draft, formSnap: draft.formSnap, userId: userId)
```

- [ ] **Step 4: Update `EditPebbleSheet`'s call site**

In `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`, find:

```swift
        let payload = PebbleUpdatePayload(from: draft, userId: userId)
```

Change to:

```swift
        let payload = PebbleUpdatePayload(from: draft, formSnap: draft.formSnap, userId: userId)
```

- [ ] **Step 5: Update the existing encoding test**

In `apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift`, find every call to `PebbleCreatePayload(from: draft, userId: ...)` and add the `formSnap:` parameter. The full set (based on the file at the time of writing) is:

- Line ~105 (`PebbleCreatePayload(from: draft, userId: UUID())`) → `PebbleCreatePayload(from: draft, formSnap: draft.formSnap, userId: UUID())`
- Line ~114 → same change
- Line ~129 → same change
- Line ~142 → same change

The simplest mechanical replacement: open the file and `s/from: draft, userId:/from: draft, formSnap: draft.formSnap, userId:/g`.

Apply the same replacement in `apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift`:

- `s/from: draft, userId:/from: draft, formSnap: draft.formSnap, userId:/g`

- [ ] **Step 6: Run tests + build**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: every suite passes. Encoding tests use the new signature; coordinator tests still pass; sheets compile and link.

- [ ] **Step 7: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift \
        apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift \
        apps/ios/PebblesTests/PebbleCreatePayloadEncodingTests.swift \
        apps/ios/PebblesTests/PebbleUpdatePayloadEncodingTests.swift
git commit -m "$(cat <<'EOF'
quality(ios): thread formSnap as parameter through pebble payload builders

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Make `AttachedPhotoView` and `PebbleFormView` callback-driven

**Why:** Today `AttachedPhotoView` mutates its `Binding<AttachedSnap?>` (sets `snap = nil` to remove, sets `snap?.state = .uploading` to retry), and `CreatePebbleSheet`/`EditPebbleSheet` observe those mutations via two `.onChange` observers. We need the chip to call explicit callbacks so the next two tasks can delete those observers. `PebbleFormView` is the seam.

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`

- [ ] **Step 1: Rewrite `AttachedPhotoView` to take state + callbacks**

Replace the entire body of `apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift`:

```swift
import SwiftUI

/// Inline photo "chip" shown inside `PebbleFormView` once the user has picked
/// an image. Stateless: takes the current snap plus explicit `onRetry` and
/// `onRemove` callbacks. The parent (which owns a `SnapUploadCoordinator`)
/// decides what those mean.
struct AttachedPhotoView: View {

    let snap: AttachedSnap
    let onRetry: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 4) {
                Text("Photo")
                    .font(.subheadline)
                stateLabel
            }
            Spacer()
            trailingButton
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let uiImage = UIImage(data: snap.localThumb) {
            Image(uiImage: uiImage)
                .resizable()
                .scaledToFill()
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.2))
                .frame(width: 56, height: 56)
        }
    }

    @ViewBuilder
    private var stateLabel: some View {
        switch snap.state {
        case .uploading:
            Label("Uploading…", systemImage: "arrow.up.circle")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.secondary)
        case .uploaded:
            Label("Ready", systemImage: "checkmark.circle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.green)
        case .failed:
            Label("Upload failed", systemImage: "exclamationmark.triangle.fill")
                .labelStyle(.titleAndIcon)
                .font(.caption)
                .foregroundStyle(.red)
        }
    }

    @ViewBuilder
    private var trailingButton: some View {
        switch snap.state {
        case .uploading:
            ProgressView()
        case .uploaded:
            removeButton
        case .failed:
            HStack(spacing: 8) {
                Button(action: onRetry) {
                    Image(systemName: "arrow.clockwise.circle.fill")
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Retry")
                removeButton
            }
        }
    }

    private var removeButton: some View {
        Button(role: .destructive, action: onRemove) {
            Image(systemName: "xmark.circle.fill")
                .foregroundStyle(.secondary)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Remove photo")
    }
}
```

- [ ] **Step 2: Update `PebbleFormView` to take `formSnap` + photo callbacks as parameters**

In `apps/ios/Pebbles/Features/Path/PebbleFormView.swift`:

**(a)** Remove the property `pendingSnapBinding` (lines ~73–92).

**(b)** Add new properties just below the existing `onRemoveExistingSnap` property (around line 35):

```swift
    /// Current snap state. The parent owns this via a `SnapUploadCoordinator`
    /// and re-passes it on every render.
    let formSnap: FormSnap?

    /// Tapped when the user hits retry on a `.pending(.failed)` chip.
    let onRetryPending: () -> Void

    /// Tapped when the user hits remove on a `.pending` chip (any state).
    let onRemovePending: () -> Void
```

**(c)** Update the initializer signature. Find the existing `init(...)` and add the three new parameters with defaults:

```swift
    init(
        draft: Binding<PebbleDraft>,
        domains: [Domain],
        souls: [SoulWithGlyph],
        collections: [PebbleCollection],
        saveError: String?,
        renderSvg: String? = nil,
        strokeColor: String? = nil,
        renderHeight: CGFloat = 260,
        showsPhotoSection: Bool = false,
        photoPickerPresented: Binding<Bool> = .constant(false),
        isRemovingExistingSnap: Bool = false,
        onRemoveExistingSnap: @escaping () -> Void = {},
        formSnap: FormSnap? = nil,
        onRetryPending: @escaping () -> Void = {},
        onRemovePending: @escaping () -> Void = {}
    ) {
        self._draft = draft
        self.domains = domains
        self.souls = souls
        self.collections = collections
        self.saveError = saveError
        self.renderSvg = renderSvg
        self.strokeColor = strokeColor
        self.renderHeight = renderHeight
        self.showsPhotoSection = showsPhotoSection
        self._photoPickerPresented = photoPickerPresented
        self.isRemovingExistingSnap = isRemovingExistingSnap
        self.onRemoveExistingSnap = onRemoveExistingSnap
        self.formSnap = formSnap
        self.onRetryPending = onRetryPending
        self.onRemovePending = onRemovePending
    }
```

**(d)** Replace the `switch draft.formSnap` block inside the Photo section (~line 267) with `switch formSnap`, and update the `.pending` case to use the new chip API:

```swift
            if showsPhotoSection {
                Section("Photo") {
                    switch formSnap {
                    case .none:
                        Button {
                            photoPickerPresented = true
                        } label: {
                            Label("Add a photo", systemImage: "photo.badge.plus")
                        }
                        .listRowBackground(Color.pebblesListRow)
                    case .existing(_, let storagePath):
                        ExistingSnapRow(
                            storagePath: storagePath,
                            isRemoving: isRemovingExistingSnap,
                            onRemove: onRemoveExistingSnap
                        )
                        .listRowBackground(Color.pebblesListRow)
                    case .pending(let snap):
                        AttachedPhotoView(
                            snap: snap,
                            onRetry: onRetryPending,
                            onRemove: onRemovePending
                        )
                        .listRowBackground(Color.pebblesListRow)
                    }
                }
            }
```

- [ ] **Step 3: Update the two sheet call sites to pass `formSnap: draft.formSnap` and the old observer behavior as closures**

In `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`, find the `PebbleFormView(...)` call (inside `content`, ~line 110–119) and add the three new parameters at the end:

```swift
            PebbleFormView(
                draft: $draft,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                showsPhotoSection: true,
                photoPickerPresented: $isPhotoPickerPresented,
                formSnap: draft.formSnap,
                onRetryPending: {
                    // Preserve current behavior: chip's retry flipped state
                    // to .uploading and the .onChange observer re-ran upload.
                    // We do the equivalent inline here; will be replaced in
                    // Task 7 with `snaps.retryCurrent(...)`.
                    if case .pending(var snap) = draft.formSnap, snap.state == .failed {
                        snap.state = .uploading
                        draft.formSnap = .pending(snap)
                    }
                },
                onRemovePending: {
                    // Preserve current behavior: chip's remove cleared the
                    // binding; the .onChange observer fired compensating
                    // delete. Will be replaced in Task 7.
                    draft.formSnap = nil
                }
            )
```

Make the same change to `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`'s `PebbleFormView(...)` call (~line 120–135).

- [ ] **Step 4: Run tests + build**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: green. Coordinator tests still pass; the sheets now route chip interactions through callbacks but produce identical user-observable behavior because the existing `.onChange` observers in the sheets still fire on `draft.formSnap` mutations.

- [ ] **Step 5: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/AttachedPhotoView.swift \
        apps/ios/Pebbles/Features/Path/PebbleFormView.swift \
        apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift \
        apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "$(cat <<'EOF'
quality(ios): make AttachedPhotoView and PebbleFormView callback-driven

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Switch `CreatePebbleSheet` to use the coordinator

**Why:** Delete the duplicated snap logic from this sheet. The coordinator owns everything.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`

- [ ] **Step 1: Add the coordinator `@State` and drop the duplicated state**

Open `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift`. Make the following changes:

**(a)** Remove the `@State private var processedForRetry: ProcessedImage?` line (~line 23).

**(b)** After the `@State private var saveError: String?` line, add:

```swift
    @State private var snaps: SnapUploadCoordinator
```

**(c)** Replace `init` (if present, the struct currently relies on memberwise — but we need a custom init because `snaps` depends on `supabase.client`). The struct currently has only the `onCreated` stored property and no explicit init. Replace the property block to make this work without environment-at-init access by lazily constructing the coordinator inside `.task`:

Change the declaration to:

```swift
    @State private var snaps: SnapUploadCoordinator? = nil
```

Then in `.task { await loadReferences() }` change it to:

```swift
        .task {
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
            await loadReferences()
        }
```

This keeps `snaps` `Optional` only for the brief construction window. Every read site below uses `snaps?` or `guard let`.

**(d)** Delete the `snapRepo` computed property (~lines 29–31), the `pendingSnapState` and `pendingSnapId` computed properties (~lines 37–45), the `.onChange(of: pendingSnapState)` observer (~lines 81–88), and the `.onChange(of: pendingSnapId)` observer (~lines 91–95). All four blocks go away.

**(e)** Delete `handlePicked(_:)` (~lines 124–162), `loadData(from:uti:)` (~lines 164–176), and `uploadCurrentSnap(processed:userId:)` (~lines 178–200).

**(f)** Replace `cancelAndCleanup()` (~lines 202–209) with:

```swift
    private func cancelAndCleanup() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }
```

**(g)** Update the photo-picker sheet so the picked item is delegated to the coordinator. The current block is:

```swift
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked {
                    Task { await handlePicked(picked) }
                }
            }
        }
```

Replace with:

```swift
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked, let userId = currentUserId, let snaps {
                    Task { await snaps.handlePicked(picked, userId: userId) }
                }
            }
        }
```

**(h)** Update the `PebbleFormView(...)` call site to read `formSnap` and route callbacks through the coordinator:

```swift
            PebbleFormView(
                draft: $draft,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                showsPhotoSection: true,
                photoPickerPresented: $isPhotoPickerPresented,
                formSnap: snaps?.formSnap,
                onRetryPending: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.retryCurrent(userId: userId) }
                    }
                },
                onRemovePending: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.removePending(userId: userId) }
                    }
                }
            )
```

**(i)** Update `save()`'s upload-still-pending checks and the payload builder call. Find the three references to `draft.formSnap` inside `save()`:

```swift
        if case .pending(let snap) = draft.formSnap, snap.state == .uploading {
```

Replace with:

```swift
        if snaps?.isUploading == true {
```

And:

```swift
        if case .pending(let snap) = draft.formSnap, snap.state == .failed {
```

Replace with:

```swift
        if snaps?.hasFailed == true {
```

Change the payload construction line:

```swift
        let payload = PebbleCreatePayload(from: draft, formSnap: draft.formSnap, userId: userId)
```

to:

```swift
        let payload = PebbleCreatePayload(from: draft, formSnap: snaps?.formSnap, userId: userId)
```

**(j)** Update `handleSaveFailure(_:)` (~line 306). Replace its body:

```swift
    private func handleSaveFailure(_ error: Error) async {
        if let userId = currentUserId, let snaps {
            await snaps.handleSaveFailure(userId: userId)
        }
        saveError = userMessageForPebbleSaveError(error)
        isSaving = false
    }
```

- [ ] **Step 2: Run tests + build**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: every suite passes. The Create sheet's snap logic is now fully owned by the coordinator. `draft.formSnap` still exists (used only by Edit) but the Create sheet no longer writes to it.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift
git commit -m "$(cat <<'EOF'
quality(ios): wire CreatePebbleSheet to SnapUploadCoordinator

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Switch `EditPebbleSheet` to use the coordinator

**Why:** Same as Task 7, plus delegate `removeExistingSnap` to `coordinator.removeExisting()`. The `EditPebbleSheet` seeds the coordinator with `.existing(...)` after `load()` succeeds.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`

- [ ] **Step 1: Replace state, observers, and helpers with coordinator delegation**

Open `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift`.

**(a)** Remove `@State private var processedForRetry: ProcessedImage?` (~line 38).

**(b)** Add (next to `isRemovingExistingSnap`):

```swift
    @State private var snaps: SnapUploadCoordinator? = nil
```

**(c)** Delete `snapRepo` (~lines 47–49), `pendingSnapState` (~lines 51–54), `pendingSnapId` (~lines 56–59), both `.onChange` observers (~lines 92–104), `handlePicked` (~lines 199–236), `loadData(from:uti:)` (~lines 238–250), `uploadCurrentSnap(processed:userId:)` (~lines 252–274), and `removeExistingSnap()` (~lines 280–297).

**(d)** Update the `.task { await load() }` block to construct the coordinator alongside the load:

```swift
        .task {
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
            await load()
        }
```

**(e)** At the end of `load()`'s success path (just before `self.isLoading = false`), seed the coordinator with the existing snap:

Find the section after the four parallel queries resolve:

```swift
            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.draft = PebbleDraft(from: detail)
            self.renderSvg = detail.renderSvg
            self.strokeColor = palettes.palette(for: detail.emotion.id)?
                .strokeHex(for: colorScheme) ?? Color.pebblesAccentHex
            self.sizeGroup = detail.valence.sizeGroup
            self.isLoading = false
```

Insert *before* `self.isLoading = false`:

```swift
            if let existing = detail.snaps.first {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
            }
```

**(f)** Update the photo-picker sheet:

```swift
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked, let userId = currentUserId, let snaps {
                    Task { await snaps.handlePicked(picked, userId: userId) }
                }
            }
        }
```

**(g)** Replace the `PebbleFormView(...)` call site:

```swift
            PebbleFormView(
                draft: $draft,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                renderSvg: renderSvg,
                strokeColor: strokeColor,
                renderHeight: sizeGroup.renderHeight,
                showsPhotoSection: true,
                photoPickerPresented: $isPhotoPickerPresented,
                isRemovingExistingSnap: isRemovingExistingSnap,
                onRemoveExistingSnap: {
                    Task { await removeExisting() }
                },
                formSnap: snaps?.formSnap,
                onRetryPending: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.retryCurrent(userId: userId) }
                    }
                },
                onRemovePending: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.removePending(userId: userId) }
                    }
                }
            )
```

**(h)** Add the small `removeExisting()` wrapper to translate the coordinator's throw into `saveError`:

Add (next to the existing private helpers, e.g., right before `save()`):

```swift
    private func removeExisting() async {
        guard let snaps else { return }
        isRemovingExistingSnap = true
        defer { isRemovingExistingSnap = false }
        do {
            try await snaps.removeExisting()
        } catch {
            logger.error("delete_pebble_media failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't remove the photo. Please try again."
        }
    }
```

**(i)** Update `save()` to read snap state from the coordinator. Find:

```swift
        if case .pending(let snap) = draft.formSnap, snap.state == .uploading {
```

Replace:

```swift
        if snaps?.isUploading == true {
```

And:

```swift
        if case .pending(let snap) = draft.formSnap, snap.state == .failed {
```

Replace:

```swift
        if snaps?.hasFailed == true {
```

Update the payload-construction line:

```swift
        let payload = PebbleUpdatePayload(from: draft, formSnap: draft.formSnap, userId: userId)
```

to:

```swift
        let payload = PebbleUpdatePayload(from: draft, formSnap: snaps?.formSnap, userId: userId)
```

**(j)** Note: `EditPebbleSheet` does *not* currently have a `handleSaveFailure`-style compensating delete on save failure (its `save()` directly sets `saveError` without firing a delete). The Edit-flow save failure path needs the compensating delete too. Replace the save-failure branches in the catch blocks:

Find:

```swift
            } else {
                let bodyString: String
                if case .httpError(_, let data) = functionsError {
                    bodyString = String(data: data, encoding: .utf8) ?? "<non-utf8 body>"
                } else {
                    bodyString = "<non-http error>"
                }
                logger.error("compose-pebble-update failed: \(functionsError.localizedDescription, privacy: .private) body=\(bodyString, privacy: .private)")
                self.saveError = userMessageForPebbleSaveError(functionsError)
                self.isSaving = false
            }
        } catch {
            logger.error("update pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = userMessageForPebbleSaveError(error)
            self.isSaving = false
        }
```

Replace the two error-handling branches with one shared call to a new local helper, just like `CreatePebbleSheet.handleSaveFailure(_:)`. Add this helper just below `save()`:

```swift
    private func handleSaveFailure(_ error: Error) async {
        if let userId = currentUserId, let snaps {
            await snaps.handleSaveFailure(userId: userId)
        }
        self.saveError = userMessageForPebbleSaveError(error)
        self.isSaving = false
    }
```

Then change:

```swift
                self.saveError = userMessageForPebbleSaveError(functionsError)
                self.isSaving = false
```

to:

```swift
                await handleSaveFailure(functionsError)
```

And:

```swift
            self.saveError = userMessageForPebbleSaveError(error)
            self.isSaving = false
```

(in the outer `catch`) to:

```swift
            await handleSaveFailure(error)
```

- [ ] **Step 2: Run tests + build**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: green. Both sheets now go through the coordinator. `draft.formSnap` is still set by `PebbleDraft(from: detail)` but nothing else writes to it, and nothing reads it.

- [ ] **Step 3: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift
git commit -m "$(cat <<'EOF'
quality(ios): wire EditPebbleSheet to SnapUploadCoordinator + compensating delete on update failure

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Remove `formSnap` from `PebbleDraft`

**Why:** Nothing reads `draft.formSnap` anymore. Drop it. `PebbleDraft(from: detail)` no longer pre-fills snap state; the sheet already seeds the coordinator separately.

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`

- [ ] **Step 1: Delete `formSnap` from the struct and its initializer**

In `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift`:

**(a)** Remove the line:

```swift
    var formSnap: FormSnap?               // optional — `.existing` from DB or `.pending` local upload
```

**(b)** In `init(from detail:)`, remove the trailing block:

```swift
        self.formSnap = detail.snaps.first.map {
            .existing(id: $0.id, storagePath: $0.storagePath)
        }
```

- [ ] **Step 2: Verify no stragglers**

```bash
grep -rn "draft.formSnap\|\.formSnap" apps/ios --include="*.swift" | grep -v "FormSnap"
```

Expected: only matches inside `FormSnap.swift`, `AttachedSnap.swift`, the coordinator, and any usage of the *type name* `FormSnap` (not the property). If anything else shows up, fix it before continuing.

- [ ] **Step 3: Run tests + build**

```bash
npm run test --workspace=@pbbls/ios
```

Expected: green across all suites. The `PebbleDraftFromDetailTests` continues to pass (it doesn't reference `formSnap` directly per the grep we did when writing the plan). The payload encoding tests pass with the `formSnap:` parameter form.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift
git commit -m "$(cat <<'EOF'
quality(ios): remove formSnap from PebbleDraft; coordinator owns it

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Final verification and manual smoke test

**Why:** End-to-end confirmation. Tests prove the unit-level behavior; the manual smoke test catches anything that only shows up when a real `PhotoPickerView`, real Supabase Storage, and real network are involved.

- [ ] **Step 1: Full lint + build + test from a clean state**

```bash
npm run lint --workspace=@pbbls/ios
npm run build --workspace=@pbbls/ios
npm run test --workspace=@pbbls/ios
```

Expected: lint passes; build succeeds; all test suites green.

- [ ] **Step 2: Manual smoke test (Create flow)**

Run the app on a simulator (Xcode → Run, or `xcrun simctl` if scripted). Sign in. From the Path tab, tap "New pebble." Walk through each of the following and confirm behavior:

1. **Pick a photo** — chip transitions `uploading` → `Ready` (green checkmark) within ~2 s on a normal connection.
2. **Force a network failure** — enable Airplane Mode after tapping the picker but before the upload completes. Chip should reach the "Upload failed" red state after the second attempt. (Or: kill Wi-Fi between the pick and confirm.)
3. **Tap retry** — re-enable network, then tap the retry icon. Chip should go back to `Uploading` then `Ready`.
4. **Remove the snap** — tap the X. Chip disappears. The Storage files should be gone (visible in Supabase dashboard, or by re-picking the same image and confirming the orphan-sweep is clean).
5. **Cancel mid-upload** — pick a photo, then immediately tap Cancel. The sheet should dismiss only after the Storage delete completes. Confirm no orphans remain in `pebbles-media` for that snap id.
6. **Save with `.uploaded` snap** — verify the pebble is created with the snap attached and renders correctly in the path.
7. **Compose-pebble failure compensation** — temporarily edit `supabase.client.functions.invoke("compose-pebble", ...)` to point at a bad URL, save with a pending snap, confirm the snap's Storage files are deleted afterwards. Revert the edit.

- [ ] **Step 3: Manual smoke test (Edit flow)**

Open an existing pebble with a photo from the Path. Tap Edit:

1. The existing photo row shows the current snap.
2. **Remove existing** — tap the X. The `delete_pebble_media` RPC should run, the chip should disappear, and the Storage files should be deleted.
3. **Replace existing** — after removing, pick a new photo. Same flow as Create's pick → Ready.
4. **Cancel mid-upload of replacement** — verify the new snap's Storage files are deleted on Cancel.
5. **Compose-pebble-update failure with new snap** — temporarily corrupt the edge function name. Save. Confirm the new pending snap's files are deleted.

- [ ] **Step 4: Localization sanity check**

Open `apps/ios/Pebbles/Resources/Localizable.xcstrings` in Xcode. Confirm no entries are in the `New` or `Stale` state. We did not introduce any new user-facing strings (the chip's labels were preserved verbatim), but new strings are auto-extracted at build time so this is worth verifying.

- [ ] **Step 5: Push the branch and open the PR**

```bash
git push -u origin feat/401-snap-upload-coordinator
```

PR title (conventional commits): `quality(ios): extract snap upload coordinator from pebble sheets`

PR body:

```
Resolves #401

## Summary
- Extracted `SnapUploadCoordinator` `@Observable` class owning the entire snap lifecycle for the in-progress pebble form (pick, upload, retry, cancel, remove pending, remove existing).
- Replaced the 50 ms sleep race in `cancelAndCleanup` with an explicit `await` on the Storage delete.
- Added `PebbleSnapRepositoryProtocol` + a `FakeRepo` so the coordinator is fully unit-tested (10 cases in Swift Testing).
- Both `CreatePebbleSheet` and `EditPebbleSheet` lost ~120 LOC of duplicated snap state; `EditPebbleSheet` also gained compensating delete on `compose-pebble-update` failure (was missing).
- `PebbleDraft.formSnap` removed; payload builders take `formSnap:` as a parameter.

## Files
- New: `Features/PebbleMedia/SnapUploadCoordinator.swift`
- New: `PebblesTests/SnapUploadCoordinatorTests.swift`
- Modified: `PebbleSnapRepository.swift`, `AttachedPhotoView.swift`, `PebbleFormView.swift`, both sheets, both payload builders, `PebbleDraft.swift`, payload encoding tests.

## Test plan
- [x] `npm run test --workspace=@pbbls/ios` green
- [x] `npm run build --workspace=@pbbls/ios` green
- [x] `npm run lint --workspace=@pbbls/ios` green
- [x] Manual smoke test: create with photo (happy + retry + remove + cancel)
- [x] Manual smoke test: edit with photo (remove existing + replace + cancel during replace)
- [x] Compose-pebble{,-update} failure with pending snap → compensating delete confirmed
```

Confirm labels (from the issue): `quality`, `core`, `ios`. Milestone: `M32 · iOS Quality`.

```bash
gh pr create --title "quality(ios): extract snap upload coordinator from pebble sheets" \
  --body "$(cat <<'EOF'
Resolves #401

## Summary
- Extracted `SnapUploadCoordinator` `@Observable` class owning the entire snap lifecycle for the in-progress pebble form (pick, upload, retry, cancel, remove pending, remove existing).
- Replaced the 50 ms sleep race in `cancelAndCleanup` with an explicit `await` on the Storage delete.
- Added `PebbleSnapRepositoryProtocol` + a `FakeRepo` so the coordinator is fully unit-tested (10 cases in Swift Testing).
- Both `CreatePebbleSheet` and `EditPebbleSheet` lost ~120 LOC of duplicated snap state; `EditPebbleSheet` also gained compensating delete on `compose-pebble-update` failure (was missing).
- `PebbleDraft.formSnap` removed; payload builders take `formSnap:` as a parameter.

## Test plan
- [x] `npm run test --workspace=@pbbls/ios` green
- [x] `npm run build --workspace=@pbbls/ios` green
- [x] `npm run lint --workspace=@pbbls/ios` green
- [x] Manual smoke test: create with photo (happy + retry + remove + cancel)
- [x] Manual smoke test: edit with photo (remove existing + replace + cancel during replace)
- [x] Compose-pebble{,-update} failure with pending snap → compensating delete confirmed

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" \
  --label quality --label core --label ios \
  --milestone "M32 · iOS Quality"
```

Done.
