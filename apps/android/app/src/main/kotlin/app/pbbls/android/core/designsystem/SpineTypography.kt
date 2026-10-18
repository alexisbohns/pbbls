package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The Spine's typographic ranks, from the web rule set of the Path design (#1052). The M3
 * roles have no slot for them, and app code may not name a font size (`ThemeLiteralsTest`),
 * so the numbers live here, like the handwritten faces do.
 *
 * Small caps and lining figures are OpenType features of the bundled Ysabeau: no extra font files.
 */
object SpineTypography {
    private const val SMALL_CAPS = "smcp, c2sc"
    private const val LINING = "lnum, pnum"
    private const val TABULAR = "lnum, tnum"

    private fun ysabeau(
        size: TextUnit,
        weight: Int,
        tracking: TextUnit = 0f.em,
        italic: Boolean = false,
        features: String? = null,
        lineHeight: TextUnit = TextUnit.Unspecified,
    ) = TextStyle(
        fontFamily = ysabeauAt(weight),
        fontSize = size,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontFeatureSettings = features,
        lineHeight = lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    val kicker = ysabeau(12.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val title = ysabeau(40.sp, 300, (-0.026f).em, features = LINING, lineHeight = 40.8.sp)
    val summary = ysabeau(18.sp, 380, italic = true)
    val sectionLabel = ysabeau(11.5.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val masthead = ysabeau(11.5.sp, 600, 0.18f.em, features = SMALL_CAPS)

    /** The masthead's week number: the web's `<b>` at 650, a cut of its own since the axis is pinned per family. */
    val mastheadNumber = ysabeau(11.5.sp, 650, 0.18f.em, features = SMALL_CAPS)
    val personName = ysabeau(15.sp, 600, 0.1f.em, features = SMALL_CAPS)
    val dayLetter = ysabeau(9.5.sp, 600, features = SMALL_CAPS, lineHeight = 12.sp)
    val contentsDay = ysabeau(10.5.sp, 600, 0.18f.em, features = SMALL_CAPS)
    val contentsTime = ysabeau(12.5.sp, 500, 0.04f.em, features = TABULAR)

    /** The persons' fold, "and *N* others": the web's `text-base font-[380] italic`. */
    val personsFold = ysabeau(16.sp, 380, italic = true)
    val entryTime = ysabeau(13.sp, 560, 0.06f.em, features = TABULAR)
    val entryEmotion = ysabeau(12.sp, 600, 0.12f.em, features = SMALL_CAPS)
    val people = ysabeau(15.sp, 400, lineHeight = 22.5.sp)
    val soulName = ysabeau(13.sp, 620, 0.1f.em, features = SMALL_CAPS)
    val dayNumeral = ysabeau(25.sp, 330, (-0.02f).em, features = LINING)
    val dayWeekday = ysabeau(10.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val mouthLabel = ysabeau(12.sp, 600, 0.18f.em, features = SMALL_CAPS)
    val mouthLine = ysabeau(16.sp, 380, italic = true)
    val rollNumeral = ysabeau(14.sp, 600, 0.1f.em, features = SMALL_CAPS + ", " + LINING)
    val still = ysabeau(15.sp, 380, italic = true)

    fun contentsName(
        weight: Int,
        italic: Boolean,
    ) = ysabeau(16.5.sp, weight, italic = italic, lineHeight = 19.8.sp)

    fun entryName(
        sizeSp: Float,
        weight: Int,
        italic: Boolean,
        trackingEm: Float,
    ) = ysabeau(sizeSp.sp, weight, trackingEm.em, italic = italic, lineHeight = (sizeSp * 1.12f).sp)

    // Card ranks take the card unit `u` in sp (the caller converts its dp unit at the density).
    fun cardIndexNumeral(u: Float) = ysabeau((5.3f * u).sp, 600, (-0.045f).em, features = LINING, lineHeight = (5.3f * u * 0.86f).sp)

    fun cardIndexDay(u: Float) = ysabeau((1.8f * u).sp, 650, 0.14f.em, features = SMALL_CAPS)

    fun cardRail(u: Float) = ysabeau((1.95f * u).sp, 560, 0.07f.em, features = TABULAR)

    fun cardRailEmotion(u: Float) = ysabeau((1.95f * u).sp, 650, 0.15f.em, features = SMALL_CAPS)

    fun cardTitle(
        u: Float,
        weight: Int,
        italic: Boolean,
    ) = ysabeau((4.4f * u).sp, weight, (-0.012f).em, italic = italic, lineHeight = (4.4f * u * 1.1f).sp)

    fun cardSoul(u: Float) = ysabeau((2.5f * u).sp, 650, 0.12f.em, features = SMALL_CAPS)

    fun cardMore(u: Float) = ysabeau((2.5f * u).sp, 560, features = TABULAR)

    /** The strip's "& twelve others": the base `2.6u` in the quiet italic. */
    fun cardOthers(u: Float) = ysabeau((2.6f * u).sp, 380, italic = true)
}
