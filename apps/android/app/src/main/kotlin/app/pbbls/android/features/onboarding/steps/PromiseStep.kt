package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.PromiseLetter

/**
 * Step 1 (#1510 D2): the letter as paragraphs, the round pebble as its signature, one button. The
 * letter is Markdown by column name but shown as plain paragraphs: the seeded letter uses none of
 * the syntax, and the admin form says "one paragraph per blank line".
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
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.onboarding_promise_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        when {
            letter != null ->
                letter.body(french).split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                    Text(paragraph.trim(), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                }
            unavailable ->
                Text(
                    stringResource(R.string.onboarding_promise_unavailable),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
        }
        if (letter != null) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PbblsIconImage(PbblsIcon.PEBBLE_ROUND_LIT_1, PbblsIconSize.TILE, contentDescription = null, tint = colors.primary)
                Text(letter.signedBy(french), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_promise_cta),
            onClick = onContinue,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
