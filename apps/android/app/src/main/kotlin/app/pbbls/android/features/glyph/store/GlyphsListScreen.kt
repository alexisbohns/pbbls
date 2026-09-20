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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.components.PebblesTextInput
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography

private const val TAG = "glyphs-store"

/**
 * The glyph store — ports iOS `GlyphsListView` as a pushed NavHost route from
 * the Profile Glyphs tile (M43 D3, reversing the M41 D11 omission): Mine /
 * Owned / Commu tabs (per-tab cache renders stale during refetch; error state
 * only over an empty cache), an adaptive glyph grid, "+" → the carve studio
 * as a cover, Mine-cell rename (own glyphs only — system glyphs are inert per
 * D7), Owned/Commu cells → [GlyphDetailDrawer]. A swap applies the returned
 * balance to the shared stats, drops the item from Commu, and invalidates
 * Owned so it refetches lazily.
 */
@Composable
fun GlyphsListScreen(
    onBack: () -> Unit,
    onCarve: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GlyphsListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val covers by viewModel.covers.collectAsStateWithLifecycle()
    val system = PebblesTheme.colors.system

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
                            tint = system.secondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                trailing = {
                    IconButton(onClick = onCarve) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.glyphs_carve_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) {
        Box(Modifier.fillMaxSize()) {
            // Exhaustive with no `else`: a new GlyphsUiState case must be rendered.
            when (val state = uiState) {
                GlyphsUiState.Loading ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
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
                            if (covers.didRenameFail) {
                                PebblesText(
                                    text = stringResource(R.string.glyph_rename_error),
                                    style = PebblesTypography.callout,
                                    color = PebblesDestructive,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(96.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding =
                                    PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(state.items, key = { it.id }) { item ->
                                    GlyphStoreCell(
                                        item = item,
                                        onTap =
                                            when {
                                                state.tab != GlyphTab.MINE -> ({ viewModel.openDetail(item) })
                                                item.glyph.userId != null -> ({ viewModel.requestRename(item.glyph) })
                                                else -> null
                                            },
                                    )
                                }
                            }
                        }
                    }
            }
            GlyphTabBar(
                selection = uiState.tab,
                onSelect = viewModel::onSelectTab,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    covers.renaming?.let { glyph ->
        RenameGlyphDialog(
            initialName = glyph.name.orEmpty(),
            onDismiss = viewModel::cancelRename,
            onSave = viewModel::confirmRename,
        )
    }

    covers.selected?.let { item ->
        GlyphDetailDrawer(
            item = item,
            balance = (uiState as? GlyphsUiState.Content)?.karma ?: 0,
            onRecorded = { result -> viewModel.onPurchased(item, result) },
            onDismiss = viewModel::closeDetail,
        )
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

/** Whichever tab the store is on, failed or not — the bar must not lie. */
private val GlyphsUiState.tab: GlyphTab
    get() =
        when (this) {
            is GlyphsUiState.Content -> tab
            is GlyphsUiState.Error -> tab
            GlyphsUiState.Loading -> GlyphTab.MINE
        }

/** Grid cell: 96dp glyph + optional name caption + price badge when listed. */
@Composable
private fun GlyphStoreCell(
    item: GlyphGridItem,
    onTap: (() -> Unit)?,
) {
    val system = PebblesTheme.colors.system
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier =
            Modifier
                .width(96.dp)
                .clip(RoundedCornerShape(12.dp))
                .let { base -> if (onTap != null) base.clickable(onClick = onTap) else base },
    ) {
        GlyphView(
            case = GlyphViewCase.DEFAULT,
            strokes = item.glyph.strokes,
            viewBox = item.glyph.viewBox,
            side = 96.dp,
        )
        item.glyph.name?.let { name ->
            PebblesText(
                text = name,
                style = PebblesTypography.captionEmphasized,
                color = system.secondary,
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
                    tint = system.muted,
                    modifier = Modifier.size(11.dp),
                )
                PebblesText(
                    text = item.price.toString(),
                    style = PebblesTypography.meta,
                    color = system.muted,
                )
            }
        }
    }
}

/** Rename alert — the iOS "Rename glyph" TextField alert on the M39 dialog chrome. */
@Composable
private fun RenameGlyphDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    var draft by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = system.background,
        title = {
            PebblesText(
                text = stringResource(R.string.glyph_rename_title),
                style = PebblesTypography.headlineEmphasized,
                color = system.foreground,
            )
        },
        text = {
            PebblesTextInput(
                placeholder = stringResource(R.string.carve_name_placeholder),
                value = draft,
                onValueChange = { draft = it },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) {
                PebblesText(
                    text = stringResource(R.string.action_save),
                    style = PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                PebblesText(
                    text = stringResource(R.string.action_cancel),
                    style = PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        },
    )
}
