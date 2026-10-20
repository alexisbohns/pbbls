package app.pbbls.android.features.path

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.common.ObserveUiEffects
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.LocalEmotionPaletteService
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.data.LocalSnapURLCache
import app.pbbls.android.core.designsystem.DeleteErrorDialog
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.SharedPebbleLink
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.core.ui.MomentDeleteDialog
import app.pbbls.android.features.path.card.PebbleCard
import app.pbbls.android.features.path.read.PebbleReadView
import java.time.ZoneId

/**
 * The moment read page (#852, ports iOS `PebbleDetailSheet`, D7): a page on
 * phones (#1135) and a pane beside Path on large screens (#940). The scene
 * decides which. Loads a `MomentDetail` through [PebbleDetailViewModel], owns
 * its loading/error/retry state, hosts the top bar, and shows a pebble as the
 * held [PebbleCard]; a bare moment still renders the pure [PebbleReadView].
 * System back is `NavDisplay`'s own — its one write, the delete, runs
 * uncancellable in the ViewModel, so it needs no back handler.
 *
 * The card's photo is signed through the ambient `LocalSnapURLCache` and its
 * ratio read from the Spine's own probe ([PebbleDetailViewModel.snapAspect]),
 * so a card opened from the Spine finds both already known.
 *
 * The ⋮ menu (R7) opens Edit moment ([onEditMoment]) and, when the moment
 * carries your pebble, Edit pebble ([onEditPebble]); returning from either is
 * picked up by [PebbleDetailViewModel.onResumed] rather than a callback. Its
 * last item, Delete, asks what to delete (R8): the moment closes the page
 * ([onMomentDeleted]), only the pebble leaves it open as a bare moment.
 *
 * A bare moment offers "Make it a pebble" under its title
 * ([onCompleteRequested], A-8); the resume refresh shows the pebble on return.
 *
 * @param requestedFace The face the held card opens on: the stone pressed on the Path opens
 *   the verso, the card the recto. Beside Path the entry stays while the face changes
 *   (one content key per moment), and the card turns to the new one.
 * @param faceRequest Which press asked for [requestedFace]; a new one with the same face is
 *   a new request, which turns back a card turned by hand since.
 * @param showBack False beside Path on a large screen (#940): the list is the
 *   way back, and system back still pops.
 */
@Composable
fun PebbleDetailScreen(
    momentId: String,
    modifier: Modifier = Modifier,
    requestedFace: CardFace = CardFace.RECTO,
    faceRequest: Int = 0,
    onEditMoment: () -> Unit = {},
    onEditPebble: () -> Unit = {},
    onMomentDeleted: () -> Unit = {},
    onCompleteRequested: () -> Unit = {},
    showBack: Boolean = true,
    onBack: () -> Unit = {},
    viewModel: PebbleDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val didDeleteFail by viewModel.didDeleteFail.collectAsStateWithLifecycle()
    val palettes = LocalEmotionPaletteService.current
    val context = LocalContext.current

    LaunchedEffect(momentId) { viewModel.start(momentId) }

    ObserveUiEffects(viewModel.effects) { effect ->
        when (effect) {
            PebbleDetailEffect.MomentDeleted -> onMomentDeleted()
        }
    }

    // Returning from an edit entry must re-read the moment: the ViewModel is
    // scoped to the back stack entry, which survives that round trip.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    val detail = (uiState as? PebbleDetailUiState.Content)?.detail
    val pebble = detail?.pebble
    val palette = pebble?.emotion?.id?.let(palettes::palette)

    // The cover photo: signed once per prefix, as the Spine's cards do, and its
    // ratio from the same process-wide probe. Keyed on the prefix, so an edit
    // that swaps the photo never shows the last one while the new one signs.
    // Only a pebble has a card: the bare moment's read view signs its own.
    val prefix =
        detail
            ?.takeIf { it.pebble != null }
            ?.sortedSnaps
            ?.firstOrNull()
            ?.storagePath
    val urls = LocalSnapURLCache.current
    var photo by remember(prefix) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(prefix, urls) {
        if (photo == null && urls != null && prefix != null) {
            photo =
                runCatchingCancellable { urls.signedUrls(prefix).let { it.original to it.thumb } }
                    .onFailure { Log.e(TAG, "snap sign failed for $prefix", it) }
                    .getOrNull()
        }
    }
    var ratio by remember(prefix) { mutableStateOf(prefix?.let(viewModel.snapAspect::cached)) }
    LaunchedEffect(prefix) {
        if (ratio == null && prefix != null) ratio = viewModel.snapAspect.ratio(prefix)
    }
    val reference = LocalReferenceDataService.current
    val souls = reference.souls
    val soulsById = remember(souls) { souls.associateBy { it.id } }

    // Share is only offered for public pebbles (M51) — anyone with the /p
    // link can open a public pebble, so sharing a secret/connections one
    // would leak it past its intended audience. The link carries the PEBBLE
    // id: since the split a moment id is not its pebble's id (A-9).
    val onShare: (() -> Unit)? =
        if (pebble?.visibility == Visibility.PUBLIC) {
            {
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, SharedPebbleLink.url(pebble.id))
                    }
                context.startActivity(Intent.createChooser(send, null))
            }
        } else {
            null
        }

    PebbleDetailContent(
        uiState = uiState,
        palette = palette,
        onEditMoment = onEditMoment,
        onEditPebble = onEditPebble,
        onDelete = viewModel::requestDelete,
        onShare = onShare,
        onRetry = viewModel::retry,
        photo = photo,
        ratio = ratio,
        soulsById = soulsById,
        domains = reference.domains,
        showBack = showBack,
        onBack = onBack,
        modifier = modifier,
        onComplete = onCompleteRequested,
        requestedFace = requestedFace,
        faceRequest = faceRequest,
    )

    if (pendingDelete && detail != null) {
        MomentDeleteDialog(
            hasPebble = pebble != null,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
    }
    if (didDeleteFail) {
        DeleteErrorDialog(message = stringResource(R.string.moment_delete_error), onDismiss = viewModel::dismissDeleteError)
    }
}

