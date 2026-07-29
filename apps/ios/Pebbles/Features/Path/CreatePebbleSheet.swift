import Supabase
import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: (UUID) -> Void

    /// Resuming a server draft (M47): its payload hydrates the form, and the row
    /// is deleted once the pebble publishes.
    var resuming: PebbleDraftRecord?

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(PebbleDraftsService.self) private var draftsService
    @Environment(ComposerSnapshotStore.self) private var snapshots
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var selectedGlyph: Glyph?

    @State private var isSaving = false
    @State private var saveError: String?

    @State private var isPhotoPickerPresented = false

    @State private var isSavingDraft = false
    /// The server draft this composer is bound to — the resumed one, or the one
    /// created by the first "Save as draft".
    @State private var serverDraftId: UUID?
    @State private var autosave: ComposerAutosave?
    @State private var restorableSnapshot: PebbleDraftPayload?
    @State private var isRestorePromptPresented = false
    @State private var hasCheckedSnapshot = false

    /// Lazily constructed in `.task` so we have access to `supabase.client`.
    /// Nil only for the very first body render before `.task` fires.
    @State private var snaps: SnapUploadCoordinator?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    private var currentUserId: UUID? {
        supabase.session?.user.id
    }

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("New pebble")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Cancel") {
                            Task { await cancelAndCleanup() }
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving || isSavingDraft {
                            ProgressView()
                        } else {
                            PebbleToolbarButton("Save") {
                                Task { await save() }
                            }
                            .disabled(!draft.isValid)
                        }
                    }
                    // Quick capture: ungated, unlike Save (design D5). "Just a
                    // name" is a valid draft.
                    ToolbarItem(placement: .bottomBar) {
                        PebbleToolbarButton("Save as draft") {
                            Task { await saveAsDraft() }
                        }
                        .disabled(!isSavableAsDraft || isSaving || isSavingDraft)
                    }
                }
                .pebblesScreen()
        }
        .task {
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
            if autosave == nil {
                autosave = ComposerAutosave(store: snapshots)
            }
            hydrateOrOfferRestore()
        }
        .onChange(of: refs.hasLoaded) { _, _ in
            // `.task` may have run before the reference fetch settled.
            hydrateOrOfferRestore()
        }
        .onChange(of: draftPayload) { _, newValue in
            // Debounced inside ComposerAutosave. Held off while the restore
            // prompt is up so the pending answer is not overwritten first.
            guard !isRestorePromptPresented else { return }
            autosave?.schedule(newValue)
        }
        .onChange(of: scenePhase) { _, phase in
            // Last reliable moment before a process kill.
            if phase != .active { autosave?.flush() }
        }
        .alert("Pick up where you left off?", isPresented: $isRestorePromptPresented) {
            Button("Restore it") { restoreSnapshot() }
            Button("Start fresh", role: .destructive) { discardSnapshot() }
        } message: {
            Text("We kept what you were writing here. Add your photo again when you're ready.")
        }
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked, let userId = currentUserId, let snaps {
                    Task { await snaps.handlePicked(picked, userId: userId) }
                }
            }
        }
    }

    @ViewBuilder
    private var content: some View {
        PebbleFormView(
            draft: $draft,
            domains: refs.domains,
            souls: refs.souls,
            collections: refs.collections,
            saveError: saveError,
            selectedGlyph: selectedGlyph,
            onGlyphPicked: { picked in selectedGlyph = picked },
            showsPhotoSection: true,
            photoPickerPresented: $isPhotoPickerPresented,
            formSnap: snaps?.formSnap,
            onRetryPending: {
                if let userId = currentUserId, let snaps {
                    Task { await snaps.retryCurrent(userId: userId) }
                }
            },
            onRemovePending: {
                if let userId = currentUserId, let snaps {
                    Task { await snaps.removePending(userId: userId) }
                }
            }
        )
        .onChange(of: draft.glyphId) { _, newValue in
            if newValue == nil { selectedGlyph = nil }
        }
    }

    private func cancelAndCleanup() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }

    // MARK: - save

    private func save() async {
        guard draft.isValid else { return }

        if snaps?.isUploading == true {
            logger.notice("save blocked: snap still uploading")
            saveError = "Photo is still uploading."
            return
        }
        if snaps?.hasFailed == true {
            logger.notice("save blocked: snap upload failed")
            saveError = "Photo upload failed. Retry or remove it."
            return
        }

        guard let userId = currentUserId else {
            logger.error("save: no current user id")
            saveError = "You must be signed in to save."
            return
        }

        isSaving = true
        saveError = nil

        let payload = PebbleCreatePayload(from: draft, formSnap: snaps?.formSnap, userId: userId)
        let requestBody = ComposePebbleRequest(payload: payload)

        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let response: ComposePebbleResponse = try await supabase.client.functions
                .invoke(
                    "compose-pebble",
                    options: FunctionInvokeOptions(body: requestBody),
                    decoder: decoder
                )
            karma.notifyEarned(amount: response.karmaDelta ?? 0, reason: .pebbleCreated)
            await consumeDraftAfterPublish()
            onCreated(response.pebbleId)
            dismiss()
        } catch let functionsError as FunctionsError {
            if let pebbleId = softSuccessPebbleId(from: functionsError) {
                logger.warning("compose-pebble returned 5xx but pebble_id found — advancing to detail sheet")
                await consumeDraftAfterPublish()
                onCreated(pebbleId)
                dismiss()
            } else {
                logger.error("compose-pebble failed: \(functionsError.localizedDescription, privacy: .private)")
                await handleSaveFailure(functionsError)
            }
        } catch {
            logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
            await handleSaveFailure(error)
        }
    }

    /// Save failed and we cannot recover — fire the compensating snap delete
    /// (if a snap was attached) and surface a user-facing message.
    private func handleSaveFailure(_ error: Error) async {
        if let userId = currentUserId, let snaps {
            await snaps.handleSaveFailure(userId: userId)
        }
        saveError = userMessageForPebbleSaveError(error)
        isSaving = false
    }

    /// Tries to extract a `pebble_id` UUID from the raw error body of a
    /// `FunctionsError.httpError`. Returns `nil` if the body is absent,
    /// unparseable, or missing the `pebble_id` key.
    private func softSuccessPebbleId(from error: FunctionsError) -> UUID? {
        guard case let .httpError(_, data) = error, !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(PebbleIdPartial.self, from: data).pebbleId
    }
}

