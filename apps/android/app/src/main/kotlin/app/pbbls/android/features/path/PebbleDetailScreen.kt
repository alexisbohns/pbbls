package app.pbbls.android.features.path

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.SharedPebbleLink
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.features.path.read.PebblePrivacyBadge
import app.pbbls.android.features.path.read.PebbleReadView
import app.pbbls.android.features.path.read.pebblePageColors

/**
 * Pebble detail — a pushed entry now (#852, ports iOS `PebbleDetailSheet`,
 * D5/D7). Self-applies `safeDrawingPadding()`. Loads a [PebbleDetail] via
 * [LocalPebbleDetailService], owns its loading/error/retry state, hosts the
 * top bar, and delegates the body to the pure [PebbleReadView]. System back is
 * `NavDisplay`'s own — this screen has no in-flight write to protect, so it no
 * longer needs its own `BackHandler`.
 *
 * [onEditRequested] opens the `EditPebble` entry; returning from it is picked
 * up by [PebbleDetailViewModel.onResumed] rather than a callback.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebbleDetailScreen(
    pebbleId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onEditRequested: () -> Unit = {},
    viewModel: PebbleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palettes = LocalEmotionPaletteService.current
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    LaunchedEffect(pebbleId) { viewModel.start(pebbleId) }

    // Returning from EditPebble must re-read the pebble: the ViewModel is
    // scoped to the back stack entry, which survives that round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    val detail = (uiState as? PebbleDetailUiState.Content)?.detail

    // Once loaded, the whole page (top bar + insets included) tints to the
    // emotion palette background (#605); before load / on a cache miss it stays
    // on the surface. PebbleReadView repaints the same tint over its
    // own body, so the two meet seamlessly.
    val pageBackground =
        detail?.let { palettes.palette(it.emotion.id) }?.let {
            pebblePageColors(it, isSystemInDarkTheme()).background
        } ?: colors.surface

    // Share is only offered for public pebbles (M51) — anyone with the /p
    // link can open a public pebble, so sharing a secret/connections one
    // would leak it past its intended audience.
    val onShare: (() -> Unit)? =
        if (detail?.visibility == Visibility.PUBLIC) {
            {
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, SharedPebbleLink.url(pebbleId))
                    }
                context.startActivity(Intent.createChooser(send, null))
            }
        } else {
            null
        }

    Column(
        modifier
            .fillMaxSize()
            .background(pageBackground)
            // Swallow all pointer input so this cover is input-opaque like the
            // iOS fullScreenCover (D5) — without it, taps over the loading/error
            // states fall through to the PathScreen rows' combinedClickable.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }.safeDrawingPadding()
            .readableWidth(),
    ) {
        DetailTopBar(
            visibility = detail?.visibility,
            editEnabled = detail != null,
            onBack = onDismiss,
            onEdit = onEditRequested,
            onShare = onShare,
        )
        // Exhaustive with no `else`: a new PebbleDetailUiState case must render.
        when (val state = uiState) {
            PebbleDetailUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingIndicator()
                }
            PebbleDetailUiState.Error ->
                DetailLoadError(onRetry = viewModel::retry)
            is PebbleDetailUiState.Content ->
                PebbleReadView(
                    detail = state.detail,
                    palette = palettes.palette(state.detail.emotion.id),
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}

/**
 * Detail top bar: leading system-back arrow, an optional privacy badge once the
 * pebble is loaded, an optional share button (public pebbles only, M51), and a
 * trailing Edit button (enabled only after load; inert in B). iOS relies on
 * swipe-to-dismiss; Android adds the explicit back arrow + [BackHandler] for
 * discoverability (D5, documented divergence).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    visibility: Visibility?,
    editEnabled: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: (() -> Unit)?,
) {
    // A stock top app bar (#854) on a clear container: the read page below is
    // tinted to the pebble's emotion, and the bar should sit on that, not on surface.
    TopAppBar(
        title = { if (visibility != null) PebblePrivacyBadge(visibility = visibility) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.pebble_detail_back_a11y),
                )
            }
        },
        actions = {
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = stringResource(R.string.pebble_share_a11y),
                    )
                }
            }
            TextButton(onClick = onEdit, enabled = editEnabled) {
                Text(stringResource(R.string.pebble_detail_edit))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

/** Centered error view with a Retry action — mirrors `PebbleDetailSheet.content`'s error branch. */
@Composable
private fun DetailLoadError(onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.pebble_detail_load_error),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text(
                stringResource(R.string.pebble_detail_retry),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary,
            )
        }
    }
}

private const val TAG = "pebble-detail"
