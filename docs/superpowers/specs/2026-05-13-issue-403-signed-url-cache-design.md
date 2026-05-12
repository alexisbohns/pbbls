# Cache signed Storage URLs (iOS) — Design

**Issue:** [#403](https://github.com/alexisbohns/pbbls/issues/403)
**Branch:** `fix/403-cache-signed-storage-urls`
**Milestone:** M32 · iOS Quality
**Labels:** `fix`, `core`, `ios`

## Problem

Three iOS views each call `PebbleSnapRepository.signedURLs(storagePrefix:)` from a `.task` modifier on every appearance, with no caching:

- `PathPebbleSnapThumb` — fires on every scroll-reveal in the path list.
- `SnapImageView` — fires on every detail banner appearance.
- `PebbleReadBanner` — signs the URL **and** fetches the JPEG bytes with `cachePolicy: .reloadIgnoringLocalCacheData`, forcing a full re-download every time.

Signed URL TTL is 1 hour (`PebbleSnapRepository.signedUrlTTL = 3600`), so re-signing on every appearance is pure waste. As pebble count grows, the path list issues O(n) sign requests per scroll session.

## Goals

1. Reuse a signed URL until it nears expiry.
2. Coalesce concurrent requests for the same `storage_path` into a single round-trip.
3. Stop bypassing URLSession's disk cache in `PebbleReadBanner`.

## Non-goals

- LRU/size-bounded eviction (TTL alone is sufficient — entries turn over within an hour).
- Cross-launch persistence (URLs expire in 1 h anyway).
- Caching decoded `UIImage`s (separate concern; out of scope).

## Architecture

### New service: `SnapURLCache`

A new `@Observable @MainActor final class SnapURLCache` in `apps/ios/Pebbles/Services/SnapURLCache.swift`. Registered in `PebblesApp.init()` and injected via `@Environment(SnapURLCache.self)`, matching the existing sibling pattern of `SupabaseService` and `EmotionPaletteService`.

```swift
@Observable @MainActor
final class SnapURLCache {
    init(client: SupabaseClient)

    /// Returns cached URLs if non-expired; otherwise signs, caches, returns.
    /// Coalesces concurrent calls for the same path into one in-flight Task.
    func signedURLs(storagePath: String) async throws -> PebbleSnapRepository.SignedURLs

    /// Drop all entries. Called from `PebblesApp` on sign-out.
    func invalidateAll()
}
```

### Internals

```swift
private struct Entry { let urls: PebbleSnapRepository.SignedURLs; let expiresAt: Date }

private var cache: [String: Entry] = [:]
private var inflight: [String: Task<PebbleSnapRepository.SignedURLs, Error>] = [:]

private let provider: SignedURLProviding   // see Testing below
private static let safetyMargin: TimeInterval = 60
```

Two dictionaries:
- `cache` — completed lookups with their expiry timestamp.
- `inflight` — running `Task`s, keyed by path, for dedup.

A 60-second `safetyMargin` shaves the effective TTL to 3540 s so a URL handed out at hit time never expires mid-download.

### Repository stays stateless

`PebbleSnapRepository` remains a `@MainActor struct`. The cache delegates sign calls to it, so there is no logic duplication. (Same shape as `EmotionPaletteService` — service-level cache, repo-level call.)

## Data flow

For each `cache.signedURLs(storagePath:)` call:

1. **Hot hit:** if `cache[path]` exists and `Date() < entry.expiresAt`, return synchronously.
2. **In-flight hit:** if `inflight[path]` exists, `await` that same `Task`. Both callers receive the same result.
3. **Miss:** create a `Task` that calls `PebbleSnapRepository(client:).signedURLs(storagePrefix:)`. Store in `inflight`. On success: write to `cache` with `expiresAt = Date() + signedUrlTTL - safetyMargin`, remove from `inflight` (`defer`), return URLs. On failure: remove from `inflight`, propagate.

Errors are not cached — the next call re-tries.

## Call-site changes

### `PathPebbleSnapThumb.swift`

Replace the direct `PebbleSnapRepository(...).signedURLs(...)` call inside `.task(id: storagePath)` with `cache.signedURLs(storagePath:)`. Pull cache from `@Environment(SnapURLCache.self)`. Drop the local `PebbleSnapRepository` instantiation.

### `SnapImageView.swift`

Same change. Replace the direct repo call in `.task` with `cache.signedURLs(storagePath:)`.

### `PebbleReadBanner.swift`

Same change in `loadPhotoIfNeeded()`. **Additionally**, drop the explicit `request.cachePolicy = .reloadIgnoringLocalCacheData` line so URLSession's default `.useProtocolCachePolicy` applies and the Storage server's `Cache-Control` headers govern. Keep the 30 s timeout.

### `PebblesApp.swift`

Construct the cache alongside the other services and inject:

```swift
@State private var snapURLs: SnapURLCache

init() {
    let supabase = SupabaseService()
    self._supabase = State(initialValue: supabase)
    self._palettes = State(initialValue: EmotionPaletteService(client: supabase.client))
    self._stats    = State(initialValue: PathStatsService(supabase: supabase))
    self._snapURLs = State(initialValue: SnapURLCache(client: supabase.client))
    Self.configureSegmentedControlAppearance()
}

var body: some Scene {
    WindowGroup {
        RootView()
            .environment(supabase)
            .environment(palettes)
            .environment(stats)
            .environment(snapURLs)
    }
}
```

## Sign-out invalidation

Defensive but cheap: on transition from a signed-in session to `nil`, call `snapURLs.invalidateAll()`. URLs are user-scoped, but tidiness matters and the cost is a single dict reset.

Wiring: keep `SupabaseService` unaware of the cache. Instead, `RootView` observes `supabase.session` and calls `snapURLs.invalidateAll()` when it transitions to `nil` — concretely, an `.onChange(of: supabase.session) { old, new in if old != nil && new == nil { snapURLs.invalidateAll() } }` modifier on `RootView`. (`RootView` already reads `SupabaseService` from the environment to drive auth-gated rendering, so the cache injection is a one-line addition there.)

## Error handling

Per `apps/ios/CLAUDE.md`: every runtime async failure logs via `os.Logger` or is surfaced in view state. The cache logs sign failures under category `snap-url-cache`. Errors propagate to callers, which already handle them (placeholder fallback in `SnapImageView`, silent log in `PathPebbleSnapThumb`, log + no-reveal in `PebbleReadBanner`). No empty catch blocks.

## Testing

Add `apps/ios/PebblesTests/SnapURLCacheTests.swift` using Swift Testing (`@Suite`, `@Test`, `#expect`).

To avoid hitting Supabase, the cache depends on a small protocol:

```swift
@MainActor
protocol SignedURLProviding {
    func signedURLs(storagePrefix: String) async throws
        -> PebbleSnapRepository.SignedURLs
}

extension PebbleSnapRepository: SignedURLProviding {}
```

`SnapURLCache.init` accepts `SignedURLProviding` (default in production: `PebbleSnapRepository(client:)`). Tests inject a fake that records call counts and can return a stub or throw.

Cases:

- **Miss → fetch:** first call fetches; cache populated.
- **Hot hit:** second call within TTL does not fetch (fake call-count stays at 1).
- **Concurrent dedup:** two simultaneous `async let` calls for the same path → fake call count is 1, both receive identical URLs.
- **Expiry refetch:** seed an entry with `expiresAt` in the past → next call fetches.
- **Error not cached:** fake throws once; next call retries (and can succeed if fake is reconfigured).
- **`invalidateAll`:** populated cache → call `invalidateAll()` → next call fetches.

Time is controlled by allowing the cache to accept an injectable `now: @Sendable () -> Date` closure (default `Date.init`). Tests advance "now" by overriding the closure.

## Files touched

**New:**
- `apps/ios/Pebbles/Services/SnapURLCache.swift`
- `apps/ios/PebblesTests/SnapURLCacheTests.swift`

**Modified:**
- `apps/ios/Pebbles/PebblesApp.swift` — register and inject the cache.
- `apps/ios/Pebbles/RootView.swift` — sign-out invalidation hook.
- `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift` — conform to `SignedURLProviding`.
- `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift` — use cache.
- `apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift` — use cache.
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` — use cache; drop `.reloadIgnoringLocalCacheData`.
- `apps/ios/project.yml` — only if `xcodegen` requires the new Service/Test files to be listed (verify before assuming).

## Risk and rollback

Low risk. The cache is additive; if a defect surfaces, reverting the call-site changes restores the prior (working but wasteful) behavior. The `PebbleReadBanner` cache-policy change is the only behavior shift visible to URLSession, and it moves *toward* default behavior — no exotic policy.

## Out of scope (revisit if needed)

- Bounding the cache by entry count (LRU). Hour-long TTL turns the dict over naturally.
- Caching `UIImage` bytes in memory (separate concern; `URLSession`'s disk cache covers the network side once the explicit `.reloadIgnoringLocalCacheData` is removed).
- Persisting cache across launches.
