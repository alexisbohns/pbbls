import Foundation
import Testing
@testable import Pebbles

/// The achievements page's service (#1306, spec §4): the last body that decoded
/// is kept per owner and zone in the wraps' disk cache, which `SignedOutPurge`
/// already wipes; a pull's empty answer is never success.
@Suite("Achievements page service")
@MainActor
struct AchievementsPageServiceTests {
    private let root = FileManager.default.temporaryDirectory.appendingPathComponent("achievements-\(UUID().uuidString)")

    @Test("a body that decodes is kept for its owner and zone, and reads back as the same page")
    func keeps() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        let data = try AchievementsFixtures.data("design-fixture")
        let service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        let page = try service.decodeAndKeep(data, zone: paris, epoch: cache.epoch)
        #expect(page.daysKept == 249)
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier) == data)
        #expect(await service.cachedPage(zone: paris) == page)
        let keptAt = try #require(await service.keptAt(zone: paris))
        #expect(abs(keptAt.timeIntervalSinceNow) < 60, "kept just now")
        let tokyo = try #require(TimeZone(identifier: "Asia/Tokyo"))
        #expect(await service.cachedPage(zone: tokyo) == nil)
    }

    @Test("nothing is kept without an owner, from a body that does not decode, or across a purge")
    func keepsNothingElse() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        let data = try AchievementsFixtures.data("design-fixture")
        let stranger = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: nil)
        _ = try stranger.decodeAndKeep(data, zone: paris, epoch: cache.epoch)
        #expect(await stranger.cachedPage(zone: paris) == nil)

        let service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        #expect(throws: DecodingError.self) {
            try service.decodeAndKeep(Data("[]".utf8), zone: paris, epoch: cache.epoch)
        }
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier) == nil)

        let asked = cache.epoch
        cache.clear()
        _ = try service.decodeAndKeep(data, zone: paris, epoch: asked)
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier) == nil)
    }

    @Test("an older read landing after a newer one never overwrites the kept copy")
    func olderReadKeepsNewer() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        var service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        service.freshness = AchievementsFreshness()
        let older = service.freshness.beginRead()
        let newer = service.freshness.beginRead()
        let newerBody = Data(#"{"days_kept": 249}"#.utf8)
        _ = try service.decodeAndKeep(newerBody, zone: paris, epoch: cache.epoch, read: newer)
        _ = try service.decodeAndKeep(Data(#"{"days_kept": 248}"#.utf8), zone: paris, epoch: cache.epoch, read: older)
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier) == newerBody)
        let tokyo = try #require(TimeZone(identifier: "Asia/Tokyo"))
        _ = try service.decodeAndKeep(newerBody, zone: tokyo, epoch: cache.epoch, read: older)
        #expect(cache.read(AchievementsPageService.cachePage, owner: "mara", zone: tokyo.identifier) == newerBody,
                "each zone keeps its own order")
    }

    @Test("a stale mark makes the kept copy not fresh until the next read rewrites it")
    func staleMark() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        let data = try AchievementsFixtures.data("design-fixture")
        var service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        service.freshness = AchievementsFreshness()
        _ = try service.decodeAndKeep(data, zone: paris, epoch: cache.epoch)
        #expect(await service.keptAt(zone: paris) != nil)
        service.markStale()
        #expect(await service.keptAt(zone: paris) == nil, "an unlock or a pull since: read again")
        #expect(await service.cachedPage(zone: paris) != nil, "the copy still shows at once")
        try await Task.sleep(nanoseconds: 20_000_000)
        _ = try service.decodeAndKeep(data, zone: paris, epoch: cache.epoch)
        #expect(await service.keptAt(zone: paris) != nil, "a read after the mark is fresh again")
    }

    @Test("a kept body this build can no longer decode is a miss")
    func undecodableKept() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        cache.write(Data("not json".utf8), page: AchievementsPageService.cachePage, owner: "mara", zone: paris.identifier)
        let service = AchievementsPageService(client: SupabaseService().client, disk: cache, owner: "mara")
        #expect(await service.cachedPage(zone: paris) == nil)
    }

    @Test("a pull's row is the karma it paid; no row is never success; not_ready is recognised")
    func pullOutcome() throws {
        let rows = try JSONDecoder().decode([AchievementsPageService.PullRow].self,
                                            from: Data(#"[{"stack": "six_firsts", "karma_granted": 30}]"#.utf8))
        #expect(AchievementsPageService.outcome(rows) == .pulled(karma: 30))
        let unpaid = try JSONDecoder().decode([AchievementsPageService.PullRow].self,
                                              from: Data(#"[{"stack": "emotions", "karma_granted": 0}]"#.utf8))
        #expect(AchievementsPageService.outcome(unpaid) == .pulled(karma: 0), "a row is a pull, even unpaid")
        #expect(AchievementsPageService.outcome([]) == .alreadyPulled)
        #expect(AchievementsPageService.isNotReady("not_ready"))
        #expect(!AchievementsPageService.isNotReady("invalid_stack"))
    }
}
