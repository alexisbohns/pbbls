import Foundation
import Supabase

/// The seam `PathModel`'s tests fake (#1226).
protocol PathWrapServicing: Sendable {
    func strip(zone: TimeZone) async throws -> Strip
    func weekWrap(_ weekStart: CalendarDay, zone: TimeZone) async throws -> WeekWrap
    func monthWrap(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap
}

/// The Path's strip, week wraps and month pages (#1226). The server aggregates
/// them (`path_strip`, `path_week_wrap`, `path_month_wrap`); this service only
/// calls, decodes and maps. Each call sends the zone the feed buckets its days
/// in (`SpineCalendar.current.timeZone`). Errors propagate; callers own their
/// loading and error state. The methods are nonisolated `async`, so under the
/// Swift 5 language mode a caller on the main actor waits while the decoding
/// runs on the global executor (Swift 6.2's nonisolated-nonsending default
/// would need `@concurrent` here).
struct PathWrapService: PathWrapServicing {
    let client: SupabaseClient

    func strip(zone: TimeZone) async throws -> Strip {
        let data = try await call("path_strip", ["p_tz": zone.identifier])
        return try WrapDecoding.decode(StripPayload.self, from: data).model
    }

    func weekWrap(_ weekStart: CalendarDay, zone: TimeZone) async throws -> WeekWrap {
        let data = try await call("path_week_wrap", ["p_week_start": weekStart.iso, "p_tz": zone.identifier])
        return try WrapDecoding.decode(WeekWrapPayload.self, from: data).model
    }

    func monthWrap(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap {
        let data = try await call("path_month_wrap", ["p_month": month.firstDay.iso, "p_tz": zone.identifier])
        return try WrapDecoding.decode(MonthWrapPayload.self, from: data).model
    }

    private func call(_ function: String, _ params: [String: String]) async throws -> Data {
        try await client.rpc(function, params: params).execute().data
    }
}
