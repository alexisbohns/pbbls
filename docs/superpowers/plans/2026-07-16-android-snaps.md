# Android snaps — pipeline, form photo section, banner reveal

> Milestone **M42 · Android Snaps**, issues #580 (A · pipeline/coordinator/repository), #581 (B · form photo section), #582 (C · banner reveal); design doc `docs/superpowers/specs/2026-07-16-android-snaps-design.md` (#579, D1–D8). One plan document — the sub-projects landed as one continuous chain under the merge-on-green mandate.

## Approach

Port the iOS PebbleMedia stack 1:1: a JVM-testable pipeline core (quality ladder pure over a pluggable encoder — D2), the `SnapUploadCoordinator` state machine verbatim (D3, injectable retry delay + logger for JVM tests), the repository write half joining M39's read half (D4), the D5 payload rules (create: `snaps` absent unless uploaded, via the write-Json's `encodeDefaults=false`; update: always-echo), the shared form Photo section with `AttachedPhotoView`/`ExistingSnapRow`, and the two-phase detail-banner reveal with `BannerAspect` buckets (D7 — no appear-animation gate on Android yet).

## Deliverables (as landed)

- **A (#580):** `features/pebblemedia/models/FormSnap.kt` (+`AttachedSnap`), `ImagePipelineCore.kt` (budgets + `encodeWithinBudget`), `ImagePipeline.kt` (`ImageDecoder` downsample-at-decode, `Bitmap.compress` strips metadata), `SnapUploadCoordinator.kt`, `PebbleSnapRepository` write half + `SnapWriteRepositing`; `PebbleCreatePayload.snaps` (absent-when-null semantics, pinned in `PebblePayloadTest`); `ImagePipelineCoreTest` + `SnapUploadCoordinatorTest` (covers the iOS suite incl. exact-call assertions, the removal-mid-upload race, and RPC-failure retention).
- **B (#581):** Photo section on the shared `PebbleForm` (after Optional, before the inline error — iOS order) with `AddPhotoRow`/`AttachedPhotoView`/`ExistingSnapRow`; `PickVisualMedia` wiring + save gates with distinct copy per state ("Photo is still uploading." / "Photo upload failed. Retry or remove it.") in Create and Edit; edit seeds `detail.sortedSnaps.first` as `FormSnap.Existing`; remove-existing = eager `delete_pebble_media` with inline "Couldn't remove the photo." error and spinner state; cancel paths run `cancelAndCleanup`; save failure runs `handleSaveFailure` (keeps the chip — iOS parity); `PebblesSuccess` color + six state icons; strings ×12 en/fr; `MediaScreenshots` gallery.
- **C (#582):** `BannerAspect` + midpoint-boundary test; `PebbleReadBanner` two-phase reveal (bottom-aligned slot, photo bucket-framed with 60dp overlap + rounded backdrop behind the pebble, original rendition via `SnapURLCache` + Coil `execute` so the bucket is chosen from intrinsic size before layout, remove-animations honored, all failures stay Phase 1); `PebbleReadView` passes `sortedSnaps.firstOrNull()?.storagePath`.

## Verification

CI green (ktlint, unit suites incl. the 3 new test classes, screenshot artifact); on device (maintainer): attach on create → photo on web/iOS; edit round-trips unrelated saves without touching the photo; remove-existing clears everywhere; cancel mid-upload leaves no orphaned storage; web-attached photo reveals on the Android banner in the right bucket; fr pass.

## Lessons learned

- (fill at review)
