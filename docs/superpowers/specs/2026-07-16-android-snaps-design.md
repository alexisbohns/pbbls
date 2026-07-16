# Android Snaps — photo attach, upload pipeline, banner reveal

> Milestone **M42 · Android Snaps** (issues #579–#582). Reference: `docs/superpowers/specs/2026-07-16-android-parity-audit.md` ("Pebble media / snaps"); iOS sources under `apps/ios/Pebbles/Features/PebbleMedia/` + `Features/Path/Read/BannerAspect.swift`/`PebbleReadBanner.swift`.

## Goal

Give Android the write half of pebble media, 1:1 with iOS: pick a photo in the create/edit form, compress it client-side, upload both renditions to the private `pebbles-media` bucket with compensating deletes on every abandon path, round-trip the `snaps` payload correctly through `create_pebble`/`update_pebble`, remove an existing photo via `delete_pebble_media`, and reveal the photo behind the detail read banner in the aspect bucket nearest the source image. The milestone ends with a cross-surface round-trip: attach on Android → visible on web/iOS; attach on web → revealed on Android's detail banner.

## Non-goals

- Multi-photo. iOS's form holds **at most one** snap (`FormSnap`); the server quota allows more but no client exposes it. Match, don't exceed.
- Camera capture, full-screen photo viewer, orphan-sweep job (iOS comments reference a follow-up sweep — cross-surface, not this milestone).
- The pebble appear animation (polish bucket) — its absence simplifies the reveal gate (D7).

## Scope — sub-projects

- **A (#580)** — pipeline + coordinator + repository write/delete half. Logic only, JVM-tested.
- **B (#581)** — form Photo section on the shared `PebbleForm` (create + edit), existing-snap row, payload echo correctness. Blocked by A.
- **C (#582)** — detail banner two-phase reveal with `BannerAspect`. Read-side only; parallel to A/B.

## Core design decisions

- **D1 — One photo per pebble; `FormSnap` ports as a sealed interface.** `Existing(id, storagePath)` | `Pending(AttachedSnap)`, with `AttachedSnap(id, localThumb: ByteArray/Bitmap, state: UPLOADING|UPLOADED|FAILED)`. Snap ids are client-generated UUIDs (lowercase) that become both the Storage folder and `snaps.id`.
- **D2 — Pipeline budgets are the iOS constants, verbatim.** Original: 1024 px long edge, ≤1 MB, start quality 0.85; thumb: 420 px, ≤300 KB, start 0.75; up to 3 quality steps of −0.1, then a hard `TooLargeAfterResize` failure. Decode via `ImageDecoder` (applies EXIF orientation); re-encode via `Bitmap.compress(JPEG)` (writes no metadata — EXIF/GPS stripped inherently). The quality-ladder/byte-cap logic extracts into a pure function over a pluggable `(quality) -> ByteArray` encoder so the ladder is JVM-testable without `android.graphics`; the bitmap wrapper itself is covered by the on-device pass (no Robolectric — repo rule).
- **D3 — Coordinator semantics are ported verbatim from `SnapUploadCoordinator`.** One auto-retry after a 2 s delay, then `FAILED` (processed bytes retained for a user-tapped retry); state mutations guard on snap-id match so a removal mid-upload can't resurrect a chip; `isBlocking` (uploading or failed) gates Save; `removePending`/`cancelAndCleanup` fire best-effort compensating Storage deletes (logged, never thrown); `handleSaveFailure` deletes the uploaded files but **keeps** the form snap so the user can retry the save; `removeExisting` calls `delete_pebble_media` (throws to the UI on failure) and then fire-and-forget Storage cleanup of the returned `storage_path`. The retry delay is a constructor parameter so tests run at zero delay.
- **D4 — Repository write half joins the existing read half.** Same `PebbleSnapRepository`: parallel upload of `{prefix}/original.jpg` + `{prefix}/thumb.jpg` (`contentType image/jpeg`), best-effort `remove` by snap-id or by prefix, `delete_pebble_media(p_snap_id) returns text`. Prefix is `{user_id}/{snap_id}` **lowercased** (bucket RLS compares the first segment to `auth.uid()::text`). A `SnapWriteRepositing` interface is extracted for the coordinator tests (the M38 rule: extract the fake's interface when a test needs it).
- **D5 — Payload rules.** Create: `snaps` key present **only** when a pending snap reached `UPLOADED` — `[{id, storage_path: prefix, sort_order: 0}]`; absent otherwise (Android's create payload gains the optional field). Update: `snaps` **always** present — an unchanged existing snap echoes `{id, storage_path}` verbatim, a new upload sends its fresh pair, no snap sends `[]` (which deletes server-side; `update_pebble` replaces on key present). This is exactly the always-echo contract M39 shipped; B's tests pin the add/keep/remove matrix.
- **D6 — The coordinator is form-scoped, not app-level.** Constructed with `remember` inside Create/Edit screens (holding the repository from the service graph), like iOS's sheet-owned `@State` coordinator — a pebble form's in-flight upload dies with the form, which is what the compensating-delete lifecycle wants. No CompositionLocal.
- **D7 — Banner reveal gates only on the photo, bucket from intrinsic size.** iOS gates phase 2 on photo-decoded AND appear-animation-finished; Android has no appear animation yet (polish bucket), which iOS also handles ("static pebble → reveal as soon as the photo is ready") — port that branch. Load the **original** rendition via the existing `SnapURLCache`/Coil; on success, compute `BannerAspect.nearest(width/height)` (16:9 / 4:3 / 1:1 — portrait buckets to square with no special case) and ease-out crossfade + height-settle over 450 ms (250 ms under reduce-motion, read from the platform animator scale). Photo-less pebbles never enter phase 2 — the banner is unchanged.
- **D8 — Copy set.** Photo section: "Photo", "Uploading…", "Ready", "Upload failed", "Saved", retry/remove a11y labels, and a photo-picker row label; all en/fr. Save-blocking needs no new copy (the Save button simply disables, iOS parity).

## Risks

1. **Bitmap memory on large sources** — `ImageDecoder.setTargetSampleSize` downsamples at decode; never load full-resolution bitmaps just to shrink them.
2. **supabase-kt storage API drift** — upload/remove/createSignedUrls names differ from swift-supabase; the read half (M39) already pins the idioms.
3. **Quota errors** — `pebble_save_error_media_quota` (M39 D16) already maps the edge-function error; B must not introduce a second path.

## Verification

CI green → JVM tests (ladder budgets, coordinator transitions incl. failure injection, payload matrix, aspect buckets) → screenshot gallery (photo section states; banner per bucket) → on device: attach on create → visible on web; edit keeps the photo on unrelated saves (echo); remove existing → gone everywhere; cancel mid-upload → no orphaned storage objects; web-attached photo reveals on Android detail in the right bucket; fr pass.

## Arkaik

`V-record` / `V-pebble-edit` (photo section), `V-pebble-detail` (banner reveal) — android notes when B/C land; `F-record-pebble` unchanged in shape. Validate on every bundle edit.
