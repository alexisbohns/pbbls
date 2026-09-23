package app.pbbls.android.core.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.pbbls.android.R

/**
 * The export's two faces (#853), bundled rather than fetched through the GMS
 * downloadable-fonts provider the Theme Builder template uses: bundled works
 * offline and on devices without Play services, and layoutlib screenshot tests
 * cannot fetch. Both files are variable (wght axis), so each weight is one
 * `FontVariation` over the same file — the pattern Nunito used before.
 */
private val VariableWeights =
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(
    upright: Int,
    italic: Int,
): FontFamily =
    FontFamily(
        VariableWeights.flatMap { weight ->
            listOf(
                Font(
                    upright,
                    weight = weight,
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                ),
                Font(
                    italic,
                    weight = weight,
                    style = FontStyle.Italic,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                ),
            )
        },
    )

/** Body and label face. */
internal val InclusiveSansFamily = variableFamily(R.font.inclusive_sans, R.font.inclusive_sans_italic)

/** Display, headline and title face. */
internal val YsabeauFamily = variableFamily(R.font.ysabeau, R.font.ysabeau_italic)

/**
 * Number Spacing → proportional, Number Case → lining: digits align to cap
 * height in Ysabeau, whose default figures are old-style.
 */
internal const val YSABEAU_NUMBER_FEATURES = "pnum, lnum"
