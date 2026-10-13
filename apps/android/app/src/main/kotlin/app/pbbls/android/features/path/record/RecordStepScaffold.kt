package app.pbbls.android.features.path.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing

/**
 * The single action a step may offer beneath its content — ports iOS
 * `RecordStepAction`.
 */
sealed interface RecordStepAction {
    /** Quiet text button — Skip / Done on the optional steps (M58 D3). */
    data class Text(
        val label: String,
        val onClick: () -> Unit,
    ) : RecordStepAction

    /**
     * Full-width prominent button — Continue on `when` / `name`, Publish on
     * `privacy`.
     */
    data class Primary(
        val label: String,
        val enabled: Boolean,
        val isLoading: Boolean,
        val onClick: () -> Unit,
    ) : RecordStepAction

    /** Capture's name step: the primary save, and the save-then-complete text button beneath it. */
    data class PrimaryWithSecondary(
        val primary: Primary,
        val secondaryLabel: String,
        val onSecondary: () -> Unit,
    ) : RecordStepAction
}

/**
 * Shared geometry for every step: a title, a scrolling content slot, and one
 * optional button beneath it — ports iOS `RecordStepScaffold`.
 *
 * Steps supply content and a button role and never their own layout, so the
 * title baseline and button position do not drift between screens as the user
 * moves through the flow — which is the whole reason the flow reads as one
 * motion rather than eleven pages.
 *
 * Stateless: every input arrives as a parameter, so screenshot previews drive
 * it with no services.
 */
@Composable
fun RecordStepScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: RecordStepAction? = null,
    /**
     * Whether the content slot scrolls. Pass `false` for content that is already
     * scrollable — see `RecordStep.bringsOwnScroll`; nesting two vertical
     * scrolls throws at measure time.
     */
    contentScrolls: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        val scrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (contentScrolls) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            content()
        }

        when (action) {
            null -> Unit
            is RecordStepAction.Text ->
                TextButton(
                    onClick = action.onClick,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                ) {
                    Text(
                        text = action.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                }
            is RecordStepAction.Primary -> PrimaryAction(action)
            is RecordStepAction.PrimaryWithSecondary -> {
                PrimaryAction(action.primary)
                val secondaryEnabled = action.primary.enabled && !action.primary.isLoading
                TextButton(
                    onClick = action.onSecondary,
                    enabled = secondaryEnabled,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                ) {
                    Text(
                        text = action.secondaryLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (secondaryEnabled) colors.primary else colors.onSurface.copy(alpha = DISABLED_ALPHA),
                    )
                }
            }
        }
    }
}

/** M3's disabled content opacity; an explicit text colour does not pick it up from the button. */
private const val DISABLED_ALPHA = 0.38f

@Composable
private fun PrimaryAction(action: RecordStepAction.Primary) {
    PebblesPrimaryButton(
        text = action.label,
        onClick = action.onClick,
        enabled = action.enabled && !action.isLoading,
        isLoading = action.isLoading,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.sm),
    )
}
