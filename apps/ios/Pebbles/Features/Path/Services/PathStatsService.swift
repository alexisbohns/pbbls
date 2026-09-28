import Foundation
import os
import Supabase

/// Shared @Observable wrapper around `v_karma_summary` and `v_ripple`.
/// PathView (bottom bar) and ProfileView read the same instance so a
/// reload from one screen is visible to the other.
@Observable
@MainActor
final class PathStatsService {
    var karma: Int?
    var ripple: RippleSummary?
    var pebbles: Int?
    var daysPracticed: Int?
    var assiduity: [Bool]?

    private var isLoading = false
    private(set) var hasLoaded = false
    /// Bumped by `reset()`, so a fetch that outlives its session is dropped.
    private var generation = 0

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

    /// Forget the signed-out user's stats. Without this `hasLoaded` stays true
    /// and the next account's Path and Profile show the previous one's karma.
    func reset() {
        generation += 1
        karma = nil
        ripple = nil
        pebbles = nil
        daysPracticed = nil
        assiduity = nil
        hasLoaded = false
        // A fetch still in flight belongs to the ended session. Releasing the
        // guard lets the next user's first `load()` start instead of no-oping.
        isLoading = false
    }

    private func performLoad() async {
        isLoading = true
        let started = generation
        defer { if generation == started { isLoading = false } }

        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary").select("total_karma, pebbles_count")
            .single().execute().value
        async let rippleResult: RippleSummary = supabase.client
            .from("v_ripple").select("ripple_level, pebbles_28d, active_today")
            .single().execute().value
        async let engagementResult: [ProfileEngagement] = supabase.client
            .rpc("get_profile_engagement", params: ["p_tz": TimeZone.current.identifier])
            .execute().value

        // Each write re-checks the generation: a sign-out can land between any
        // two of these awaits.
        do {
            let summary = try await karmaResult
            guard generation == started else { return }
            self.karma   = summary.totalKarma
            self.pebbles = summary.pebblesCount
        } catch {
            logger.error("karma fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        do {
            let ripple = try await rippleResult
            guard generation == started else { return }
            self.ripple = ripple
        } catch {
            logger.error("ripple fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        do {
            let row = try await engagementResult.first
            guard generation == started else { return }
            if let row {
                self.daysPracticed = row.daysPracticed
                self.assiduity     = row.assiduity
            }
        } catch {
            logger.error("engagement fetch failed: \(error.localizedDescription, privacy: .private)")
        }

        guard generation == started else { return }
        hasLoaded = true
    }
}
