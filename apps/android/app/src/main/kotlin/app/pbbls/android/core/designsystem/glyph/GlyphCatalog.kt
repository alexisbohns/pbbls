package app.pbbls.android.core.designsystem.glyph

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.vector.ImageVector
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphState
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * `res/raw/pbbls_glyphs.json`, indexed once and decoded a glyph at a time (spec
 * §2; iOS `GlyphCatalog`): [load] scans the file into a [GlyphIndex], and a
 * glyph's data is decoded and cached the first time it is asked for.
 * `MainActivity` calls [load] off the main thread at launch; `PebblesTheme` and
 * `PbblsGlyphImage` [install] it from their context if nothing did (previews,
 * Robolectric); plain JUnit installs the repository's file. A read before any
 * install gets empty data and is not cached, so a later install still loads.
 */
object GlyphCatalog {
    private const val TAG = "PbblsGlyphs"
    private val lock = Any()

    @Volatile private var source: (() -> InputStream)? = null

    @Volatile private var index: GlyphIndex? = null
    private val glyphs = ConcurrentHashMap<String, GlyphData>()
    private val vectors = ConcurrentHashMap<String, ImageVector>()

    /** Points the catalog at the app's raw resource, once. */
    fun install(context: Context) {
        if (source != null) return
        val resources = context.applicationContext.resources
        installSourceIfAbsent { resources.openRawResource(R.raw.pbbls_glyphs) }
    }

    /** Installs the app's resource and indexes it, blocking until it is done (call it off the main thread). */
    fun load(context: Context) {
        install(context)
        index()
    }

    /** Replaces the source and drops what was loaded from the previous one (tests). */
    fun installSource(open: () -> InputStream) =
        synchronized(lock) {
            source = open
            index = null
            glyphs.clear()
            vectors.clear()
        }

    private fun installSourceIfAbsent(open: () -> InputStream) =
        synchronized(lock) {
            if (source == null) source = open
        }

    val states: Map<String, List<String>> get() = index().states

    fun glyph(glyph: PbblsGlyph): GlyphData {
        val name = glyph.manifestName
        glyphs[name]?.let { return it }
        val indexed = index()
        val decoded =
            runCatching { indexed.decode(name) }
                .onFailure { Log.e(TAG, "glyph $name did not decode", it) }
                .getOrNull()
        if (decoded == null) {
            if (indexed !== GlyphIndex.EMPTY) Log.e(TAG, "no data for glyph $name")
            return GlyphData.EMPTY
        }
        return glyphs.putIfAbsent(name, decoded) ?: decoded
    }

    /** A form as a vector, cached per glyph and form (a vector scales: no size in the key). */
    fun vector(
        glyph: PbblsGlyph,
        form: GlyphForm,
    ): ImageVector {
        val key = "${glyph.manifestName}|${form.key}"
        vectors[key]?.let { return it }
        val built = GlyphRenderer.vector(key, GlyphRenderer.render(glyph(glyph), form))
        // Only cache once the data is there: a vector built before the install is empty.
        return if (index != null) vectors.putIfAbsent(key, built) ?: built else built
    }

    private fun index(): GlyphIndex {
        index?.let { return it }
        synchronized(lock) {
            index?.let { return it }
            val open = source ?: return GlyphIndex.EMPTY.also { Log.w(TAG, "read before install") }
            val start = System.nanoTime()
            val read = readIndex(open)
            Log.i(TAG, "pbbls_glyphs.json indexed in ${(System.nanoTime() - start) / 1_000_000} ms (${read.size} glyphs)")
            index = read
            return read
        }
    }

    /** Reads and indexes the file; a file that cannot be read or scanned is logged and indexes as empty. */
    fun readIndex(open: () -> InputStream): GlyphIndex =
        runCatching { GlyphIndex.parse(open().bufferedReader(Charsets.UTF_8).use { it.readText() }) }
            .getOrElse { error ->
                Log.e(TAG, "pbbls_glyphs.json did not index", error)
                GlyphIndex.EMPTY
            }
}

/** The forms this glyph has, in the generator's order (spec D3). */
val PbblsGlyph.forms: List<GlyphForm>
    get() = GlyphCatalog.glyph(this).formKeys.mapNotNull(GlyphForm::fromKey)

/**
 * The form a state shows on this glyph (spec D6): the state's first form the
 * glyph has. [k] is the caller's progress for [PbblsGlyphState.PROGRESS].
 */
fun PbblsGlyph.form(
    state: PbblsGlyphState,
    k: Int = 0,
): GlyphForm = GlyphRenderer.resolve(GlyphCatalog.states, GlyphCatalog.glyph(this).formKeys, state.key, k)
