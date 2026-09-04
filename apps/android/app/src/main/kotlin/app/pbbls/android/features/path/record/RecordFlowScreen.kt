package app.pbbls.android.features.path.record

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import app.pbbls.android.R
import app.pbbls.android.features.karma.KarmaReason
import app.pbbls.android.features.karma.LocalKarmaNotificationService
import app.pbbls.android.features.path.create.pickers.rememberGlyphPickerState
import app.pbbls.android.features.path.models.ComposePebbleResponse
import app.pbbls.android.features.path.models.KnownDraftIds
import app.pbbls.android.features.path.models.PebbleDraftPayload
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.isSavableAsDraft
import app.pbbls.android.features.path.record.steps.RecordSuccessStep
import app.pbbls.android.features.path.valence.prewarmValenceStones
import app.pbbls.android.features.pebblemedia.ExifCaptureDate
import app.pbbls.android.features.pebblemedia.ImagePipeline
import app.pbbls.android.features.pebblemedia.SnapUploadCoordinator
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.ComposerDraftCoordinator
import app.pbbls.android.services.LocalAchievementsService
import app.pbbls.android.services.LocalComposerSnapshotStore
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalPebbleDraftsService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.PebbleDraftRecord
import app.pbbls.android.services.PebbleSnapRepository
import app.pbbls.android.services.rememberTapHaptics
import app.pbbls.android.theme.PebblesDestructive
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

private const val TAG = "record-flow"

/** Long enough that typing does not thrash SharedPreferences (iOS parity). */
private const val AUTOSAVE_DEBOUNCE_MS = 800L

/** Enough for the slide to read as one motion without holding the user up. */
private const val STEP_TRANSITION_MS = 280

/**
 * The step-by-step pebble composer (M58) — ports iOS `RecordFlowView`, and the
 * default way to record a pebble on Android. `CreatePebbleScreen` stays in the
 * tree and is reachable by long-pressing the same "New pebble" entry (D1).
 *
 * Owns the coordinators the flow needs and the orchestration between them;
 * everything about *the flow itself* — gating, back, skip labels, resume,
 * haptics — lives on [RecordFlowModel].
 *
 * Self-applies `safeDrawingPadding()` + `imePadding()`, so the caller composes
 * it in an edge-to-edge (unpadded) slot, sibling to the detail cover in
 * `PathScreen`'s outer Box.
 *
 * [onPublished] fires as soon as the pebble publishes, while the success step is
 * still up, so the Path is already reloaded by the time the user exits (D10).
 * [onDismiss] is the exit: cancel, and the success step's own button.
 */
