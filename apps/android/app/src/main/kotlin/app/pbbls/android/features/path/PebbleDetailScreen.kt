package app.pbbls.android.features.path

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.SharedPebbleLink
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.features.path.read.PebblePrivacyBadge
import app.pbbls.android.features.path.read.PebbleReadView

/**
 * Pebble detail (#852, ports iOS `PebbleDetailSheet`, D7): a docked sheet on
 * phones and a pane beside Path on large screens (#940). The scene decides
 * which, and owns dismissal. Loads a [PebbleDetail] via
 * [LocalPebbleDetailService], owns its loading/error/retry state, hosts the
 * top bar, and delegates the body to the pure [PebbleReadView]. System back is
 * the scene's own (the sheet's window, or `NavDisplay` beside Path) — this
 * screen has no in-flight write to protect, so it needs no `BackHandler`.
 *
 * [onEditRequested] opens the `EditPebble` entry; returning from it is picked
 * up by [PebbleDetailViewModel.onResumed] rather than a callback.
 */
@Composable
fun PebbleDetailScreen(
    pebbleId: String,
    modifier: Modifier = Modifier,
    onEditRequested: () -> Unit = {},
    viewModel: PebbleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palettes = LocalEmotionPaletteService.current
    val context = LocalContext.current

    LaunchedEffect(pebbleId) { viewModel.start(pebbleId) }

    // Returning from EditPebble must re-read the pebble: the ViewModel is
    // scoped to the back stack entry, which survives that round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    val detail = (uiState as? PebbleDetailUiState.Content)?.detail
    val palette = detail?.let { palettes.palette(it.emotion.id) }

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

    PebbleDetailContent(
        uiState = uiState,
        palette = palette,
        onEdit = onEditRequested,
        onShare = onShare,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * One pebble without its ViewModel (#940): the top bar and the three states.
 * [PebbleDetailScreen] wires it; screenshots drive it.
 *
 * It draws no page background, so it takes the sheet's container colour on a
 * phone and `surface` in the pane. The read page uses theme roles; only the
 * pebble visual carries the emotion palette (#940, maintainer decision), which
 * is all [palette] is passed down for. It is null while loading or on a
 * palette-cache miss.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebbleDetailContent(
    uiState: PebbleDetailUiState,
    palette: EmotionPalette?,
    onEdit: () -> Unit,
    onShare: (() -> Unit)?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = (uiState as? PebbleDetailUiState.Content)?.detail

    Column(
        modifier
            .fillMaxSize()
            .readableWidth(),
    ) {
        DetailTopBar(
            visibility = detail?.visibility,
            editEnabled = detail != null,
            onEdit = onEdit,
            onShare = onShare,
        )
        // Exhaustive with no `else`: a new PebbleDetailUiState case must render.
        when (val state = uiState) {
            PebbleDetailUiState.Loading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingIndicator()
                }
            PebbleDetailUiState.Error ->
                DetailLoadError(onRetry = onRetry)
            is PebbleDetailUiState.Content ->
                PebbleReadView(
                    detail = state.detail,
                    palette = palette,
                    // Clear of the gesture bar in the sheet and in the pane
                    // alike (#940).
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                )
        }
    }
}

/**
 * Detail top bar: an optional privacy badge once the pebble is loaded, an
 * optional share button (public pebbles only, M51), and a trailing Edit button
 * (enabled only after load). No back arrow (#940): on a phone it is a sheet,
 * dismissed by drag, scrim or system back; on a large screen Path is beside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    visibility: Visibility?,
    editEnabled: Boolean,
    onEdit: () -> Unit,
    onShare: (() -> Unit)?,
) {
    // A stock top app bar (#854) on a clear container, so it sits on whatever
    // hosts the page (the sheet's container, or surface in the pane) with no seam.
    TopAppBar(
        title = { if (visibility != null) PebblePrivacyBadge(visibility = visibility) },
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
