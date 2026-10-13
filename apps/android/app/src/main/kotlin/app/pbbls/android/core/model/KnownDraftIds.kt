package app.pbbls.android.core.model

/**
 * Ids the composer can verify synchronously from `ReferenceDataService`. Souls
 * and collections are user-owned and deletable, so a draft can outlive them;
 * emotions and domains are seeded reference rows and pass through untouched.
 *
 * Glyphs are deliberately absent: usability is `can_use_glyph(id, user)` (own ∪
 * system ∪ entitled), the same predicate `create_pebble` enforces, and only the
 * server can answer it. `CreatePebbleScreen` verifies it after hydrating.
 */
data class KnownDraftIds(
    val soulIds: Set<String>,
    val collectionIds: Set<String>,
)
