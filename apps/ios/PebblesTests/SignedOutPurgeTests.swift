import Foundation
import Testing
@testable import Pebbles

/// On a shared or handed-over phone, whatever survives a sign-out is served to
/// the next account. These pin that the purge leaves nothing of the previous
/// user in any store it owns.
@Suite("SignedOutPurge")
@MainActor
struct SignedOutPurgeTests {

    private struct Fixture {
        let purge: SignedOutPurge
        let completeDrafts: CompleteDraftStore
        let completeDraftsDirectory: URL
        let refs: ReferenceDataService
        let stats: PathStatsService
        let urlCache: URLCache
    }

    private let owner = UUID()

    private func makeFixture() -> Fixture {
        let supabase = SupabaseService()
        let completeDraftsDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("signed-out-purge-drafts-\(UUID().uuidString)")
        let completeDrafts = CompleteDraftStore(directory: completeDraftsDirectory)
        let refs = ReferenceDataService(client: supabase.client)
        let stats = PathStatsService(supabase: supabase)
        // Memory-only, so the test never touches the app's real disk cache.
        let urlCache = URLCache(memoryCapacity: 1_000_000, diskCapacity: 0)
        let purge = SignedOutPurge(
            completeDrafts: completeDrafts,
            refs: refs,
            stats: stats,
            snapURLs: SnapURLCache(provider: FakeSignedURLProvider(), now: { Date() }),
            urlCache: urlCache
        )
        return Fixture(
            purge: purge,
            completeDrafts: completeDrafts, completeDraftsDirectory: completeDraftsDirectory,
            refs: refs, stats: stats, urlCache: urlCache
        )
    }

    @Test("Half-finished Complete flows are deleted from disk")
    func clearsCompleteDrafts() {
        let fixture = makeFixture()
        defer { try? FileManager.default.removeItem(at: fixture.completeDraftsDirectory) }
        let moment = UUID()
        var draft = CompleteDraft()
        draft.valence = .highlightSmall
        draft.emotionId = UUID()
        fixture.completeDrafts.save(draft, momentId: moment, ownerId: owner)
        #expect(fixture.completeDrafts.load(momentId: moment, ownerId: owner) == draft)

        fixture.purge.run()

        #expect(fixture.completeDrafts.load(momentId: moment, ownerId: owner) == nil)
        #expect(!FileManager.default.fileExists(atPath: fixture.completeDraftsDirectory.path))
    }

    @Test("Souls and collections are forgotten, shared domains are kept")
    func resetsReferenceData() {
        let fixture = makeFixture()
        let collection = PebbleCollection(id: UUID(), name: "Therapy")
        fixture.refs.replace(domains: [], souls: [], collections: [collection])
        #expect(fixture.refs.hasLoaded)

        fixture.purge.run()

        #expect(fixture.refs.souls.isEmpty)
        #expect(fixture.refs.collections.isEmpty)
        // A composer opened by the next user must wait for their own lists.
        #expect(!fixture.refs.hasLoaded)
    }

    @Test("Path stats are forgotten and reload for the next user")
    func resetsStats() {
        let fixture = makeFixture()
        fixture.stats.karma = 42
        fixture.stats.pebbles = 7
        fixture.stats.daysPracticed = 3
        fixture.stats.assiduity = [true, false]

        fixture.purge.run()

        #expect(fixture.stats.karma == nil)
        #expect(fixture.stats.pebbles == nil)
        #expect(fixture.stats.daysPracticed == nil)
        #expect(fixture.stats.assiduity == nil)
        #expect(!fixture.stats.hasLoaded)
    }

    @Test("Cached photo responses are removed")
    func clearsURLCache() throws {
        let fixture = makeFixture()
        let url = try #require(URL(string: "https://example.com/storage/v1/object/sign/snaps/a.jpg"))
        let request = URLRequest(url: url)
        let response = try #require(HTTPURLResponse(
            url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: nil
        ))
        fixture.urlCache.storeCachedResponse(
            CachedURLResponse(response: response, data: Data("jpeg".utf8)), for: request
        )
        #expect(fixture.urlCache.cachedResponse(for: request) != nil)

        fixture.purge.run()

        #expect(fixture.urlCache.cachedResponse(for: request) == nil)
    }
}
