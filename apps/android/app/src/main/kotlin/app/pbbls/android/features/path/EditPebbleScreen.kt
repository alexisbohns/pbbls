package app.pbbls.android.features.path

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.karma.KarmaReason
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.features.path.create.PebbleForm
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.features.path.models.renderHeightDp
import app.pbbls.android.features.pebblemedia.ImagePipeline
import app.pbbls.android.features.pebblemedia.SnapUploadCoordinator
import app.pbbls.android.features.pebblemedia.models.FormSnap
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPebbleDetailService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.PebbleSnapRepository
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "edit-pebble"

/**
 * Full-screen surface for editing an existing pebble — ports EditPebbleSheet.swift
 * (minus the photo flow, a milestone non-goal). Loads the PebbleDetail via B's
 * fetch, prefills the shared PebbleForm via PebbleDraft.from(detail), renders the
 * current render_svg at the top at the valence-derived height with the palette
 * stroke color, and saves through PebbleWriteService.update (D2/D3). Soft-success
 * (any 5xx) advances; the pebbleEnriched flash fires only when karma_delta > 0
 * (D10, guarded inside KarmaNotificationService).
 */
@Composable
fun EditPebbleScreen(
    pebbleId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val detailService = LocalPebbleDetailService.current
    val writeService = LocalPebbleWriteService.current
    val referenceData = LocalReferenceDataService.current
    val palettes = LocalEmotionPaletteService.current
    val karma = LocalKarmaNotificationService.current
    val supabase = LocalSupabaseService.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent

    var draft by remember(pebbleId) { mutableStateOf(PebbleDraft()) }
    var selectedGlyph by remember(pebbleId) { mutableStateOf<Glyph?>(null) }
    var renderSvg by remember(pebbleId) { mutableStateOf<String?>(null) }
    var strokeColor by remember(pebbleId) { mutableStateOf<String?>(null) }
    var renderHeight by remember(pebbleId) { mutableStateOf(260.dp) }

    var isLoading by remember(pebbleId) { mutableStateOf(true) }
    var loadError by remember(pebbleId) { mutableStateOf(false) }
    var isSaving by remember(pebbleId) { mutableStateOf(false) }
    var saveErrorRes by remember(pebbleId) { mutableStateOf<Int?>(null) }
    var reloadToken by remember(pebbleId) { mutableIntStateOf(0) }
    var isRemovingExistingSnap by remember(pebbleId) { mutableStateOf(false) }

    // Form-scoped (M42 D6); seeded with the existing snap once the detail loads.
    val snaps = remember(pebbleId) { SnapUploadCoordinator(repo = PebbleSnapRepository(supabase)) }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val userId = supabase.session?.user?.id
            if (uri != null && userId != null) {
                scope.launch {
                    try {
                        val processed = withContext(Dispatchers.IO) { ImagePipeline.process(context, uri) }
                        snaps.attach(processed, userId)
                    } catch (e: Exception) {
                        // iOS parity: a failed pick/decode logs and drops silently.
                        Log.e(TAG, "photo pick processing failed", e)
                    }
                }
            }
        }

    fun dismiss() {
        if (isSaving) return
        scope.launch {
            supabase.session
                ?.user
                ?.id
                ?.let { snaps.cancelAndCleanup(it) }
            onDismiss()
        }
    }

    BackHandler(enabled = !isSaving) { dismiss() }

    LaunchedEffect(pebbleId, reloadToken) {
        isLoading = true
        loadError = false
        try {
            val detail = detailService.load(pebbleId)
            draft = PebbleDraft.from(detail)
            selectedGlyph = detail.glyph
            renderSvg = detail.renderSvg
            // iOS parity: EditPebbleSheet uses strokeHex(colorScheme), NOT the
            // intensity-based pebbleFrameColors the read banner uses.
            strokeColor = palettes.palette(detail.emotion.id)?.strokeHex(isDark) ?: accent.primaryHex
            // Extracted so neither line exceeds ktlint's 3-dot chain limit.
            val sizeGroup = detail.valence.sizeGroup
            renderHeight = sizeGroup.renderHeightDp.dp
            // iOS seeds only the first saved snap (at most one photo — M42 D1).
            snaps.seedExisting(
                detail.sortedSnaps.firstOrNull()?.let { FormSnap.Existing(id = it.id, storagePath = it.storagePath) },
            )
            isLoading = false
        } catch (e: Exception) {
            Log.e(TAG, "edit pebble load failed", e)
            loadError = true
            isLoading = false
        }
    }

    // Ports iOS `.onChange(of: draft.glyphId) { if nil { selectedGlyph = nil } }`.
    LaunchedEffect(draft.glyphId) {
        if (draft.glyphId == null) selectedGlyph = null
    }

    val selectedEmotion = draft.emotionId?.let { palettes.byEmotionId[it] }
    val saveError = saveErrorRes?.let { stringResource(it) }

    fun save() {
        if (!draft.isValid || isSaving) return
        // Snap gates (M42): distinct copy per state, checked before isSaving.
        if (snaps.isUploading) {
            saveErrorRes = R.string.pebble_save_error_photo_uploading
            return
        }
        if (snaps.hasFailed) {
            saveErrorRes = R.string.pebble_save_error_photo_failed
            return
        }
        isSaving = true
        saveErrorRes = null
        scope.launch {
            val userId = supabase.session?.user?.id
            // Always-echo contract (M42 D5): existing echoes verbatim, a fresh
            // upload sends its pair, no snap sends [] (which deletes server-side).
            val snapPayload =
                when (val formSnap = snaps.formSnap) {
                    is FormSnap.Existing -> listOf(PebbleSnapPayload(formSnap.id, formSnap.storagePath, 0))
                    is FormSnap.Pending ->
                        snaps.pendingSnapForPayload()?.let { snap ->
                            userId?.let { listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(it), 0)) }
                        } ?: emptyList()
                    null -> emptyList()
                }
            when (val result = writeService.update(pebbleId, draft, snapPayload)) {
                is ComposeResult.Success -> {
                    renderSvg = result.response.renderSvg ?: renderSvg
                    karma.notifyEarned(result.response.karmaDelta ?: 0, KarmaReason.PEBBLE_ENRICHED)
                    onSaved()
                }
                is ComposeResult.SoftSuccess -> onSaved()
                is ComposeResult.Failure -> {
                    saveErrorRes = result.messageRes
                    isSaving = false
                    userId?.let { snaps.handleSaveFailure(it) }
                }
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(system.background)
            .safeDrawingPadding(),
    ) {
        EditTopBar(
            isSaving = isSaving,
            saveEnabled = draft.isValid && !isLoading,
            onCancel = { dismiss() },
            onSave = { save() },
        )
        when {
            isLoading ->
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = accent.primary)
                }
            loadError ->
                EditLoadError(onRetry = { reloadToken++ })
            else ->
                PebbleForm(
                    draft = draft,
                    onDraftChange = { draft = it },
                    domains = referenceData.domains,
                    souls = referenceData.souls,
                    collections = referenceData.collections,
                    selectedEmotion = selectedEmotion,
                    selectedGlyph = selectedGlyph,
                    onGlyphPicked = { selectedGlyph = it },
                    saveError = saveError,
                    renderSvg = renderSvg,
                    strokeColor = strokeColor,
                    renderHeight = renderHeight,
                    modifier = Modifier.fillMaxSize(),
                    formSnap = snaps.formSnap,
                    onAddPhoto = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onRetryPending = {
                        supabase.session
                            ?.user
                            ?.id
                            ?.let { id -> scope.launch { snaps.retryCurrent(id) } }
                    },
                    onRemovePending = {
                        supabase.session
                            ?.user
                            ?.id
                            ?.let { id -> scope.launch { snaps.removePending(id) } }
                    },
                    isRemovingExistingSnap = isRemovingExistingSnap,
                    onRemoveExistingSnap = {
                        scope.launch {
                            isRemovingExistingSnap = true
                            try {
                                snaps.removeExisting()
                            } catch (e: Exception) {
                                Log.e(TAG, "delete_pebble_media failed", e)
                                saveErrorRes = R.string.photo_remove_error
                            } finally {
                                isRemovingExistingSnap = false
                            }
                        }
                    },
                )
        }
    }
}

@Composable
private fun EditTopBar(
    isSaving: Boolean,
    saveEnabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            PebblesText(
                stringResource(R.string.action_cancel),
                PebblesTypography.buttonLabel,
                color = accent.primary,
            )
        }
        Spacer(Modifier.weight(1f))
        PebblesText(
            stringResource(R.string.edit_pebble_title),
            PebblesTypography.buttonLabel,
            color = system.foreground,
        )
        Spacer(Modifier.weight(1f))
        if (isSaving) {
            Box(Modifier.size(48.dp), Alignment.Center) {
                CircularProgressIndicator(
                    color = accent.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            TextButton(onClick = onSave, enabled = saveEnabled) {
                PebblesText(
                    stringResource(R.string.action_save),
                    PebblesTypography.buttonLabel,
                    color = accent.primary,
                )
            }
        }
    }
}

@Composable
private fun EditLoadError(onRetry: () -> Unit) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
