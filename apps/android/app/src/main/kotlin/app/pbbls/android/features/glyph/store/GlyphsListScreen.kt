package app.pbbls.android.features.glyph.store

import android.util.Log
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.PebblesTextInput
import app.pbbls.android.features.glyph.carve.GlyphCarveScreen
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.services.GlyphService
import app.pbbls.android.features.glyph.services.LocalGlyphMarketService
import app.pbbls.android.features.glyph.services.LocalGlyphService
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.services.LocalPathStatsService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

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
    modifier: Modifier = Modifier,
) {
    val market = LocalGlyphMarketService.current
    val glyphService = LocalGlyphService.current
    val stats = LocalPathStatsService.current
    val scope = rememberCoroutineScope()
    val system = PebblesTheme.colors.system

    var tab by remember { mutableStateOf(GlyphTab.MINE) }
    val itemsByTab = remember { mutableStateMapOf<GlyphTab, List<GlyphGridItem>>() }
    var isLoading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Glyph?>(null) }
    var renameError by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<GlyphGridItem?>(null) }
    var isPresentingCarve by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { stats.load() }
    LaunchedEffect(tab) {
        isLoading = true
        loadFailed = false
        try {
            itemsByTab[tab] =
                when (tab) {
                    GlyphTab.MINE -> market.listMine()
                    GlyphTab.OWNED -> market.listOwned()
                    GlyphTab.COMMU -> market.listCommunity()
                }
        } catch (e: Exception) {
            Log.e(TAG, "glyph tab load failed: $tab", e)
            // Stale cache keeps rendering; the error state needs an empty tab.
            if (itemsByTab[tab].isNullOrEmpty()) loadFailed = true
        } finally {
            isLoading = false
        }
    }

    val items = itemsByTab[tab].orEmpty()

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
                    IconButton(onClick = { isPresentingCarve = true }) {
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
            when {
                isLoading && items.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                    }

                loadFailed ->
                    ProfileEmptyState(
                        title = stringResource(R.string.glyphs_load_error),
                        message = stringResource(R.string.glyphs_load_error_hint),
                    )

                items.isEmpty() ->
                    ProfileEmptyState(
                        title = stringResource(tab.emptyTitleRes),
                        message = stringResource(tab.emptyMessageRes),
                    )

                else ->
                    Column(Modifier.fillMaxSize()) {
                        if (renameError) {
                            PebblesText(
                                text = stringResource(R.string.glyph_rename_error),
                                style = PebblesTypography.callout,
                                color = PebblesDestructive,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(96.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(items, key = { it.id }) { item ->
                                GlyphStoreCell(
                                    item = item,
                                    onTap =
                                        when {
                                            tab != GlyphTab.MINE -> ({ selected = item })
                                            item.glyph.userId != null -> ({
                                                renameError = false
                                                renaming = item.glyph
                                            })
                                            else -> null
                                        },
                                )
                            }
                        }
                    }
            }
            GlyphTabBar(
                selection = tab,
                onSelect = { tab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (isPresentingCarve) {
        GlyphCarveScreen(
            onSaved = { glyph ->
                isPresentingCarve = false
                val fresh = GlyphGridItem(glyph = glyph, price = 0, owned = false, createdAt = null, acquiredAt = null)
                itemsByTab[GlyphTab.MINE] = listOf(fresh) + itemsByTab[GlyphTab.MINE].orEmpty()
                tab = GlyphTab.MINE
            },
            onCancel = { isPresentingCarve = false },
            modifier = Modifier.fillMaxSize(),
        )
    }

    renaming?.let { glyph ->
        RenameGlyphDialog(
            initialName = glyph.name.orEmpty(),
            onDismiss = { renaming = null },
            onSave = { draft ->
                renaming = null
                val original = itemsByTab[GlyphTab.MINE].orEmpty()
                val optimistic = glyph.copy(name = GlyphService.normalizedName(draft))
                itemsByTab[GlyphTab.MINE] =
                    original.map { if (it.id == glyph.id) it.copy(glyph = optimistic) else it }
                scope.launch {
                    try {
                        val server = glyphService.updateName(glyphId = glyph.id, name = draft)
                        itemsByTab[GlyphTab.MINE] =
                            itemsByTab[GlyphTab.MINE].orEmpty().map {
                                if (it.id == glyph.id) it.copy(glyph = server) else it
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "glyph rename failed", e)
                        itemsByTab[GlyphTab.MINE] = original
                        renameError = true
                    }
                }
            },
        )
    }

    selected?.let { item ->
        GlyphDetailDrawer(
            item = item,
            balance = stats.karma ?: 0,
            onSwapped = { result ->
                stats.applyKarmaBalance(result.balance)
                itemsByTab[GlyphTab.COMMU] = itemsByTab[GlyphTab.COMMU].orEmpty().filter { it.id != item.id }
                // Owned refetches lazily on its next visit (iOS parity).
                itemsByTab.remove(GlyphTab.OWNED)
            },
            onDismiss = { selected = null },
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
