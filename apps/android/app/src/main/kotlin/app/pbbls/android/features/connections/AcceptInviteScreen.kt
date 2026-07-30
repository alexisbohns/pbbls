package app.pbbls.android.features.connections

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.services.AcceptInviteResult
import app.pbbls.android.services.InvitePreview
import app.pbbls.android.services.LocalConnectionsService
import app.pbbls.android.services.connectionsErrorMessage
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "connections-accept"

/**
 * Opened by an invite App Link (M49) — ports iOS `InviteAcceptSheet`. Shows
 * who is inviting first, then waits for an explicit tap: accepting is the
 * mutual consent, so it is never fired automatically (design D5/D12).
 */
@Composable
fun AcceptInviteScreen(
    token: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val service = LocalConnectionsService.current
    val scope = rememberCoroutineScope()

    var preview by remember(token) { mutableStateOf<InvitePreview?>(null) }
    var accepted by remember(token) { mutableStateOf<AcceptInviteResult?>(null) }
    var isLoading by remember(token) { mutableStateOf(true) }
    var isAccepting by remember(token) { mutableStateOf(false) }
    var errorRes by remember(token) { mutableStateOf<Int?>(null) }

    LaunchedEffect(token) {
        isLoading = true
        errorRes = null
        try {
            preview = service.preview(token)
        } catch (e: Exception) {
            Log.e(TAG, "invite preview failed", e)
            errorRes = connectionsErrorMessage(e.message)
        }
        isLoading = false
    }

    BackHandler { onDismiss() }

    AcceptInviteContent(
        preview = preview,
        accepted = accepted,
        isLoading = isLoading,
        isAccepting = isAccepting,
        errorRes = errorRes,
        onAccept = {
            isAccepting = true
            errorRes = null
            scope.launch {
                try {
                    accepted = service.accept(token)
                } catch (e: Exception) {
                    Log.e(TAG, "invite accept failed", e)
                    errorRes = connectionsErrorMessage(e.message)
                }
                isAccepting = false
            }
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/** Stateless accept surface — what screenshot previews drive. */
@Composable
fun AcceptInviteContent(
    preview: InvitePreview?,
    accepted: AcceptInviteResult?,
    isLoading: Boolean,
    isAccepting: Boolean,
    errorRes: Int?,
    onAccept: () -> Unit,
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when {
                    isLoading -> CircularProgressIndicator(color = accent.primary)

                    accepted != null -> {
                        accepted.peer.glyph?.let { glyph ->
                            GlyphView(
                                case = GlyphViewCase.PROFILE,
                                strokes = glyph.strokes,
                                viewBox = glyph.viewBox,
                                side = 72.dp,
                            )
                        }
                        val name =
                            accepted.peer.displayName
                                ?: stringResource(R.string.connections_unnamed_peer)
                        PebblesText(
                            // A repeat accept is a success state, never an error.
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

                    preview != null && preview.isValid -> {
                        preview.inviter?.glyph?.let { glyph ->
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
                                    preview.inviter?.displayName
                                        ?: stringResource(R.string.connections_unnamed_peer),
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
                        errorRes?.let { res ->
                            PebblesText(
                                text = stringResource(res),
                                style = PebblesTypography.meta,
                                color = system.secondary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        TextButton(onClick = onAccept, enabled = !isAccepting) {
                            PebblesText(
                                text = stringResource(R.string.connections_accept_action),
                                style = PebblesTypography.buttonLabel,
                                color = accent.primary,
                            )
                        }
                    }

                    else -> {
                        // Expired, revoked and unknown tokens share one outward
                        // state — a withdrawn invite reveals no inviter.
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
                }
            }
        }
    }
}
