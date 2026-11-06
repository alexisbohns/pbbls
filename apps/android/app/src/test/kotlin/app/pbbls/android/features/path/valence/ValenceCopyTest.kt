package app.pbbls.android.features.path.valence

import app.pbbls.android.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The valence copy against iOS's own strings, in both languages (a unit test cannot load resources, so
 * the `strings.xml` files are parsed, as `LocalizationParityTest` does). The lockup stays a word over a
 * span, as iOS shipped it: each line is its own string, so French agreement never depends on
 * concatenation. `Valence.spanWord` exists on iOS only for a summary pill that was dropped.
 */
class ValenceCopyTest {
    private fun strings(path: String): Map<String, String> {
        val nodes =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(File(path))
                .getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate {
            it.getAttribute("name") to
                it.textContent.replace("\\'", "'")
        }
    }

    private val en by lazy { strings("src/main/res/values/strings.xml") }
    private val fr by lazy { strings("src/main/res/values-fr/strings.xml") }

    @Test
    fun `the neutral word is Neutral and Neutre for every reader`() {
        assertEquals("Neutral", en["valence_headline_neutral"])
        assertEquals("Neutre", fr["valence_headline_neutral"])
        assertEquals(en["valence_neutral"], en["valence_headline_neutral"])
        assertEquals(fr["valence_neutral"], fr["valence_headline_neutral"])
    }

    @Test
    fun `the lockup words and spans match iOS, in English`() {
        assertEquals("Lowlight", en["valence_lowlight"])
        assertEquals("Highlight", en["valence_highlight"])
        assertEquals(listOf("OF MY DAY", "OF MY WEEK", "OF MY MONTH"), listOf("day", "week", "month").map { en["valence_span_$it"] })
    }

    @Test
    fun `the lockup words and spans match iOS, in French`() {
        assertEquals("Creux", fr["valence_lowlight"])
        assertEquals("Temps fort", fr["valence_highlight"])
        assertEquals(listOf("DE MA JOURNÉE", "DE MA SEMAINE", "DE MON MOIS"), listOf("day", "week", "month").map { fr["valence_span_$it"] })
    }

    @Test
    fun `the axis labels match iOS in both languages`() {
        assertEquals(listOf("Month", "Week", "Day"), listOf("month", "week", "day").map { en["valence_axis_$it"] })
        assertEquals(listOf("Mois", "Semaine", "Jour"), listOf("month", "week", "day").map { fr["valence_axis_$it"] })
    }

    @Test
    fun `the lockup resolves the neutral word through the changed key`() {
        assertEquals(R.string.valence_headline_neutral, valenceWordRes(app.pbbls.android.core.model.ValencePolarity.NEUTRAL))
    }

    @Test
    fun `the step copy matches iOS`() {
        assertEquals("Swipe or tap to choose", en["valence_ghost_prompt"])
        assertEquals("Glisse ou touche pour choisir", fr["valence_ghost_prompt"])
        assertEquals("Moment saved · 3 questions to make it a pebble", en["complete_after_save"])
        assertEquals("Moment enregistré · 3 questions pour en faire un galet", fr["complete_after_save"])
        assertEquals("Couldn't load feelings", en["emotion_load_error"])
        assertEquals("Impossible de charger les émotions", fr["emotion_load_error"])
        assertEquals("Got it", en["valence_help_got_it"])
        assertEquals("Compris", fr["valence_help_got_it"])
    }
}
