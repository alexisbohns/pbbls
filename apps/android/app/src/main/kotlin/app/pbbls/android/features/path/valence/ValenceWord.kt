package app.pbbls.android.features.path.valence

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.R
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import java.util.Locale

/**
 * The word's size relative to the token it is authored at.
 *
 * iOS swaps between three Caveat tokens (34 / 44 / 56pt) and lets SwiftUI
 * spring the layout. Compose cannot animate a font size — a text node
 * re-measures to the new size in one frame — so the word is always typeset at
 * the largest token and scaled down by a layer transform instead. Two things
 * fall out of that, both wanted: the size change can spring, and the row's
 * height is the large word's height whatever is showing, so the span and the
 * pyramid below never move. The ratios are the iOS point sizes.
 */
internal fun valenceWordScale(size: ValenceSizeGroup): Float =
    when (size) {
        ValenceSizeGroup.SMALL -> 34f / 56f
        ValenceSizeGroup.MEDIUM -> 44f / 56f
        ValenceSizeGroup.LARGE -> 1f
    }

/**
 * The top line of the lockup under the fan: the polarity word in the hand font,
 * coloured by polarity — ports the word half of iOS `ValenceHeadlineView`.
 *
 * "Highlight" wears its stone's gradient, "Lowlight" reads as ink (darker than
 * its stone's: at headline size a grey word looks disabled rather than quiet),
 * and small events drop to lowercase.
 *
 * [scale] is applied about the row's **bottom** edge, so the word grows upward
 * into space the row already occupies rather than pushing what is under it.
 */
@Composable
internal fun ValenceWord(
    valence: Valence,
    scale: Float,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.colors
    val token = PebblesTypography.valenceWord
    val raw = stringResource(valenceWordRes(valence.polarity))
    // Lowercased here rather than through a case transform, and padded with a
    // space on each side so the hand font's terminal flick has advance width to
    // live in — see `PebblesTypography.needsInkPadding`. A space is what
    // survives: it is part of the line the glyphs are clipped to, where a
    // layout box's padding is not.
    val cased = if (valence.sizeGroup == ValenceSizeGroup.SMALL) raw.lowercase(Locale.getDefault()) else raw
    val word = if (PebblesTypography.needsInkPadding(token)) " $cased " else cased

    // `BasicText` rather than `PebblesText` (or Material's `Text`): the
    // highlight word is painted with the stone's baked mesh, which only rides
    // on `TextStyle.brush`, and `Text` resolves a foreground *colour* into the
    // style it merges — the one thing that can quietly win over a brush. None
    // of the word tokens is an uppercase token, so the case transform
    // `PebblesText` exists for has nothing to do here either.
    BasicText(
        text = word,
        style =
            token.copy(
                brush = ValenceStoneStyles.headlineInk(valence.polarity, colors.system, colors.accent),
                textAlign = TextAlign.Center,
            ),
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }.padding(horizontal = PebblesTypography.inkOverhang(token)),
    )
}

/**
 * "Moment" rather than the picker's "Neutral" — the lockup reads as a phrase,
 * and nobody calls their afternoon a neutral. The other two reuse the polarity
 * labels, which are already the words the lockup wants.
 */
private fun valenceWordRes(polarity: ValencePolarity): Int =
    when (polarity) {
        ValencePolarity.LOWLIGHT -> R.string.valence_lowlight
        ValencePolarity.NEUTRAL -> R.string.valence_headline_neutral
        ValencePolarity.HIGHLIGHT -> R.string.valence_highlight
    }

/** The second line: what the event covered. */
internal fun valenceSpanRes(size: ValenceSizeGroup): Int =
    when (size) {
        ValenceSizeGroup.SMALL -> R.string.valence_span_day
        ValenceSizeGroup.MEDIUM -> R.string.valence_span_week
        ValenceSizeGroup.LARGE -> R.string.valence_span_month
    }
