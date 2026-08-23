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

    /// Resuming a server draft wins over the local snapshot. The `refs.hasLoaded`
    /// gate (#647) lives in the coordinator, so this is safe to call from both
    /// `.task` and the `refs.hasLoaded` change handler.
    func hydrateOrOfferRestore() {
        guard let drafts,
              let decision = drafts.hydrate(resuming: resuming, refsLoaded: refs.hasLoaded)
        else { return }

        switch decision {
        case .resume(let payload):
            draft = PebbleDraft(payload: payload, known: knownIds)
            if let existing = payload.existingSnap {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
            }
            Task { await verifyGlyph() }
        case .offerRestore, .fresh:
            break
        }
    }

    func restoreSnapshot() {
        guard let snapshot = drafts?.takeRestorableSnapshot() else { return }
        draft = PebbleDraft(payload: snapshot, known: knownIds)
        Task { await verifyGlyph() }
    }

    func discardSnapshot() {
        drafts?.discardSnapshot()
    }

    /// Drop a glyph the user can no longer use, and load the one they can for the
    /// form's preview (D7).
    func verifyGlyph() async {
        guard let glyphId = draft.glyphId, let userId = currentUserId, let drafts else { return }
        switch await drafts.verifyGlyph(glyphId: glyphId, userId: userId) {
        case .usable(let glyph):
            selectedGlyph = glyph
        case .unusable:
            draft.glyphId = nil
            selectedGlyph = nil
        case .unknown:
            break
        }
    }

    /// Intentional "save as draft".
    func saveAsDraft() async {
        guard isSavableAsDraft, let drafts else { return }
        guard let userId = currentUserId else {
            logger.error("save draft: no current user id")
            saveError = "You must be signed in to save."
            return
        }
        if let message = await drafts.saveAsDraft(payload: draftPayload, userId: userId) {
            saveError = message
        } else {
            dismiss()
        }
    }

    /// Publishing consumed the draft.
    func consumeDraftAfterPublish() async {
        await drafts?.consumeAfterPublish()
    }
}
