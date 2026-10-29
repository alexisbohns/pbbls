package app.pbbls.android.core.designsystem

import android.app.Application
import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.roundToInt

/**
 * The Android half of the icon contract (spec §7): the generated enum against the real manifest,
 * read in place from the repository (the `StoneComposerTest` precedent), and every entry against
 * the compiled drawable resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PbblsIconsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    /** A manifest row the generator emits: its name and its twins, in manifest order. */
    private data class Row(
        val name: String,
        val variants: List<String>,
    )

    private fun rows(): List<Row> {
        val manifest = Json.parseToJsonElement(File("../../../packages/icons/icons.json").readText())
        return manifest.jsonObject
            .getValue("icons")
            .jsonArray
            .map { it.jsonObject }
            .filter { it.getValue("status").jsonPrimitive.content != "exploring" }
            .map { row ->
                Row(
                    name = row.getValue("name").jsonPrimitive.content,
                    variants = row.getValue("variants").jsonArray.map { it.jsonPrimitive.content },
                )
            }
    }

    private fun emitted(rows: List<Row>) = rows.flatMap { row -> listOf(row.name) + row.variants.map { "${row.name}.$it" } }

    @Test
    fun `every entry inflates as a 24dp vector drawable`() {
        val density = context.resources.displayMetrics.density
        val side = (24 * density).roundToInt()
        PbblsIcon.entries.forEach { icon ->
            val drawable = context.getDrawable(icon.drawableRes)
            assertTrue(icon.manifestName, drawable is VectorDrawable)
            assertEquals(icon.manifestName, side, drawable!!.intrinsicWidth)
            assertEquals(icon.manifestName, side, drawable.intrinsicHeight)
        }
    }

    @Test
    fun `entries match the manifest`() {
        val emitted = emitted(rows())
        assertEquals(emitted, PbblsIcon.entries.map { it.manifestName })
        assertTrue(emitted.isNotEmpty())
    }

    @Test
    fun `drawable names fold the manifest name`() {
        val resources = context.resources
        PbblsIcon.entries.forEach { icon ->
            val expected = "ic_pbbls_" + icon.manifestName.split('.', '-').joinToString("_")
            assertEquals(icon.manifestName, expected, resources.getResourceEntryName(icon.drawableRes))
        }
    }

    @Test
    fun `twins match variants`() {
        rows().forEach { row ->
            val base = PbblsIcon.fromManifestName(row.name)
            assertNotNull(row.name, base)
            assertEquals(row.name, "active" in row.variants, base!!.activeTwin != null)
            assertEquals(row.name, if ("active" in row.variants) "${row.name}.active" else null, base.activeTwin?.manifestName)
            (1..3).forEach { n ->
                assertEquals("${row.name} lit-$n", "lit-$n" in row.variants, base.lit(n) != null)
            }
            assertNull(row.name, base.lit(0))
            assertNull(row.name, base.lit(4))
        }
    }

    @Test
    fun `a twin answers for its base`() {
        val twin = PbblsIcon.entries.first { it.manifestName.endsWith(".active") }
        assertEquals(twin.manifestName.removeSuffix(".active"), twin.base)
        assertSame(twin, twin.activeTwin)
    }
}
