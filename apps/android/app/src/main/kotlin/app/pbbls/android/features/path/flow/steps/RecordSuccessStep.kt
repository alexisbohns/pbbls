package app.pbbls.android.features.path.flow.steps

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.PebbleGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.ui.MomentNameText
import app.pbbls.android.features.path.read.PebbleReadPetroglyph

/**
 * Step 10 — the pebble, drawn on — ports iOS `RecordSuccessStep`.
 *
 * Reuses [PebbleReadPetroglyph], drawn for the old read view (#1135 retired it): it
 * composites the outline backdrop and the composed stone, coloured through the
 * emotion palette. The stone always draws: a null [glyph] is a bare stone, not
 * an absent one (R12).
 *
 * Stateless: the palette arrives as a parameter so screenshot previews drive it
 * without a live client.
 */
@Composable
fun RecordSuccessStep(
    name: String?,
    glyph: PebbleGlyph?,
    karmaDelta: Int?,
    valence: Valence,
    palette: EmotionPalette?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

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

        PebbleReadPetroglyph(
            valence = valence,
            glyph = glyph,
            palette = palette,
            modifier = Modifier.fillMaxWidth().height(280.dp),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // User-authored, so never localized; a photo-only moment reads "Untitled" (R3, R12).
            MomentNameText(
                name = name,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
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
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.record_karma, karmaDelta),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
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
