import Foundation

/// Compile-time mirror of the emotion and domain slugs in the live
/// Supabase `public.emotions` and `public.domains` tables. Snapshot
/// taken 2026-04-21.
///
/// The seed migration at
/// `packages/supabase/supabase/migrations/20260411000000_reference_tables.sql`
/// is NOT the authoritative source — reference rows have been added /
/// renamed via the Supabase dashboard since the initial seed. When a new
/// reference row is added server-side, this list AND the corresponding
/// `emotion.<slug>.name` / `domain.<slug>.name` catalog entries MUST be
/// updated in the same change — otherwise the coverage test in
/// `LocalizationTests` fails.
enum ReferenceSlugs {
    static let emotions: [String] = [
        "amazed", "amused", "angry", "annoyed", "anxious", "ashamed",
        "brave", "calm", "confident", "content", "disappointed",
        "discouraged", "disgusted", "drained", "embarrassed", "excited",
        "frustrated", "grateful", "guilty", "happy", "hopeful", "hopeless",
        "indifferent", "irritated", "jealous", "joyful", "lonely",
        "overwhelmed", "passionate", "peaceful", "proud", "relieved",
        "sad", "satisfied", "scared", "stressed", "surprised", "worried"
    ]

    static let domains: [String] = [
        "community", "currentevents", "dating", "education", "family",
        "fitness", "friends", "health", "hobbies", "identity", "money",
        "partner", "selfcare", "spirituality", "tasks", "travel",
        "weather", "work"
    ]
}
