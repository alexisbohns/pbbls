package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pbbls.android.R

private val ReenieBeanieFamily =
    FontFamily(Font(R.font.reenie_beanie, weight = FontWeight.Normal))

// Caveat is a variable font (wght axis), the same file iOS bundles — declared
// at Regular via FontVariation rather than shipping a second static cut.
@OptIn(ExperimentalTextApi::class)
private val CaveatFamily =
    FontFamily(
        Font(
            R.font.caveat,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        // The valence lockup's word. Declared through the wght axis for the
        // same reason iOS sets `kCTFontVariationAttribute`: there is no
        // "Caveat-Bold" face in this file to resolve by name.
        Font(
            R.font.caveat,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )

private fun caveat(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = CaveatFamily,
    fontSize = size,
    fontWeight = weight,
    // Caveat is already tightly connected; no extra tracking (iOS parity).
    letterSpacing = 0f.em,
)

private fun reenieBeanie(
    size: TextUnit,
    tracking: TextUnit,
) = TextStyle(
    fontFamily = ReenieBeanieFamily,
    fontSize = size,
    fontWeight = FontWeight.Normal,
    letterSpacing = tracking,
)

/**
 * The only type Pebbles still owns (#853): handwritten faces with no M3 role.
 * Reached through `PebblesTheme.hand`.
 */
object PebblesHandTypography {
    // Reenie Beanie sits loose by default; tightened toward connected
    // handwriting (issue #515 speced -2%, dialed in on iOS review to these
    // values). Any user/soul/creator name renders in the hand font.
    val bodyLeadHand = reenieBeanie(size = 22.sp, tracking = (-0.045f).em)
    val largeTitleHand = reenieBeanie(size = 41.sp, tracking = (-0.049f).em)

    /**
     * Handwritten (Caveat) input face, 36sp. The pebble name field in the
     * record flow — the one place the user writes on a bare page.
     */
    val nameInputHand = caveat(size = 36.sp)

    /**
     * Handwritten (Caveat Bold) headline word naming the picked valence, at its
     * largest.
     *
     * iOS carries three tokens — 34 / 44 / 56pt, one per `ValenceSizeGroup`, so
     * the word grows with the event the way the stones do. Compose cannot
     * animate a font size, so the word is typeset once at this size and scaled
     * down by a layer transform instead (`valenceWordScale`), which also keeps
     * the row's height constant as the size changes.
     */
    val valenceWord = caveat(size = 56.sp, weight = FontWeight.Bold)

    /**
     * Horizontal breathing room a token needs so its glyphs are not clipped at
     * the text's layout width.
     *
     * Caveat Bold's terminal `t` flicks to the right past the glyph's advance,
     * and text is clipped to the advance — so "Moment" and "Lowlight" lose the
     * end of their last letter to a hard vertical cut. Measured on iOS with
     * CoreText across all three sizes: the ink runs 3–8pt past the advance on
     * the right, and never past the ascent, so the cut is only ever horizontal.
     *
     * **Padding alone does not fix it.** A layout box is not what the glyphs
     * are clipped to, so callers pad the *string* as well — see
     * [needsInkPadding]. This value is the belt to that's braces, and it is
     * applied by the caller so the tokens stay pure type.
     */
    fun inkOverhang(style: TextStyle): Dp = if (style == valenceWord) 10.dp else 0.dp

    /**
     * True for the faces whose ink escapes its advance. Callers put a space on
     * each side of the string: a space carries real advance width, so the room
     * is part of the line the glyphs are clipped to, and padding both sides
     * keeps the word centred.
     */
    fun needsInkPadding(style: TextStyle): Boolean = inkOverhang(style) > 0.dp
}
