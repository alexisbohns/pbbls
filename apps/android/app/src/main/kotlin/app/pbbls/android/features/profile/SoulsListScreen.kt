package app.pbbls.android.features.profile

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.features.shared.SoulItem
import app.pbbls.android.features.shared.SoulItemCase
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSoulsService
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "souls-list"

/**
 * The souls grid — ports iOS `SoulsListView.swift` as a NavHost push (D1):
 * adaptive-96 grid of shared [SoulItem] cells, "+" top-bar create, tap → the
 * detail route, long-press → delete menu + confirm (D7 unifies on the M39 D8
 * idiom over iOS's context menu). The screen fetches its own rows (like iOS)
 * and refreshes the reference-data souls cache after every mutation so the
 * pebble-form picker stays in sync.
 */
@Composable
fun SoulsListScreen(
    onBack: () -> Unit,
    onOpenSoul: (SoulWithGlyph) -> Unit,
    modifier: Modifier = Modifier,
) {
    val soulsService = LocalSoulsService.current
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    val system = PebblesTheme.colors.system

    var items by remember { mutableStateOf<List<SoulWithGlyph>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var loadKey by remember { mutableIntStateOf(0) }
    var isPresentingCreate by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<SoulWithGlyph?>(null) }
    var deleteError by remember { mutableStateOf(false) }

    LaunchedEffect(loadKey) {
        isLoading = true
        loadFailed = false
        try {
            items = soulsService.list()
        } catch (e: Exception) {
            Log.e(TAG, "souls fetch failed", e)
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = stringResource(R.string.souls_title),
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
                    IconButton(onClick = { isPresentingCreate = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_plus),
                            contentDescription = stringResource(R.string.souls_add_a11y),
                            tint = system.secondary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
        },
    ) {
        when {
            isLoading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = PebblesTheme.colors.accent.primary)
                }

            loadFailed ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PebblesText(
                        text = stringResource(R.string.souls_load_error),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                    TextButton(onClick = { loadKey++ }) {
                        PebblesText(
                            text = stringResource(R.string.profile_retry),
                            style = PebblesTypography.buttonLabel,
                            color = PebblesTheme.colors.accent.primary,
                        )
                    }
                }

            items.isEmpty() ->
                ProfileEmptyState(
                    title = stringResource(R.string.souls_empty_title),
                    message = stringResource(R.string.souls_empty_message),
                )

            else ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(PebblesTheme.spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
                ) {
                    items(items, key = { it.id }) { soul ->
                        SoulCell(
                            soul = soul,
                            onTap = { onOpenSoul(soul) },
                            onDelete = { pendingDeletion = soul },
                        )
                    }
                }
        }
    }

    if (isPresentingCreate) {
        SoulFormScreen(
            original = null,
            onDismiss = { isPresentingCreate = false },
            onSaved = {
                isPresentingCreate = false
                loadKey++
                scope.launch { refs.refreshSouls() }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    val target = pendingDeletion
    if (target != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.pebble_delete_confirm_title, target.name),
            message = stringResource(R.string.souls_delete_message),
            onConfirm = {
                pendingDeletion = null
                scope.launch {
                    try {
                        soulsService.delete(target.id)
                        loadKey++
                        refs.refreshSouls()
                    } catch (e: Exception) {
                        Log.e(TAG, "delete soul failed", e)
                        deleteError = true
                    }
                }
            },
            onDismiss = { pendingDeletion = null },
        )
    }
    if (deleteError) DeleteErrorDialog(onDismiss = { deleteError = false })
}

/** Grid cell wrapper anchoring the long-press delete menu — the PebbleRow menu idiom. */
@Composable
private fun SoulCell(
    soul: SoulWithGlyph,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        SoulItem(
            case = SoulItemCase.DEFAULT,
            soul = soul,
            count = soul.pebblesCount,
            onTap = onTap,
            onLongPress = { menuExpanded = true },
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    PebblesText(
                        text = stringResource(R.string.pebble_delete),
                        style = PebblesTypography.buttonLabel,
                        color = PebblesDestructive,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_trash),
                        contentDescription = null,
                        tint = PebblesDestructive,
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
            )
        }
    }
}
