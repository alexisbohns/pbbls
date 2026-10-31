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

    /** A wrap tile's caption (#1207): the board's `.tile .sm`, 12.5 px, and its italic. */
    val tileCaption = ysabeau(12.5.sp, 400, lineHeight = 15.6.sp)
    val tileCaptionItalic = ysabeau(12.5.sp, 400, italic = true, lineHeight = 15.6.sp)

    /** A sheet list's opening sentence (#1207): the board's `.lead`, 17 px light italic. */
    val lead = ysabeau(17.sp, 380, italic = true, lineHeight = 23.sp)

    /** The month's name inside the garland (#1207): the round-2 board's `.mo-name`. */
    val monthName = ysabeau(36.sp, 380, italic = true, lineHeight = 40.sp)

    /** A section's italic hint beside its label (#1207), "from the 1st, clockwise". */
    val sectionHint = ysabeau(13.sp, 380, italic = true)

    /** The masthead's way back a week: the web link inherits the body's 400; only the number is bold. */
    val masthead = ysabeau(11.5.sp, 400, 0.18f.em, features = SMALL_CAPS)

    /** The masthead's week number: the web's `<b>` at 650, a cut of its own since the axis is pinned per family. */
    val mastheadNumber = ysabeau(11.5.sp, 650, 0.18f.em, features = SMALL_CAPS)
    val personName = ysabeau(15.sp, 600, 0.1f.em, features = SMALL_CAPS)
    val dayLetter = ysabeau(9.5.sp, 600, features = SMALL_CAPS, lineHeight = 12.sp)
    val contentsDay = ysabeau(10.5.sp, 600, 0.18f.em, features = SMALL_CAPS)
    val contentsTime = ysabeau(12.5.sp, 400, 0.04f.em, features = TABULAR)

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

    // The achievements page (#1307): the approved board's rule set (`final/_build/style.css`, `sheets.css`).
    val daysKept = ysabeau(78.sp, 230, (-0.025f).em, features = LINING, lineHeight = 66.sp)
    val daysUnit = ysabeau(15.sp, 560, 0.16f.em, features = SMALL_CAPS)
    val sinceLine = ysabeau(11.5.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val cardMargin = ysabeau(11.sp, 600, 0.26f.em, features = SMALL_CAPS)
    val cardBand = ysabeau(11.5.sp, 600, 0.22f.em, features = SMALL_CAPS)
    val cardHint = ysabeau(14.sp, 380, italic = true)
    val cardLine = ysabeau(16.sp, 380, italic = true, lineHeight = 21.6.sp)
    val stackLabel = ysabeau(11.sp, 600, 0.14f.em, features = SMALL_CAPS)
    val stackCount = ysabeau(20.sp, 300, features = LINING)
    val registerSub = ysabeau(14.5.sp, 420, lineHeight = 18.sp)
    val registerDate = ysabeau(12.5.sp, 400, 0.03f.em, features = TABULAR)
    val milestone = ysabeau(21.sp, 300, features = LINING)
    val milestoneSmall = ysabeau(17.sp, 300, features = LINING)
    val cellNumber = ysabeau(14.sp, 400, features = TABULAR)
    val cellNumberDone = ysabeau(14.sp, 560, features = TABULAR)
    val tableHead = ysabeau(10.5.sp, 600, 0.14f.em, features = SMALL_CAPS)
    val discNumeral = ysabeau(9.sp, 400, features = LINING)
    val discNumeralRim = ysabeau(9.sp, 600, features = LINING)
    val rosaceCount = ysabeau(28.sp, 300, features = LINING)
    val rosaceOf = ysabeau(11.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val rosaceFamily = ysabeau(11.sp, 600, 0.14f.em, features = SMALL_CAPS)

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

    /** The photo-less recto's slip: the entry's cut at 6.4 card units (the mock's `.plate .pname`). */
    fun cardSlipName(
        u: Float,
        weight: Int,
        italic: Boolean,
    ) = ysabeau((6.4f * u).sp, weight, (-0.014f).em, italic = italic, lineHeight = (6.4f * u * 1.08f).sp)

    // The held card (pebble card spec): fixed sizes, not card units.
    val cardFooterGrade = ysabeau(10.5.sp, 650, 0.16f.em, features = SMALL_CAPS)
    val cardFooterNote = ysabeau(12.5.sp, 380, italic = true)
    val cardTurnTarget = ysabeau(13.sp, 380, italic = true)
    val cardLeafDateline = ysabeau(10.5.sp, 600, 0.18f.em, features = SMALL_CAPS + ", " + LINING)
    val cardLeafBody = ysabeau(17.sp, 380, lineHeight = 27.sp)
    val cardLockupSpan = ysabeau(10.sp, 650, 0.22f.em, features = SMALL_CAPS)
    val cardEmotion = ysabeau(20.sp, 400)
    val cardBandName = ysabeau(14.sp, 400, italic = true)
    val bareTitle = ysabeau(34.sp, 300, (-0.02f).em, lineHeight = 38.sp)
    val placeBody = ysabeau(16.sp, 380, lineHeight = 24.sp)

    /** The postmark ring: capitals (not small caps, too small at 4 px), sized by [RingFit]. */
    fun postmarkRing(sizeSp: Float) = ysabeau(sizeSp.sp, 650, features = LINING)
}
