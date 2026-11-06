package app.pbbls.android.features.path.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
     * Full-width prominent button — Continue on Complete's `valence`, the saves on Capture's
     * moment page (the main one under [PrimaryWithSecondary]), Make a pebble on Complete's `privacy`.
     */
    data class Primary(
        val label: String,
        val enabled: Boolean,
        val isLoading: Boolean,
        val onClick: () -> Unit,
    ) : RecordStepAction

    /**
     * Capture's moment page: the primary save ("Save & make it a pebble") and the plain save beneath it as
     * a text button. [secondaryIsLoading] swaps the secondary's label for a spinner while its own save is in flight.
     */
    data class PrimaryWithSecondary(
        val primary: Primary,
        val secondaryLabel: String,
        val onSecondary: () -> Unit,
        val secondaryIsLoading: Boolean = false,
    ) : RecordStepAction
}

/**
 * Shared geometry for every step: a title, an optional fixed slot, a scrolling content slot, and one
 * optional button beneath it — ports iOS `RecordStepScaffold`.
 *
 * Steps supply content and a button role and never their own layout, so the
 * title baseline and button position do not drift between screens as the user
 * moves through the flow — which is the whole reason the flow reads as one
 * motion rather than eleven pages.
 *
 * Scrolling content dissolves at both ends instead of clipping (never a hard edge): an overlay gradient
 * from `surface` to `surface` at zero alpha, over a content padding of the same height, so at rest nothing
 * is faded. It is an overlay and not a `DstIn` mask because HWUI ignored the mask on a Pixel 8 while
 * Robolectric honoured it (`apps/android/CLAUDE.md`, Path rendering).
 *
 * Stateless: every input arrives as a parameter, so screenshot previews drive
 * it with no services.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecordStepScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: RecordStepAction? = null,
    /**
     * Whether the content slot scrolls. Pass `false` for content that is already
     * scrollable — see `CompleteStep.bringsOwnScroll`; nesting two vertical
     * scrolls throws at measure time.
     */
    contentScrolls: Boolean = true,
    /** A row held between the title and the content, that does not scroll with it (the photo step's controls). */
    fixedSlot: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize(),
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

        if (fixedSlot != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg).padding(horizontal = Spacing.lg)) { fixedSlot() }
        }

        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(if (contentScrolls) Modifier.verticalScroll(scrollState) else Modifier)
                        .padding(horizontal = Spacing.lg)
                        .padding(vertical = FadeHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
            if (contentScrolls) {
                EdgeFade(top = true, modifier = Modifier.align(Alignment.TopCenter))
                EdgeFade(top = false, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
        Spacer(Modifier.height(Spacing.lg))

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
                val secondaryEnabled = action.primary.enabled && !action.primary.isLoading && !action.secondaryIsLoading
                TextButton(
                    onClick = action.onSecondary,
                    enabled = secondaryEnabled,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                ) {
                    if (action.secondaryIsLoading) {
                        LoadingIndicator(color = colors.primary, modifier = Modifier.size(SecondarySpinner))
                    } else {
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
}

/** The height of a fade, and of the content padding that keeps it off resting content. */
private val FadeHeight = Spacing.lg

private val SecondarySpinner = 24.dp

/** `surface` to `surface` at zero alpha: the same hue, so the edge never greys. */
@Composable
private fun EdgeFade(
    top: Boolean,
    modifier: Modifier = Modifier,
) {
    val surface = MaterialTheme.colorScheme.surface
    val clear = surface.copy(alpha = 0f)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FadeHeight)
                .background(Brush.verticalGradient(if (top) listOf(surface, clear) else listOf(clear, surface))),
    )
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
