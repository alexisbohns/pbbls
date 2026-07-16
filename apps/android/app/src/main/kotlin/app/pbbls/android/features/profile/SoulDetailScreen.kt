package app.pbbls.android.features.profile

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.components.PebbleRow
import app.pbbls.android.features.glyph.views.GlyphView
import app.pbbls.android.features.glyph.views.GlyphViewCase
import app.pbbls.android.features.path.EditPebbleScreen
import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.features.profile.components.ConfirmDeleteDialog
import app.pbbls.android.features.profile.components.DeleteErrorDialog
import app.pbbls.android.features.profile.components.ProfileEmptyState
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSoulsService
import app.pbbls.android.theme.PebblesListSection
import app.pbbls.android.theme.PebblesScreen
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.launch

private const val TAG = "soul-detail"

/**
 * Pushed detail for one soul — ports iOS `SoulDetailView.swift`: compact
 * header (56dp glyph + name + live pebble count), the pebbles tagged with the
 * soul on the shared [PebbleRow] (tap → [EditPebbleScreen] cover, long-press →
 * `delete_pebble` with confirm), and an Edit top-bar action opening
 * [SoulFormScreen] as a cover (D9 surface swap). The NavHost passes only the
 * soul id, so the screen fetches the soul itself (iOS receives the row from
 * the list; deviation noted in the plan).
 */
@Composable
fun SoulDetailScreen(
    soulId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val soulsService = LocalSoulsService.current
    val writeService = LocalPebbleWriteService.current
    val palettes = LocalEmotionPaletteService.current
    val refs = LocalReferenceDataService.current
    val scope = rememberCoroutineScope()
    val system = PebblesTheme.colors.system

    var soul by remember(soulId) { mutableStateOf<SoulWithGlyph?>(null) }
    var pebbles by remember(soulId) { mutableStateOf<List<Pebble>>(emptyList()) }
    var isLoading by remember(soulId) { mutableStateOf(true) }
    var loadFailed by remember(soulId) { mutableStateOf(false) }
    var loadKey by remember(soulId) { mutableIntStateOf(0) }
    var isPresentingEdit by remember(soulId) { mutableStateOf(false) }
    var editingPebbleId by remember(soulId) { mutableStateOf<String?>(null) }
    var pendingDeletion by remember(soulId) { mutableStateOf<Pebble?>(null) }
    var deleteError by remember(soulId) { mutableStateOf(false) }

    LaunchedEffect(soulId, loadKey) {
        isLoading = true
        loadFailed = false
        try {
            soul = soulsService.loadSoul(soulId)
            pebbles = soulsService.loadPebbles(soulId)
        } catch (e: Exception) {
            Log.e(TAG, "soul detail load failed", e)
            loadFailed = true
        } finally {
            isLoading = false
        }
    }

    PebblesScreen(
        modifier = modifier,
        topBar = {
            PebblesTopBar(
                title = soul?.name.orEmpty(),
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
                    if (soul != null) {
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

            else ->
                Column(Modifier.fillMaxSize()) {
                    soul?.let { SoulHeader(soul = it, pebbleCount = pebbles.size) }
                    if (pebbles.isEmpty()) {
                        ProfileEmptyState(
                            title = stringResource(R.string.soul_detail_empty_title),
                            message = stringResource(R.string.soul_detail_empty_message),
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 32.dp),
                        ) {
                            PebblesListSection(
                                rows =
                                    pebbles.map { pebble ->
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
    }

    if (isPresentingEdit) {
        soul?.let { current ->
            SoulFormScreen(
                original = current,
                onDismiss = { isPresentingEdit = false },
                onSaved = {
                    isPresentingEdit = false
                    loadKey++
                    scope.launch { refs.refreshSouls() }
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
                        refs.refreshSouls()
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

/** Compact identity header — 56dp glyph + hand-face name + live pebble count. */
@Composable
private fun SoulHeader(
    soul: SoulWithGlyph,
    pebbleCount: Int,
) {
    val system = PebblesTheme.colors.system
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphView(
            case = GlyphViewCase.DEFAULT,
            strokes = soul.glyph.strokes,
            viewBox = soul.glyph.viewBox,
            side = 56.dp,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PebblesText(
                text = soul.name,
                style = PebblesTypography.bodyLeadHand,
                color = system.foreground,
            )
            // iOS uses the system caption face; the token set's closest match
            // is captionEmphasized (also the shared PebbleRow date treatment).
            PebblesText(
                text = pluralStringResource(R.plurals.pebbles_count, pebbleCount, pebbleCount),
                style = PebblesTypography.captionEmphasized,
                color = system.secondary,
            )
        }
    }
}
