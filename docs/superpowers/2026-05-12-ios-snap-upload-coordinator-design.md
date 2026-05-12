# iOS: SnapUploadCoordinator extraction

**Issue:** [#401 — Extract snap upload logic shared by CreatePebbleSheet and EditPebbleSheet](https://github.com/Bohns/pbbls/issues/401)
**Milestone:** M32 · iOS Quality
**Date:** 2026-05-12

## Problem

`CreatePebbleSheet` and `EditPebbleSheet` contain ~200 lines of nearly identical snap upload, retry, and compensating-delete logic — the safety-critical Storage cleanup path is duplicated verbatim. Future fixes will need to be applied in two places, and the two copies will drift.

Specifically duplicated:

- `handlePicked(_:)`, `loadData(from:uti:)`, `uploadCurrentSnap(processed:userId:)` — verbatim
- `pendingSnapState` / `pendingSnapId` computed properties — verbatim
- `snapRepo` computed property — verbatim
- `.onChange(of: pendingSnapState)` retry observer — verbatim
- `.onChange(of: pendingSnapId)` compensating-delete observer — verbatim
- `processedForRetry` `@State` — verbatim

The differences between the two sheets are limited to `loadReferences()` vs `load()` and the final RPC call in `save()` (`compose-pebble` vs `compose-pebble-update`).

Two related defects are folded into this extraction:

1. **`cancelAndCleanup()` uses a 50 ms sleep before `dismiss()`** to give the `.onChange` observer time to fire the Storage delete. The delete is not awaited, so if the network call exceeds 50 ms (it usually does), the view tears down mid-request and the file is orphaned. Safety-critical.
2. **Snap state is split** across `draft.formSnap` (inside `PebbleDraft`) and `processedForRetry` (a `@State` on the sheet). Mutations from two paths (user action + `.onChange` reaction) hit overlapping state, which is exactly the shape that hides bugs.

## Goal

A single `@Observable` `SnapUploadCoordinator` owns the entire snap lifecycle for an in-progress pebble form. Both sheets delegate to it. Storage side effects are explicitly awaited. The coordinator is unit-tested behind a `PebbleSnapRepository` protocol.

## Non-goals

- Changing the upload semantics (retry count, backoff duration, two-file Storage layout) — preserved as-is.
- Changing `ImagePipeline` or the photo-picker UI.
- Adding new snap features (multiple snaps per pebble, reordering, etc.).
- Touching the web app's equivalent flow.

## Design

### New type

`apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift`:

```swift
@MainActor
@Observable
final class SnapUploadCoordinator {
    // Source of truth for all snap state in the form
    private(set) var formSnap: FormSnap?
    private(set) var processedForRetry: ProcessedImage?

    private let repo: PebbleSnapRepositoryProtocol
    private let retryDelay: Duration
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-coordinator")

    init(
        repo: PebbleSnapRepositoryProtocol,
        initialSnap: FormSnap? = nil,
        retryDelay: Duration = .seconds(2)
    )

    // Derived state for the sheet to consult
    var isUploading: Bool { /* .pending(_, .uploading) */ }
    var hasFailed: Bool   { /* .pending(_, .failed) */ }
    var isBlocking: Bool  { isUploading || hasFailed }
    func pendingSnapForPayload() -> AttachedSnap?  // nil unless .pending(.uploaded)

    // User-driven transitions
    func handlePicked(_ picked: PhotoPickerView.PickedItem, userId: UUID) async
    func retryCurrent(userId: UUID) async       // chip's "retry" action calls this directly
    func removePending(userId: UUID) async      // chip's "remove" action for .pending
    func removeExisting() async throws          // chip's "remove" action for .existing

    // Sheet lifecycle hooks
    func cancelAndCleanup(userId: UUID) async   // explicitly awaits Storage delete; no sleep
    func handleSaveFailure(userId: UUID) async  // compensating delete after compose-pebble failure
}
```

### Repository protocol

Extract a protocol so the coordinator can be unit-tested. Existing `PebbleSnapRepository` becomes the live conformance. The protocol adds one method to keep the coordinator from touching the raw Supabase client:

```swift
protocol PebbleSnapRepositoryProtocol {
    func uploadProcessed(_ processed: ProcessedImage, snapId: UUID, userId: UUID) async throws
    func deleteFiles(snapId: UUID, userId: UUID) async
    func deleteFiles(storagePrefix: String) async
    func deletePebbleMedia(snapId: UUID) async throws -> String  // returns storage_path; calls delete_pebble_media RPC
}
```

`deletePebbleMedia` is new: it wraps the `delete_pebble_media` RPC that `EditPebbleSheet.removeExistingSnap()` currently calls directly on the client.

### State changes

**`PebbleDraft`** loses `formSnap`. The struct becomes pure form data. `PebbleDraft(from: detail)` no longer pre-fills snap state — that pre-fill moves to the sheet after `load()` completes.

**`PebbleCreatePayload(from: draft, formSnap:, userId:)`** and **`PebbleUpdatePayload(from: draft, formSnap:, userId:)`** take an additional `formSnap: FormSnap?` parameter and switch on it exactly the same way they currently switch on `draft.formSnap`:

- `PebbleCreatePayload`: handles `.none` → `nil` and `.pending` → `[SnapPayload]`; keeps the `assertionFailure` for `.existing`.
- `PebbleUpdatePayload`: handles `.none` → `[]`, `.existing` → `[SnapPayload]`, `.pending` → `[SnapPayload]`.

This preserves the existing semantics verbatim — only the source of the `FormSnap` value moves from `draft` to a parameter supplied by the sheet (which reads it from `snaps.formSnap`).

**`PebbleFormView`** swaps `draft.formSnap` reads for explicit parameters:
- `formSnap: FormSnap?`
- `isRemovingExistingSnap: Bool`
- `onRetryPending: () -> Void`
- `onRemovePending: () -> Void`
- `onRemoveExisting: () -> Void`

The `.onChange` observers that currently translate state mutations into actions are deleted — the chip already knows when the user taps retry vs remove, so it can call the callback directly.

### Behavior preservation

- **Initial upload:** `handlePicked` → load bytes → `Task.detached` `ImagePipeline.process` → set `formSnap = .pending(.uploading)`, retain `processedForRetry` → call internal `performUpload`. Identical to today.
- **Retry on failure:** internal `performUpload` does first attempt → on throw, `try? await Task.sleep(retryDelay)` → second attempt. On success → `.uploaded`. On second failure → `.failed`. Identical to today, with `retryDelay` parameterized for testability.
- **User-tapped retry from `.failed`:** chip calls `coordinator.retryCurrent(userId:)`. Replaces the `.onChange(of: pendingSnapState)` observer. Sets state back to `.uploading`, re-runs `performUpload` with the retained `processedForRetry`.
- **User-tapped remove pending:** chip calls `coordinator.removePending(userId:)`. Captures current snap id, sets `formSnap = nil`, clears `processedForRetry`, awaits `repo.deleteFiles(snapId:userId:)`. Replaces the `.onChange(of: pendingSnapId)` observer.
- **User-tapped remove existing:** chip calls `coordinator.removeExisting()`. Calls `repo.deletePebbleMedia(snapId:)`, then fires fire-and-forget `repo.deleteFiles(storagePrefix:)` with the returned path, then sets `formSnap = nil`. Throws on RPC failure so the sheet can surface `saveError`. Identical to today's `removeExistingSnap()`.
- **Cancel with pending snap:** sheet calls `await snaps.cancelAndCleanup(userId:)` then `dismiss()`. The coordinator captures the pending snap id, sets `formSnap = nil`, *awaits* `repo.deleteFiles(snapId:userId:)`. No sleep, no race. If the delete itself fails it's logged at `.error` and `cancelAndCleanup` still returns — Cancel must not be un-cancellable.
- **Save failure with pending snap:** sheet calls `await snaps.handleSaveFailure(userId:)` before setting `saveError`. The coordinator awaits the compensating delete. Identical compensating-delete behavior, deduplicated.

### Sheet wiring

`CreatePebbleSheet`:

```swift
@State private var snaps = SnapUploadCoordinator(
    repo: PebbleSnapRepository(client: supabase.client)
)
```

`EditPebbleSheet` seeds the initial snap *after* `load()` succeeds:

```swift
if let snap = detail.snap {
    snaps = SnapUploadCoordinator(
        repo: PebbleSnapRepository(client: supabase.client),
        initialSnap: .existing(id: snap.id, storagePath: snap.storagePath)
    )
}
```

(Reassigning `@State` is fine here — the assignment runs once after async load, before the form renders.)

Both sheets' `save()` reads `snaps.isBlocking` for the upload-still-in-flight / failed checks, and `snaps.pendingSnapForPayload()` for payload construction.

Both sheets' Cancel toolbar item becomes:

```swift
Button("Cancel") {
    Task {
        if let userId = currentUserId {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }
}
```

### File touch list

- **New:** `apps/ios/Pebbles/Features/PebbleMedia/SnapUploadCoordinator.swift` (~180 LOC)
- **New:** `apps/ios/PebblesTests/SnapUploadCoordinatorTests.swift` (Swift Testing)
- **Modified:** `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift` — add protocol conformance, add `deletePebbleMedia(snapId:)` method
- **Modified:** `apps/ios/Pebbles/Features/Path/Models/PebbleDraft.swift` — remove `formSnap`
- **Modified:** `apps/ios/Pebbles/Features/Path/Models/PebbleCreatePayload.swift` — take snap as parameter
- **Modified:** `apps/ios/Pebbles/Features/Path/Models/PebbleUpdatePayload.swift` — take snap as parameter
- **Modified:** `apps/ios/Pebbles/Features/Path/PebbleFormView.swift` — formSnap and callbacks as parameters
- **Modified:** `apps/ios/Pebbles/Features/Path/CreatePebbleSheet.swift` — delete ~120 LOC, delegate to coordinator
- **Modified:** `apps/ios/Pebbles/Features/Path/EditPebbleSheet.swift` — delete ~120 LOC, delegate to coordinator

## Testing

Per `apps/ios/CLAUDE.md`: Swift Testing in `PebblesTests/`, protocol extraction is justified now because tests need to fake the repo.

`SnapUploadCoordinatorTests`:

1. `handlePicked` happy path → transitions `nil → .pending(.uploading) → .pending(.uploaded)`; `uploadProcessed` called once
2. First upload throws, retry succeeds → ends `.uploaded`; `uploadProcessed` called twice
3. Both attempts throw → ends `.pending(.failed)`; `processedForRetry` retained
4. `retryCurrent` from `.failed` → `.uploading → .uploaded`; `uploadProcessed` called once more
5. `removePending` → state `nil`; `deleteFiles(snapId:userId:)` called once with correct ids; `processedForRetry` cleared
6. `cancelAndCleanup` with pending snap → asserts `deleteFiles` *awaited* before return (fake records `awaitOrder`)
7. `cancelAndCleanup` with delete failure → returns normally, error logged, `formSnap` cleared
8. `handleSaveFailure` with pending snap → compensating delete fired
9. `removeExisting` happy path → `deletePebbleMedia` called, storage cleanup fired, state `nil`
10. `removeExisting` RPC failure → throws, state retained

Fake `PebbleSnapRepositoryProtocol` records call order and lets each method be stubbed to throw. `retryDelay: .milliseconds(1)` in tests keeps the suite fast.

## Risks

- **`PebbleDraft` API change ripples.** Anything currently reading `draft.formSnap` (form view, payload builders, possibly other callers) must be updated. Need to grep before implementation to confirm the touch list is complete.
- **`@State` reassignment in `EditPebbleSheet` after load.** Legal but uncommon — the alternative is making `initialSnap` settable on the coordinator post-init (a `seedExisting(_:)` method). Either works; implementation can pick whichever reads cleaner.
- **Existing tests, if any, that depend on `PebbleDraft.formSnap`.** Need to check during implementation; the iOS test suite is small today.

## Out of scope (explicit)

- Issue #274 (consolidating `PebbleIdPartial` into `ComposePebbleResponse`) — separate quality issue.
- Web equivalents of any of this.
- Adding multiple-snaps support.

## Done when

- `SnapUploadCoordinator` exists and is the single source of truth for snap state in the form layer.
- `CreatePebbleSheet` and `EditPebbleSheet` no longer contain `handlePicked`, `loadData`, `uploadCurrentSnap`, `processedForRetry`, or either `.onChange` snap observer.
- `cancelAndCleanup` awaits the Storage delete explicitly (no `Task.sleep` hack).
- `SnapUploadCoordinatorTests` covers the ten cases above and passes.
- `npm run lint --workspace=apps/ios` (or equivalent iOS scoped check) is green; the iOS app builds.
