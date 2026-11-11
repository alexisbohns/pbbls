package app.pbbls.android.core.designsystem.glyph

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.vector.ImageVector
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * `res/raw/pbbls_glyphs.json`, loaded once (spec §2; iOS `GlyphCatalog`):
 * `PebblesApp` installs and prewarms it off the main thread at launch;
 * `PbblsGlyphImage` installs it from its context if nothing did (previews,
 * Robolectric); plain JUnit installs the repository's file. A read before any
 * install gets empty data and is not cached, so a later install still loads.
 */
object GlyphCatalog {
    private const val TAG = "PbblsGlyphs"
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    @Volatile private var source: (() -> InputStream)? = null

    @Volatile private var loaded: GlyphFile? = null
    private val vectors = ConcurrentHashMap<String, ImageVector>()

    /** Points the catalog at the app's raw resource, once. */
    fun install(context: Context) {
        if (source != null) return
        val resources = context.applicationContext.resources
        installSourceIfAbsent { resources.openRawResource(R.raw.pbbls_glyphs) }
    }

    /** Loads the data on a background thread so the first glyph on screen does not wait for it. */
    fun prewarm(context: Context) {
        install(context)
        thread(name = "pbbls-glyphs", isDaemon = true) { file() }
    }

    /** Replaces the source and drops what was loaded from the previous one (tests). */
    fun installSource(open: () -> InputStream) =
        synchronized(lock) {
            source = open
            loaded = null
            vectors.clear()
        }

    private fun installSourceIfAbsent(open: () -> InputStream) =
        synchronized(lock) {
            if (source == null) source = open
        }

    val states: Map<String, List<String>> get() = file().states

    fun glyph(glyph: PbblsGlyph): GlyphData =
        file().glyphs[glyph.manifestName] ?: GlyphData.EMPTY.also {
            if (loaded != null) Log.e(TAG, "no data for glyph ${glyph.manifestName}")
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
        return if (loaded != null) vectors.putIfAbsent(key, built) ?: built else built
    }

    private fun file(): GlyphFile {
        loaded?.let { return it }
        synchronized(lock) {
            loaded?.let { return it }
            val open = source ?: return GlyphFile.EMPTY.also { Log.w(TAG, "read before install") }
            val start = System.nanoTime()
            val read = read(open)
            Log.i(TAG, "pbbls_glyphs.json loaded in ${(System.nanoTime() - start) / 1_000_000} ms")
            loaded = read
            return read
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun read(open: () -> InputStream): GlyphFile =
        runCatching { open().use { json.decodeFromStream<GlyphFile>(it) } }
            .getOrElse { error ->
                Log.e(TAG, "pbbls_glyphs.json did not decode", error)
                GlyphFile.EMPTY
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
