package app.pbbls.android.features.path

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
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
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.Domain
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.SharedPebbleLink
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.core.ui.MomentDeleteDialog
import app.pbbls.android.features.path.card.BareMomentPage
import app.pbbls.android.features.path.card.CardCompletion
import app.pbbls.android.features.path.card.CardCompletionState
import app.pbbls.android.features.path.card.PebbleCard
import kotlinx.coroutines.delay
import java.time.ZoneId
import kotlin.math.min

/**
 * The moment read page (#852, ports iOS `PebbleDetailSheet`, D7): a page on
 * phones (#1135) and a pane beside Path on large screens (#940). The scene
 * decides which. Loads a `MomentDetail` through [PebbleDetailViewModel], owns
 * its loading/error/retry state, hosts the top bar, and shows a pebble as the
 * held [PebbleCard]; a bare moment as the [BareMomentPage].
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
 * A bare moment offers "Make it a pebble" in the place its card will print into
 * ([onCompleteRequested], A-8). The resume refresh brings the pebble back already
 * [PebbleDetailUiState.Content.completing], and the card prints into that place
 * ([CardCompletion], pebble card spec §Completion): the page renders the print from the
 * very frame the pebble arrives in, plays it once, and reports it shown
 * ([PebbleDetailViewModel.onCompletionShown]). The card lands verso up, scrolled to its
 * top, and stays verso up until the key asks for another face. Interrupted:
 * - back leaves with the entry, and the print with it;
 * - a configuration change mid-print lands at rest (the print that was cut short is
 *   saved, so the new composition does not replay it), verso up;
 * - the place not whole on screen: the page scrolls it into view, then the card prints;
 *   a place taller than the screen, or reduced motion: a crossfade instead.
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

    // The print (spec §Completion). The face asked for when a print began: the card stays
    // verso up until the key asks again (another face, or the same one pressed anew). Saved,
    // as is the print under way, so a recreation mid-print lands at rest rather than
    // printing twice.
    val completing = (uiState as? PebbleDetailUiState.Content)?.completing == true
    val reduceMotion = rememberReduceMotion()
    var printedOver by rememberSaveable { mutableStateOf<CardFace?>(null) }
    var printedRequest by rememberSaveable { mutableIntStateOf(0) }
    var printingFor by rememberSaveable { mutableStateOf<String?>(null) }
    var cutShort by remember { mutableStateOf(printingFor) }
    // The verso heading's live region, from the print's start until just after it lands:
    // the arrival is said once, and later changes to the heading (an edit) are not.
    var announcing by remember { mutableStateOf(false) }
    val currentFace by rememberUpdatedState(requestedFace)
    val currentRequest by rememberUpdatedState(faceRequest)
    val printing = completing && detail != null && cutShort != detail.id
    val completion = remember(detail?.id, printing) { if (printing) CardCompletionState() else null }
    LaunchedEffect(completion, completing) {
        when {
            completion != null -> {
                printedOver = currentFace
                printedRequest = currentRequest
                printingFor = detail?.id
                announcing = true
                completion.play(reduceMotion)
                // Cleared only where the print ends in this composition: here, or below when
                // the state stops completing (a pebble deleted mid-print, another moment
                // opened). Never on cancellation: a disposed screen has already saved this
                // very state object (a recreation, Nav3's saveable holder), and a write from a
                // cancelled coroutine would reach the saved copy and replay the print.
                printingFor = null
                viewModel.onCompletionShown()
            }
            completing -> {
                printingFor = null
                viewModel.onCompletionShown()
            }
            else -> {
                cutShort = null
                printingFor = null
            }
        }
    }
    LaunchedEffect(announcing, completing) {
        if (announcing && !completing) {
            delay(ANNOUNCE_HOLD_MS)
            announcing = false
        }
    }
    val askedSincePrint = printedOver != requestedFace || printedRequest != faceRequest
    LaunchedEffect(requestedFace, faceRequest) {
        if (printedOver != null && askedSincePrint) printedOver = null
    }
    val face = if (completing || (printedOver != null && !askedSincePrint)) CardFace.VERSO else requestedFace

    // The cover photo: signed once per prefix, as the Spine's cards do, and its
    // ratio from the same process-wide probe. Keyed on the prefix, so an edit
    // that swaps the photo never shows the last one while the new one signs.
    // The bare page mats the same photo.
    val prefix =
        detail
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
        requestedFace = face,
        faceRequest = faceRequest,
        completion = completion,
        printed = announcing,
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
 * pebble shows as the held [PebbleCard] and a bare moment as the
 * [BareMomentPage]; both cap their own width, so the page column is not
 * `readableWidth`.
 * [palette] is the emotion's, null while loading or on a palette-cache miss.
 *
 * @param photo the cover's signed original and thumb; null until signed, or with no photo.
 * @param ratio the cover's width / height; null until probed.
 * @param soulsById the souls cache, for the recto's marks.
 * @param domains the reference domains, for the verso's postmarks.
 * @param showBack False beside Path on a large screen (#940).
 * @param requestedFace the face the held card opens on, and turns to when it changes.
 * @param faceRequest which press asked for [requestedFace]; a new one is a new request.
 * @param completion the print under way while the state is
 *   [PebbleDetailUiState.Content.completing]: the bare page stays up under the card printing
 *   into its place ([CardCompletion]). Null, a completing state shows the card at rest.
 * @param printed the card has just printed on this page: its arrival is announced, once.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PebbleDetailContent(
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
    /** "Make it a pebble", a bare moment's one call to action. */
    onComplete: () -> Unit = {},
    /** Opens the ⋮ menu on first composition; for previews of the open menu only. */
    initialMenuOpen: Boolean = false,
    /** Where the card's (or the bare page's) day, time and dateline fall; previews pin it. */
    zone: ZoneId = ZoneId.systemDefault(),
    requestedFace: CardFace = CardFace.RECTO,
    faceRequest: Int = 0,
    completion: CardCompletionState? = null,
    printed: Boolean = false,
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
            is PebbleDetailUiState.Content -> {
                val hasPebble = state.detail.pebble != null
                val print = completion?.takeIf { state.completing && hasPebble }
                // Two layers in one host, so neither remounts when a print starts or ends: the
                // bare page while there is no pebble or one is printing into it, the card once
                // there is a pebble. Each scrolls on its own: the card's opens at its top.
                CardCompletion(
                    completion = print,
                    page =
                        if (!hasPebble || print != null) {
                            { placeModifier, ghostModifier ->
                                PageColumn {
                                    BareMomentPage(
                                        detail = state.detail,
                                        photoUrl = photo?.first,
                                        thumbUrl = photo?.second,
                                        ratio = ratio,
                                        // Mid-print the page is under the card: nothing on it acts.
                                        onComplete = if (print != null) ({}) else onComplete,
                                        zone = zone,
                                        modifier = Modifier.padding(bottom = 24.dp),
                                        placeModifier = placeModifier,
                                        ghostModifier = ghostModifier,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    card =
                        if (hasPebble) {
                            { cardModifier ->
                                PageColumn {
                                    PebbleCard(
                                        detail = state.detail,
                                        palette = palette,
                                        ratio = ratio,
                                        photoUrl = photo?.first,
                                        thumbUrl = photo?.second,
                                        soulsById = soulsById,
                                        domains = domains,
                                        requestedFace = if (print != null) CardFace.VERSO else requestedFace,
                                        faceRequest = faceRequest,
                                        zone = zone,
                                        completion = print,
                                        printed = printed,
                                        modifier = cardModifier,
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** One layer of the moment: the page's scroll, its foot clear of the navigation bar. */
@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberHeldScrollState())
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(vertical = 8.dp),
        content = content,
    )
}

/**
 * A layer's scroll that comes back where the reader left it. A plain [rememberScrollState]
 * saves its value, and the value is clamped to the content's reach whenever the reach
 * shrinks: an entry pushed without the bar (Edit moment, Make it a pebble) snaps the bar
 * away while this page is still laid out under the transition, so its viewport grows by
 * the bar, a page read to its foot is pulled up by as much, and that is what is saved.
 * Back on the page the bar returns and the page sits a bar's height higher than it was
 * left: at its top when it was scarcely taller than the screen, and with a bare moment's
 * place half under the bar when the card prints into it.
 *
 * So the reader's position is held apart from the value, and is what is saved. A clamp
 * that comes with a grown viewport (the bar going, or the first layout of a restored page,
 * grown from nothing) pins the value and leaves the position as it was; while pinned, a
 * reach growing back (the bar returning, a restored page laid out short for a frame)
 * returns the value toward the position, frame by frame as the transition resizes the
 * page, until it is reached. Any other change takes the position: a scroll, and a
 * clamp by content that really got shorter (text edited away, a photo removed), so a later
 * longer page never moves the reader without a touch.
 */
@Composable
internal fun rememberHeldScrollState(): ScrollState {
    var held by rememberSaveable { mutableIntStateOf(0) }
    val scroll = remember { ScrollState(Snapshot.withoutReadObservation { held }) }
    LaunchedEffect(scroll) {
        var pinnedAt = NOT_PINNED
        var lastViewport = 0
        snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.viewportSize) }.collect { (value, reach, viewport) ->
            val viewportGrew = viewport > lastViewport
            lastViewport = viewport
            when {
                // Pinned and untouched: follow the reach back toward the position. Pinned still
                // until it is reached, as the viewport shrinks back over a transition's frames.
                pinnedAt != NOT_PINNED && value == pinnedAt -> {
                    if (reach > value) {
                        val to = min(held, reach)
                        scroll.dispatchRawDelta((to - value).toFloat())
                        pinnedAt = if (to < held) to else NOT_PINNED
                    }
                }
                value == reach && value < held && viewportGrew -> pinnedAt = value
                else -> {
                    pinnedAt = NOT_PINNED
                    held = value
                }
            }
        }
    }
    return scroll
}

private const val NOT_PINNED = -1

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

/** How long the verso heading stays a live region after a print lands: long enough for its one announcement. */
private const val ANNOUNCE_HOLD_MS = 1_000L
