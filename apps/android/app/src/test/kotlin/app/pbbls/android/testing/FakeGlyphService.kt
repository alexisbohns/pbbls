package app.pbbls.android.testing

import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.features.glyph.services.GlyphServicing
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [GlyphServicing] (#849) — the carve save and the store rename,
 * drivable without a live project. See [PebblesTestHarness] for where these
 * live and why.
 */
class FakeGlyphService(
    /** What [list] returns on a successful call. */
    var glyphs: List<Glyph> = emptyList(),
) : GlyphServicing {
    /** `(strokes, name)` of every [create], oldest first. */
    val createCalls = mutableListOf<Pair<List<GlyphStroke>, String?>>()

    /** `(glyphId, name)` of every [updateName], oldest first. */
    val updateNameCalls = mutableListOf<Pair<String, String?>>()

    /** Awaited by [create] and [updateName] when set, to hold a write in flight. */
    var writeGate: CompletableDeferred<Unit>? = null

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun list(): List<Glyph> {
        armed.fire()
        return glyphs
    }

    override suspend fun create(
        strokes: List<GlyphStroke>,
        name: String?,
    ): Glyph {
        createCalls += strokes to name
        writeGate?.await()
        armed.fire()
        return Glyph(
            id = "glyph-new",
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            strokes = strokes,
            viewBox = "0 0 200 200",
            userId = "me",
        )
    }

    override suspend fun updateName(
        glyphId: String,
        name: String?,
    ): Glyph {
        updateNameCalls += glyphId to name
        writeGate?.await()
        armed.fire()
        return Glyph(
            id = glyphId,
            // The server normalizes; the fake mirrors it so a test can tell the
            // optimistic caption from the one that came back.
            name = name?.trim()?.takeIf { it.isNotEmpty() },
            strokes = emptyList(),
            viewBox = "0 0 200 200",
            userId = "me",
        )
    }
}
