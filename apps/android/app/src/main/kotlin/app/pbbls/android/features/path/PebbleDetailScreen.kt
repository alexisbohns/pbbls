package app.pbbls.android.features.path

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.models.PebbleDetail
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.features.path.read.PebblePrivacyBadge
import app.pbbls.android.features.path.read.PebbleReadView
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPebbleDetailService
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Full-screen pebble detail surface — ports iOS `PebbleDetailSheet` (D5/D7).
 * Composed OVER `PathScreen` (the `fullScreenCover` analog) and self-applies
 * `safeDrawingPadding()`, so it must live in `PathScreen`'s OUTER (unpadded) Box
 * or the inset doubles. Loads a [PebbleDetail] via [LocalPebbleDetailService],
 * owns its loading/error/retry state, hosts the top bar + system-back
 * ([BackHandler]), and delegates the body to the pure [PebbleReadView].
 *
 * [onEditRequested] is a stub in B (Edit button is present but inert); D swaps
 * in the edit surface.
 */
@Composable
fun PebbleDetailScreen(
    pebbleId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onEditRequested: () -> Unit = {},
) {
    val detailService = LocalPebbleDetailService.current
    val palettes = LocalEmotionPaletteService.current
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    var detail by remember(pebbleId) { mutableStateOf<PebbleDetail?>(null) }
    var isLoading by remember(pebbleId) { mutableStateOf(true) }
    var loadError by remember(pebbleId) { mutableStateOf(false) }
    var reloadToken by remember(pebbleId) { mutableIntStateOf(0) }

    BackHandler { onDismiss() }

    // Re-runs on retry (reloadToken++). isLoading gates the spinner; loadError
    // gates the error view. The caller (PathScreen) closes this cover when the
    // pebble is deleted, so a load against a stale id never renders.
    LaunchedEffect(pebbleId, reloadToken) {
        isLoading = true
        loadError = false
        try {
            detail = detailService.load(pebbleId)
        } catch (e: Exception) {
            Log.e(TAG, "pebble detail load failed", e)
            loadError = true
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(system.background)
            // Swallow all pointer input so this cover is input-opaque like the
            // iOS fullScreenCover (D5) — without it, taps over the loading/error
            // states fall through to the PathScreen rows' combinedClickable.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .safeDrawingPadding(),
    ) {
        DetailTopBar(
            visibility = detail?.visibility,
            editEnabled = detail != null,
            onBack = onDismiss,
            onEdit = onEditRequested,
        )
        val loaded = detail
        when {
            isLoading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = accent.primary)
                }
            loadError || loaded == null ->
                DetailLoadError(onRetry = { reloadToken++ })
            else ->
                PebbleReadView(
                    detail = loaded,
                    palette = palettes.palette(loaded.emotion.id),
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}

/**
 * Detail top bar: leading system-back arrow, an optional privacy badge once the
 * pebble is loaded, and a trailing Edit button (enabled only after load; inert
 * in B). iOS relies on swipe-to-dismiss; Android adds the explicit back arrow +
 * [BackHandler] for discoverability (D5, documented divergence).
 */
@Composable
private fun DetailTopBar(
    visibility: Visibility?,
    editEnabled: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.pebble_detail_back_a11y),
                tint = system.secondary,
            )
        }
        if (visibility != null) {
            PebblePrivacyBadge(visibility = visibility)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onEdit, enabled = editEnabled) {
            PebblesText(
                stringResource(R.string.pebble_detail_edit),
                PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
    }
}

/** Centered error view with a Retry action — mirrors `PebbleDetailSheet.content`'s error branch. */
@Composable
private fun DetailLoadError(onRetry: () -> Unit) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PebblesText(
            stringResource(R.string.pebble_detail_load_error),
            PebblesTypography.body,
            color = system.secondary,
        )
        TextButton(onClick = onRetry) {
            PebblesText(
                stringResource(R.string.pebble_detail_retry),
                PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
    }
}

private const val TAG = "pebble-detail"
