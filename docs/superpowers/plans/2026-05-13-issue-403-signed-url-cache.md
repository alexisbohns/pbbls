# Cache signed Storage URLs (iOS) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate redundant Supabase Storage `createSignedURLs` round-trips by caching results until near-expiry, deduplicating concurrent requests, and stop forcing JPEG re-downloads in `PebbleReadBanner`.

**Architecture:** Introduce `SnapURLCache` — a new `@Observable @MainActor` service registered in `PebblesApp` and injected via `@Environment` into the three views that consume signed URLs (`PathPebbleSnapThumb`, `SnapImageView`, `PebbleReadBanner`). The cache delegates signing to a `SignedURLProviding` protocol that `PebbleSnapRepository` conforms to, which keeps the production path identical while allowing tests to inject a fake. Sign-out invalidation is wired in `RootView` via `.onChange(of:)`.

**Tech Stack:** SwiftUI, Swift Concurrency, `@Observable`, Swift Testing (`@Suite`/`@Test`/`#expect`), Supabase Swift SDK 2.x.

**Spec:** `docs/superpowers/specs/2026-05-13-issue-403-signed-url-cache-design.md`

**Issue:** [#403](https://github.com/alexisbohns/pbbls/issues/403)
**Branch:** `fix/403-cache-signed-storage-urls`

---

## File Structure

**New files:**
- `apps/ios/Pebbles/Services/SnapURLCache.swift` — the service. Holds the protocol `SignedURLProviding`, the cache class, and a private `Entry` struct.
- `apps/ios/PebblesTests/SnapURLCacheTests.swift` — Swift Testing suite covering miss→fetch, hot hit, concurrent dedup, expiry refetch, error non-caching, and invalidation.

**Modified files:**
- `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift` — add conformance to `SignedURLProviding`.
- `apps/ios/Pebbles/PebblesApp.swift` — construct `SnapURLCache` alongside other services; inject via `.environment`.
- `apps/ios/Pebbles/RootView.swift` — sign-out invalidation via `.onChange(of: supabase.session)`.
- `apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift` — call cache instead of constructing a repo locally.
- `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift` — same.
- `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift` — same, AND drop `request.cachePolicy = .reloadIgnoringLocalCacheData`.

**Not modified:** `project.yml` requires no changes — its `sources: - path: Pebbles` and `sources: - path: PebblesTests` already include any new file under those roots recursively.

**File responsibilities:**
- The cache file owns: protocol declaration, cache class, internal `Entry`, logging. Single responsibility.
- `RootView` already drives auth-gated rendering and reads `supabase.session`; adding one `.onChange` modifier is the minimal touch.

---

## Task 1 — Extract `SignedURLProviding` protocol

**Files:**
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift`

Pure refactor preparing for testability. No behavior change. No test in this task — Task 2 exercises the protocol via the fake.

- [ ] **Step 1: Add the protocol declaration**

At the top of `PebbleSnapRepository.swift`, just under `import os` (line 3), add:

```swift
/// Minimal signing surface the cache depends on. `PebbleSnapRepository`
/// is the live conformance; tests inject a fake.
@MainActor
protocol SignedURLProviding {
    func signedURLs(storagePrefix: String) async throws
        -> PebbleSnapRepository.SignedURLs
}
```

- [ ] **Step 2: Conform `PebbleSnapRepository` to `SignedURLProviding`**

Change the struct declaration on line 22 from:

```swift
struct PebbleSnapRepository: PebbleSnapRepositoryProtocol {
```

to:

```swift
struct PebbleSnapRepository: PebbleSnapRepositoryProtocol, SignedURLProviding {
```

The existing `func signedURLs(storagePrefix prefix: String) async throws -> SignedURLs` already satisfies the protocol (argument label `storagePrefix` matches; `SignedURLs` resolves to `PebbleSnapRepository.SignedURLs` from within the type).

- [ ] **Step 3: Verify it compiles**

Run from `apps/ios/`:

```bash
npm run generate --workspace=@pbbls/ios && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles -destination 'generic/platform=iOS Simulator' -quiet build-for-testing
```

Expected: build succeeds. If `xcodebuild` is unavailable in your shell, open the project in Xcode and ⌘B — same outcome.

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift
git commit -m "fix(ios): add SignedURLProviding protocol on snap repo"
```

---

## Task 2 — Create `SnapURLCache` (tests first, then implementation)

**Files:**
- Create: `apps/ios/Pebbles/Services/SnapURLCache.swift`
- Create: `apps/ios/PebblesTests/SnapURLCacheTests.swift`

Build the cache test-first. Each behavior gets its own test; the implementation grows just enough to satisfy them.

- [ ] **Step 1: Write the fake provider and the first failing test**

Create `apps/ios/PebblesTests/SnapURLCacheTests.swift`:

```swift
import Foundation
import Testing
@testable import Pebbles

/// Test double for `SignedURLProviding`. Records every call and returns
/// stubbed results in FIFO order. When `suspendUntilResumed` is true, each
/// call awaits an internal continuation the test must resume by calling
/// `resumeNext(with:)` — used to exercise concurrent dedup deterministically.
@MainActor
final class FakeSignedURLProvider: SignedURLProviding {

    private(set) var callCount = 0
    var suspendUntilResumed = false
    private var pending: [CheckedContinuation<Void, Never>] = []

    /// FIFO results. If empty when called, returns `defaultResult`.
    var results: [Result<PebbleSnapRepository.SignedURLs, Error>] = []
    var defaultResult: PebbleSnapRepository.SignedURLs =
        .init(
            original: URL(string: "https://example.com/original.jpg")!,
            thumb: URL(string: "https://example.com/thumb.jpg")!
        )

    func signedURLs(storagePrefix: String) async throws
        -> PebbleSnapRepository.SignedURLs
    {
        callCount += 1
        if suspendUntilResumed {
            await withCheckedContinuation { cont in
                pending.append(cont)
            }
        }
        guard !results.isEmpty else { return defaultResult }
        switch results.removeFirst() {
        case .success(let urls): return urls
        case .failure(let error): throw error
        }
    }

    /// Resumes the oldest pending continuation. Call after both concurrent
    /// callers have suspended so the dedup test is deterministic.
    func resumePending() {
        guard !pending.isEmpty else { return }
        pending.removeFirst().resume()
    }
}

@MainActor
@Suite("SnapURLCache")
struct SnapURLCacheTests {

    private func makeURLs(_ tag: String) -> PebbleSnapRepository.SignedURLs {
        .init(
            original: URL(string: "https://example.com/\(tag)/original.jpg")!,
            thumb: URL(string: "https://example.com/\(tag)/thumb.jpg")!
        )
    }

    @Test("cache miss triggers fetch and stores result")
    func missFetchesAndStores() async throws {
        let fake = FakeSignedURLProvider()
        fake.defaultResult = makeURLs("a")
        let cache = SnapURLCache(provider: fake, now: { Date() })

        let result = try await cache.signedURLs(storagePath: "uid/sid")

        #expect(fake.callCount == 1)
        #expect(result.original.absoluteString == "https://example.com/a/original.jpg")
    }
}
```

- [ ] **Step 2: Run the test — expect failure (type not found)**

From `apps/ios/`:

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache
```

Expected: build fails with "cannot find 'SnapURLCache' in scope". Good.

- [ ] **Step 3: Create the minimal `SnapURLCache` implementation**

Create `apps/ios/Pebbles/Services/SnapURLCache.swift`:

```swift
import Foundation
import Observation
import Supabase
import os

/// Per-session cache of signed Storage URLs keyed by `storage_path`.
/// Returns cached entries until they near expiry; coalesces concurrent
/// requests for the same path into a single round-trip.
///
/// Production wiring lives in `PebblesApp`, which constructs the cache
/// with a live `PebbleSnapRepository` as the provider. Sign-out
/// invalidation is wired in `RootView` via `.onChange(of: supabase.session)`.
@Observable
@MainActor
final class SnapURLCache {

    /// Effective TTL is `signedUrlTTL - safetyMargin`, so a URL handed out
    /// at hit time never expires mid-download.
    private static let signedUrlTTL: TimeInterval = 3600
    private static let safetyMargin: TimeInterval = 60

    private struct Entry {
        let urls: PebbleSnapRepository.SignedURLs
        let expiresAt: Date
    }

    private let provider: SignedURLProviding
    private let now: @Sendable () -> Date

    private var cache: [String: Entry] = [:]
    private var inflight: [String: Task<PebbleSnapRepository.SignedURLs, Error>] = [:]

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "snap-url-cache")

    /// Production initializer. `PebblesApp` calls this with the live
    /// SupabaseClient.
    convenience init(client: SupabaseClient) {
        self.init(provider: PebbleSnapRepository(client: client), now: { Date() })
    }

    /// Test initializer. Allows injecting a fake provider and a virtual
    /// clock.
    init(provider: SignedURLProviding, now: @escaping @Sendable () -> Date) {
        self.provider = provider
        self.now = now
    }

    func signedURLs(storagePath: String) async throws
        -> PebbleSnapRepository.SignedURLs
    {
        if let entry = cache[storagePath], now() < entry.expiresAt {
            return entry.urls
        }
        if let task = inflight[storagePath] {
            return try await task.value
        }
        let task = Task<PebbleSnapRepository.SignedURLs, Error> { [provider] in
            try await provider.signedURLs(storagePrefix: storagePath)
        }
        inflight[storagePath] = task
        defer { inflight[storagePath] = nil }
        do {
            let urls = try await task.value
            cache[storagePath] = Entry(
                urls: urls,
                expiresAt: now() + Self.signedUrlTTL - Self.safetyMargin
            )
            return urls
        } catch {
            logger.error(
                "sign failed for \(storagePath, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
            throw error
        }
    }

    func invalidateAll() {
        cache.removeAll()
    }
}
```

- [ ] **Step 4: Run the first test — expect pass**

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache/missFetchesAndStores
```

Expected: PASS.

- [ ] **Step 5: Add the hot-hit test**

Append inside `SnapURLCacheTests` (before the closing brace):

```swift
@Test("hot hit within TTL does not refetch")
func hotHitDoesNotRefetch() async throws {
    let fake = FakeSignedURLProvider()
    fake.defaultResult = makeURLs("a")
    let fixed = Date(timeIntervalSince1970: 1_700_000_000)
    let cache = SnapURLCache(provider: fake, now: { fixed })

    _ = try await cache.signedURLs(storagePath: "uid/sid")
    _ = try await cache.signedURLs(storagePath: "uid/sid")

    #expect(fake.callCount == 1)
}
```

- [ ] **Step 6: Run it — expect pass**

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache/hotHitDoesNotRefetch
```

Expected: PASS (cache short-circuit already works).

- [ ] **Step 7: Add the expiry refetch test**

Append inside `SnapURLCacheTests`:

```swift
@Test("expired entry triggers refetch")
func expiredEntryRefetches() async throws {
    let fake = FakeSignedURLProvider()
    var nowValue = Date(timeIntervalSince1970: 1_700_000_000)
    let cache = SnapURLCache(provider: fake, now: { nowValue })

    _ = try await cache.signedURLs(storagePath: "uid/sid")
    // Advance past effective TTL (3600 - 60 = 3540 s).
    nowValue = nowValue.addingTimeInterval(3541)
    _ = try await cache.signedURLs(storagePath: "uid/sid")

    #expect(fake.callCount == 2)
}
```

- [ ] **Step 8: Run it — expect pass**

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache/expiredEntryRefetches
```

Expected: PASS.

- [ ] **Step 9: Add the dedup test**

Append inside `SnapURLCacheTests`:

```swift
@Test("concurrent calls for the same path share one fetch")
func concurrentCallsDedup() async throws {
    let fake = FakeSignedURLProvider()
    fake.suspendUntilResumed = true
    fake.defaultResult = makeURLs("a")
    let cache = SnapURLCache(provider: fake, now: { Date() })

    async let a = cache.signedURLs(storagePath: "uid/sid")
    async let b = cache.signedURLs(storagePath: "uid/sid")

    // Yield enough times for both callers to enter the cache method
    // and reach their await point. Reentrant @MainActor lets the second
    // call observe the inflight Task created by the first.
    for _ in 0..<10 { await Task.yield() }
    fake.resumePending()

    let resultA = try await a
    let resultB = try await b

    #expect(fake.callCount == 1)
    #expect(resultA.original == resultB.original)
}
```

- [ ] **Step 10: Run it — expect pass**

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache/concurrentCallsDedup
```

Expected: PASS. If it fails with `callCount == 2`, that means the second caller did not observe the inflight Task — increase the yield loop count or verify the implementation's "check inflight before creating" ordering is correct.

- [ ] **Step 11: Add the error-not-cached test**

Append inside `SnapURLCacheTests`:

```swift
private struct StubError: Error {}

@Test("errors are not cached")
func errorsAreNotCached() async throws {
    let fake = FakeSignedURLProvider()
    fake.results = [.failure(StubError())]
    let cache = SnapURLCache(provider: fake, now: { Date() })

    await #expect(throws: StubError.self) {
        _ = try await cache.signedURLs(storagePath: "uid/sid")
    }

    // Second call has no stub left -> falls back to defaultResult success.
    let urls = try await cache.signedURLs(storagePath: "uid/sid")
    #expect(fake.callCount == 2)
    #expect(urls.original.absoluteString == "https://example.com/original.jpg")
}
```

- [ ] **Step 12: Run it — expect pass**

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache/errorsAreNotCached
```

Expected: PASS.

- [ ] **Step 13: Add the invalidate test**

Append inside `SnapURLCacheTests`:

```swift
@Test("invalidateAll drops cached entries")
func invalidateAllDropsCache() async throws {
    let fake = FakeSignedURLProvider()
    let cache = SnapURLCache(provider: fake, now: { Date() })

    _ = try await cache.signedURLs(storagePath: "uid/sid")
    cache.invalidateAll()
    _ = try await cache.signedURLs(storagePath: "uid/sid")

    #expect(fake.callCount == 2)
}
```

- [ ] **Step 14: Run it — expect pass**

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache/invalidateAllDropsCache
```

Expected: PASS.

- [ ] **Step 15: Run the whole suite to confirm green**

```bash
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test -only-testing:PebblesTests/SnapURLCache
```

Expected: 6 passing, 0 failing.

- [ ] **Step 16: Commit**

```bash
git add apps/ios/Pebbles/Services/SnapURLCache.swift \
        apps/ios/PebblesTests/SnapURLCacheTests.swift
git commit -m "fix(ios): add SnapURLCache service with TTL + dedup"
```

---

## Task 3 — Register `SnapURLCache` and wire sign-out invalidation

**Files:**
- Modify: `apps/ios/Pebbles/PebblesApp.swift`
- Modify: `apps/ios/Pebbles/RootView.swift`

Construct the cache alongside the other services and inject it into the environment, then add the sign-out invalidation hook on `RootView`.

- [ ] **Step 1: Add the cache to `PebblesApp.init` and the environment**

In `apps/ios/Pebbles/PebblesApp.swift`, replace the `@State` block and `init`:

```swift
@State private var supabase: SupabaseService
@State private var palettes: EmotionPaletteService
@State private var stats: PathStatsService
@State private var snapURLs: SnapURLCache

init() {
    let supabase = SupabaseService()
    self._supabase = State(initialValue: supabase)
    self._palettes = State(initialValue: EmotionPaletteService(client: supabase.client))
    self._stats    = State(initialValue: PathStatsService(supabase: supabase))
    self._snapURLs = State(initialValue: SnapURLCache(client: supabase.client))
    Self.configureSegmentedControlAppearance()
}
```

Then update the `body` to inject the new service:

```swift
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

- [ ] **Step 2: Add the sign-out invalidation hook on `RootView`**

In `apps/ios/Pebbles/RootView.swift`, add the new environment read near the existing two (after line 19, the `EmotionPaletteService` env line):

```swift
@Environment(SnapURLCache.self) private var snapURLs
```

Then append a new `.onChange` modifier inside the existing chain after the current `.onChange(of: supabase.session?.user.id)` block (after line 90, just before the closing brace of `body`):

```swift
.onChange(of: supabase.session == nil) { wasSignedOut, isSignedOut in
    if !wasSignedOut && isSignedOut {
        snapURLs.invalidateAll()
    }
}
```

This fires only on signed-in → signed-out transitions, not on the initial `nil` state at launch.

Also update the `#Preview` at the bottom of the file (lines 94–99) to inject the cache:

```swift
#Preview {
    let supabase = SupabaseService()
    return RootView()
        .environment(supabase)
        .environment(EmotionPaletteService(client: supabase.client))
        .environment(SnapURLCache(client: supabase.client))
}
```

- [ ] **Step 3: Build to verify**

```bash
cd apps/ios && npm run generate --workspace=@pbbls/ios && \
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: build succeeds. (If the preview snippet shows a deprecation warning, leave it — matches the existing preview style.)

- [ ] **Step 4: Commit**

```bash
git add apps/ios/Pebbles/PebblesApp.swift apps/ios/Pebbles/RootView.swift
git commit -m "fix(ios): inject SnapURLCache and invalidate on sign-out"
```

---

## Task 4 — Migrate the three call sites to use the cache

**Files:**
- Modify: `apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift`
- Modify: `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`

Replace each view's local `PebbleSnapRepository` instantiation with a cache call. In `PebbleReadBanner`, also drop the `.reloadIgnoringLocalCacheData` override so URLSession's disk cache applies.

- [ ] **Step 1: Migrate `PathPebbleSnapThumb`**

In `apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift`, replace the environment read on line 12:

```swift
@Environment(SupabaseService.self) private var supabase
```

with:

```swift
@Environment(SnapURLCache.self) private var snapURLs
```

Then replace the `.task(id: storagePath)` block (lines 25–33):

```swift
.task(id: storagePath) {
    do {
        let urls = try await PebbleSnapRepository(client: supabase.client)
            .signedURLs(storagePrefix: storagePath)
        url = urls.thumb
    } catch {
        logger.error("snap sign failed: \(error.localizedDescription, privacy: .private)")
    }
}
```

with:

```swift
.task(id: storagePath) {
    do {
        let urls = try await snapURLs.signedURLs(storagePath: storagePath)
        url = urls.thumb
    } catch {
        logger.error("snap sign failed: \(error.localizedDescription, privacy: .private)")
    }
}
```

- [ ] **Step 2: Migrate `SnapImageView`**

In `apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift`, replace the environment read on line 14:

```swift
@Environment(SupabaseService.self) private var supabase
```

with:

```swift
@Environment(SnapURLCache.self) private var snapURLs
```

Then replace the `.task` block (lines 45–52):

```swift
.task {
    do {
        urls = try await PebbleSnapRepository(client: supabase.client)
            .signedURLs(storagePrefix: storagePath)
    } catch {
        loadError = true
    }
}
```

with:

```swift
.task {
    do {
        urls = try await snapURLs.signedURLs(storagePath: storagePath)
    } catch {
        loadError = true
    }
}
```

- [ ] **Step 3: Migrate `PebbleReadBanner` and drop the cache-policy override**

In `apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift`, add a new environment read alongside the existing `supabase` env (insert after line 24):

```swift
@Environment(SnapURLCache.self) private var snapURLs
```

Keep the existing `@Environment(SupabaseService.self) private var supabase` because `loadPhotoIfNeeded` still needs no Supabase client access after the change — actually, the `supabase` env can be removed entirely once the only call site (`PebbleSnapRepository(client: supabase.client)`) is gone. Remove the `@Environment(SupabaseService.self) private var supabase` line.

Then replace `loadPhotoIfNeeded()` (lines 113–136):

```swift
private func loadPhotoIfNeeded() async {
    guard let path = snapStoragePath else { return }
    loadedImage = nil
    revealPhoto = false
    do {
        let urls = try await PebbleSnapRepository(client: supabase.client)
            .signedURLs(storagePrefix: path)
        var request = URLRequest(url: urls.original)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.timeoutInterval = 30
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let image = UIImage(data: data) else {
            Self.logger.error(
                "decode failed for \(path, privacy: .public)"
            )
            return
        }
        loadedImage = image
    } catch {
        Self.logger.error(
            "photo load failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
        )
    }
}
```

with:

```swift
private func loadPhotoIfNeeded() async {
    guard let path = snapStoragePath else { return }
    loadedImage = nil
    revealPhoto = false
    do {
        let urls = try await snapURLs.signedURLs(storagePath: path)
        var request = URLRequest(url: urls.original)
        request.timeoutInterval = 30
        let (data, _) = try await URLSession.shared.data(for: request)
        guard let image = UIImage(data: data) else {
            Self.logger.error(
                "decode failed for \(path, privacy: .public)"
            )
            return
        }
        loadedImage = image
    } catch {
        Self.logger.error(
            "photo load failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
        )
    }
}
```

Two changes: cache call instead of repo instantiation, and the `request.cachePolicy = .reloadIgnoringLocalCacheData` line is gone (URLSession defaults to `.useProtocolCachePolicy`, which honors the Storage server's `Cache-Control` headers).

- [ ] **Step 4: Update the three `#Preview` blocks in `PebbleReadBanner.swift`**

The three previews (lines 186–244) currently call `.environment(supabase)` and `.environment(EmotionPaletteService(client: supabase.client))`. Add the cache to each. Replace every occurrence of:

```swift
.environment(supabase)
.environment(EmotionPaletteService(client: supabase.client))
```

with:

```swift
.environment(supabase)
.environment(EmotionPaletteService(client: supabase.client))
.environment(SnapURLCache(client: supabase.client))
```

Use Edit's `replace_all: true` for this if multiple occurrences exist.

- [ ] **Step 5: Build to verify**

```bash
cd apps/ios && npm run generate --workspace=@pbbls/ios && \
xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'generic/platform=iOS Simulator' -quiet build
```

Expected: build succeeds with zero warnings related to unused `supabase` properties.

- [ ] **Step 6: Run the full test suite to confirm no regressions**

```bash
cd apps/ios && xcodebuild -project Pebbles.xcodeproj -scheme Pebbles \
  -destination 'platform=iOS Simulator,name=iPhone 15' \
  -quiet test
```

Expected: all existing tests pass plus the 6 new `SnapURLCache` tests.

- [ ] **Step 7: Smoke-test in the simulator (manual)**

Boot the app in the simulator on iPhone 15 (iOS 17+). Sign in. Confirm:

1. **Path scroll**: scrolling the path generates thumbs; scroll back up — thumbs reappear without a visible re-fetch flash. (Watch Xcode console: `snap-url-cache` logs only on first encounter per path.)
2. **Detail open**: tap a pebble with a snap → the read view banner photo loads and animates in. Close and reopen the same pebble — the photo appears immediately without re-downloading (no log line; URLSession serves from disk cache).
3. **Sign out / sign in as different user**: confirm the previous user's URLs aren't reused (no cross-session leakage).

If any step fails, fix and re-test before committing.

- [ ] **Step 8: Commit**

```bash
git add apps/ios/Pebbles/Features/Path/Components/PathPebbleSnapThumb.swift \
        apps/ios/Pebbles/Features/PebbleMedia/SnapImageView.swift \
        apps/ios/Pebbles/Features/Path/Read/PebbleReadBanner.swift
git commit -m "fix(ios): use SnapURLCache in snap views and stop bypassing URL cache"
```

---

## Task 5 — Final verification + PR

**Files:** none new.

- [ ] **Step 1: Verify branch state**

```bash
git status
git log --oneline main..HEAD
```

Expected: clean working tree; four commits on `fix/403-cache-signed-storage-urls` since `main`:

1. `fix(ios): add SignedURLProviding protocol on snap repo`
2. `fix(ios): add SnapURLCache service with TTL + dedup`
3. `fix(ios): inject SnapURLCache and invalidate on sign-out`
4. `fix(ios): use SnapURLCache in snap views and stop bypassing URL cache`

Plus the earlier `docs(ios): spec for cache signed Storage URLs` commit.

- [ ] **Step 2: Push the branch**

```bash
git push -u origin fix/403-cache-signed-storage-urls
```

- [ ] **Step 3: Open the PR**

Issue #403 carries labels `core`, `fix`, `ios` and milestone `M32 · iOS Quality`. Inherit labels (no transform needed since species is already `fix`) and milestone. Confirm with the user before applying.

```bash
gh pr create \
  --title "fix(ios): cache signed Storage URLs" \
  --body "$(cat <<'EOF'
Resolves #403.

## Summary

- New `SnapURLCache` service caches signed Storage URLs until 60 s before TTL expiry, keyed by `storage_path`.
- Concurrent requests for the same path coalesce into a single round-trip via an in-flight `Task` map.
- `PebbleReadBanner.loadPhotoIfNeeded` no longer overrides `URLRequest.cachePolicy`, letting URLSession's disk cache do its job.
- Sign-out invalidation wired on `RootView` to keep cache state clean across user transitions.

## Key files

- `apps/ios/Pebbles/Services/SnapURLCache.swift` (new)
- `apps/ios/PebblesTests/SnapURLCacheTests.swift` (new)
- `apps/ios/Pebbles/Features/PebbleMedia/PebbleSnapRepository.swift` — conforms to new `SignedURLProviding` protocol
- `apps/ios/Pebbles/PebblesApp.swift`, `RootView.swift` — wiring + sign-out invalidation
- `apps/ios/Pebbles/Features/{Path/Components/PathPebbleSnapThumb.swift,PebbleMedia/SnapImageView.swift,Path/Read/PebbleReadBanner.swift}` — call sites migrated

## Test plan

- [ ] `xcodebuild test -only-testing:PebblesTests/SnapURLCache` — 6/6 passing
- [ ] Full suite green
- [ ] Manual: scroll path twice → second scroll shows thumbs immediately, no `snap-url-cache` log spam
- [ ] Manual: open a snap-bearing pebble twice → second open shows photo without a network re-download
- [ ] Manual: sign out → `invalidateAll` runs (cache is empty for the next user)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)" \
  --label fix --label core --label ios \
  --milestone "M32 · iOS Quality"
```

If the milestone string differs from the issue's exact spelling, run `gh issue view 403 --json milestone` first to copy the exact value.

- [ ] **Step 4: Confirm CI / done**

Return the PR URL to the user. Done.

---

## Self-review notes

- **Spec coverage:** every spec section maps to a task. Architecture (Task 2), public surface (Task 2), data flow (Task 2 + 4), call-site changes (Task 4), `PebblesApp` registration (Task 3), sign-out hook (Task 3), error handling (Task 2 — logger + propagation), testing (Task 2's six cases).
- **Naming consistency:** `signedURLs(storagePath:)` is the only public method on the cache and is used identically across Tasks 2, 4, and the views. `invalidateAll()` matches across spec, Task 2, Task 3.
- **No placeholders.** Every step shows the exact code or command.
- **Reverse compatibility:** `PebbleSnapRepository.SignedURLs` is unchanged; existing code paths that already used the repo directly continue to compile (only the three migrated views switch to the cache).
