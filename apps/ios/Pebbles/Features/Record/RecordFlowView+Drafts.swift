import SwiftUI

// MARK: - Drafts and leaving (M47 semantics, D9)

/// The record flow's draft lifecycle and its exits, split out of
/// `RecordFlowView` the way `CreatePebbleSheet+Drafts` is split out of the
/// sheet. Both composers drive the same `ComposerDraftCoordinator`; this is
/// only the view-side glue.
extension RecordFlowView {

    func hydrateOrOfferRestore() {
        guard let drafts,
              let decision = drafts.hydrate(resuming: resuming, refsLoaded: refs.hasLoaded)
        else { return }

        switch decision {
        case .resume(let payload):
            model.resume(from: payload, known: knownIds)
            if let existing = payload.existingSnap {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
                model.hasSnap = true
            }
            Task { await verifyGlyph() }
        case .offerRestore, .fresh:
            break
        }
    }

    func restoreSnapshot() {
        guard let snapshot = drafts?.takeRestorableSnapshot() else { return }
        model.resume(from: snapshot, known: knownIds)
        Task { await verifyGlyph() }
    }

    func verifyGlyph() async {
        guard let glyphId = model.draft.glyphId, let userId = currentUserId, let drafts else { return }
        switch await drafts.verifyGlyph(glyphId: glyphId, userId: userId) {
        case .usable(let glyph):
            selectedGlyph = glyph
        case .unusable:
            model.draft.glyphId = nil
            selectedGlyph = nil
        case .unknown:
            break
        }
    }

    // MARK: - Leaving

    /// `✕` only asks when there is something to keep (D9).
    func handleClose() {
        if isSavableAsDraft {
            isCloseConfirmPresented = true
        } else {
            Task { await cancelAndCleanup() }
        }
    }

    func saveAsDraftAndClose() async {
        guard let drafts, let userId = currentUserId else {
            logger.error("save draft: no coordinator or no current user id")
            dismiss()
            return
        }
        // Deliberately no snap cleanup: the draft references that snap.
        if let message = await drafts.saveAsDraft(payload: draftPayload, userId: userId) {
            model.fail(message)
        } else {
            dismiss()
        }
    }

    func cancelAndCleanup() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        drafts?.discardSnapshot()
        dismiss()
    }

}