@Composable
fun RecordFlowScreen(
    onPublished: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    resuming: PebbleDraftRecord? = null,
    onDraftSaved: () -> Unit = onDismiss,
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

    val haptic = rememberTapHaptics()
    val model = remember(haptic) { RecordFlowModel(haptic = haptic) }
    val glyphPickerState = rememberGlyphPickerState()
    val drafts = remember(draftsService, snapshots) { ComposerDraftCoordinator(draftsService, snapshots) }

    // Form-scoped (M42 D6): an in-flight upload dies with this cover.
    val snaps = remember { SnapUploadCoordinator(repo = PebbleSnapRepository(supabase)) }
    var captureDate by remember { mutableStateOf<OffsetDateTime?>(null) }
    var isCloseConfirmPresented by remember { mutableStateOf(false) }

    val userId = supabase.session?.user?.id

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null && userId != null) {
                scope.launch {
                    try {
                        // EXIF first: `ImagePipeline` re-encodes with
                        // `Bitmap.compress`, which writes no metadata at all, so
                        // the capture date is gone by the time bytes exist (D7).
                        val picked = withContext(Dispatchers.IO) { ExifCaptureDate.from(context, uri) }
                        captureDate = picked
                        model.applyCaptureDate(picked)
                        val processed = withContext(Dispatchers.IO) { ImagePipeline.process(context, uri) }
                        snaps.attach(processed, userId)
                    } catch (e: Exception) {
                        // iOS parity: a failed pick/decode logs and drops silently.
                        Log.e(TAG, "photo pick processing failed", e)
                    }
                }
            }
        }

    val knownIds =
        KnownDraftIds(
            soulIds = refs.souls.map { it.id }.toSet(),
            collectionIds = refs.collections.map { it.id }.toSet(),
        )

    suspend fun verifyGlyph(glyphId: String) {
        val id = supabase.session?.user?.id ?: return
        when (drafts.verifyGlyph(glyphId, id)) {
            ComposerDraftCoordinator.GlyphVerdict.Unusable -> model.clearGlyph()
            ComposerDraftCoordinator.GlyphVerdict.Usable,
            ComposerDraftCoordinator.GlyphVerdict.Unknown,
            -> Unit
        }
    }

    // The valence fan wobbles eighteen assets the first time it draws, which is
    // a visible hitch on the main thread. Two steps of runway is plenty, and the
    // caches are process-wide, so a second flow pays nothing.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) { prewarmValenceStones(context) }
    }

    // Hydrate-or-offer-restore, gated on refs.hasLoaded (#647): hydrating before
    // the souls / collections caches arrive would sanitize against empty sets and
    // silently drop every soul and collection. Re-keyed on hasLoaded because the
    // first composition may run before the reference fetch settles; the
    // coordinator only ever decides once.
    LaunchedEffect(resuming?.id, refs.hasLoaded) {
        val decision = drafts.hydrate(resuming, refs.hasLoaded)
        if (decision is ComposerDraftCoordinator.Decision.Resume) {
            model.resume(decision.payload, knownIds)
            decision.payload.existingSnap?.let {
                snaps.seedExisting(it)
                model.hasSnap = true
            }
            model.draft.glyphId?.let { verifyGlyph(it) }
        }
    }

    // The photo step's Skip / Done label reads off the model, so the upload
    // coordinator's state is mirrored onto it rather than the model owning media.
    LaunchedEffect(snaps.formSnap) { model.hasSnap = snaps.formSnap != null }

    val currentPayload = PebbleDraftPayload.from(model.draft, snaps.formSnap, userId)
    LaunchedEffect(currentPayload, drafts.isRestorePromptPresented) {
        // Held off while the restore prompt is up so the pending answer is not
        // overwritten before it is given.
        if (drafts.isRestorePromptPresented || currentPayload.isEmpty) return@LaunchedEffect
        drafts.stage(currentPayload)
        delay(AUTOSAVE_DEBOUNCE_MS)
        drafts.flush()
    }

    /** Non-null while the attached photo blocks publishing — the two rules the sheet enforces. */
    val snapBlockedMessage =
        when {
            snaps.isUploading -> stringResource(R.string.pebble_save_error_photo_uploading)
            snaps.hasFailed -> stringResource(R.string.pebble_save_error_photo_failed)
            else -> null
        }
    val publishError = model.publishErrorRes?.let { stringResource(it) }

    fun cancelAndCleanup() {
        scope.launch {
            userId?.let { snaps.cancelAndCleanup(it) }
            drafts.discardSnapshot()
            onDismiss()
        }
    }

    /** ✕ only asks when there is something to keep (D9). */
    fun handleClose() {
        if (model.isPublishing) return
        if (model.draft.isSavableAsDraft(snaps.formSnap, userId)) {
            isCloseConfirmPresented = true
        } else {
            cancelAndCleanup()
        }
    }

    fun saveAsDraftAndClose() {
        val id = userId
        if (id == null) {
            Log.e(TAG, "save draft: no current user id")
            model.fail(R.string.record_signed_out_error)
            return
        }
        scope.launch {
            // Deliberately no snap cleanup: the draft references that snap.
            val error = drafts.saveAsDraft(PebbleDraftPayload.from(model.draft, snaps.formSnap, id), id)
            if (error != null) model.fail(error) else onDraftSaved()
        }
    }

    fun publish() {
        val id = userId
        if (id == null) {
            Log.e(TAG, "publish: no current user id")
            model.fail(R.string.record_signed_out_error)
            return
        }
        // Snap gates (M42): distinct copy per state, checked before the request.
        if (snaps.isUploading) {
            model.fail(R.string.pebble_save_error_photo_uploading)
            return
        }
        if (snaps.hasFailed) {
            model.fail(R.string.pebble_save_error_photo_failed)
            return
        }
        scope.launch {
            model.beginPublish()
            val snapPayload =
                snaps.pendingSnapForPayload()?.let { snap ->
                    listOf(PebbleSnapPayload(snap.id, snap.storagePrefix(id), 0))
                }
            when (val result = writeService.create(model.draft, snapPayload)) {
                is ComposeResult.Success -> {
                    // The success step shows the amount, so the capsule would be
                    // redundant (D10).
                    karma.notifyEarned(
                        result.response.karmaDelta ?: 0,
                        KarmaReason.PEBBLE_CREATED,
                        presentsCapsule = false,
                    )
                    achievements.fireCheck()
                    drafts.consumeAfterPublish()
                    model.succeed(result.response)
                    // Reload the Path behind the cover so it is fresh on exit.
                    onPublished(result.response.pebbleId)
                }
                is ComposeResult.SoftSuccess -> {
                    // The pebble exists but the compose step failed, so there is
                    // no render and no karma amount to show — the success step
                    // degrades to the name alone rather than blocking (D10).
                    achievements.fireCheck()
                    drafts.consumeAfterPublish()
                    model.succeed(ComposePebbleResponse(pebbleId = result.pebbleId))
                    onPublished(result.pebbleId)
                }
                is ComposeResult.Failure -> {
                    // A hard failure never reaches the success step: the flow
                    // stays on privacy so ✕ → Save as draft is still a way out.
                    model.fail(result.messageRes)
                    snaps.handleSaveFailure(id)
                }
            }
        }
    }

    // System back mirrors the chrome: unwind an open glyph swap first, then step
    // backwards, and only ask to leave from the first step.
    //
    // Always enabled, never conditional: a disabled handler lets back fall
    // through to whatever hosts the cover, so "back does nothing here" has to be
    // an explicit branch rather than an absent handler. That is also why the
    // terminal step handles it — it has no back chevron, but the system button
    // exists regardless and has to mean "leave", not "exit the app".
    BackHandler {
        when {
            model.isPublishing -> Unit
            model.step == RecordStep.SUCCESS -> onDismiss()
            glyphPickerState.unwind() -> Unit
            model.step.previous != null -> model.back()
            else -> handleClose()
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
        if (model.step != RecordStep.SUCCESS) {
            RecordFlowChrome(step = model.step, onBack = { model.back() }, onClose = { handleClose() })
        }

        AnimatedContent(
            targetState = model.step,
            transitionSpec = {
                // Direction comes from the transition itself rather than a
                // remembered "previous step", which would be a second source of
                // truth for something the animation already knows.
                val entering = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                val spec = tween<Float>(STEP_TRANSITION_MS)
                val slideSpec = tween<IntOffset>(STEP_TRANSITION_MS)
                val enter = slideInHorizontally(slideSpec) { it * entering } + fadeIn(spec)
                val exit = slideOutHorizontally(slideSpec) { it * -entering } + fadeOut(spec)
                enter togetherWith exit using SizeTransform(clip = false)
            },
            label = "recordFlowStep",
            // weight, not fillMaxSize: the chrome above is measured first, and a
            // weighted child is the unambiguous way to say "everything left".
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { step ->
            val response = model.published
            if (step == RecordStep.SUCCESS && response != null) {
                RecordSuccessStep(
                    name = model.draft.name,
                    renderSvg = response.renderSvg,
                    karmaDelta = response.karmaDelta,
                    valence = model.draft.valence ?: Valence.NEUTRAL_MEDIUM,
                    palette = model.draft.emotionId?.let { palettes.palette(it) },
                    onExit = onDismiss,
                )
            } else {
                RecordStepScaffold(
                    title = stringResource(step.titleRes),
                    subtitle = step.subtitleRes?.let { stringResource(it) },
                    action = actionFor(step, model, snapBlockedMessage, onPublish = { publish() }),
                    contentScrolls = !step.bringsOwnScroll,
                ) {
                    RecordStepContent(
                        step = step,
                        model = model,
                        domains = refs.domains,
                        collections = refs.collections,
                        snap = snaps.formSnap,
                        seededFromPhoto = captureDate != null,
                        snapBlockedMessage = snapBlockedMessage,
                        publishError = publishError,
                        glyphPickerState = glyphPickerState,
                        onPickPhoto = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRetryPhoto = { userId?.let { id -> scope.launch { snaps.retryCurrent(id) } } },
                        onRemovePhoto = { userId?.let { id -> scope.launch { snaps.removePending(id) } } },
                        onGlyphPicked = { model.selectGlyph(it.id) },
                    )
                }
            }
        }
    }

    // Crash-insurance restore, unchanged from the sheet (M47): local autosave is
    // invisible, and the prompt fires on entry.
    if (drafts.isRestorePromptPresented) {
        AlertDialog(
            onDismissRequest = { drafts.discardSnapshot() },
            containerColor = system.background,
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
                    drafts.takeRestorableSnapshot()?.let { snapshot ->
                        model.resume(snapshot, knownIds)
                        model.draft.glyphId?.let { id -> scope.launch { verifyGlyph(id) } }
                    }
                }) {
                    PebblesText(
                        text = stringResource(R.string.draft_restore_confirm),
                        style = PebblesTypography.body,
                        color = accent.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { drafts.discardSnapshot() }) {
                    PebblesText(
                        text = stringResource(R.string.draft_restore_discard),
                        style = PebblesTypography.body,
                        color = system.secondary,
                    )
                }
            },
        )
    }

    if (isCloseConfirmPresented) {
        CloseConfirmDialog(
            onSaveAsDraft = {
                isCloseConfirmPresented = false
                saveAsDraftAndClose()
            },
            onDiscard = {
                isCloseConfirmPresented = false
                cancelAndCleanup()
            },
            onKeepGoing = { isCloseConfirmPresented = false },
        )
    }
}