/**
 * One moment without its ViewModel (#940): the top bar and the three states.
 * [PebbleDetailScreen] wires it; screenshots drive it.
 *
 * It is a page on `surface`, as on a phone it is no longer a sheet (#1135). A
 * pebble shows as the held [PebbleCard], which caps its own width, so the page
 * column is not [readableWidth]; a bare moment keeps the read view at it.
 * [palette] is the emotion's, null while loading or on a palette-cache miss.
 *
 * @param photo the cover's signed original and thumb; null until signed, or with no photo.
 * @param ratio the cover's width / height; null until probed.
 * @param soulsById the souls cache, for the recto's marks.
 * @param domains the reference domains, for the verso's postmarks.
 * @param showBack False beside Path on a large screen (#940).
 * @param requestedFace the face the held card opens on, and turns to when it changes.
 * @param faceRequest which press asked for [requestedFace]; a new one is a new request.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebbleDetailContent(
    uiState: PebbleDetailUiState,
    palette: EmotionPalette?,
    onEditMoment: () -> Unit,
    onEditPebble: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)?,
    onRetry: () -> Unit,
    photo: Pair<String, String>?,
    ratio: Float?,
    soulsById: Map<String, SoulWithGlyph>,
    domains: List<Domain>,
    showBack: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** "Make it a pebble" on a bare moment; null offers no call to action. */
    onComplete: (() -> Unit)? = null,
    /** Opens the ⋮ menu on first composition; for previews of the open menu only. */
    initialMenuOpen: Boolean = false,
    /** Where the card's day, time and dateline fall; previews pin it. */
    zone: ZoneId = ZoneId.systemDefault(),
    requestedFace: CardFace = CardFace.RECTO,
    faceRequest: Int = 0,
) {
    val detail = (uiState as? PebbleDetailUiState.Content)?.detail

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        DetailTopBar(
            showBack = showBack,
            onBack = onBack,
            menuEnabled = detail != null,
            hasPebble = detail?.pebble != null,
            onEditMoment = onEditMoment,
            onEditPebble = onEditPebble,
            onDelete = onDelete,
            onShare = onShare,
            initialMenuOpen = initialMenuOpen,
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
                if (state.detail.pebble != null) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            .padding(vertical = 8.dp),
                    ) {
                        PebbleCard(
                            detail = state.detail,
                            palette = palette,
                            ratio = ratio,
                            photoUrl = photo?.first,
                            thumbUrl = photo?.second,
                            soulsById = soulsById,
                            domains = domains,
                            requestedFace = requestedFace,
                            faceRequest = faceRequest,
                            zone = zone,
                        )
                    }
                } else {
                    // Part 3 of #1135 replaces the bare moment's read view.
                    PebbleReadView(
                        detail = state.detail,
                        palette = palette,
                        modifier = Modifier.fillMaxSize().readableWidth(),
                        onComplete = onComplete,
                    )
                }
        }
    }
}

/**
 * Detail top bar: a back arrow when it is a page of its own ([showBack]; beside
 * Path on a large screen the list is the way back, #940), an optional share
 * button (public pebbles only, M51), and a trailing ⋮ menu (enabled only after
 * load) with Edit moment, with a pebble Edit pebble, a divider, and Delete (R7)
 * — no standalone trash button. The title slot is empty: privacy lives in the
 * card's footer (#1135).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailTopBar(
    showBack: Boolean,
    onBack: () -> Unit,
    menuEnabled: Boolean,
    hasPebble: Boolean,
    onEditMoment: () -> Unit,
    onEditPebble: () -> Unit,
    onDelete: () -> Unit,
    onShare: (() -> Unit)?,
    initialMenuOpen: Boolean,
) {
    // A stock top app bar (#854) on a clear container, so it sits on whatever
    // hosts the page (the page's or the pane's surface) with no seam.
    TopAppBar(
        title = {},
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.profile_back_a11y),
                    )
                }
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
            // Keyed on menuEnabled: a retry flipping to Loading closes the menu, and it stays closed after.
            var menuOpen by remember(menuEnabled) { mutableStateOf(initialMenuOpen) }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = menuEnabled) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.more_actions_a11y),
                    )
                }
                DropdownMenu(expanded = menuOpen && menuEnabled, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.edit_moment)) },
                        onClick = {
                            menuOpen = false
                            onEditMoment()
                        },
                    )
                    if (hasPebble) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit_pebble_title)) },
                            onClick = {
                                menuOpen = false
                                onEditPebble()
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
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
