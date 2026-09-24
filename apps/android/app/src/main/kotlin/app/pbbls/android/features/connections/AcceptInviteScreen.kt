package app.pbbls.android.features.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.PebblesTopBarTextButton
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase

/**
 * Opened by an invite App Link (M49) — ports iOS `InviteAcceptSheet`. Shows
 * who is inviting first, then waits for an explicit tap: accepting is the
 * mutual consent, so it is never fired automatically (design D5/D12).
 *
 * A [app.pbbls.android.navigation.PebblesKey.AcceptInvite] nav entry (#852,
 * replacing the earlier cover composed above the nav host) —
 * `rememberViewModelStoreNavEntryDecorator()` scopes `hiltViewModel()` to this
 * entry, so [AcceptInviteViewModel] is destroyed when it pops.
 */
@Composable
fun AcceptInviteScreen(
    token: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AcceptInviteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Guarded on the token, so a rotation re-runs this without re-previewing.
    LaunchedEffect(token) { viewModel.start(token) }

    // NavigationBackHandler (not the legacy BackHandler), matching every other
    // migrated surface (#852) — never previewed with a real hiltViewModel, so
    // no LocalInspectionMode guard is needed here (contrast OnboardingScreen).
    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) { onDismiss() }

    AcceptInviteContent(
        uiState = uiState,
        onAccept = viewModel::accept,
        onRetry = viewModel::retry,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/** Stateless accept surface — what screenshot previews drive. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AcceptInviteContent(
    uiState: AcceptInviteUiState,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.surface)
                .safeDrawingPadding(),
    ) {
        PebblesTopBar(
            title = stringResource(R.string.connections_accept_title),
            titleStyle = MaterialTheme.typography.titleMediumEmphasized,
            titleColor = colors.onSurface,
            leading = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    color = colors.primary,
                )
            },
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Exhaustive with no `else`: a new AcceptInviteUiState case must
                // be rendered.
                when (uiState) {
                    AcceptInviteUiState.Loading -> CircularProgressIndicator(color = colors.primary)

                    is AcceptInviteUiState.Error -> PreviewFailed(uiState.messageRes, onRetry)

                    is AcceptInviteUiState.Accepted -> AcceptedPeer(uiState, onDismiss)

                    is AcceptInviteUiState.Ready ->
                        if (uiState.preview.isValid) {
                            ConsentPrompt(uiState, onAccept)
                        } else {
                            UnusableInvite(onDismiss)
                        }
                }
            }
        }
    }
}

/**
 * The preview request itself failed.
 *
 * It used to fall into the "this invite is no longer usable" branch, so being
 * offline told the user their friend's invite was dead — a claim the app had no
 * basis for, and one that sends them back to the inviter for a link they did
 * not need. It is its own state now, with a retry.
 */
@Composable
private fun ColumnScope.PreviewFailed(
    messageRes: Int,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(messageRes),
        style = MaterialTheme.typography.bodyLarge,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onRetry) {
        Text(
            text = stringResource(R.string.action_retry),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
    }
}

/** Connected. A repeat accept is a success state, never an error. */
@Composable
private fun ColumnScope.AcceptedPeer(
    state: AcceptInviteUiState.Accepted,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accepted = state.result

    accepted.peer.glyph?.let { glyph ->
        GlyphView(
            case = GlyphViewCase.PROFILE,
            strokes = glyph.strokes,
            viewBox = glyph.viewBox,
            side = 72.dp,
        )
    }
    val name = accepted.peer.displayName ?: stringResource(R.string.connections_unnamed_peer)
    Text(
        text =
            if (accepted.alreadyConnected) {
                stringResource(R.string.connections_accept_already, name)
            } else {
                stringResource(R.string.connections_accept_done, name)
            },
        style = MaterialTheme.typography.titleSmall,
        color = colors.onSurface,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onDismiss) {
        Text(
            text = stringResource(R.string.action_done),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
    }
}

/** Who is inviting, and the one tap that is the mutual consent. */
@Composable
private fun ColumnScope.ConsentPrompt(
    state: AcceptInviteUiState.Ready,
    onAccept: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val inviter = state.preview.inviter

    inviter?.glyph?.let { glyph ->
        GlyphView(
            case = GlyphViewCase.PROFILE,
            strokes = glyph.strokes,
            viewBox = glyph.viewBox,
            side = 72.dp,
        )
    }
    Text(
        text =
            stringResource(
                R.string.connections_accept_prompt,
                inviter?.displayName ?: stringResource(R.string.connections_unnamed_peer),
            ),
        style = MaterialTheme.typography.titleSmall,
        color = colors.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.connections_accept_explainer),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    // A failed accept leaves the prompt up: the token may well still be good,
    // so the tap is worth offering again.
    state.acceptErrorRes?.let { res ->
        Text(
            text = stringResource(res),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    TextButton(onClick = onAccept, enabled = !state.isAccepting) {
        Text(
            text = stringResource(R.string.connections_accept_action),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
    }
}

/**
 * Expired, revoked and unknown tokens share one outward state — a withdrawn
 * invite reveals no inviter.
 */
@Composable
private fun ColumnScope.UnusableInvite(onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.connections_accept_unusable),
        style = MaterialTheme.typography.titleSmall,
        color = colors.onSurface,
        textAlign = TextAlign.Center,
    )
    Text(
        text = stringResource(R.string.connections_accept_unusable_hint),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onDismiss) {
        Text(
            text = stringResource(R.string.action_close),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
        )
    }
}
