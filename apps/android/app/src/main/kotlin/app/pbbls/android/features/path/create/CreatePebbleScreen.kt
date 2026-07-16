package app.pbbls.android.features.path.create

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.karma.KarmaReason
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.features.pebblemedia.ImagePipeline
import app.pbbls.android.features.pebblemedia.SnapUploadCoordinator
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.PebbleSnapRepository
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "create-pebble"

/**
 * The create-pebble surface (D5) — ports iOS `CreatePebbleSheet`. Owns the
 * [PebbleDraft] (D4) plus the picked glyph, save-in-flight, and inline
 * save-error state, and reads the write / reference / karma / palette services
 * here so the hosted [PebbleForm] stays pure and previewable. `save()` branches
 * on [ComposeResult]: `Success` flashes karma (`karma_delta`, `PEBBLE_CREATED`)
 * and reveals; `SoftSuccess` skips the flash but still reveals; `Failure`
 * surfaces an inline error (D16). Self-applies `safeDrawingPadding()` +
 * `imePadding()`, so the caller composes it in an edge-to-edge (unpadded) slot,
 * sibling to the detail cover in `PathScreen`'s OUTER Box.
 */
@Composable
fun CreatePebbleScreen(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val writeService = LocalPebbleWriteService.current
    val refs = LocalReferenceDataService.current
    val karma = LocalKarmaNotificationService.current
    val palettes = LocalEmotionPaletteService.current
    val supabase = LocalSupabaseService.current
    val context = LocalContext.current
    val system = PebblesTheme.colors.system
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(PebbleDraft()) }
    var selectedGlyph by remember { mutableStateOf<Glyph?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveErrorRes by remember { mutableStateOf<Int?>(null) }

    // Form-scoped (M42 D6): an in-flight upload dies with this cover.
    val snaps = remember { SnapUploadCoordinator(repo = PebbleSnapRepository(supabase)) }
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

    fun cancel() {
        if (isSaving) return
        scope.launch {
            supabase.session?.user?.id?.let { snaps.cancelAndCleanup(it) }
            onCancel()
        }
    }

    BackHandler(enabled = !isSaving) { cancel() }

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
        scope.launch {
            isSaving = true
            saveErrorRes = null
            val userId = supabase.session?.user?.id
            val snapPayload =
                snaps.pendingSnapForPayload()?.let { snap ->
                    userId?.let { listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(it), 0)) }
                }
            when (val result = writeService.create(draft, snapPayload)) {
                is ComposeResult.Success -> {
                    karma.notifyEarned(result.response.karmaDelta ?: 0, KarmaReason.PEBBLE_CREATED)
                    onCreated(result.response.pebbleId)
                }
                is ComposeResult.SoftSuccess -> onCreated(result.pebbleId)
                is ComposeResult.Failure -> {
                    saveErrorRes = result.messageRes
                    isSaving = false
                    userId?.let { snaps.handleSaveFailure(it) }
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background)
                .safeDrawingPadding()
                .imePadding(),
    ) {
        CreateTopBar(
            saveEnabled = draft.isValid,
            isSaving = isSaving,
            onCancel = { cancel() },
            onSave = { save() },
        )
        PebbleForm(
            draft = draft,
            onDraftChange = { draft = it },
            domains = refs.domains,
            souls = refs.souls,
            collections = refs.collections,
            selectedEmotion = selectedEmotion,
            selectedGlyph = selectedGlyph,
            onGlyphPicked = { selectedGlyph = it },
            saveError = saveError,
            modifier = Modifier.weight(1f),
            formSnap = snaps.formSnap,
            onAddPhoto = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRetryPending = {
                supabase.session?.user?.id?.let { id -> scope.launch { snaps.retryCurrent(id) } }
            },
            onRemovePending = {
                supabase.session?.user?.id?.let { id -> scope.launch { snaps.removePending(id) } }
            },
        )
    }
}

/**
 * Create-surface top bar: Cancel (left), the title (centered), and a Save
 * button that swaps to an inline spinner while [isSaving] and is disabled until
 * the draft is valid. Ports `CreatePebbleSheet`'s toolbar, composed on the
 * shared [PebblesTopBar]; keeps the shipped M39 look (headline title, accent
 * buttons) via the style overrides — see the PebblesTopBar doc for the
 * iOS-idiom defaults new screens should use.
 */
@Composable
private fun CreateTopBar(
    saveEnabled: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    PebblesTopBar(
        title = stringResource(R.string.create_new_pebble),
        titleStyle = PebblesTypography.headlineEmphasized,
        titleColor = system.foreground,
        leading = {
            PebblesTopBarTextButton(
                text = stringResource(R.string.action_cancel),
                onClick = onCancel,
                color = accent.primary,
            )
        },
        trailing = {
            if (isSaving) {
                CircularProgressIndicator(
                    color = accent.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                PebblesTopBarTextButton(
                    text = stringResource(R.string.action_save),
                    onClick = onSave,
                    enabled = saveEnabled,
                    color = if (saveEnabled) accent.primary else system.muted,
                )
            }
        },
    )
}
