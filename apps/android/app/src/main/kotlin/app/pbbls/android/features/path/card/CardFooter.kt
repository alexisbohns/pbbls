package app.pbbls.android.features.path.card

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.SpineTypography
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.core.ui.iconRes
import app.pbbls.android.core.ui.labelRes

private val PRIVACY_GLYPH = 16.dp
private val PRIVACY_GLYPH_GAP = 8.dp
private val MINIATURE_GAP = 8.dp
private val BALANCED = LineBreak(LineBreak.Strategy.Balanced, LineBreak.Strictness.Normal, LineBreak.WordBreak.Default)

/**
 * The card's still footer (pebble card spec §The card): privacy left, the turn right.
 * The turn target is one 48 dp box covering label and miniature out to the card's
 * edge; the custom layout places it so it never overlaps the privacy sentence, and
 * drops it under the privacy when it would take more than half the width.
 */
@Composable
internal fun CardFooter(
    visibility: Visibility,
    turnTo: CardFaceTarget,
    stone: Color,
    onTurn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val grade = stringResource(visibility.labelRes)
    val explanation = stringResource(visibility.explanationRes)
    val privacy = stringResource(R.string.visibility_badge_a11y, grade) + ". " + explanation
    val action =
        stringResource(
            if (turnTo == CardFaceTarget.PEBBLE) R.string.card_turn_action_to_pebble else R.string.card_turn_action_to_moment,
        )

    Layout(
        modifier = modifier,
        content = {
            Column(
                Modifier
                    .padding(start = CardLayout.FOOTER_PADDING_H, top = CardLayout.FOOTER_PADDING_V, bottom = CardLayout.FOOTER_PADDING_V)
                    .clearAndSetSemantics { contentDescription = privacy },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(visibility.iconRes),
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(PRIVACY_GLYPH),
                    )
                    Spacer(Modifier.width(PRIVACY_GLYPH_GAP))
                    Text(grade, style = SpineTypography.cardFooterGrade, color = colors.ink)
                }
                // The sentence hangs under the grade, past the glyph, as the mock sets it.
                Text(
                    text = explanation,
                    // Balanced, as the mock's `text-wrap: pretty`: a French sentence never leaves one word alone on its second line.
                    style = SpineTypography.cardFooterNote.copy(lineBreak = BALANCED),
                    color = colors.ink2,
                    modifier = Modifier.padding(start = PRIVACY_GLYPH + PRIVACY_GLYPH_GAP),
                )
            }
            Row(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        // No onClickLabel: the description already names the action, and TalkBack would read it twice.
                        .clickable(role = Role.Button, onClick = onTurn)
                        .clearAndSetSemantics { contentDescription = action }
                        .padding(start = MINIATURE_GAP, end = CardLayout.FOOTER_PADDING_H),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(stringResource(R.string.card_turn), style = SpineTypography.cardFooterGrade, color = colors.ink)
                    Text(
                        text =
                            stringResource(
                                if (turnTo == CardFaceTarget.PEBBLE) R.string.card_turn_to_pebble else R.string.card_turn_to_moment,
                            ),
                        style = SpineTypography.cardTurnTarget,
                        color = colors.ink2,
                    )
                }
                Spacer(Modifier.width(MINIATURE_GAP))
                FaceMiniature(to = turnTo, stone = stone, ink = colors.ink, stock = colors.stock)
            }
        },
    ) { measurables, constraints ->
        val (privacyColumn, turn) = measurables
        val gap = CardLayout.FOOTER_GAP.roundToPx()
        val right = turn.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        // Side by side while the turn takes at most half the width; past that (a large font
        // scale) the turn drops under the privacy, end-aligned, rather than squeezing it to a column.
        val stacked = constraints.hasBoundedWidth && right.width > constraints.maxWidth / 2
        val leftMax =
            if (stacked) constraints.maxWidth else (constraints.maxWidth - right.width - gap).coerceAtLeast(0)
        val left = privacyColumn.measure(Constraints(maxWidth = leftMax, maxHeight = constraints.maxHeight))
        val target = CardLayout.TURN_TARGET.roundToPx()
        val height =
            (if (stacked) left.height + maxOf(right.height, target) else maxOf(left.height, right.height, target))
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else left.width + gap + right.width
        layout(width, height) {
            if (stacked) {
                left.placeRelative(0, 0)
                right.placeRelative(width - right.width, left.height)
            } else {
                left.placeRelative(0, (height - left.height) / 2)
                right.placeRelative(width - right.width, (height - right.height) / 2)
            }
        }
    }
}

/** The shipped sentence per grade, from the record flow's privacy step. */
@get:StringRes
private val Visibility.explanationRes: Int
    get() =
        when (this) {
            Visibility.SECRET -> R.string.record_privacy_secret_explanation
            Visibility.PRIVATE -> R.string.record_privacy_private_explanation
            Visibility.PUBLIC -> R.string.record_privacy_public_explanation
        }