/**
 * The ✕ confirmation (D9). The moment a user wants to keep a half-finished
 * pebble is precisely the moment they try to leave, so the choice lives here
 * rather than taking permanent residence in the chrome — which converts an
 * accidental discard into a deliberate one.
 *
 * `internal` so the screenshot preview can render it directly.
 */
@Composable
internal fun CloseConfirmDialog(
    onSaveAsDraft: () -> Unit,
    onDiscard: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    AlertDialog(
        // Tapping outside is "keep going": the least destructive of the three.
        onDismissRequest = onKeepGoing,
        containerColor = system.background,
        title = {
            PebblesText(
                text = stringResource(R.string.record_close_title),
                style = PebblesTypography.headlineEmphasized,
                color = system.foreground,
            )
        },
        // Material lays out exactly two action slots, and the flow needs three —
        // so the two exits share the confirm slot and the stay-put option keeps
        // the dismiss slot, where a cancel is expected.
        confirmButton = {
            Row(horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDiscard) {
                    PebblesText(
                        text = stringResource(R.string.record_close_discard),
                        style = PebblesTypography.body,
                        color = PebblesDestructive,
                    )
                }
                TextButton(onClick = onSaveAsDraft) {
                    PebblesText(
                        text = stringResource(R.string.draft_save),
                        style = PebblesTypography.body,
                        color = accent.primary,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepGoing) {
                PebblesText(
                    text = stringResource(R.string.record_close_keep_going),
                    style = PebblesTypography.body,
                    color = system.secondary,
                )
            }
        },
    )
}

/**
 * The one action a step offers, if any. Tile steps offer none — the pick is the
 * advance (M58 D3); optional steps carry a text button reading Skip while empty
 * and Done once filled.
 */
@Composable
private fun actionFor(
    step: RecordStep,
    model: RecordFlowModel,
    snapBlockedMessage: String?,
    onPublish: () -> Unit,
): RecordStepAction? =
    when (step) {
        RecordStep.EMOTION, RecordStep.DOMAIN, RecordStep.SUCCESS -> null

        // Unlike the other tile steps, valence commits without advancing (the
        // fan is worth looking at once a stone is lit), so it needs a button.
        RecordStep.VALENCE, RecordStep.WHEN, RecordStep.NAME ->
            RecordStepAction.Primary(
                label = stringResource(R.string.record_action_continue),
                enabled = model.isAnswered,
                isLoading = false,
                onClick = { model.advance() },
            )

        RecordStep.PRIVACY ->
            RecordStepAction.Primary(
                label = stringResource(R.string.record_action_publish),
                enabled = snapBlockedMessage == null && model.draft.isValid,
                isLoading = model.isPublishing,
                onClick = onPublish,
            )

        RecordStep.PHOTO, RecordStep.SOULS, RecordStep.COLLECTION, RecordStep.GLYPH ->
            RecordStepAction.Text(
                label =
                    stringResource(
                        if (model.optionalButtonIsSkip) R.string.record_action_skip else R.string.action_done,
                    ),
                onClick = { model.advance() },
            )
    }
