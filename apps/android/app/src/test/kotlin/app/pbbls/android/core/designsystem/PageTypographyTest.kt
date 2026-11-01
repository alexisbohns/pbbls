package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The floors of the page type set (spec 2026-10-31 D3): nothing under 12 sp, a row or body
 * you read at 15 sp or more, and the figures lining, since Ysabeau's default figures are
 * old-style. Every rank is read off the object, so a new one is checked without being listed.
 */
class PageTypographyTest {
    private val ranks: Map<String, TextStyle> =
        PageTypography::class.java.declaredMethods
            .filter { it.returnType == TextStyle::class.java && it.parameterCount == 0 && it.name.startsWith("get") }
            .associate { method ->
                method.name.removePrefix("get").replaceFirstChar { it.lowercase() } to method.invoke(PageTypography) as TextStyle
            }

    @Test
    fun theSetHoldsEveryRankOfTheSpec() {
        assertEquals(26, ranks.size)
    }

    @Test
    fun everyRankIsInSpAndAtLeastTwelve() {
        ranks.forEach { (name, style) ->
            assertEquals("$name is not sp", TextUnitType.Sp, style.fontSize.type)
            assertTrue("$name is ${style.fontSize}", style.fontSize.value >= 12f)
        }
    }

    @Test
    fun inclusiveRanksAreAtLeastTwelveAndBodyRanksAtLeastFifteen() {
        val inclusive = ranks.filterValues { it.fontFamily == InclusiveSansFamily }
        assertTrue(inclusive.isNotEmpty())
        inclusive.forEach { (name, style) -> assertTrue("$name is ${style.fontSize}", style.fontSize.value >= 12f) }
        BODY_RANKS.forEach { name ->
            val style = ranks.getValue(name)
            assertEquals("$name is not Inclusive Sans", InclusiveSansFamily, style.fontFamily)
            assertTrue("$name is ${style.fontSize}", style.fontSize.value >= 15f)
        }
    }

    @Test
    fun figureRanksAreLiningAndDatesOldStyle() {
        FIGURE_RANKS.forEach { name ->
            val features = ranks.getValue(name).fontFeatureSettings.orEmpty()
            assertTrue("$name: $features", "lnum" in features && "onum" !in features)
        }
        OLD_STYLE_RANKS.forEach { name ->
            val features = ranks.getValue(name).fontFeatureSettings.orEmpty()
            assertTrue("$name: $features", "onum" in features && "lnum" !in features)
        }
    }

    @Test
    fun ysabeauRanksPinTheirExactWeight() {
        assertEquals(ysabeauAt(270), PageTypography.greeting.fontFamily)
        assertEquals(ysabeauAt(280), PageTypography.counterFigure.fontFamily)
    }

    @Test
    fun theProfileNameIsTheHandAtSixty() {
        assertEquals(60f, PebblesHandTypography.profileName.fontSize.value)
        assertEquals(54f, PebblesHandTypography.profileName.lineHeight.value)
    }

    private companion object {
        val BODY_RANKS =
            listOf("rowTitle", "rowTitleDone", "rowLine", "actionLabel", "counterLabel", "rippleLabel", "postLine", "calloutLine")
        val FIGURE_RANKS = listOf("greeting", "sectionFigure", "counterFigure", "wrapFigure")
        val OLD_STYLE_RANKS = listOf("dateline", "wrapYear", "since", "postKicker", "logDate")
    }
}
