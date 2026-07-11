package app.pbbls.android.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.pbbls.android.R

// Number Spacing (type 6) → Proportional Numbers, Number Case (type 21) →
// Upper Case Numbers/lining — same OpenType feature pair the iOS
// `ysabeauSemibold` font descriptor sets so digits align to cap height.
private const val YSABEAU_NUMBER_FEATURES = "pnum, lnum"

@OptIn(ExperimentalTextApi::class)
private val NunitoFamily =
    FontFamily(
        Font(
            R.font.nunito,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.nunito,
            weight = FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.nunito,
            weight = FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            R.font.nunito,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )

private val YsabeauSemiBoldFamily =
    FontFamily(Font(R.font.ysabeau_semibold, weight = FontWeight.SemiBold))

private val ReenieBeanieFamily =
    FontFamily(Font(R.font.reenie_beanie, weight = FontWeight.Normal))

private fun nunito(
    size: TextUnit,
    weight: FontWeight,
    tracking: TextUnit,
) = TextStyle(fontFamily = NunitoFamily, fontSize = size, fontWeight = weight, letterSpacing = tracking)

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
 * Typography tokens used across Pebbles Android — the Compose analog of
 * iOS `PebblesFont`/`Font+Pebbles.swift`. Each token bundles font, size,
 * weight, and tracking so call sites cannot forget one half of the pair.
 * SF Pro/Compact Rounded has no Android equivalent bundled with the app, so
 * every "rounded" iOS token maps to Nunito (maintainer-approved 2026-07-11).
 * `meta`/`metaEmphasized`/`cardHeading`/`cardHeadingEmphasized` render
 * uppercase — see [PebblesText], which applies the case transform Compose's
 * `TextStyle` cannot express.
 */
object PebblesTypography {
    val body = nunito(size = 17.sp, weight = FontWeight.Normal, tracking = 0.02f.em)
    val bodyEmphasized = nunito(size = 17.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val subhead = nunito(size = 15.sp, weight = FontWeight.Normal, tracking = 0.02f.em)
    val subheadEmphasized = nunito(size = 15.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val headline = nunito(size = 17.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val headlineEmphasized = nunito(size = 17.sp, weight = FontWeight.Bold, tracking = 0.02f.em)
    val callout = nunito(size = 16.sp, weight = FontWeight.Medium, tracking = 0.02f.em)
    val calloutEmphasized = nunito(size = 16.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val meta = nunito(size = 12.sp, weight = FontWeight.Medium, tracking = 0.10f.em)
    val metaEmphasized = nunito(size = 12.sp, weight = FontWeight.Bold, tracking = 0.10f.em)
    val cardHeading = nunito(size = 15.sp, weight = FontWeight.SemiBold, tracking = 0.10f.em)
    val cardHeadingEmphasized = nunito(size = 15.sp, weight = FontWeight.Bold, tracking = 0.10f.em)
    val counterLg = nunito(size = 17.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)
    val captionEmphasized = nunito(size = 12.sp, weight = FontWeight.SemiBold, tracking = 0.02f.em)

    val title =
        TextStyle(
            fontFamily = YsabeauSemiBoldFamily,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.02f).em,
            fontFeatureSettings = YSABEAU_NUMBER_FEATURES,
        )
    val buttonLabel =
        TextStyle(
            fontFamily = YsabeauSemiBoldFamily,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.02f.em,
            fontFeatureSettings = YSABEAU_NUMBER_FEATURES,
        )

    // Reenie Beanie sits loose by default; tightened toward connected
    // handwriting (issue #515 speced -2%, dialed in on iOS review to these
    // values). Any user/soul/creator name renders in the hand font.
    val bodyLeadHand = reenieBeanie(size = 22.sp, tracking = (-0.045f).em)
    val largeTitleHand = reenieBeanie(size = 41.sp, tracking = (-0.049f).em)

    /** Tokens whose case is uppercase per the type spec — see [PebblesText]. */
    val uppercaseTokens: Set<TextStyle> = setOf(meta, metaEmphasized, cardHeading, cardHeadingEmphasized)
}
