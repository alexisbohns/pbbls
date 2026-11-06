import Foundation
import Supabase

/// The onboarding's reads and its one write (#1510), a seam so the flow model is tested with a fake.
protocol OnboardingServicing: Sendable {
    /// The most recently updated published letter, or nil when none is published.
    func promiseLetter() async throws -> PromiseLetter?
    /// The system glyphs for the starter grid (D7): eight are shown.
    func systemGlyphs() async throws -> [Glyph]
    /// `profiles.onboarding_completed` for the signed-in user. Throws rather than guessing (#784).
    func onboardingCompleted() async throws -> Bool
    /// `complete_onboarding`: one transaction for everything collected (D5). Throws on refusal.
    func complete(_ answers: OnboardingAnswers) async throws
}

struct OnboardingService: OnboardingServicing {
    let client: SupabaseClient

    func promiseLetter() async throws -> PromiseLetter? {
        let rows: [PromiseLetter] = try await client
            .from("promise_letters")
            .select("body_md_en, body_md_fr, signed_by_en, signed_by_fr")
            .eq("published", value: true)
            .order("updated_at", ascending: false)
            .limit(1)
            .execute()
            .value
        return rows.first
    }

    func systemGlyphs() async throws -> [Glyph] {
        try await client
            .from("glyphs")
            .select("id, name, strokes, view_box, user_id")
            .eq("is_system", value: true)
            .order("created_at", ascending: true)
            .limit(8)
            .execute()
            .value
    }

    func onboardingCompleted() async throws -> Bool {
        let row: OnboardingCompletedRow = try await client
            .from("profiles")
            .select("onboarding_completed")
            .single()
            // One attempt: the app holds on this read, and the SDK's retries (three more, 60 s each)
            // would outlast the gate's ceiling. Past that the consent gate decides, so there's no
            // reason to keep asking.
            .retry(enabled: false)
            .execute()
            .value
        return row.completed
    }

    func complete(_ answers: OnboardingAnswers) async throws {
        try await client.rpc("complete_onboarding", params: answers.params).execute()
    }
}

private struct OnboardingCompletedRow: Decodable {
    let completed: Bool
    enum CodingKeys: String, CodingKey { case completed = "onboarding_completed" }
}
