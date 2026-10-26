import Foundation
import Supabase

/// Home's server reads, behind a protocol so the Lab and tests use fakes.
/// Nothing here aggregates: every number comes from an RPC or a table the
/// server owns (spec D9). Later parts add wraps, reveal and guides.
protocol HomeServicing: Sendable {
    /// `profiles.display_name` of the signed-in user, or nil when unset.
    func displayName() async throws -> String?
}

struct HomeService: HomeServicing {
    let client: SupabaseClient

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
}
