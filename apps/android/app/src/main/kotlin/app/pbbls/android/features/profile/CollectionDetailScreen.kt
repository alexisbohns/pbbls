package app.pbbls.android.features.profile

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.PebbleRow
import app.pbbls.android.features.path.EditPebbleScreen
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.components.CollectionModeBadge
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.services.LocalCollectionsService
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val TAG = "collection-detail"

/**
 * Pushed detail for one collection — ports iOS `CollectionDetailView.swift`:
 * a subheader row (mode badge + live pebble count), the collection's pebbles
 * grouped by calendar month with locale-formatted headers (D14), tap →
 * [EditPebbleScreen] cover, long-press → `delete_pebble` with confirm, and an
 * Edit top-bar action opening [CollectionFormScreen] as a cover (D9 surface
 * swap). The NavHost passes only the collection id, so the screen fetches the
 * collection itself (same named deviation as the soul detail).
 */
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val collectionsService = LocalCollectionsService.current
    val writeService = LocalPebbleWriteService.current
    val palettes = LocalEmotionPaletteService.current
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    val system = PebblesTheme.colors.system

    var collection by remember(collectionId) { mutableStateOf<Collection?>(null) }
    var pebbles by remember(collectionId) { mutableStateOf<List<Pebble>>(emptyList()) }
    var isLoading by remember(collectionId) { mutableStateOf(true) }
    var loadFailed by remember(collectionId) { mutableStateOf(false) }
    var loadKey by remember(collectionId) { mutableIntStateOf(0) }
    var isPresentingEdit by remember(collectionId) { mutableStateOf(false) }
    var editingPebbleId by remember(collectionId) { mutableStateOf<String?>(null) }
    var pendingDeletion by remember(collectionId) { mutableStateOf<Pebble?>(null) }
    var deleteError by remember(collectionId) { mutableStateOf(false) }

    LaunchedEffect(collectionId, loadKey) {
        isLoading = true
        loadFailed = false
        try {
            collection = collectionsService.loadCollection(collectionId)
            pebbles = collectionsService.loadPebbles(collectionId)
        } catch (e: Exception) {
            Log.e(TAG, "collection detail load failed", e)
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    val locale = Locale.getDefault()
    val monthFormatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val groupedPebbles = remember(pebbles) { groupPebblesByMonth(pebbles, ZoneId.systemDefault()) }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = collection?.name.orEmpty(),
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
                    if (collection != null) {
                        PebblesTopBarTextButton(
                            text = stringResource(R.string.pebble_detail_edit),
                            onClick = { isPresentingEdit = true },
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
                        text = stringResource(R.string.soul_detail_load_error),
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

            pebbles.isEmpty() ->
                ProfileEmptyState(
                    title = stringResource(R.string.soul_detail_empty_title),
                    message = stringResource(R.string.collection_detail_empty_message),
                )

            else ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.xl),
                ) {
                    PebblesListSection(
                        rows =
                            listOf(
                                {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        CollectionModeBadge(mode = collection?.mode)
                                        Spacer(Modifier.weight(1f))
                                        PebblesText(
                                            text = pebbleCountLabel(pebbles.size),
                                            style = PebblesTypography.captionEmphasized,
                                            color = system.secondary,
                                        )
                                    }
                                },
                            ),
                    )
                    groupedPebbles.forEach { (month, monthPebbles) ->
                        PebblesListSection(
                            header = month.format(monthFormatter),
                            rows =
                                monthPebbles.map { pebble ->
                                    {
                                        PebbleRow(
                                            pebble = pebble,
                                            palette = pebble.emotion?.let { palettes.palette(it.id) },
                                            onTap = { editingPebbleId = pebble.id },
                                            onDelete = { pendingDeletion = pebble },
                                        )
                                    }
                                },
                        )
                    }
                }
        }
    }

    if (isPresentingEdit) {
        collection?.let { current ->
            CollectionFormScreen(
                original = current,
                onDismiss = { isPresentingEdit = false },
                onSaved = {
                    isPresentingEdit = false
                    loadKey++
                    scope.launch { refs.refreshCollections() }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    editingPebbleId?.let { pebbleId ->
        EditPebbleScreen(
            pebbleId = pebbleId,
            onDismiss = { editingPebbleId = null },
            onSaved = {
                editingPebbleId = null
                loadKey++
            },
            modifier = Modifier.fillMaxSize(),
        )
    }

    val target = pendingDeletion
    if (target != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.pebble_delete_confirm_title, target.name),
            message = stringResource(R.string.pebble_delete_confirm_message),
            onConfirm = {
                pendingDeletion = null
                scope.launch {
                    try {
                        writeService.delete(target.id)
                        loadKey++
                        refs.refreshCollections()
                    } catch (e: Exception) {
                        Log.e(TAG, "delete pebble failed", e)
                        deleteError = true
                    }
                }
            },
            onDismiss = { pendingDeletion = null },
        )
    }
    if (deleteError) DeleteErrorDialog(onDismiss = { deleteError = false })
}
