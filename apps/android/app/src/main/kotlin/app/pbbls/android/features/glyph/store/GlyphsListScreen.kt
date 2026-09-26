package app.pbbls.android.features.glyph.store

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesScreen
import app.pbbls.android.core.designsystem.PebblesTopBar
import app.pbbls.android.core.designsystem.ProfileEmptyState
import app.pbbls.android.core.designsystem.isWideWindow
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase

private const val TAG = "glyphs-store"

/**
 * The glyph store — ports iOS `GlyphsListView` as a pushed NavHost route from
 * the Profile Glyphs tile (M43 D3, reversing the M41 D11 omission): Mine /
 * Owned / Commu tabs (per-tab cache renders stale during refetch; error state
 * only over an empty cache), an adaptive glyph grid, "+" → the carve studio
 * as a cover, Mine-cell rename (own glyphs only — system glyphs are inert per
 * D7), Owned/Commu cells → [onOpenGlyph], the glyph detail entry (#940). A
 * swap drops the item from Commu and invalidates Owned: the view model hears
 * of it from the market service, whichever host ran the buy.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GlyphsListScreen(
    onBack: () -> Unit,
    onCarve: () -> Unit,
    onOpenGlyph: (GlyphGridItem) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * True as the list pane of a list-detail scene (#940): the grid is
     * pane-wide there, so the toolbar goes back to the bottom.
     */
    isInListPane: Boolean = false,
    viewModel: GlyphsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()

    // Returning from the carve studio must re-read the current tab: the
    // ViewModel is scoped to the back stack entry, which survives the round
    // trip that used to be a callback-driven optimistic prepend.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResumed()
        onPauseOrDispose {}
    }

    GlyphsListContent(
        uiState = uiState,
        didRenameFail = covers.didRenameFail,
        toolbarOnEndEdge = isWideWindow() && !isInListPane,
        onBack = onBack,
        onCarve = onCarve,
        onSelectTab = viewModel::onSelectTab,
        onOpenGlyph = onOpenGlyph,
        onRename = viewModel::requestRename,
        modifier = modifier,
    )

    covers.renaming?.let { glyph ->
        RenameGlyphDialog(
            initialName = glyph.name.orEmpty(),
            onDismiss = viewModel::cancelRename,
            onSave = viewModel::confirmRename,
        )
    }
}

/**
 * The store without its ViewModel (#940): the top bar, the tab toolbar and the
 * three states. [GlyphsListScreen] wires it; the list-detail screenshots
 * drive it in its pane layout.
 *
 * [toolbarOnEndEdge] puts the tabs on the window's end edge, opposite the
 * rail — a wide window with the store alone in it. As a list pane the grid is
 * pane-wide, so the tabs go back to the bottom. The grid's padding follows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GlyphsListContent(
    uiState: GlyphsUiState,
    didRenameFail: Boolean,
    toolbarOnEndEdge: Boolean,
    onBack: () -> Unit,
    onCarve: () -> Unit,
    onSelectTab: (GlyphTab) -> Unit,
    onOpenGlyph: (GlyphGridItem) -> Unit,
    onRename: (Glyph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.glyphs_title),
                leading = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.profile_back_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                trailing = {
                    IconButton(onClick = onCarve) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.glyphs_carve_a11y),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
        // The tabs float over the window, not the readable column (#855): a
        // bar across the bottom on phones, a vertical toolbar on the end edge
        // of a tablet, opposite the navigation rail.
        overlay = {
            GlyphTabBar(
                selection = uiState.tab,
                onSelect = onSelectTab,
                vertical = toolbarOnEndEdge,
                modifier = Modifier.align(if (toolbarOnEndEdge) Alignment.CenterEnd else Alignment.BottomCenter),
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Exhaustive with no `else`: a new GlyphsUiState case must be rendered.
            when (val state = uiState) {
                is GlyphsUiState.Loading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        LoadingIndicator()
                    }

                is GlyphsUiState.Error ->
                    ProfileEmptyState(
                        title = stringResource(state.messageRes),
                        message = stringResource(R.string.glyphs_load_error_hint),
                    )

                is GlyphsUiState.Content ->
                    if (state.items.isEmpty()) {
                        ProfileEmptyState(
                            title = stringResource(state.tab.emptyTitleRes),
                            message = stringResource(state.tab.emptyMessageRes),
                        )
                    } else {
                        Column(Modifier.fillMaxSize()) {
                            if (didRenameFail) {
                                Text(
                                    text = stringResource(R.string.glyph_rename_error),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.error,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(96.dp),
                                modifier = Modifier.fillMaxSize(),
                                // Clear of the tab toolbar: its height at the bottom
                                // on phones, its width at the end edge on tablets.
                                contentPadding =
                                    if (toolbarOnEndEdge) {
                                        PaddingValues(start = 16.dp, end = 88.dp, top = 16.dp, bottom = 16.dp)
                                    } else {
                                        PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
                                    },
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.items, key = { it.id }) { item ->
                                    GlyphStoreCell(
                                        item = item,
                                        onTap =
                                            when {
                                                state.tab != GlyphTab.MINE -> ({ onOpenGlyph(item) })
                                                item.glyph.userId != null -> ({ onRename(item.glyph) })
                                                else -> null
                                            },
                                    )
                                }
                            }
                        }
                    }
            }
        }
    }
}

private val GlyphTab.emptyTitleRes: Int
    get() =
        when (this) {
            GlyphTab.MINE -> R.string.glyphs_empty_mine_title
            GlyphTab.OWNED -> R.string.glyphs_empty_owned_title
            GlyphTab.COMMU -> R.string.glyphs_empty_commu_title
        }

private val GlyphTab.emptyMessageRes: Int
    get() =
        when (this) {
            GlyphTab.MINE -> R.string.glyphs_empty_mine_message
            GlyphTab.OWNED -> R.string.glyphs_empty_owned_message
            GlyphTab.COMMU -> R.string.glyphs_empty_commu_message
        }

/** Whichever tab the store is on, loading, failed or not — the bar must not lie. */
private val GlyphsUiState.tab: GlyphTab
    get() =
        when (this) {
            is GlyphsUiState.Content -> tab
            is GlyphsUiState.Error -> tab
            is GlyphsUiState.Loading -> tab
        }

/** Grid cell: 96dp glyph + optional name caption + price badge when listed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GlyphStoreCell(
    item: GlyphGridItem,
    onTap: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .width(96.dp)
                .clip(MaterialTheme.shapes.medium)
                .let { base -> if (onTap != null) base.clickable(onClick = onTap) else base },
    ) {
        GlyphView(
            case = GlyphViewCase.DEFAULT,
            strokes = item.glyph.strokes,
            viewBox = item.glyph.viewBox,
            side = 96.dp,
        )
        item.glyph.name?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (item.price > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text = item.price.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Rename alert — the iOS "Rename glyph" TextField alert on the M39 dialog chrome. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RenameGlyphDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.glyph_rename_title),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = colors.onSurface,
            )
        },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                label = { Text(stringResource(R.string.carve_name_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                )
            }
        },
    )
}
