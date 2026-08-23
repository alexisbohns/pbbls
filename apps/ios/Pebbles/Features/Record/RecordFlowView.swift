import SwiftUI
import os

/// The step-by-step pebble composer (M58) — the default way to record a pebble
/// on iOS. `CreatePebbleSheet` remains reachable by long-pressing the `+` (D1).
///
/// Presented as a `fullScreenCover`. Owns the coordinators the flow needs and
/// the orchestration between them; everything about *the flow itself* — gating,
/// back, skip labels, resume, haptics — lives on `RecordFlowModel`.
struct RecordFlowView: View {
    /// Fired as soon as the pebble publishes, while the success step is still
    /// up, so the Path is already reloaded by the time the user exits (D10).
    let onPublished: (UUID) -> Void

    /// Resuming a server draft: its payload hydrates the flow at the first
    /// unanswered step, and the row is deleted once the pebble publishes (D9).
    var resuming: PebbleDraftRecord?

    @Environment(SupabaseService.self) var supabase
    @Environment(ReferenceDataService.self) var refs
    @Environment(KarmaNotificationService.self) private var karma
    @Environment(AchievementsService.self) private var achievements
    @Environment(PebbleDraftsService.self) private var draftsService
    @Environment(ComposerSnapshotStore.self) private var snapshots
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) var dismiss

    @State var model = RecordFlowModel()
    @State var selectedGlyph: Glyph?

    /// Lazily constructed in `.task` so they have `supabase.client`.
    @State var snaps: SnapUploadCoordinator?
    @State var drafts: ComposerDraftCoordinator?

    @State private var isPhotoPickerPresented = false
    @State var isCloseConfirmPresented = false
    /// Drives the step transition direction so back slides back.
    @State private var isMovingBack = false

    let logger = Logger(subsystem: "app.pbbls.ios", category: "record-flow")

    var currentUserId: UUID? { supabase.session?.user.id }
    private var hasSnapNow: Bool { snaps?.formSnap != nil }

    var draftPayload: PebbleDraftPayload {
        PebbleDraftPayload(from: model.draft, formSnap: snaps?.formSnap, userId: currentUserId)
    }

    var isSavableAsDraft: Bool {
        model.draft.isSavableAsDraft(formSnap: snaps?.formSnap, userId: currentUserId)
    }

    var knownIds: PebbleDraft.KnownIds {
        PebbleDraft.KnownIds(
            soulIds: Set(refs.souls.map(\.id)),
            collectionIds: Set(refs.collections.map(\.id))
        )
    }

    /// Non-nil while the attached photo blocks publishing. Same two rules the
    /// sheet enforces — a snap must be neither in flight nor failed.
    private var snapBlockedMessage: String? {
        if snaps?.isUploading == true { return String(localized: "Photo is still uploading.") }
        if snaps?.hasFailed == true { return String(localized: "Photo upload failed. Retry or remove it.") }
        return nil
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            if model.step != .success {
                RecordFlowChrome(step: model.step, onBack: { model.back() }, onClose: handleClose)
            }
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.system.background)
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in
            isMovingBack = new.rawValue < old.rawValue
        }
        .task {
            TapHaptics.prepare()
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
            if drafts == nil {
                drafts = ComposerDraftCoordinator(
                    client: supabase.client, drafts: draftsService, snapshots: snapshots
                )
            }
            hydrateOrOfferRestore()
        }
        // `.task` may have run before the reference fetch settled.
        .onChange(of: refs.hasLoaded) { _, _ in hydrateOrOfferRestore() }
        .onChange(of: hasSnapNow) { _, newValue in model.hasSnap = newValue }
        .onChange(of: draftPayload) { _, newValue in
            // Held off while the restore prompt is up so the pending answer is
            // not overwritten first.
            guard drafts?.isRestorePromptPresented != true else { return }
            drafts?.schedule(newValue)
        }
        .onChange(of: scenePhase) { _, phase in
            // Last reliable moment before a process kill.
            if phase != .active { drafts?.flush() }
        }
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                guard let picked, let userId = currentUserId, let snaps else { return }
                Task {
                    await snaps.handlePicked(picked, userId: userId)
                    // Seed the date step from the photo before the user gets there (D7).
                    model.applyCaptureDate(snaps.pickedCaptureDate)
                }
            }
        }
        .alert("Pick up where you left off?", isPresented: Binding(
            get: { drafts?.isRestorePromptPresented ?? false },
            set: { drafts?.isRestorePromptPresented = $0 }
        )) {
            Button("Restore it") { restoreSnapshot() }
            Button("Start fresh", role: .destructive) { drafts?.discardSnapshot() }
        } message: {
            Text("We kept what you were writing here. Add your photo again when you're ready.")
        }
        .confirmationDialog(
            "Keep this pebble?",
            isPresented: $isCloseConfirmPresented,
            titleVisibility: .visible
        ) {
            Button("Save as draft") { Task { await saveAsDraftAndClose() } }
            Button("Discard", role: .destructive) { Task { await cancelAndCleanup() } }
            Button("Keep going", role: .cancel) {}
        }
    }

    // MARK: - Steps

    @ViewBuilder
    private var stepBody: some View {
        Group {
            if model.step == .success, let response = model.published {
                RecordSuccessStep(
                    name: model.draft.name,
                    response: response,
                    valence: model.draft.valence ?? .neutralMedium,
                    emotionId: model.draft.emotionId,
                    onExit: { dismiss() }
                )
            } else {
                RecordStepScaffold(
                    title: model.step.title,
                    subtitle: model.step.subtitle,
                    action: action(for: model.step)
                ) {
                    RecordStepContent(
                        step: model.step,
                        model: model,
                        snap: snaps?.formSnap,
                        seededFromPhoto: snaps?.pickedCaptureDate != nil,
                        snapBlockedMessage: snapBlockedMessage,
                        selectedGlyph: $selectedGlyph,
                        onPickPhoto: { isPhotoPickerPresented = true },
                        onRetryPhoto: {
                            if let userId = currentUserId, let snaps {
                                Task { await snaps.retryCurrent(userId: userId) }
                            }
                        },
                        onRemovePhoto: {
                            if let userId = currentUserId, let snaps {
                                Task { await snaps.removePending(userId: userId) }
                            }
                        }
                    )
                }
            }
        }
        .id(model.step)
        .transition(.asymmetric(
            insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
            removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
        ))
    }

    /// The one action a step offers, if any. Tile steps offer none — the pick
    /// is the advance (D3).
    private func action(for step: RecordStep) -> RecordStepAction? {
        switch step {
        case .emotion, .domain, .success:
            return nil

        // Unlike the other tile steps, valence commits without advancing (the
        // fan is worth looking at once a stone is lit), so it needs a button.
        case .valence:
            return .primary("Continue", enabled: model.isAnswered, loading: false) { model.advance() }

        case .when:
            return .primary("Continue", enabled: true, loading: false) { model.advance() }

        case .name:
            return .primary("Continue", enabled: model.isAnswered, loading: false) { model.advance() }

        case .privacy:
            return .primary(
                "Publish",
                enabled: snapBlockedMessage == nil && model.draft.isValid,
                loading: model.isPublishing
            ) {
                Task { await publish() }
            }

        case .photo, .souls, .collection, .glyph:
            return .text(model.optionalButtonIsSkip ? "Skip" : "Done") { model.advance() }
        }
    }

    // MARK: - Publish

    private func publish() async {
        guard let userId = currentUserId else {
            logger.error("publish: no current user id")
            model.fail(String(localized: "You must be signed in to save."))
            return
        }
        if let blocked = snapBlockedMessage {
            logger.notice("publish blocked by snap state")
            model.fail(blocked)
            return
        }

        model.beginPublish()
        do {
            let response = try await PebblePublisher(client: supabase.client)
                .publish(draft: model.draft, formSnap: snaps?.formSnap, userId: userId)
            // The success step shows the amount, so the pastille would be
            // redundant — but the sound and haptic still fire (D10).
            karma.notifyEarned(
                amount: response.karmaDelta ?? 0, reason: .pebbleCreated, presentsCapsule: false
            )
            achievements.fireCheck()
            await drafts?.consumeAfterPublish()
            model.succeed(with: response)
            // Reload the Path behind the cover so it is fresh on exit.
            onPublished(response.pebbleId)
        } catch {
            logger.error("publish failed: \(error.localizedDescription, privacy: .private)")
            if let snaps { await snaps.handleSaveFailure(userId: userId) }
            model.fail(userMessageForPebbleSaveError(error))
        }
    }
}
