import Supabase
import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: (UUID) -> Void

    /// Resuming a server draft (M47): its payload hydrates the form, and the row
    /// is deleted once the pebble publishes.
    var resuming: PebbleDraftRecord?

    @Environment(SupabaseService.self) var supabase
    @Environment(ReferenceDataService.self) var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(PebbleDraftsService.self) var draftsService
    @Environment(ComposerSnapshotStore.self) var snapshots
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) var dismiss

    @State var draft = PebbleDraft()
    @State var selectedGlyph: Glyph?

    @State private var isSaving = false
    @State var saveError: String?

    @State private var isPhotoPickerPresented = false

    @State var isSavingDraft = false
    /// The server draft this composer is bound to — the resumed one, or the one
    /// created by the first "Save as draft".
    @State var serverDraftId: UUID?
    @State var autosave: ComposerAutosave?
    @State var restorableSnapshot: PebbleDraftPayload?
    @State var isRestorePromptPresented = false
    @State var hasCheckedSnapshot = false

    /// Lazily constructed in `.task` so we have access to `supabase.client`.
    /// Nil only for the very first body render before `.task` fires.
    @State var snaps: SnapUploadCoordinator?

    let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    var currentUserId: UUID? {
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
                    ToolbarItemGroup(placement: .bottomBar) {
                        VisibilityChip(visibility: $draft.visibility)
                        Spacer()
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

        do {
            let response = try await PebblePublisher(client: supabase.client)
                .publish(draft: draft, formSnap: snaps?.formSnap, userId: userId)
            // Soft success returns a nil delta; `notifyEarned` no-ops on zero,
            // which is exactly what the old inline branch did.
            karma.notifyEarned(amount: response.karmaDelta ?? 0, reason: .pebbleCreated)
            achievements.fireCheck()
            await consumeDraftAfterPublish()
            onCreated(response.pebbleId)
            dismiss()
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
