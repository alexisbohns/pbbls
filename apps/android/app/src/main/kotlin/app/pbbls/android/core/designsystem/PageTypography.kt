package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type ranks of Home and You (spec 2026-10-31-android-home-and-you-design.md, D3), read
 * through `PebblesTheme.page`. The M3 scale is untouched: these pages need ranks it has no slot
 * for, and app code may not name a font size (`ThemeLiteralsTest`), so the numbers live here,
 * like [SpineTypography]'s.
 *
 * Ysabeau rides [ysabeauAt] so each rank renders at its exact wght; its default figures are
 * old-style, so a figure rank asks for lining ones. Inclusive Sans rides [InclusiveSansFamily]
 * at its 400/500/600 cuts. Every size is sp so every rank follows the font scale; line heights
 * are the mock's ratios worked out in sp.
 */
object PageTypography {
    private const val LINING = "lnum, pnum"
    private const val OLD_STYLE = "onum, pnum"

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

    private fun inclusive(
        size: TextUnit,
        weight: Int,
        lineHeight: TextUnit = TextUnit.Unspecified,
    ) = TextStyle(
        fontFamily = InclusiveSansFamily,
        fontSize = size,
        fontWeight = FontWeight(weight),
        lineHeight = lineHeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    /** Home's date, in `primary`. */
    val dateline = ysabeau(17.sp, 540, 0.005f.em, features = OLD_STYLE)

    /** Home's title; one line up to 1.3 × font scale (D18). */
    val greeting = ysabeau(40.sp, 270, (-0.024f).em, features = LINING, lineHeight = 41.6.sp)

    /** The greeting's line, in `onSurfaceVariant`. */
    val lead = ysabeau(21.sp, 360, italic = true, lineHeight = 26.25.sp)

    /** Every section head: sentence case, no caps. */
    val sectionHead = ysabeau(30.sp, 300, (-0.018f).em, lineHeight = 31.5.sp)

    /** A section's figure ("2 of 5", "3", "12 earned"), in `primary`. */
    val sectionFigure = ysabeau(30.sp, 300, (-0.01f).em, features = LINING, lineHeight = 31.5.sp)

    /** The "of" / "earned" inside a figure, in `onSurfaceVariant`. */
    val figureWord = ysabeau(19.sp, 380, italic = true)

    /** A row's title (tasks, guides, glyph rows). */
    val rowTitle = inclusive(17.sp, 600, lineHeight = 21.25.sp)

    /** A done task's title, in `onSurfaceVariant`. */
    val rowTitleDone = inclusive(17.sp, 500, lineHeight = 21.25.sp)

    /** A row's second line. */
    val rowLine = inclusive(15.sp, 400, lineHeight = 21.sp)

    /** Quick actions, "All updates". */
    val actionLabel = inclusive(15.sp, 600, lineHeight = 17.25.sp)

    /** A counter's figure, in `onSurface`. */
    val counterFigure = ysabeau(36.sp, 280, (-0.025f).em, features = LINING, lineHeight = 36.sp)

    /** A counter's label. */
    val counterLabel = inclusive(15.sp, 400)

    /** The count inside a wrap ring. */
    val wrapFigure = ysabeau(34.sp, 280, (-0.02f).em, features = LINING)

    /** A wrap card's month. */
    val wrapMonth = ysabeau(23.sp, 400, (-0.005f).em, lineHeight = 25.3.sp)

    /** A wrap card's year. */
    val wrapYear = ysabeau(16.sp, 400, italic = true, features = OLD_STYLE)

    /** An achievement card's label. */
    val badgeLabel = inclusive(12.5.sp, 500, lineHeight = 15.sp)

    /** "Member since March 2026". */
    val since = ysabeau(19.sp, 380, italic = true, features = OLD_STYLE)

    /** "Ripples Level 3". */
    val rippleLabel = inclusive(15.sp, 600)

    /** "Lab notes · 31 October". */
    val postKicker = ysabeau(15.sp, 400, italic = true, features = OLD_STYLE)

    /** The announcement's title. */
    val postTitle = ysabeau(26.sp, 340, (-0.015f).em, lineHeight = 28.6.sp)

    /** The announcement's line. */
    val postLine = inclusive(15.sp, 400, lineHeight = 21.sp)

    /** A changelog entry's date, "31 Oct". */
    val logDate = ysabeau(15.sp, 500, features = OLD_STYLE)

    /** A changelog entry. */
    val logTitle = inclusive(16.sp, 600, lineHeight = 20.sp)

    /** A changelog entry's area. */
    val logLine = inclusive(14.sp, 400)

    /** The community callout's title. */
    val calloutTitle = ysabeau(22.sp, 420, (-0.01f).em, lineHeight = 23.1.sp)

    /** The community callout's line. */
    val calloutLine = inclusive(15.sp, 400)
}
