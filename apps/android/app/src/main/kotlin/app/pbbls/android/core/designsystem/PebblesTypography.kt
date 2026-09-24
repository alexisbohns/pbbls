package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private fun inclusiveSans(
    size: TextUnit,
    weight: FontWeight,
    tracking: TextUnit,
) = TextStyle(fontFamily = InclusiveSansFamily, fontSize = size, fontWeight = weight, letterSpacing = tracking)

/**
 * The #853 bridge for type — the old iOS-derived token names, kept so Part 1
 * can change the whole app's faces without touching every call site.
 *
 * Faces are the export's: Inclusive Sans where Nunito was, Ysabeau for
 * [title] and [buttonLabel]. Sizes, weights and tracking stay the iOS ones
 * only until Parts 3–5 remap each call site onto a `MaterialTheme.typography`
 * role; Part 7 deletes this object. The handwritten tokens are aliases of
 * [PebblesHandTypography], which outlives the bridge.
 *
 * `meta`/`metaEmphasized`/`cardHeading`/`cardHeadingEmphasized` render
 * uppercase — see [PebblesText], which applies the case transform Compose's
 * `TextStyle` cannot express.
 */
@Deprecated("Bridge for #853: use MaterialTheme.typography roles or PebblesTheme.hand")
object PebblesTypography {
    val body = inclusiveSans(size = 17.sp, weight = FontWeight.Normal, tracking = 0.02f.em)
    val bodyEmphasized = inclusiveSans(size = 17.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val subhead = inclusiveSans(size = 15.sp, weight = FontWeight.Normal, tracking = 0.02f.em)
    val subheadEmphasized = inclusiveSans(size = 15.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val headline = inclusiveSans(size = 17.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val headlineEmphasized = inclusiveSans(size = 17.sp, weight = FontWeight.Bold, tracking = 0.02f.em)
    val callout = inclusiveSans(size = 16.sp, weight = FontWeight.Medium, tracking = 0.02f.em)
    val calloutEmphasized = inclusiveSans(size = 16.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val meta = inclusiveSans(size = 12.sp, weight = FontWeight.Medium, tracking = 0.10f.em)
    val metaEmphasized = inclusiveSans(size = 12.sp, weight = FontWeight.Bold, tracking = 0.10f.em)
    val cardHeading = inclusiveSans(size = 15.sp, weight = FontWeight.SemiBold, tracking = 0.10f.em)
    val cardHeadingEmphasized = inclusiveSans(size = 15.sp, weight = FontWeight.Bold, tracking = 0.10f.em)
    val counterLg = inclusiveSans(size = 17.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val captionEmphasized = inclusiveSans(size = 12.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)

    val title =
        TextStyle(
            fontFamily = YsabeauFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02f).em,
            fontFeatureSettings = YSABEAU_NUMBER_FEATURES,
        )
    val buttonLabel =
        TextStyle(
            fontFamily = YsabeauFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.02f.em,
            fontFeatureSettings = YSABEAU_NUMBER_FEATURES,
        )

    val bodyLeadHand = PebblesHandTypography.bodyLeadHand
    val largeTitleHand = PebblesHandTypography.largeTitleHand
    val nameInputHand = PebblesHandTypography.nameInputHand
    val valenceWord = PebblesHandTypography.valenceWord

    /** Tokens whose case is uppercase per the type spec — see [PebblesText]. */
    val uppercaseTokens: Set<TextStyle> = setOf(meta, metaEmphasized, cardHeading, cardHeadingEmphasized)

    /** See [PebblesHandTypography.inkOverhang]. */
    fun inkOverhang(style: TextStyle): Dp = PebblesHandTypography.inkOverhang(style)

    /** See [PebblesHandTypography.needsInkPadding]. */
    fun needsInkPadding(style: TextStyle): Boolean = PebblesHandTypography.needsInkPadding(style)
}
