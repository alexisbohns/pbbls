package app.pbbls.android.core.model

/**
 * Ids the Complete flow can verify synchronously from `ReferenceDataService`
 * when it restores an autosave. Souls and collections are user-owned and
 * deletable, so an autosave can outlive them; emotions and domains are seeded
 * reference rows and pass through untouched.
 */
data class KnownDraftIds(
    val soulIds: Set<String>,
    val collectionIds: Set<String>,
)
