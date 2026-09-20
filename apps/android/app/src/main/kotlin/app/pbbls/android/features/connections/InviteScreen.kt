package app.pbbls.android.features.connections

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Your invite (M49) — ports iOS `InviteSheet`. One link, shown as text and as
 * a QR so a friend across the table can just scan it. The same invite comes
 * back on every open until it is rotated or expires, so a link already shared
 * stays alive (design D3).
 */
@Composable
fun InviteScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Guarded, so a rotation is not undone by a configuration change.
    LaunchedEffect(Unit) { viewModel.start() }

    fun dismiss() {
        onDismiss()
    }

    InviteContent(
        uiState = uiState,
        onRetry = viewModel::retry,
        onCopy = { url -> clipboard.setText(AnnotatedString(url)) },
        onShare = { url ->
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.connections_invite_share)),
            )
        },
        onRotate = viewModel::rotate,
        onDismiss = { dismiss() },
        modifier = modifier,
    )
}

/** Stateless invite surface — what screenshot previews drive. */
@Composable
fun InviteContent(
    uiState: InviteUiState,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRotate: () -> Unit,
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
            title = stringResource(R.string.connections_invite_title),
            titleStyle = PebblesTypography.headlineEmphasized,
            titleColor = system.foreground,
            leading = {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_done),
                    onClick = onDismiss,
                    color = accent.primary,
                )
            },
        )

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Exhaustive with no `else`: a new InviteUiState case must be rendered.
            when (uiState) {
                InviteUiState.Loading -> CircularProgressIndicator(color = accent.primary)

                is InviteUiState.Error ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PebblesText(
                            text = stringResource(uiState.messageRes),
                            style = PebblesTypography.body,
                            color = system.secondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        TextButton(onClick = onRetry) {
                            PebblesText(
                                text = stringResource(R.string.action_retry),
                                style = PebblesTypography.buttonLabel,
                                color = accent.primary,
                            )
                        }
                    }

                is InviteUiState.Content ->
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PebblesText(
                            text = stringResource(R.string.connections_invite_explainer),
                            style = PebblesTypography.body,
                            color = system.secondary,
                            textAlign = TextAlign.Center,
                        )

                        QrCode(
                            content = uiState.invite.url,
                            contentDescription = stringResource(R.string.connections_invite_qr_alt),
                        )

                        // The link is always shown and copyable: the QR is
                        // never the sole affordance.
                        PebblesText(
                            text = uiState.invite.url,
                            style = PebblesTypography.meta,
                            color = system.secondary,
                            textAlign = TextAlign.Center,
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onCopy(uiState.invite.url) }) {
                                PebblesText(
                                    text = stringResource(R.string.connections_invite_copy),
                                    style = PebblesTypography.buttonLabel,
                                    color = accent.primary,
                                )
                            }
                            TextButton(onClick = { onShare(uiState.invite.url) }) {
                                PebblesText(
                                    text = stringResource(R.string.connections_invite_share),
                                    style = PebblesTypography.buttonLabel,
                                    color = accent.primary,
                                )
                            }
                        }

                        TextButton(onClick = onRotate, enabled = !uiState.isRotating) {
                            PebblesText(
                                text = stringResource(R.string.connections_invite_rotate),
                                style = PebblesTypography.buttonLabel,
                                color = accent.primary,
                            )
                        }

                        PebblesText(
                            text = stringResource(R.string.connections_invite_rotate_note),
                            style = PebblesTypography.meta,
                            color = system.secondary,
                            textAlign = TextAlign.Center,
                        )

                        // A rotation that failed leaves the live invite on
                        // screen — it is still the server's answer — with the
                        // reason under it.
                        uiState.rotateErrorRes?.let { res ->
                            PebblesText(
                                text = stringResource(res),
                                style = PebblesTypography.meta,
                                color = PebblesDestructive,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
            }
        }
    }
}

/**
 * Draws the zxing [BitMatrix] straight into a Compose [Canvas] — no Bitmap
 * allocation, matching how glyphs render as vectors.
 */
@Composable
private fun QrCode(
    content: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(content) { encodeQr(content) } ?: return

    Box(
        modifier =
            modifier
                .size(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(12.dp)
                .semantics { this.contentDescription = contentDescription },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cell = size.width / matrix.width
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    if (matrix.get(x, y)) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(x * cell, y * cell),
                            size = Size(cell, cell),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Encodes [content] as a QR matrix. Pure and JVM-testable (zxing `core` has no
 * Android dependency). Returns null rather than throwing: the link text and
 * share button stay usable, so a QR failure degrades instead of blocking.
 */
internal fun encodeQr(
    content: String,
    size: Int = 512,
): BitMatrix? =
    runCatching {
        QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 0,
            ),
        )
    }.getOrNull()
