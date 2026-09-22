package app.pbbls.android.testing

import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.SoulWithGlyph

/**
 * In-memory [ReferenceDataServicing] (#848). See [PebblesTestHarness] for where
 * these live and why.
 *
 * The four state properties are plain `var`s; a fake needs no Compose state.
 *
 * **It cannot be made to throw, deliberately.** Every method on the real
 * [app.pbbls.android.core.data.ReferenceDataService] swallows its own failure:
 * `load`, `refreshSouls` and `refreshCollections` wrap their whole body in
 * `try`/`catch` + `Log.e` and return normally, and `createSoul` returns null.
 * Reference-data failure is observable ONLY as "the lists stayed empty" or
 * "`hasLoaded` stayed false" — never as an exception. A fake that could throw
 * here would let a test assert an error path the screen can never actually
 * reach.
 *
 * So the error path is [createSoulResult] = null, and "the load failed" is
 * modelled by leaving the lists empty.
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
     * rather than throwing.
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

    override suspend fun load() {
        loadCount += 1
        hasLoaded = true
    }

    override suspend fun refreshSouls() {
        refreshSoulsCount += 1
    }

    override suspend fun refreshCollections() {
        refreshCollectionsCount += 1
    }

    /**
     * Appends to [souls] on success, so a test can assert the cache updated —
     * re-sorting by name the way the real service does, because the picker's
     * ordering is what the cache update exists to keep correct.
     */
    override suspend fun createSoul(name: String): SoulWithGlyph? {
        createSoulCalls += name
        val created = createSoulResult(name) ?: return null
        souls = (souls + created).sortedBy { it.name }
        return created
    }
}
