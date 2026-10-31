import Foundation
import os
import Supabase

/// Home's server reads, behind a protocol so the Lab and tests use fakes.
/// Nothing here aggregates: every number comes from an RPC or a table the
/// server owns (spec D9). Later parts add wraps, reveal and guides.
protocol HomeServicing: Sendable {
    /// `profiles.display_name` of the signed-in user, or nil when unset.
    func displayName() async throws -> String?
    /// `home_wraps`: the months holding a pebble, newest first.
    func wraps(zone: TimeZone) async throws -> HomeWraps
    /// `reveal_wrap`: unfolds `month`; the server records when.
    func reveal(month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal
    /// `guides`: the published About Pebbles readings, in sort order.
    func guides() async throws -> [Guide]
    /// `path_month_wrap` for Home's week columns (#1382 D8), through the Path's
    /// service so the device's copy is the one the Path keeps.
    func month(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap
    /// The device's last copy of `month`, or nil: display only, never instead of a fetch.
    func cachedMonth(_ month: CalendarMonth, zone: TimeZone) async -> MonthWrap?
}

struct HomeService: HomeServicing {
    let client: SupabaseClient
    /// The signed-in user's id, so the month's disk copy is the Path's.
    var owner: String?

    private var pathWraps: PathWrapService { PathWrapService(client: client, disk: .standard, owner: owner) }

    func month(_ month: CalendarMonth, zone: TimeZone) async throws -> MonthWrap {
        try await pathWraps.monthWrap(month, zone: zone)
    }

    func cachedMonth(_ month: CalendarMonth, zone: TimeZone) async -> MonthWrap? {
        await pathWraps.cachedMonthWrap(month, zone: zone)
    }

    func displayName() async throws -> String? {
        struct Row: Decodable { let display_name: String? }
        // RLS returns the caller's own row, as `ProfileView` relies on.
        let row: Row = try await client
            .from("profiles")
            .select("display_name")
            .single()
            .execute()
            .value
        return row.display_name
    }

    func wraps(zone: TimeZone) async throws -> HomeWraps {
        let data = try await client.rpc("home_wraps", params: ["p_tz": zone.identifier]).execute().data
        let payload = try WrapDecoding.decode(HomeWrapsPayload.self, from: data)
        if payload.dropped > 0 {
            Logger(subsystem: "app.pbbls.ios", category: "home.service")
                .warning("home_wraps: dropped \(payload.dropped) month(s) the client could not decode")
        }
        return payload.model
    }

    func reveal(month: CalendarMonth, zone: TimeZone) async throws -> HomeReveal {
        let data = try await client
            .rpc("reveal_wrap", params: ["p_month": month.firstDay.iso, "p_tz": zone.identifier])
            .execute().data
        return try WrapDecoding.decode(HomeRevealPayload.self, from: data).model
    }

    func guides() async throws -> [Guide] {
        let rows: LossyGuideArray = try await client
            .from("guides")
            .select()
            .eq("published", value: true)
            .order("sort_order")
            .execute()
            .value
        return rows.guides
    }
}
