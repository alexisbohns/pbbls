import Foundation
import os
import Supabase

/// Shared @Observable wrapper around `v_karma_summary`, `v_bounce`, and
/// `v_ripple`. PathView (bottom bar) and ProfileView read the same
/// instance so a reload from one screen is visible to the other.
@Observable
@MainActor
final class PathStatsService {
    var karma: Int?
    var bounce: Int?
    var ripple: RippleSummary?

    private var isLoading = false
    private(set) var hasLoaded = false

    private let supabase: SupabaseService
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path-stats")

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    /// Idempotent. Returns immediately if already loaded or currently loading,
    /// so it is safe to call from every view's `.task` modifier.
    func load() async {
        guard !hasLoaded, !isLoading else { return }
        await performLoad()
    }

    /// Forces a network reload, bypassing the `hasLoaded` cache. Still guards
    /// against concurrent calls so spam-tapping cannot fan out parallel queries.
    func refresh() async {
        guard !isLoading else { return }
        await performLoad()
    }

    private func performLoad() async {
        isLoading = true
        defer { isLoading = false }

        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary").select("total_karma, pebbles_count")
            .single().execute().value
        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce").select("bounce_level, active_days")
            .single().execute().value
        async let rippleResult: RippleSummary = supabase.client
            .from("v_ripple").select("ripple_level, pebbles_28d, active_today")
            .single().execute().value

        do {
            self.karma = try await karmaResult.totalKarma
        } catch {
            logger.error("karma fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        do {
            self.bounce = try await bounceResult.bounceLevel
        } catch {
            logger.error("bounce fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        do {
            self.ripple = try await rippleResult
        } catch {
            logger.error("ripple fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        hasLoaded = true
    }
}
