import Foundation
import os
import Supabase

/// Shared @Observable wrapper around `v_karma_summary` and `v_bounce`.
/// PathView (bottom bar) and ProfileView read the same instance so a
/// reload from one screen is visible to the other.
@Observable
@MainActor
final class PathStatsService {
    var karma: Int?
    var bounce: Int?

    private let supabase: SupabaseService
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path-stats")

    init(supabase: SupabaseService) {
        self.supabase = supabase
    }

    func load() async {
        async let karmaResult: KarmaSummary = supabase.client
            .from("v_karma_summary").select("total_karma, pebbles_count")
            .single().execute().value
        async let bounceResult: BounceSummary = supabase.client
            .from("v_bounce").select("bounce_level, active_days")
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
    }
}
