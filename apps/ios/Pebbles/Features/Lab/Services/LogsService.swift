import Foundation
import Supabase
import os

/// Read/write access to `public.logs` and `public.log_reactions` for the Lab tab.
/// Reads go through `v_logs_with_counts` so every row carries its reaction count.
/// Reactions are single-table writes on `log_reactions` — no RPC needed.
@MainActor
struct LogsService {
    let supabase: SupabaseService

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "logs-service")

    // MARK: - Feeds

    /// Published announcements, most recent first.
    func announcements(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.announcement.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
        if let limit {
            let wrapper: LossyLogArray = try await base.limit(limit).execute().value
            return wrapper.logs
        }
        let wrapper: LossyLogArray = try await base.execute().value
        return wrapper.logs
    }

    /// Shipped features, most recent first.
    func changelog(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.shipped.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
        if let limit {
            let wrapper: LossyLogArray = try await base.limit(limit).execute().value
            return wrapper.logs
        }
        let wrapper: LossyLogArray = try await base.execute().value
        return wrapper.logs
    }

    /// Features currently in progress.
    func initiatives() async throws -> [Log] {
        let wrapper: LossyLogArray = try await supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.inProgress.rawValue)
            .eq("published", value: true)
            .order("published_at", ascending: false)
            .execute()
            .value
        return wrapper.logs
    }

    /// Backlog features, most upvoted first. Ties broken by recency.
    func backlog(limit: Int? = nil) async throws -> [Log] {
        let base = supabase.client
            .from("v_logs_with_counts")
            .select()
            .eq("species", value: LogSpecies.feature.rawValue)
            .eq("status", value: LogStatus.backlog.rawValue)
            .eq("published", value: true)
            .order("reaction_count", ascending: false)
            .order("created_at", ascending: false)
        if let limit {
            let wrapper: LossyLogArray = try await base.limit(limit).execute().value
            return wrapper.logs
        }
        let wrapper: LossyLogArray = try await base.execute().value
        return wrapper.logs
    }

    // MARK: - Reactions

    /// The set of log ids the current user has upvoted. Used by views to
    /// render the reaction button in its "reacted" state and to pick the
    /// right toggle action. Returns an empty set if no session.
    func myReactions() async throws -> Set<UUID> {
        guard let userId = supabase.session?.user.id else { return [] }
        let rows: [ReactionRow] = try await supabase.client
            .from("log_reactions")
            .select("log_id")
            .eq("user_id", value: userId)
            .execute()
            .value
        return Set(rows.map(\.logId))
    }

    /// Upvote a log on behalf of the current user. No-op on unique-key conflict —
    /// the PK `(log_id, user_id)` ensures each user can react at most once.
    func react(logId: UUID) async throws {
        guard let userId = supabase.session?.user.id else {
            throw LogsServiceError.missingSession
        }
        do {
            try await supabase.client
                .from("log_reactions")
                .insert(ReactionInsert(logId: logId, userId: userId))
                .execute()
        } catch {
            Self.logger.error("react failed: \(error.localizedDescription, privacy: .private)")
            throw error
        }
    }

    /// Remove the current user's upvote on a log.
    func unreact(logId: UUID) async throws {
        guard let userId = supabase.session?.user.id else {
            throw LogsServiceError.missingSession
        }
        do {
            try await supabase.client
                .from("log_reactions")
                .delete()
                .eq("log_id", value: logId)
                .eq("user_id", value: userId)
                .execute()
        } catch {
            Self.logger.error("unreact failed: \(error.localizedDescription, privacy: .private)")
            throw error
        }
    }
}

// MARK: - Wire types

private struct ReactionRow: Decodable {
    let logId: UUID
    enum CodingKeys: String, CodingKey { case logId = "log_id" }
}

private struct ReactionInsert: Encodable {
    let logId: UUID
    let userId: UUID
    enum CodingKeys: String, CodingKey {
        case logId = "log_id"
        case userId = "user_id"
    }
}

enum LogsServiceError: Error, LocalizedError {
    case missingSession

    var errorDescription: String? {
        switch self {
        case .missingSession: return "Please sign in again."
        }
    }
}
