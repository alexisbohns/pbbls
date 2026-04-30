# iOS Pebble Read View — Improve Picture Display (Issue #335)

## Context

The pebble read view shows the pebble's first attached photo as a 16:9 banner with the pebble shape overlapping its bottom edge. Two problems with the current behavior:

1. **The photo's load disturbs the pebble's stroke animation.** `AsyncImage` resolves whenever the bytes arrive — often during the 1.8s stroke animation — and the photo appears suddenly under the pebble box. The image's arrival visually competes with the animation that is still drawing.
2. **The banner is hard-coded to 16:9.** Portrait or near-square uploads are aggressively cropped to a wide landscape, ignoring the original aspect ratio and producing a poor read of the user's photo.

This spec covers the iOS app only.

## Goals

- Give the pebble's stroke animation exclusive stage time. The photo is loaded in the background but never affects layout or pixels until the animation has finished.
- Replace the fixed 16:9 banner with a banner that adopts the closest of three aspect ratios — 16:9, 4:3, 1:1 — based on the source image's natural dimensions.
- Reveal the photo with a single coordinated layout transition (fade + content shift) when both the animation and the image load have completed.
- Keep portrait sources from filling the banner with vertical space; they bucket to 1:1 with center-cropped cover.

## Non-goals

- No carousel or gallery for multiple snaps. Banner shows the first snap, same as today.
- No changes to the snaps schema, the read RPC, or `PebbleDetail`.
- No changes to the upload pipeline (`ImagePipeline`, `PebbleSnapRepository.uploadProcessed`).
- No changes to `EditPebbleSheet` / `CreatePebbleSheet` or the edit-form photo chip (`AttachedPhotoView`).
- No web parity work.
- No retry affordance for a failed photo load.
- No user-positioned crops (Instagram-style).

## Behavior & sequencing

### Phase 1 — animation in progress

On first appearance of `PebbleReadView`, the banner zone always renders the no-photo layout regardless of whether the pebble has a snap:

- Pebble centered in its 120pt-tall zone, no banner above.
- Title, pills, description, souls below — identical to today's no-snap layout.

Two background workers run in parallel during Phase 1:

- The pebble stroke animation, owned by `PebbleAnimatedRenderView`, which runs for `PebbleAnimationTimings.totalDuration` seconds for the pebble's `renderVersion` (1.8s for `"0.1.0"`).
- An async photo load: signed URL fetch via `PebbleSnapRepository.signedURLs(storagePrefix:)`, byte download via `URLSession.shared.data(for:)`, decode via `UIImage(data:)`.

Phase 1 holds until both workers complete.

### Phase 2 — reveal

Reveal fires when *both* gates are open:

- `animationFinished == true` — flipped after `PebbleAnimationTimings.totalDuration` seconds, or set to `true` immediately when no animation applies (Reduce Motion on, or `renderVersion` has no registered timings).
- `loadedImage != nil` — populated when bytes are downloaded and decoded.

The transition is a single state flip wrapped in `withAnimation(.easeOut(duration: 0.45)) { revealPhoto = true }`. SwiftUI interpolates the resulting layout change:

- The banner inserts above the pebble box (fades in, content slides from the top edge).
- The pebble box settles into its overlap position — vertical center on banner's bottom edge.
- Content below the banner shifts down to make room.

The pebble itself does not rerun its stroke animation; only its enclosing layout changes.

Under Reduce Motion the transition uses an opacity-only fade (no slide) and the 1.8s gate is skipped — the photo reveals as soon as the bytes arrive.

### Edge cases

- **No snap on the pebble** → no photo load kicks off; banner never reveals; layout stays in the no-photo composition. Same as today.
- **Snap exists, signed URL fails / bytes 404 / decode fails** → logged; banner never reveals; read view stays in no-photo layout for this appearance. No retry affordance.
- **`renderVersion` has no registered timings or is `nil`** → static pebble (no animation); `animationFinished` is `true` from `onAppear`. Reveal is gated only on the photo load.
- **Reduce Motion on** → static pebble; no 1.8s gate; opacity-only fade on reveal.
- **View disappears mid-load** → SwiftUI cancels the `.task`; no state writes after teardown.
- **Pebble re-entered** (sheet closed and reopened) → `.task` re-runs with the same storage path; `URLCache.shared` services the second fetch; second appearance still respects the 1.8s gate.
- **Portrait images** → bucketed to 1:1 (see "Aspect-ratio bucket" below); center-cropped cover.
- **Animation finishes before image loads** → `animationFinished` sits at `true` until the bytes arrive; reveal fires the moment they land.
- **Image loads before animation finishes** → reveal fires the moment the 1.8s gate elapses.
- **Multiple snaps** → first snap only, same as today.

## Aspect-ratio bucket

When the loaded `UIImage` is decoded, the banner picks one of three aspect ratios — `16:9`, `4:3`, `1:1` — by minimal absolute distance to the source's `width / height`.

```swift
enum BannerAspect {
    case sixteenNine, fourThree, square

    static func nearest(to ratio: CGFloat) -> BannerAspect

    var cgRatio: CGFloat
}
```

Pure function, no view dependencies, easy to unit-test.

Behavior implied by the rule:

- Landscape near 3:2 (r ≈ 1.5) → 4:3 (distance 0.17) wins over 16:9 (distance 0.28).
- Landscape near or above 16:9 → 16:9.
- Square-ish images (r in roughly `[0.86, 1.16]`) → 1:1.
- Any portrait (r < 1) → 1:1, since 1:1 is the smallest of the three candidates. No special-case branch.

