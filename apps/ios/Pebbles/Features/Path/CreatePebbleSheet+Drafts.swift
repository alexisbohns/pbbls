import Supabase
import SwiftUI

// MARK: - Drafts (M47)

/// The **server** draft (an intentional "save as draft") and the **local**
/// snapshot (crash insurance) share one payload shape and nothing else. Design:
/// docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md.
extension CreatePebbleSheet {

    /// Drives both autosave and the server draft, so the two cannot disagree.
    var draftPayload: PebbleDraftPayload {
        PebbleDraftPayload(from: draft, formSnap: snaps?.formSnap, userId: currentUserId)
    }

    var isSavableAsDraft: Bool {
        draft.isSavableAsDraft(formSnap: snaps?.formSnap, userId: currentUserId)
    }

    /// Souls/collections are deletable, so a draft can outlive them. Glyphs are
    /// verified server-side below.
    var knownIds: PebbleDraft.KnownIds {
        PebbleDraft.KnownIds(
            soulIds: Set(refs.souls.map(\.id)),
            collectionIds: Set(refs.collections.map(\.id))
        )
    }

    /// Resuming a server draft wins over the local snapshot — the more deliberate
    /// of the two, so we never prompt on top of it.
    /// Gated on `refs.hasLoaded` (#647): hydrating before reference data arrives
    /// would sanitize against empty sets and silently drop every soul and
    /// collection — the exact failure D7 exists to prevent.
    func hydrateOrOfferRestore() {
        guard !hasCheckedSnapshot, refs.hasLoaded else { return }
        hasCheckedSnapshot = true

        if let resuming {
            serverDraftId = resuming.id
            draft = PebbleDraft(payload: resuming.payload, known: knownIds)
            if let existing = resuming.payload.existingSnap {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
            }
            Task { await verifyGlyph() }
            return
        }

        if let snapshot = snapshots.load(), !snapshot.isEmpty {
            restorableSnapshot = snapshot
            isRestorePromptPresented = true
        }
    }

    func restoreSnapshot() {
        guard let snapshot = restorableSnapshot else { return }
        draft = PebbleDraft(payload: snapshot, known: knownIds)
        restorableSnapshot = nil
        Task { await verifyGlyph() }
    }

    func discardSnapshot() {
        restorableSnapshot = nil
        autosave?.clear()
    }

    /// Drop a glyph the user can no longer use, and load the one they can for the
    /// form's preview (D7). `can_use_glyph` is what `create_pebble` enforces, so
    /// passing here means publish cannot 42501.
    func verifyGlyph() async {
        guard let glyphId = draft.glyphId, let userId = currentUserId else { return }
        do {
            let usable: Bool = try await supabase.client
                .rpc(
                    "can_use_glyph",
                    params: ["p_glyph_id": glyphId.uuidString, "p_user": userId.uuidString]
                )
                .execute()
                .value
            guard usable else {
                logger.notice("resumed draft referenced an unusable glyph — dropping it")
                draft.glyphId = nil
                selectedGlyph = nil
                return
            }
            let glyphs: [Glyph] = try await supabase.client
                .from("glyphs")
                .select("id, name, strokes, view_box, user_id")
                .eq("id", value: glyphId)
                .limit(1)
                .execute()
                .value
            selectedGlyph = glyphs.first
        } catch {
            // A verification failure is not a reason to lose the user's glyph;
            // publishing will surface a clear message if it really is unusable.
            logger.error("glyph verification failed: \(error.localizedDescription, privacy: .private)")
        }
    }

    /// Intentional "save as draft". Deliberately does NOT run
    /// `snaps.cancelAndCleanup`, which would delete the snap the draft references.
    func saveAsDraft() async {
        guard isSavableAsDraft else { return }
        guard let userId = currentUserId else {
            logger.error("save draft: no current user id")
            saveError = "You must be signed in to save."
            return
        }

        isSavingDraft = true
        saveError = nil
        do {
            serverDraftId = try await draftsService.save(
                payload: draftPayload, id: serverDraftId, userId: userId
            )
            // Once the draft is on the server the local snapshot is redundant.
            autosave?.clear()
            await draftsService.refreshCount()
            dismiss()
        } catch {
            logger.error("save draft failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save that draft. Please try again."
            isSavingDraft = false
        }
    }

    /// Publishing consumed the draft. Runs on soft-success too: a 5xx carrying a
    /// `pebble_id` still created the pebble, so a kept draft would duplicate it.
    func consumeDraftAfterPublish() async {
        autosave?.clear()
        guard let serverDraftId else { return }
        await draftsService.deleteIgnoringFailure(id: serverDraftId)
        self.serverDraftId = nil
        await draftsService.refreshCount()
    }
}
