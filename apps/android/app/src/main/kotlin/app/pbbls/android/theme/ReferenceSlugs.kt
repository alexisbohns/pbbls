package app.pbbls.android.theme

/**
 * Compile-time mirror of the emotion, category, and domain slugs in the live
 * Supabase tables. Ported from
 * `apps/ios/Pebbles/Features/Path/Models/ReferenceSlugs.swift` (snapshot
 * 2026-04-21; categories added 2026-05-09).
 *
 * The seed migration at
 * `packages/supabase/supabase/migrations/20260411000000_reference_tables.sql`
 * is NOT the authoritative source — reference rows have been added / renamed
 * via the Supabase dashboard since the initial seed. When a new reference row
 * is added server-side, this list AND the corresponding
 * `emotion_<slug>_name` / `emotionCategory_<slug>_name` / `domain_<slug>_name`
 * string-resource entries MUST be updated in the same change — otherwise the
 * coverage test in `LocalizationParityTest` fails.
 */
object ReferenceSlugs {
    val emotions: List<String> =
        listOf(
            "amazed",
            "amused",
            "angry",
            "annoyed",
            "anxious",
            "ashamed",
            "brave",
            "calm",
            "confident",
            "content",
            "disappointed",
            "discouraged",
            "disgusted",
            "drained",
            "embarrassed",
            "excited",
            "frustrated",
            "grateful",
            "guilty",
            "happy",
            "hopeful",
            "hopeless",
            "indifferent",
            "irritated",
            "jealous",
            "joyful",
            "lonely",
            "overwhelmed",
            "passionate",
            "peaceful",
            "proud",
            "relieved",
            "sad",
            "satisfied",
            "scared",
            "stressed",
            "surprised",
            "worried",
        )

    val emotionCategories: List<String> =
        listOf("anger", "fear", "joy", "peace", "pride", "sadness", "shame")

    val domains: List<String> =
        listOf(
            "community",
            "currentevents",
            "dating",
            "education",
            "family",
            "fitness",
            "friends",
            "health",
            "hobbies",
            "identity",
            "money",
            "partner",
            "selfcare",
            "spirituality",
            "tasks",
            "travel",
            "weather",
            "work",
        )
}
