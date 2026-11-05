package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PageTypography
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.PromiseLetter

private val RAIL_WIDTH = 44.dp
private val RULE_WIDTH = 64.dp

/**
 * Step 1 (#1510 D2): the letter beside its rail, the first paragraph in the italic lead, the mark and
 * the signature under a rule, one button. The letter is Markdown by column name but shown as plain
 * paragraphs: the seeded letter uses none of the syntax, and the admin form says "one paragraph per
 * blank line".
 */
@Composable
fun PromiseStep(
    letter: PromiseLetter?,
    unavailable: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val french = LocalConfiguration.current.locales[0].language == "fr"
    // The rail runs from the top to just above the button, and only the letter scrolls: the button
    // never leaves the screen, however long the admin's letter gets.
    Column(
        modifier
            .fillMaxSize()
            .padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            PromiseRail(stringResource(R.string.onboarding_promise_rail))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.lg, end = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                val emphasis = stringResource(R.string.onboarding_promise_title_em)
                val lead = stringResource(R.string.onboarding_promise_title)
                Text(
                    buildAnnotatedString {
                        append(lead)
                        append(" ")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.primary)) { append(emphasis) }
                    },
                    style = PageTypography.sectionHead,
                    modifier = Modifier.semantics { heading() },
                )
                HorizontalDivider(Modifier.width(RULE_WIDTH), color = colors.outlineVariant)
                when {
                    letter != null ->
                        letter.body(french).split("\n\n").filter { it.isNotBlank() }.forEachIndexed { index, paragraph ->
                            Text(
                                paragraph.trim(),
                                style = if (index == 0) PageTypography.lead else MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                            )
                        }
                    unavailable ->
                        Text(
                            stringResource(R.string.onboarding_promise_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                        )
                }
                if (letter != null) {
                    HorizontalDivider(color = colors.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        PbblsIconImage(PbblsIcon.PEBBLE_ROUND_LIT_1, PbblsIconSize.TILE, contentDescription = null, tint = colors.primary)
                        Text(letter.signedBy(french), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                    }
                }
            }
        }
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_promise_cta),
            onClick = onContinue,
            modifier =
                Modifier
                    .padding(horizontal = Spacing.xl)
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}

/** The letter's margin: a vertical label against a hairline. Decoration: the title already says it. */
@Composable
private fun PromiseRail(text: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .width(RAIL_WIDTH)
            .fillMaxHeight()
            .clearAndSetSemantics {},
    ) {
        Box(
            Modifier
                .weight(1f)
                .padding(top = Spacing.xs),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.em),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                modifier =
                    Modifier.layout { measurable, _ ->
                        val placeable = measurable.measure(Constraints())
                        layout(placeable.height, placeable.width) {
                            placeable.placeWithLayer(
                                x = (placeable.height - placeable.width) / 2,
                                y = (placeable.width - placeable.height) / 2,
                            ) { rotationZ = -90f }
                        }
                    },
            )
        }
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.outlineVariant),
        )
    }
}
