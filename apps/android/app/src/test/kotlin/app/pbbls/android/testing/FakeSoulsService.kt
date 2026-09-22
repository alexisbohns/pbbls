package app.pbbls.android.testing

import app.pbbls.android.core.data.SoulsServicing
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.SoulWithGlyph
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [SoulsServicing] (#849) — the souls list, detail and form paths,
 * drivable without a live project. See [PebblesTestHarness] for where these
 * live and why.
 */
class FakeSoulsService(
    /** What [list] returns on a successful call. Settable mid-test. */
    var souls: List<SoulWithGlyph> = emptyList(),
    /** What [loadSoul] returns on a successful call. */
    var soul: SoulWithGlyph? = null,
    /** What [loadPebbles] returns on a successful call. */
    var pebbles: List<Pebble> = emptyList(),
    /** What [loadGlyph] and [create] hand back. */
    var glyph: Glyph? = null,
) : SoulsServicing {
    var listCount = 0
        private set

    var loadSoulCount = 0
        private set

    /** `(name, glyphId)` of every [create], oldest first. */
    val createCalls = mutableListOf<Pair<String, String>>()

    /** `(soulId, name, glyphId)` of every [update], oldest first. */
    val updateCalls = mutableListOf<Triple<String, String, String>>()

    /** Every id passed to [delete], oldest first. */
    val deleteCalls = mutableListOf<String>()

    /** Every id passed to [loadGlyph], oldest first. */
    val loadGlyphCalls = mutableListOf<String>()

    /**
     * Awaited by [create] and [update] when set, so a test can hold a save open
     * at the server call and act while it is in flight — the shape of the
     * cancellation #849 is about. Complete it to let the save proceed.
     */
    var writeGate: CompletableDeferred<Unit>? = null

    /** Awaited by [loadGlyph] when set — the create form's late default glyph. */
    var loadGlyphGate: CompletableDeferred<Unit>? = null

    /** Awaited by [delete] when set, to hold a delete open at the server call. */
    var deleteGate: CompletableDeferred<Unit>? = null

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun list(): List<SoulWithGlyph> {
        listCount += 1
        armed.fire()
        return souls
    }

    override suspend fun loadSoul(soulId: String): SoulWithGlyph {
        loadSoulCount += 1
        armed.fire()
        return soul ?: souls.first { it.id == soulId }
    }

    override suspend fun loadPebbles(soulId: String): List<Pebble> {
        armed.fire()
        return pebbles
    }

    override suspend fun create(
        name: String,
        glyphId: String,
    ): SoulWithGlyph {
        createCalls += name to glyphId
        writeGate?.await()
        armed.fire()
        return SoulWithGlyph(
            id = "new-soul",
            name = name,
            glyphId = glyphId,
            glyph = requireNotNull(glyph) { "seed FakeSoulsService.glyph to create" },
        )
    }

    override suspend fun update(
        soulId: String,
        name: String,
        glyphId: String,
    ) {
        updateCalls += Triple(soulId, name, glyphId)
        writeGate?.await()
        armed.fire()
    }

    override suspend fun delete(soulId: String) {
        deleteCalls += soulId
        deleteGate?.await()
        armed.fire()
    }

    override suspend fun loadGlyph(glyphId: String): Glyph {
        loadGlyphCalls += glyphId
        loadGlyphGate?.await()
        armed.fire()
        return requireNotNull(glyph) { "seed FakeSoulsService.glyph to loadGlyph" }
    }
}
