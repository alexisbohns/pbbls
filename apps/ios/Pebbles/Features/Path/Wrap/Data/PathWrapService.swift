import Foundation
import os
import Supabase

/// The seam `PathModel`'s tests fake (#1226).
protocol PathWrapServicing: Sendable {
    func strip(zone: TimeZone) async throws -> Strip
    func weekWrap(_ weekStart: CalendarDay, zone: TimeZone) async throws -> WeekWrap
    func monthWrap(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap

    /// The device's last copy of each payload (spec §7.1), or nil: display only,
    /// never instead of a fetch.
    func cachedStrip(zone: TimeZone) async -> Strip?
    func cachedWeekWrap(_ weekStart: CalendarDay, zone: TimeZone) async -> WeekWrap?
    func cachedMonthWrap(_ month: CalendarMonth, zone: TimeZone) async -> MonthWrap?
}

/// The Path's strip, week wraps and month pages (#1226). The server aggregates
/// them (`path_strip`, `path_week_wrap`, `path_month_wrap`); this service only
/// calls, decodes and maps. Each call sends the zone the feed buckets its days
/// in (`SpineCalendar.current.timeZone`). Errors propagate; callers own their
/// loading and error state. The methods are nonisolated `async`, so under the
/// Swift 5 language mode a caller on the main actor waits while the decoding
/// (and the disk) runs on the global executor (Swift 6.2's nonisolated-nonsending
/// default would need `@concurrent` here).
///
/// Each body that decodes is kept on the device for its `owner` (`disk`), so
/// the next cold start can show it while the fetch runs. A kept body this build
/// can no longer decode is a miss.
struct PathWrapService: PathWrapServicing {
    let client: SupabaseClient
    var disk: WrapDiskCache?
    /// The signed-in user's id; nothing is kept or read without one.
    var owner: String?

    func strip(zone: TimeZone) async throws -> Strip {
        try await fetch(WrapPages.strip, zone, "path_strip", [:], as: StripPayload.self).model
    }

    func weekWrap(_ weekStart: CalendarDay, zone: TimeZone) async throws -> WeekWrap {
        try await fetch(WrapPages.week(weekStart), zone, "path_week_wrap", ["p_week_start": weekStart.iso],
                        as: WeekWrapPayload.self).model
    }

    func monthWrap(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap {
        try await fetch(WrapPages.month(month), zone, "path_month_wrap", ["p_month": month.firstDay.iso],
                        as: MonthWrapPayload.self).model
    }

    func cachedStrip(zone: TimeZone) async -> Strip? {
        kept(WrapPages.strip, zone, as: StripPayload.self)?.model
    }

    func cachedWeekWrap(_ weekStart: CalendarDay, zone: TimeZone) async -> WeekWrap? {
        kept(WrapPages.week(weekStart), zone, as: WeekWrapPayload.self)?.model
    }

    func cachedMonthWrap(_ month: CalendarMonth, zone: TimeZone) async -> MonthWrap? {
        kept(WrapPages.month(month), zone, as: MonthWrapPayload.self)?.model
    }

    /// Calls, decodes and keeps. The purge epoch is taken before the request,
    /// so a body that lands after a sign-out is not kept.
    private func fetch<Payload: Decodable>(_ page: String, _ zone: TimeZone, _ function: String,
                                           _ params: [String: String], as type: Payload.Type) async throws -> Payload {
        var params = params
        params["p_tz"] = zone.identifier
        let epoch = disk?.epoch
        let data = try await client.rpc(function, params: params).execute().data
        return try decodeAndKeep(data, page: page, zone: zone, epoch: epoch, as: type)
    }

    /// Decodes `data` and, once it has decoded, keeps it for the owner; nothing
    /// is kept without an owner, from a body that does not decode, or across a purge.
    func decodeAndKeep<Payload: Decodable>(_ data: Data, page: String, zone: TimeZone, epoch: Int?,
                                           as type: Payload.Type) throws -> Payload {
        let payload = try WrapDecoding.decode(type, from: data)
        if let disk, let owner { disk.write(data, page: page, owner: owner, zone: zone.identifier, epoch: epoch) }
        return payload
    }

    private func kept<Payload: Decodable>(_ page: String, _ zone: TimeZone, as type: Payload.Type) -> Payload? {
        guard let disk, let owner, let data = disk.read(page, owner: owner, zone: zone.identifier) else { return nil }
        do {
            return try WrapDecoding.decode(type, from: data)
        } catch {
            Self.logger.error("a kept wrap no longer decodes: \(error.localizedDescription, privacy: .private)")
            return nil
        }
    }

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "path-wraps")
}

/// The kept pages' names: one file each.
enum WrapPages {
    static let strip = "strip"

    static func week(_ start: CalendarDay) -> String { "week-\(start.iso)" }

    static func month(_ month: CalendarMonth) -> String { "month-\(month.firstDay.iso)" }
}
