package app.pbbls.android.features.path.record

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.components.PebblesPrimaryButton
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.Spacing

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
    val system = PebblesTheme.colors.system
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
            PebblesText(
                text = title,
                style = PebblesTypography.title,
                color = system.foreground,
                textAlign = TextAlign.Center,
            )
            if (subtitle != null) {
                PebblesText(
                    text = subtitle,
                    style = PebblesTypography.subhead,
                    color = system.secondary,
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
                    PebblesText(
                        text = action.label,
                        style = PebblesTypography.callout,
                        color = system.secondary,
                    )
                }
            is RecordStepAction.Primary ->
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
    }
}
