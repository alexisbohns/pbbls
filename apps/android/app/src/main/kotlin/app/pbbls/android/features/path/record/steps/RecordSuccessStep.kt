package app.pbbls.android.features.path.record.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.PebblesPrimaryButton
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.read.PebbleReadPetroglyph
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.Spacing

/**
 * Step 10 — the pebble, drawn on — ports iOS `RecordSuccessStep`.
 *
 * Reuses [PebbleReadPetroglyph], the same component the read view uses: it
 * composites the outline backdrop and the server-composed render, coloured
 * through the emotion palette.
 *
 * On soft success ([renderSvg] null) it degrades to name + karma with no
 * artwork rather than blocking — the pebble exists and the user should be told
 * so (M58 D10).
 *
 * Stateless: the palette arrives as a parameter so screenshot previews drive it
 * without a live client.
 */
@Composable
fun RecordSuccessStep(
    name: String,
    renderSvg: String?,
    karmaDelta: Int?,
    valence: Valence,
    palette: EmotionPalette?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        Spacer(Modifier.weight(1f))

        if (renderSvg != null) {
            PebbleReadPetroglyph(
                renderSvg = renderSvg,
                valence = valence,
                palette = palette,
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // User-authored, so never localized.
            PebblesText(
                text = name,
                style = PebblesTypography.title,
                color = system.foreground,
                textAlign = TextAlign.Center,
            )

            if (karmaDelta != null && karmaDelta > 0) {
                val karmaLabel = stringResource(R.string.record_karma_a11y, karmaDelta)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = karmaLabel },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sparkle),
                        contentDescription = null,
                        tint = accent.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    PebblesText(
                        text = stringResource(R.string.record_karma, karmaDelta),
                        style = PebblesTypography.headline,
                        color = system.foreground,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        PebblesPrimaryButton(
            text = stringResource(R.string.record_success_exit),
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