The chosen aspect ratio is applied to the banner frame as `.aspectRatio(chosenRatio, contentMode: .fit)`. The image inside is `.resizable().aspectRatio(contentMode: .fill).clipped()` — the iOS analogue of CSS `background-size: cover`. Banner corner radius stays 24pt. The pebble box's overlap position (vertical center on banner's bottom edge) is unchanged across all three buckets.

## Component breakdown

### Modified

**`apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`**

Owns the new sequencing. New state:

- `@State private var loadedImage: UIImage?`
- `@State private var animationFinished: Bool` — initialized based on Reduce Motion / `renderVersion`
- Computed `revealPhoto: Bool { loadedImage != nil && animationFinished }`

Body always renders Phase 1 (pebble-only). When `revealPhoto` is `true`, the banner inserts above the pebble box inside the same `VStack`. The state flip is wrapped in `withAnimation(.easeOut(0.45))`.

Photo load runs inside a single `.task(id: storagePath)`:

1. `let urls = try await PebbleSnapRepository(client: …).signedURLs(storagePrefix: storagePath)`
2. `let (data, _) = try await URLSession.shared.data(for: URLRequest(url: urls.original))`
3. `guard let img = UIImage(data: data) else { throw … }`
4. `loadedImage = img`

Errors are logged with `Logger(subsystem: "app.pbbls.ios", category: "pebble-read-banner")` and surface as "no reveal" — the view stays in Phase 1.

The 1.8s gate runs as a sibling `.task` (or inline inside the same `.task` after the load is kicked off, awaited via `try await Task.sleep(for: .seconds(timings.totalDuration))`). Whichever ordering is cleaner at implementation time; the spec only requires that both gates are independent and the reveal happens once both are satisfied.

The banner no longer uses `SnapImageView` — it owns the load directly so it can read the `UIImage`'s natural size and gate the reveal on the bytes being present.

**`apps/ios/Pebbles/Features/Path/Render/PebbleAnimationTimings.swift`**

Add a small extension:

```swift
extension PebbleAnimationTimings.Timings {
    var totalDuration: Double { settle.delay + settle.duration }
}
```

### New

**`apps/ios/Pebbles/Features/Path/Read/BannerAspect.swift`**

The pure aspect-ratio bucket helper sketched above. Lives next to the banner.

**`apps/ios/PebblesTests/BannerAspectTests.swift`**

Swift Testing suite covering: 16:9 (r ≈ 1.78), 3:2 (r ≈ 1.5), 4:3 (r ≈ 1.33), square (r = 1.0), portrait 9:16 (r ≈ 0.56), extreme 21:9 (r ≈ 2.33).

### Untouched

- `SnapImageView.swift` — still available for any other caller; no longer used by the read banner.
- `PebbleSnapRepository.swift` — read API (`signedURLs(storagePrefix:)`) is sufficient as-is.
- `PebbleAnimatedRenderView.swift` — animation and Reduce Motion handling unchanged.
- `PebbleReadView.swift`, `PebbleReadTitle.swift`, `PebbleMetaPill.swift`, `PebblePillFlow.swift` — no layout changes outside the banner.
- `PebbleDetail`, `PebbleDetailSheet`, the read RPC, `public.snaps`.

## Visual specs

- Banner aspect ratio: one of `16:9`, `4:3`, `1:1`, chosen per image. Corner radius 24pt.
- Image content mode: `.fill` with `.clipped()` (cover behavior).
- Pebble box overlap: 120×120pt, corner radius 24pt, fill `Color.pebblesBackground`. Vertical center on banner's bottom edge — unchanged across buckets.
- Reveal animation: `easeOut`, duration `0.45`s, applied to `revealPhoto` state. Under Reduce Motion: opacity-only `easeOut(0.25)`.
- Pebble stroke animation: unchanged. Total duration sourced from `PebbleAnimationTimings.totalDuration`.

## Accessibility

- Banner photo: decorative, `accessibilityHidden(true)` (unchanged).
- Pebble shape: `accessibilityHidden(true)` (unchanged).
- No new VoiceOver elements. Reveal is silent (no announce).
- Reduce Motion respected per "Behavior & sequencing".

## Localization

- No new user-facing strings.
- No `Localizable.xcstrings` changes.

## Error visibility & logging

- Single category: `pebble-read-banner`.
- Each failure point logs with the storage prefix at `privacy: .public` and the underlying error at `privacy: .private`:
  - signed URL fetch failed
  - bytes download failed
  - decode failed
- Silent failures are not acceptable. The fallback (no reveal) is intentional UX; the diagnostic log is mandatory.

## Testing & previews

`#Preview` blocks in `PebbleReadBanner.swift`:

- With photo (force-revealed via a debug initializer or preview-only state) — landscape 16:9 source.
- With photo, square source.
- With photo, portrait source (bucketed to 1:1).
- Without photo.

Swift Testing in `PebblesTests/BannerAspectTests.swift` — pure function coverage for `BannerAspect.nearest(to:)`.

No UI tests.

## Out of scope

- Snap gallery / carousel.
- Snap aspect-ratio persisted on `public.snaps` (could be added later as a perf optimization to skip the byte-decode step before reveal; not needed for this issue).
- Web pebble read view changes.
- Visual changes to `EditPebbleSheet`, `CreatePebbleSheet`, `PathView`, `AttachedPhotoView`.
- Retry UI for a failed photo load.
- User-positioned crops.
