package app.pbbls.android.features.connections

import androidx.activity.compose.BackHandler
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
import app.pbbls.android.R
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography

/**
 * Opened by an invite App Link (M49) — ports iOS `InviteAcceptSheet`. Shows
 * who is inviting first, then waits for an explicit tap: accepting is the
 * mutual consent, so it is never fired automatically (design D5/D12).
 *
 * `RootScreen` composes this *above* the nav host, so its ViewModel is
 * activity-scoped — see [AcceptInviteViewModel] for why its `finish()` is
 * load-bearing here in a way it is not for a cover inside a destination.
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

    fun dismiss() {
        viewModel.finish()
        onDismiss()
    }

    BackHandler { dismiss() }

    AcceptInviteContent(
        uiState = uiState,
        onAccept = viewModel::accept,
        onRetry = viewModel::retry,
        onDismiss = { dismiss() },
        modifier = modifier,
    )
}

/** Stateless accept surface — what screenshot previews drive. */
@Composable
fun AcceptInviteContent(
    uiState: AcceptInviteUiState,
    onAccept: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding(),
    ) {
        PebblesTopBar(
            title = stringResource(R.string.connections_accept_title),
            titleStyle = PebblesTypography.headlineEmphasized,
            titleColor = system.foreground,
            leading = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_close),
                    onClick = onDismiss,
                    color = accent.primary,
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
                    AcceptInviteUiState.Loading -> CircularProgressIndicator(color = accent.primary)

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
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    PebblesText(
        text = stringResource(messageRes),
        style = PebblesTypography.body,
        color = system.secondary,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onRetry) {
        PebblesText(
            text = stringResource(R.string.action_retry),
            style = PebblesTypography.buttonLabel,
            color = accent.primary,
        )
    }
}

/** Connected. A repeat accept is a success state, never an error. */
@Composable
private fun ColumnScope.AcceptedPeer(
    state: AcceptInviteUiState.Accepted,
    onDismiss: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
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
    PebblesText(
        text =
            if (accepted.alreadyConnected) {
                stringResource(R.string.connections_accept_already, name)
            } else {
                stringResource(R.string.connections_accept_done, name)
            },
        style = PebblesTypography.cardHeading,
        color = system.foreground,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onDismiss) {
        PebblesText(
            text = stringResource(R.string.action_done),
            style = PebblesTypography.buttonLabel,
            color = accent.primary,
        )
    }
}

/** Who is inviting, and the one tap that is the mutual consent. */
@Composable
private fun ColumnScope.ConsentPrompt(
    state: AcceptInviteUiState.Ready,
    onAccept: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val inviter = state.preview.inviter

    inviter?.glyph?.let { glyph ->
        GlyphView(
            case = GlyphViewCase.PROFILE,
            strokes = glyph.strokes,
            viewBox = glyph.viewBox,
            side = 72.dp,
        )
    }
    PebblesText(
        text =
            stringResource(
                R.string.connections_accept_prompt,
                inviter?.displayName ?: stringResource(R.string.connections_unnamed_peer),
            ),
        style = PebblesTypography.cardHeading,
        color = system.foreground,
        textAlign = TextAlign.Center,
    )
    PebblesText(
        text = stringResource(R.string.connections_accept_explainer),
        style = PebblesTypography.meta,
        color = system.secondary,
        textAlign = TextAlign.Center,
    )
    // A failed accept leaves the prompt up: the token may well still be good,
    // so the tap is worth offering again.
    state.acceptErrorRes?.let { res ->
        PebblesText(
            text = stringResource(res),
            style = PebblesTypography.meta,
            color = system.secondary,
            textAlign = TextAlign.Center,
        )
    }
    TextButton(onClick = onAccept, enabled = !state.isAccepting) {
        PebblesText(
            text = stringResource(R.string.connections_accept_action),
            style = PebblesTypography.buttonLabel,
            color = accent.primary,
        )
    }
}

/**
 * Expired, revoked and unknown tokens share one outward state — a withdrawn
 * invite reveals no inviter.
 */
@Composable
private fun ColumnScope.UnusableInvite(onDismiss: () -> Unit) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    PebblesText(
        text = stringResource(R.string.connections_accept_unusable),
        style = PebblesTypography.cardHeading,
        color = system.foreground,
        textAlign = TextAlign.Center,
    )
    PebblesText(
        text = stringResource(R.string.connections_accept_unusable_hint),
        style = PebblesTypography.meta,
        color = system.secondary,
        textAlign = TextAlign.Center,
    )
    TextButton(onClick = onDismiss) {
        PebblesText(
            text = stringResource(R.string.action_close),
            style = PebblesTypography.buttonLabel,
            color = accent.primary,
        )
    }
}
