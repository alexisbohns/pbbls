import Foundation
import Testing
@testable import Pebbles

/// Test double for `SignedURLProviding`. Records every call and returns
/// stubbed results in FIFO order. When `suspendUntilResumed` is true, each
/// call awaits an internal continuation the test must resume by calling
/// `resumePending()` — used to exercise concurrent dedup deterministically.
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

    /// Resumes the oldest pending continuation.
    func resumePending() {
        guard !pending.isEmpty else { return }
        pending.removeFirst().resume()
    }
}

private struct StubError: Error {}

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

    @Test("expired entry triggers refetch")
    func expiredEntryRefetches() async throws {
        let fake = FakeSignedURLProvider()
        nonisolated(unsafe) var nowValue = Date(timeIntervalSince1970: 1_700_000_000)
        let cache = SnapURLCache(provider: fake, now: { nowValue })

        _ = try await cache.signedURLs(storagePath: "uid/sid")
        // Advance past effective TTL (3600 - 60 = 3540 s).
        nowValue = nowValue.addingTimeInterval(3541)
        _ = try await cache.signedURLs(storagePath: "uid/sid")

        #expect(fake.callCount == 2)
    }

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

    @Test("invalidateAll drops cached entries")
    func invalidateAllDropsCache() async throws {
        let fake = FakeSignedURLProvider()
        let cache = SnapURLCache(provider: fake, now: { Date() })

        _ = try await cache.signedURLs(storagePath: "uid/sid")
        cache.invalidateAll()
        _ = try await cache.signedURLs(storagePath: "uid/sid")

        #expect(fake.callCount == 2)
    }
}
