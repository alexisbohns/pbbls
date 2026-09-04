package app.pbbls.android.features.path.create

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import app.pbbls.android.features.path.models.KnownDraftIds
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.features.path.models.isSavableAsDraft
import app.pbbls.android.features.path.models.toDraft
import app.pbbls.android.features.pebblemedia.ImagePipeline
import app.pbbls.android.features.pebblemedia.SnapUploadCoordinator
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.ComposerAutosave
import app.pbbls.android.services.LocalAchievementsService
import app.pbbls.android.services.LocalComposerSnapshotStore
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPebbleDraftsService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.services.PebbleSnapRepository
import app.pbbls.android.services.asSink
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTopBar
import app.pbbls.android.theme.PebblesTopBarTextButton
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "create-pebble"

/** Long enough that typing does not thrash SharedPreferences (iOS parity). */
private const val AUTOSAVE_DEBOUNCE_MS = 800L

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
 *
 * M47 adds draft mode: a "Save as draft" action ungated by the publish
 * requirements (design D5), a debounced local snapshot of the open form as crash
 * insurance, and [resuming] to hydrate from a server draft.
 */
@Composable
fun CreatePebbleScreen(
    onCreated: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    resuming: PebbleDraftRecord? = null,
    onDraftSaved: () -> Unit = onCancel,
) {
    val writeService = LocalPebbleWriteService.current
    val refs = LocalReferenceDataService.current
    val karma = LocalKarmaNotificationService.current
    val achievements = LocalAchievementsService.current
    val palettes = LocalEmotionPaletteService.current
    val supabase = LocalSupabaseService.current
    val draftsService = LocalPebbleDraftsService.current
    val snapshots = LocalComposerSnapshotStore.current
    val context = LocalContext.current
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(PebbleDraft()) }
    var selectedGlyph by remember { mutableStateOf<Glyph?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveErrorRes by remember { mutableStateOf<Int?>(null) }

    // Drafts (M47). `serverDraftId` is the row this composer is bound to — the
    // resumed one, or the one created by the first "Save as draft".
    var isSavingDraft by remember { mutableStateOf(false) }
    var serverDraftId by remember { mutableStateOf(resuming?.id) }
    var restorableSnapshot by remember { mutableStateOf<PebbleDraftPayload?>(null) }
    var hasCheckedSnapshot by remember { mutableStateOf(false) }
    val autosave = remember { ComposerAutosave(snapshots.asSink()) }

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

    // Souls and collections are user-owned and deletable, so a resumed draft may
    // reference ones that are gone. Glyphs are verified server-side below.
    val knownIds =
        KnownDraftIds(
            soulIds = refs.souls.map { it.id }.toSet(),
            collectionIds = refs.collections.map { it.id }.toSet(),
        )

    /**
     * Drop a glyph the user can no longer use (design D7). `can_use_glyph` is the
     * predicate `create_pebble` enforces, so passing here means publish cannot
     * fail on 42501 later. A verification failure keeps the glyph — publishing
     * will surface a clear message if it really is unusable.
     */
    suspend fun verifyGlyph(glyphId: String) {
        val userId = supabase.session?.user?.id ?: return
        try {
            if (!draftsService.canUseGlyph(glyphId, userId)) {
                Log.i(TAG, "resumed draft referenced an unusable glyph — dropping it")
                draft = draft.copy(glyphId = null)
                selectedGlyph = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "glyph verification failed", e)
        }
    }

    // Resuming a server draft wins over the local snapshot — it is the more
    // deliberate of the two, so we never prompt on top of it.
    //
    // Gated on refs.hasLoaded: hydrating before reference data arrives would
    // sanitize against empty sets and silently drop every soul and collection.
    LaunchedEffect(resuming?.id, refs.hasLoaded) {
        if (hasCheckedSnapshot || !refs.hasLoaded) return@LaunchedEffect
        hasCheckedSnapshot = true
        if (resuming != null) {
            draft = resuming.payload.toDraft(knownIds)
            resuming.payload.existingSnap?.let { snaps.seedExisting(it) }
            draft.glyphId?.let { verifyGlyph(it) }
        } else {
            restorableSnapshot = snapshots.load()
        }
    }

    // Autosave the open composer. The debounce lives here (ComposerAutosave stays
    // a pure, JVM-testable policy object) and restarts on every keystroke because
    // LaunchedEffect is keyed on the payload.
    val currentPayload = PebbleDraftPayload.from(draft, snaps.formSnap, supabase.session?.user?.id)
    LaunchedEffect(currentPayload, restorableSnapshot) {
        // Held off while the restore prompt is up so the pending answer is not
        // overwritten before it is given.
        if (restorableSnapshot != null || currentPayload.isEmpty) return@LaunchedEffect
        autosave.stage(currentPayload)
        delay(AUTOSAVE_DEBOUNCE_MS)
        autosave.flush()
    }

    fun cancel() {
        if (isSaving) return
        scope.launch {
            supabase.session
                ?.user
                ?.id
                ?.let { snaps.cancelAndCleanup(it) }
            onCancel()
        }
    }

    BackHandler(enabled = !isSaving) { cancel() }

    val selectedEmotion = draft.emotionId?.let { palettes.byEmotionId[it] }
    val saveError = saveErrorRes?.let { stringResource(it) }

    /**
     * Intentional "save as draft". Deliberately does NOT run
     * `snaps.cancelAndCleanup` — that would delete from Storage the very object
     * the draft references (design D3).
     */
    fun saveAsDraft() {
        if (isSaving || isSavingDraft) return
        val userId = supabase.session?.user?.id
        if (userId == null) {
            saveErrorRes = R.string.pebble_save_error_generic
            return
        }
        scope.launch {
            isSavingDraft = true
            saveErrorRes = null
            try {
                serverDraftId =
                    draftsService.save(
                        payload = PebbleDraftPayload.from(draft, snaps.formSnap, userId),
                        id = serverDraftId,
                        userId = userId,
                    )
                // Once the draft is on the server the local snapshot is redundant.
                autosave.clear()
                onDraftSaved()
            } catch (e: Exception) {
                Log.e(TAG, "save draft failed", e)
                saveErrorRes = R.string.draft_save_error
                isSavingDraft = false
            }
        }
    }

    /**
     * Publishing consumed the draft. Runs on the soft-success path too: a 5xx
     * carrying a `pebble_id` still created the pebble, so leaving the draft would
     * duplicate it in the list. A failed delete must not fail the publish.
     */
    suspend fun consumeDraftAfterPublish() {
        autosave.clear()
        val id = serverDraftId ?: return
        try {
            draftsService.delete(id)
        } catch (e: Exception) {
            Log.w(TAG, "draft cleanup after publish failed", e)
        }
        serverDraftId = null
    }

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
                    achievements.fireCheck()
                    consumeDraftAfterPublish()
                    onCreated(result.response.pebbleId)
                }
                is ComposeResult.SoftSuccess -> {
                    // Soft success still inserted the pebble, so counts changed.
                    achievements.fireCheck()
                    consumeDraftAfterPublish()
                    onCreated(result.pebbleId)
                }
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
            isSaving = isSaving || isSavingDraft,
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
        )
        // Quick capture: ungated, unlike Save (design D5). "Just a name" is a
        // valid draft. Mirrors the iOS .bottomBar toolbar item.
        val draftEnabled =
            draft.isSavableAsDraft(snaps.formSnap, supabase.session?.user?.id) &&
                !isSaving &&
                !isSavingDraft
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VisibilityChip(value = draft.visibility, onChange = { draft = draft.copy(visibility = it) })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { saveAsDraft() }, enabled = draftEnabled) {
                PebblesText(
                    text = stringResource(R.string.draft_save),
                    style = PebblesTypography.body,
                    color = if (draftEnabled) accent.primary else system.muted,
                )
            }
        }
    }

    // Crash-insurance restore. Only offered when not already resuming a server
    // draft, and only once per composition.
    restorableSnapshot?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { restorableSnapshot = null },
            title = {
                PebblesText(
                    text = stringResource(R.string.draft_restore_title),
                    style = PebblesTypography.headlineEmphasized,
                    color = system.foreground,
                )
            },
            text = {
                PebblesText(
                    text = stringResource(R.string.draft_restore_body),
                    style = PebblesTypography.body,
                    color = system.secondary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    draft = snapshot.toDraft(knownIds)
                    restorableSnapshot = null
                    snapshot.glyphId?.let { id -> scope.launch { verifyGlyph(id) } }
                }) {
                    PebblesText(
                        text = stringResource(R.string.draft_restore_confirm),
                        style = PebblesTypography.body,
                        color = accent.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    autosave.clear()
                    restorableSnapshot = null
                }) {
                    PebblesText(
                        text = stringResource(R.string.draft_restore_discard),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                }
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
