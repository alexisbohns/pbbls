package app.pbbls.android.services

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pbbls.android.R
import app.pbbls.android.features.path.models.PebbleDraftPayload

private const val TAG = "composer-drafts"

/**
 * The M47 draft lifecycle for a composer, in one object — ports iOS
 * `ComposerDraftCoordinator` (M58 design D8).
 *
 * The riskiest logic around a composer is not the form: it is the draft glue —
 * hydrate-or-offer-restore gated on `refs.hasLoaded` (#647), the `can_use_glyph`
 * check (M47 D7), save-as-draft, and consuming the draft on the soft-success
 * path so a kept draft cannot duplicate a published pebble. Every one of those
 * is a bug already found and fixed once, and the failure mode of a second copy
 * is silent: a new composer keeps working while quietly losing the M47 fixes.
 *
 * `CreatePebbleScreen` still carries its own inline copy of this glue —
 * migrating it here is a follow-up, not part of the flow's landing, because
 * touching a shipped composer is a refactor of working code.
 */
class ComposerDraftCoordinator(
    private val drafts: PebbleDraftsService,
    private val snapshots: ComposerSnapshotStore,
) {
    /** What [hydrate] decided the composer should open with. */
    sealed interface Decision {
        /** A server draft was passed in: hydrate from its payload. */
        data class Resume(
            val payload: PebbleDraftPayload,
        ) : Decision

        /** A local crash snapshot exists: the composer should prompt. */
        data object OfferRestore : Decision

        /** Nothing to restore. */
        data object Fresh : Decision
    }

    /** Outcome of the `can_use_glyph` check on a resumed draft's glyph. */
    sealed interface GlyphVerdict {
        data object Usable : GlyphVerdict

        data object Unusable : GlyphVerdict

        /** The check itself failed — keep the glyph; publish will say if it really is unusable. */
        data object Unknown : GlyphVerdict
    }

    private val autosave = ComposerAutosave(snapshots.asSink())

    /** The row this composer is bound to — the resumed one, or the first save's. */
    var serverDraftId: String? = null
        private set

    /** True while the restore prompt is up, so autosave does not overwrite the pending answer. */
    var isRestorePromptPresented: Boolean by mutableStateOf(false)
        private set

    private var restorable: PebbleDraftPayload? = null
    private var hasHydrated = false

    /**
     * Decide what the composer opens with. Returns null until reference data has
     * loaded, and only ever decides once.
     *
     * Gated on [refsLoaded] because hydrating before the souls / collections
     * caches arrive would sanitize against empty sets and silently drop every
     * soul and collection on the draft (#647). Resuming a server draft wins over
     * the local snapshot — it is the more deliberate of the two, so we never
     * prompt on top of it.
     */
    fun hydrate(
        resuming: PebbleDraftRecord?,
        refsLoaded: Boolean,
    ): Decision? {
        if (hasHydrated || !refsLoaded) return null
        hasHydrated = true
        if (resuming != null) {
            serverDraftId = resuming.id
            return Decision.Resume(resuming.payload)
        }
        restorable = snapshots.load()
        return if (restorable != null) {
            isRestorePromptPresented = true
            Decision.OfferRestore
        } else {
            Decision.Fresh
        }
    }

    /** The user accepted the restore prompt. Consumes the snapshot. */
    fun takeRestorableSnapshot(): PebbleDraftPayload? {
        isRestorePromptPresented = false
        val snapshot = restorable
        restorable = null
        return snapshot
    }

    /** The user declined the restore prompt, or is leaving without keeping anything. */
    fun discardSnapshot() {
        isRestorePromptPresented = false
        restorable = null
        autosave.clear()
    }

    /** Remember the current payload; the caller owns the debounce delay. */
    fun stage(payload: PebbleDraftPayload) {
        autosave.stage(payload)
    }

    /** Write the staged snapshot — after the debounce, or when leaving the foreground. */
    fun flush() {
        autosave.flush()
    }

    /**
     * Intentional "save as draft". Returns a message resource id on failure, or
     * null on success.
     *
     * Deliberately does NOT clean up the attached snap: that would delete from
     * Storage the very object the draft references (M47 D3).
     */
    suspend fun saveAsDraft(
        payload: PebbleDraftPayload,
        userId: String,
    ): Int? =
        try {
            serverDraftId = drafts.save(payload = payload, id = serverDraftId, userId = userId)
            // Once the draft is on the server the local snapshot is redundant.
            autosave.clear()
            null
        } catch (e: Exception) {
            Log.e(TAG, "save draft failed", e)
            R.string.draft_save_error
        }

    /**
     * Publishing consumed the draft. Runs on the soft-success path too: a 5xx
     * carrying a `pebble_id` still created the pebble, so leaving the draft would
     * duplicate it in the list. A failed delete must not fail the publish.
     */
    suspend fun consumeAfterPublish() {
        autosave.clear()
        val id = serverDraftId ?: return
        try {
            drafts.delete(id)
        } catch (e: Exception) {
            Log.w(TAG, "draft cleanup after publish failed", e)
        }
        serverDraftId = null
    }

    /**
     * Whether a resumed draft's glyph is still usable. `can_use_glyph` is the
     * predicate `create_pebble` enforces, so checking it while hydrating means
     * publishing cannot fail on 42501 later (M47 D7).
     */
    suspend fun verifyGlyph(
        glyphId: String,
        userId: String,
    ): GlyphVerdict =
        try {
            if (drafts.canUseGlyph(glyphId, userId)) {
                GlyphVerdict.Usable
            } else {
                Log.i(TAG, "resumed draft referenced an unusable glyph — dropping it")
                GlyphVerdict.Unusable
            }
        } catch (e: Exception) {
            Log.e(TAG, "glyph verification failed", e)
            GlyphVerdict.Unknown
        }
}
