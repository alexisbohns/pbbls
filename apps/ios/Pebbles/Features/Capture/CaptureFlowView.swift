import PhotosUI
import SwiftUI
import os

/// The Capture flow (spec §6, A1): photo · when · name → `save_moment`.
/// "Make it a pebble now" saves, then swaps this same cover to the Complete
/// flow for the new moment, so there is never a second presentation.
struct CaptureFlowView: View {
    /// Fired after the moment is saved and again after it is completed, so
    /// the Path reloads and focuses its week (A5).
    let onMomentChanged: (UUID) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var model = CaptureFlowModel()
    @State private var snaps: SnapUploadCoordinator?
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var isCloseConfirmPresented = false
    /// Drives the step transition direction so back slides back.
    @State private var isMovingBack = false
    /// Set once the moment is saved by "Make it a pebble now": the cover then
    /// shows Complete for it instead of Capture.
    @State private var completing: CompleteTarget?
    /// Which button started the save in flight, so only that one spins.
    @State private var savingThenComplete = false

    private struct CompleteTarget: Equatable {
        let momentId: UUID
        let name: String?
    }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "capture-flow")

    private var currentUserId: UUID? { supabase.session?.user.id }

    /// Non-nil while the attached photo blocks saving: a snap must be neither
    /// in flight nor failed. A pick still being read counts as uploading.
    private var snapBlockedMessage: String? {
        if snaps?.isUploading == true { return String(localized: "Photo is still uploading.") }
        if snaps?.hasFailed == true { return String(localized: "Photo upload failed. Retry or remove it.") }
        return nil
    }

    private var canSaveNow: Bool { model.canSave && snapBlockedMessage == nil && !model.isSaving }

    /// R2: a picked photo counts as something entered from the moment it is
    /// picked, before the coordinator has a snap for it.
    private var hasSomethingToKeep: Bool { model.canSave || pickedPhoto != nil }

    var body: some View {
        Group {
            if let completing {
                CompleteFlowView(
                    momentId: completing.momentId,
                    momentName: completing.name,
                    onCompleted: onMomentChanged
                )
            } else {
                capture
            }
        }
        .task {
            TapHaptics.prepare()
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
        }
    }

    // MARK: - Capture

    private var capture: some View {
        VStack(spacing: 0) {
            RecordFlowChrome(
                dotCount: CaptureStep.allCases.count,
                dotIndex: model.step.dotIndex,
                canGoBack: model.step.previous != nil && !model.isSaving,
                onBack: { model.back() },
                onClose: handleClose
            )
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.system.background)
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in
            isMovingBack = new.rawValue < old.rawValue
        }
        // A pick being read already counts: `Done`, not `Skip`.
        .onChange(of: snaps?.formSnap != nil || snaps?.isProcessingPick == true) { _, hasSnap in
            model.hasSnap = hasSnap
        }
        .onChange(of: pickedPhoto) { _, picked in
            guard let picked, let userId = currentUserId, let snaps else { return }
            // Never cancelled: once the cover closes or saves, the coordinator
            // rejects the pick and deletes whatever it already uploaded.
            Task { await snaps.handlePicked(picked, userId: userId) }
        }
        // Seed the when step as soon as the photo's date is read, before its
        // upload finishes; the model keeps a date the user set.
        .onChange(of: snaps?.pickedCaptureDate) { _, date in model.applyCaptureDate(date) }
        .confirmationDialog(
            "Keep this moment?",
            isPresented: $isCloseConfirmPresented,
            titleVisibility: .visible
        ) {
            if canSaveNow {
                Button("Save moment") { Task { await save(thenComplete: false) } }
            }
            Button("Discard", role: .destructive) { Task { await discard() } }
            Button("Keep going", role: .cancel) {}
        }
    }

    @ViewBuilder
    private var stepBody: some View {
        RecordStepScaffold(
            title: model.step.title,
            subtitle: model.step.subtitle,
            action: action(for: model.step),
            // The library grid runs to the bottom, `Skip`/`Done` over it.
            contentFillsHeight: model.step == .photo,
            secondaryAction: model.step == .name
                ? RecordStepSecondaryAction(
                    label: "Make it a pebble now",
                    enabled: canSaveNow,
                    isLoading: model.isSaving && savingThenComplete
                ) {
                    Task { await save(thenComplete: true) }
                }
                : nil
        ) {
            stepContent
        }
        .id(model.step)
        .transition(.asymmetric(
            insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
            removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
        ))
    }

    @ViewBuilder
    private var stepContent: some View {
        switch model.step {
        case .photo:
            RecordPhotoStep(
                snap: snaps?.formSnap,
                selection: $pickedPhoto,
                onRetry: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.retryCurrent(userId: userId) }
                    }
                },
                onRemove: {
                    pickedPhoto = nil
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.removePending(userId: userId) }
                    }
                }
            )
            // A save started from the close dialog sends the photo on screen;
            // neither a new pick nor Remove may change it under the save.
            .disabled(model.isSaving)
        case .when:
            RecordWhenStep(
                happenedAt: Binding(
                    get: { model.draft.happenedAt },
                    set: { model.setHappenedAt($0) }
                ),
                // Only while the date on screen is still the photo's.
                seededFromPhoto: snaps?.pickedCaptureDate != nil && !model.hasEditedDate
            )
        case .name:
            VStack(spacing: Spacing.md) {
                RecordNameStep(
                    name: model.draft.name,
                    limit: CaptureFlowModel.nameLimit,
                    onChange: { model.setName($0) }
                )
                if let blocked = snapBlockedMessage {
                    Text(verbatim: blocked)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.center)
                }
                if let error = model.saveError {
                    Text(verbatim: error)
                        .pebblesFont(.callout)
                        .foregroundStyle(.red) // #921
                        .multilineTextAlignment(.center)
                }
            }
        }
    }

    /// The one action a step offers. The photo step never auto-advances on
    /// pick (A1): `Skip` until a photo is attached, then `Done`.
    private func action(for step: CaptureStep) -> RecordStepAction {
        switch step {
        case .photo:
            return .primary(model.optionalButtonIsSkip ? "Skip" : "Done", enabled: true, loading: false) {
                model.advance()
            }
        case .when:
            return .primary("Continue", enabled: true, loading: false) { model.advance() }
        case .name:
            return .primary("Save moment", enabled: canSaveNow, loading: model.isSaving && !savingThenComplete) {
                Task { await save(thenComplete: false) }
            }
        }
    }

    // MARK: - Leaving

    /// ✕ only asks when there is something to keep (R2). Ignored while a save
    /// is in flight, so Discard can never delete a photo the save is sending.
    private func handleClose() {
        guard !model.isSaving else { return }
        if hasSomethingToKeep {
            isCloseConfirmPresented = true
        } else {
            Task { await discard() }
        }
    }

    /// The only path that deletes the uploaded photo's files (A2).
    private func discard() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }

    // MARK: - Save

    private func save(thenComplete: Bool) async {
        guard !model.isSaving else { return }
        guard let userId = currentUserId else {
            logger.error("save_moment: no current user id")
            failSave(MomentWriteError.notAuthenticated.userMessage)
            return
        }
        if let blocked = snapBlockedMessage {
            failSave(blocked)
            return
        }
        savingThenComplete = thenComplete
        model.beginSaving()
        // From here until the save settles, no pick or Remove can change the
        // photo the payload sends.
        snaps?.beginCommit()
        let payload = SaveMomentPayload(capture: model.draft, formSnap: snaps?.formSnap, userId: userId)
        do {
            let momentId = try await MomentsService(client: supabase.client).saveMoment(payload)
            // The photo is the saved moment's now; a late pick must not touch it.
            snaps?.markCommitted()
            model.succeed()
            onMomentChanged(momentId)
            if thenComplete {
                completing = CompleteTarget(momentId: momentId, name: MomentName.display(model.draft.name))
            } else {
                dismiss()
            }
        } catch {
            // A2: the uploaded photo stays so a retry can use it.
            logger.error("save_moment failed: \(error.localizedDescription, privacy: .private)")
            snaps?.abortCommit()
            failSave(userMessageForMomentWriteError(error))
        }
    }

    /// A save can start from the close dialog on any step; the error lives on
    /// the name step, so a failure lands there to be seen.
    private func failSave(_ message: String) {
        if model.step != .name { model.go(to: .name) }
        model.fail(message)
    }
}
