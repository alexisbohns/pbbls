import Foundation
import Testing
@testable import Pebbles

/// The wraps kept on the device (#1226, spec §7.1). Android: `WrapDiskCacheTest`.
@Suite("Wrap disk cache")
struct WrapDiskCacheTests {
    private let root = FileManager.default.temporaryDirectory.appendingPathComponent("wraps-\(UUID().uuidString)")
    private let body = Data(#"{"items":[]}"#.utf8)

    @Test("another owner or another zone never reads a file")
    func isolation() {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        cache.write(body, page: "strip", owner: "mara", zone: "Europe/Paris")
        #expect(cache.read("strip", owner: "mara", zone: "Europe/Paris") == body)
        #expect(cache.read("strip", owner: "elio", zone: "Europe/Paris") == nil)
        #expect(cache.read("strip", owner: "mara", zone: "America/New_York") == nil)
    }

    @Test("a rewrite replaces the body, and a format bump drops the old tree")
    func rewriteAndBump() throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let old = root.appendingPathComponent("v0/mara/Europe_Paris")
        try FileManager.default.createDirectory(at: old, withIntermediateDirectories: true)
        try body.write(to: old.appendingPathComponent("strip.json"))
        cache.write(body, page: "week-2026-10-05", owner: "mara", zone: "Europe/Paris")
        let newer = Data(#"{"items":[{"kind":"month"}]}"#.utf8)
        cache.write(newer, page: "week-2026-10-05", owner: "mara", zone: "Europe/Paris")
        #expect(cache.read("week-2026-10-05", owner: "mara", zone: "Europe/Paris") == newer)
        #expect(!FileManager.default.fileExists(atPath: root.appendingPathComponent("v0").path))
    }

    @Test("clearing removes everything kept")
    func clear() {
        let cache = WrapDiskCache(root: root)
        cache.write(body, page: "strip", owner: "mara", zone: "Europe/Paris")
        cache.clear()
        #expect(cache.read("strip", owner: "mara", zone: "Europe/Paris") == nil)
        #expect(!FileManager.default.fileExists(atPath: root.path))
    }

    @Test("no path part climbs out of the root")
    func noEscape() {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let url = cache.file("../../secrets", owner: "../..", zone: "/etc")
        #expect(url.standardizedFileURL.path.hasPrefix(root.standardizedFileURL.path))
        #expect(WrapDiskCache.component("Europe/Paris") == "Europe_Paris")
        #expect(WrapDiskCache.component("") == "_")
    }

    @Test("the service reads a kept body back as the server's payload, and a body it cannot decode is a miss")
    @MainActor
    func serviceReadsKept() async throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        let service = PathWrapService(client: SupabaseService().client, disk: cache, owner: "mara")
        let week = CalendarDay(year: 2026, month: 10, day: 5)
        cache.write(try WrapPayloads.data("week-full"), page: WrapPages.week(week), owner: "mara",
                    zone: paris.identifier)
        cache.write(Data("not json".utf8), page: WrapPages.strip, owner: "mara", zone: paris.identifier)
        #expect(await service.cachedWeekWrap(week, zone: paris)?.pebbles == 11)
        #expect(await service.cachedStrip(zone: paris) == nil)
        let stranger = PathWrapService(client: SupabaseService().client, disk: cache, owner: nil)
        #expect(await stranger.cachedWeekWrap(week, zone: paris) == nil)
    }

    @Test("a body asked for before a purge is not kept after it")
    func purgeDropsLateWrites() {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let asked = cache.epoch
        cache.clear()
        cache.write(body, page: "strip", owner: "mara", zone: "Europe/Paris", epoch: asked)
        #expect(cache.read("strip", owner: "mara", zone: "Europe/Paris") == nil)
        cache.write(body, page: "strip", owner: "mara", zone: "Europe/Paris", epoch: cache.epoch)
        #expect(cache.read("strip", owner: "mara", zone: "Europe/Paris") == body)
    }

    @Test("the service keeps a body that decodes for its owner, and nothing else")
    @MainActor
    func serviceKeeps() throws {
        let cache = WrapDiskCache(root: root)
        defer { cache.clear() }
        let paris = try #require(TimeZone(identifier: "Europe/Paris"))
        let data = try WrapPayloads.data("week-full")
        let service = PathWrapService(client: SupabaseService().client, disk: cache, owner: "mara")
        let week = try service.decodeAndKeep(data, page: "week-2026-10-05", zone: paris, epoch: cache.epoch,
                                             as: WeekWrapPayload.self)
        #expect(week.model.pebbles == 11)
        #expect(cache.read("week-2026-10-05", owner: "mara", zone: paris.identifier) == data)

        #expect(throws: DecodingError.self) {
            try service.decodeAndKeep(Data("[]".utf8), page: "strip", zone: paris, epoch: cache.epoch,
                                      as: StripPayload.self)
        }
        #expect(cache.read("strip", owner: "mara", zone: paris.identifier) == nil)

        let stranger = PathWrapService(client: SupabaseService().client, disk: cache, owner: nil)
        _ = try stranger.decodeAndKeep(data, page: "week-2026-10-12", zone: paris, epoch: cache.epoch,
                                       as: WeekWrapPayload.self)
        #expect(cache.read("week-2026-10-12", owner: "mara", zone: paris.identifier) == nil)
    }
}