// MARK: - Drafts (M47)

/// The **server** draft (an intentional "save as draft") and the **local**
/// snapshot (crash insurance) share one payload shape and nothing else. Design:
/// docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md.
extension CreatePebbleSheet {

    /// Drives both autosave and the server draft, so the two cannot disagree.
    fileprivate var draftPayload: PebbleDraftPayload {
        PebbleDraftPayload(from: draft, formSnap: snaps?.formSnap, userId: currentUserId)
    }

    fileprivate var isSavableAsDraft: Bool {
        draft.isSavableAsDraft(formSnap: snaps?.formSnap, userId: currentUserId)
    }

    /// Souls/collections are deletable, so a draft can outlive them. Glyphs are
    /// verified server-side below.
    fileprivate var knownIds: PebbleDraft.KnownIds {
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
    fileprivate func hydrateOrOfferRestore() {
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

    fileprivate func restoreSnapshot() {
        guard let snapshot = restorableSnapshot else { return }
        draft = PebbleDraft(payload: snapshot, known: knownIds)
        restorableSnapshot = nil
        Task { await verifyGlyph() }
    }

    fileprivate func discardSnapshot() {
        restorableSnapshot = nil
        autosave?.clear()
    }

    /// Drop a glyph the user can no longer use, and load the one they can for the
    /// form's preview (D7). `can_use_glyph` is what `create_pebble` enforces, so
    /// passing here means publish cannot 42501.
    fileprivate func verifyGlyph() async {
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
    fileprivate func saveAsDraft() async {
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
    fileprivate func consumeDraftAfterPublish() async {
        autosave?.clear()
        guard let serverDraftId else { return }
        await draftsService.deleteIgnoringFailure(id: serverDraftId)
        self.serverDraftId = nil
        await draftsService.refreshCount()
    }
}

private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}

private struct PebbleIdPartial: Decodable {
    let pebbleId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}

#Preview {
    let supabase = SupabaseService()
    return CreatePebbleSheet(onCreated: { _ in })
        .environment(supabase)
        .environment(ReferenceDataService(client: supabase.client))
        .environment(KarmaNotificationService())
        .environment(PebbleDraftsService(client: supabase.client))
        .environment(ComposerSnapshotStore())
}

/// Maps a thrown error to a user-facing localized string. Module-private so
/// `CreatePebbleSheet` and `EditPebbleSheet` share one mapping.
func userMessageForPebbleSaveError(_ error: Error) -> String {
    if let fnError = error as? FunctionsError, case let .httpError(_, data) = fnError,
       let body = try? JSONDecoder().decode([String: String].self, from: data) {
        let message = body["error"] ?? body["message"] ?? ""
        if message.contains("media_quota_exceeded") || message.contains("P0001") {
            return "Photo limit reached on this pebble."
        }
    }
    if let pipelineError = error as? ImagePipelineError {
        switch pipelineError {
        case .unsupportedFormat:    return "That image format isn't supported."
        case .decodeFailed:         return "Couldn't read the image."
        case .encodeFailed:         return "Couldn't process the image."
        case .tooLargeAfterResize:  return "That image is too large to attach."
        }
    }
    return "Couldn't save your pebble. Please try again."
}
