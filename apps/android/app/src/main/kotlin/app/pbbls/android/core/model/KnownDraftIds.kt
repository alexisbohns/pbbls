package app.pbbls.android.core.model

/**
 * Ids the Complete flow can verify synchronously from `ReferenceDataService`
 * when it restores an autosave. Souls and collections are user-owned and
 * deletable, so an autosave can outlive them. Domains are reference rows but a
 * retired one must not survive a restore: nothing could untick it and the server would refuse it for
 * ever. Emotions pass through untouched.
 */
data class KnownDraftIds(
    val domainIds: Set<String>,
    val soulIds: Set<String>,
    val collectionIds: Set<String>,
)
