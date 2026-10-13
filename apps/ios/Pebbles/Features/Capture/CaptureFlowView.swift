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

    private struct CompleteTarget: Equatable {
        let momentId: UUID
        let name: String?
    }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "capture-flow")

    private var currentUserId: UUID? { supabase.session?.user.id }

    /// Non-nil while the attached photo blocks saving: a snap must be neither
    /// in flight nor failed.
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
        .onChange(of: snaps?.formSnap != nil) { _, hasSnap in model.hasSnap = hasSnap }
        .onChange(of: pickedPhoto) { _, picked in
            guard let picked, let userId = currentUserId, let snaps else { return }
            Task {
                await snaps.handlePicked(picked, userId: userId)
                // Seed the when step from the photo before the user gets there.
                model.applyCaptureDate(snaps.pickedCaptureDate)
            }
        }
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
                ? RecordStepSecondaryAction(label: "Make it a pebble now", enabled: canSaveNow) {
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
        case .when:
            RecordWhenStep(
                happenedAt: Binding(
                    get: { model.draft.happenedAt },
                    set: { model.draft.happenedAt = $0 }
                ),
                seededFromPhoto: snaps?.pickedCaptureDate != nil
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
            return .primary("Save moment", enabled: canSaveNow, loading: model.isSaving) {
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
        model.beginSaving()
        let payload = SaveMomentPayload(capture: model.draft, formSnap: snaps?.formSnap, userId: userId)
        do {
            let momentId = try await MomentsService(client: supabase.client).saveMoment(payload)
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
