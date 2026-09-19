package app.pbbls.android.testing

import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.ReferenceDataServicing

/**
 * In-memory [ReferenceDataServicing] for tests.
 *
 * It holds no `SupabaseClient` and reads no `BuildConfig` — that is the whole
 * point: the form's pickers are drivable without a live project (#848). The
 * four state properties are plain `var`s; a fake needs no Compose state.
 *
 * Lives in `src/test` because that is the only consumer until #857 lands
 * Robolectric; it moves to a real `core/testing` module with #851.
 */
class FakeReferenceDataService(
    override var domains: List<Domain> = emptyList(),
    override var souls: List<SoulWithGlyph> = emptyList(),
    override var collections: List<PebbleCollection> = emptyList(),
    override var hasLoaded: Boolean = false,
) : ReferenceDataServicing {
    /**
     * What [createSoul] returns, built from the requested name. **null is the
     * error path** — the real service swallows the failure and returns null
     * rather than throwing, so a test drives that case here, not with
     * [failNext].
     */
    var createSoulResult: (String) -> SoulWithGlyph? = { name ->
        SoulWithGlyph(
            id = "soul-$name",
            name = name,
            glyphId = "glyph-system",
            glyph = Glyph(id = "glyph-system", strokes = emptyList(), viewBox = "0 0 100 100"),
        )
    }

    /** Every name passed to [createSoul], oldest first. */
    val createSoulCalls = mutableListOf<String>()

    var loadCount = 0
        private set

    var refreshSoulsCount = 0
        private set

    var refreshCollectionsCount = 0
        private set

    /**
     * Thrown by the next call, then cleared — so one fake can drive a failure
     * and the retry that follows it without being rebuilt. [createSoul] is
     * exempt: its failure is a null result, matching the real service.
     */
    var failNext: Exception? = null

    override suspend fun load() {
        loadCount += 1
        throwIfArmed()
        hasLoaded = true
    }

    override suspend fun refreshSouls() {
        refreshSoulsCount += 1
        throwIfArmed()
    }

    override suspend fun refreshCollections() {
        refreshCollectionsCount += 1
        throwIfArmed()
    }

    /** Appends to [souls] on success, so a test can assert the cache updated. */
    override suspend fun createSoul(name: String): SoulWithGlyph? {
        createSoulCalls += name
        val created = createSoulResult(name) ?: return null
        souls = souls + created
        return created
    }

    private fun throwIfArmed() {
        val failure = failNext ?: return
        failNext = null
        throw failure
    }
}
