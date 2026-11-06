import PhotosUI
import SwiftUI
import os

/// The Capture flow (record-flow D1, D5): photo → moment page → `save_moment`.
/// "Save & make it a pebble" saves, then swaps this same cover to the
/// Complete flow for the new moment, so there is never a second presentation
/// (D6). The unsaved page is autosaved on the device so a kill brings it back
/// (D2).
struct CaptureFlowView: View {
    /// Fired after the moment is saved and again after it is completed, so
    /// the Path reloads and focuses its week (A5).
    let onMomentChanged: (UUID) -> Void
    /// Home's "New pebble": the final step saves and swaps straight to
    /// Complete, so the save/make-a-pebble choice is not offered.
    var chainsComplete = false
    /// Home's "Quick moment" (D13): the page straight away, no photo screen
    /// and no way back to it.
    var startsAtMomentPage = false

    @Environment(SupabaseService.self) private var supabase
    @Environment(CaptureDraftStore.self) private var drafts
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dismiss) private var dismiss

    @State private var model: CaptureFlowModel
    @State private var snaps: SnapUploadCoordinator?
    @State private var pickedPhoto: PhotosPickerItem?
    /// The in-flow camera (D3), presented over the photo step.
    @State private var showsCamera = false
    @State private var isCloseConfirmPresented = false
    /// Drives the step transition direction so back slides back.
    @State private var isMovingBack = false
    /// Set once the moment is saved by "Save & make it a pebble": the cover then
    /// shows Complete for it instead of Capture.
    @State private var completing: CompleteTarget?
    /// Which button started the save in flight, so only that one spins.
    @State private var savingThenComplete = false
    /// The debounced device-local autosave of the page (D2), built in `.task`
    /// so it has the store and the session.
    @State private var autosaver: CaptureDraftAutosaver?
    /// One-time guard: the restore is attempted once per presentation.
    @State private var hasRestored = false

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
                    onCompleted: onMomentChanged,
                    afterSave: true,
                    momentHappenedAt: model.draft.happenedAt,
                    momentHasWords: !model.draft.description.trimmingCharacters(in: .whitespaces).isEmpty,
                    onRecordAnother: { startAnother() }
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
            if autosaver == nil {
                autosaver = CaptureDraftAutosaver(store: drafts, ownerId: { [supabase] in supabase.session?.user.id })
            }
            // After the coordinator exists, so a restored photo has a place to land.
            restoreIfNeeded()
        }
    }

    // MARK: - Capture

    private var capture: some View {
        VStack(spacing: 0) {
            RecordFlowChrome(
                dotCount: CaptureStep.allCases.count,
                dotIndex: model.step.dotIndex,
                canGoBack: model.step.previous != nil && !model.isSaving && !startsAtMomentPage,
                onBack: { model.back() },
                onClose: handleClose
            )
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color.pebbles.surface)
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
        .onChange(of: model.draft) { _, _ in scheduleAutosave() }
        .onChange(of: model.hasEditedDate) { _, _ in scheduleAutosave() }
        // D3: a new pick lands, a beat, the moment page. Photo step only (a
        // restore moves to the page before seeding), new snap id only (an
        // upload state change must not bounce a "Change photo" user back).
        // A task outliving a dismiss just advances an unrendered model.
        .onChange(of: snaps?.formSnap) { old, new in
            scheduleAutosave()
            guard model.step == .photo, case .pending(let attached) = new else { return }
            if case .pending(let previous) = old, previous.id == attached.id { return }
            Task {
                try? await Task.sleep(for: .milliseconds(400))
                if model.step == .photo { model.advance() }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            // Last reliable moment before a process kill.
            if phase != .active { autosaver?.flush() }
        }
        .fullScreenCover(isPresented: $showsCamera) {
            CameraPicker(
                onCapture: { image, shutter in
                    showsCamera = false
                    guard let userId = currentUserId, let snaps,
                          let data = image.jpegData(compressionQuality: 0.9) else { return }
                    // The shutter is when, until the user says otherwise.
                    model.applyCaptureDate(shutter)
                    Task { await snaps.handleCaptured(data, userId: userId) }
                },
                onCancel: { showsCamera = false }
            )
            .ignoresSafeArea()
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
            // The grid runs to the bottom under the two controls; nothing floats.
            contentFillsHeight: model.step == .photo,
            fixedSlot: model.step == .photo
                ? AnyView(RecordPhotoControls(onJustWrite: { model.advance() }, onCamera: { showsCamera = true }))
                : nil,
            secondaryAction: model.step == .moment && !chainsComplete
                ? RecordStepSecondaryAction(
                    label: "Just save",
                    enabled: canSaveNow,
                    isLoading: model.isSaving && !savingThenComplete
                ) {
                    Task { await save(thenComplete: false) }
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
            RecordPhotoStep(selection: $pickedPhoto)
            // A save started from the close dialog sends the photo on screen;
            // neither a new pick nor Remove may change it under the save.
            .disabled(model.isSaving)
        case .moment:
            RecordMomentStep(
                model: model,
                snap: snaps?.formSnap,
                blockedMessage: snapBlockedMessage,
                onChangePhoto: { model.back() },
                onRetry: {
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.retryCurrent(userId: userId) }
                    }
                },
                onRemove: {
                    pickedPhoto = nil
                    // A restored photo is pending too, so this deletes its files.
                    if let userId = currentUserId, let snaps {
                        Task { await snaps.removePending(userId: userId) }
                    }
                }
            )
        }
    }

    /// The one action a step offers. The photo step has none: its controls
    /// are the fixed slot above the grid.
    private func action(for step: CaptureStep) -> RecordStepAction? {
        switch step {
        case .photo:
            return nil
        case .moment:
            return .primary(
                "Save & make it a pebble",
                enabled: canSaveNow,
                loading: model.isSaving && (chainsComplete || savingThenComplete)
            ) {
                Task { await save(thenComplete: true) }
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

    /// The only path that deletes the uploaded photo's files (A2), a restored
    /// one included: it is re-seeded as a pending upload, so the coordinator
    /// deletes it by id like a fresh pick.
    private func discard() async {
        autosaver?.clear()
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }

    // MARK: - Save

    /// "Record another" on the end screen (D12): the same cover, a fresh grid.
    private func startAnother() {
        completing = nil
        model = CaptureFlowModel(startingAt: startsAtMomentPage ? .moment : .photo)
        pickedPhoto = nil
        snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
        savingThenComplete = false
        hasRestored = true
        // The save cleared the autosave; the restore that re-enables it is skipped here.
        autosaver?.enabled = true
    }

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
            autosaver?.clear()
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
    /// the moment page, so a failure lands there to be seen.
    private func failSave(_ message: String) {
        if model.step != .moment { model.go(to: .moment) }
        model.fail(message)
    }
}

// MARK: - Draft (D2)

extension CaptureFlowView {
    private func scheduleAutosave() {
        guard let userId = currentUserId, completing == nil else { return }
        autosaver?.schedule(.init(
            draft: model.draft,
            hasEditedDate: model.hasEditedDate,
            snap: CaptureDraftAutosaver.savedSnap(from: snaps?.formSnap, userId: userId)
        ))
    }

    /// A page the user was on when the app died comes back as it was (D2).
    /// Its photo comes back as a pending, already-uploaded snap (no thumbnail
    /// bytes), so Discard, Remove and a new pick delete its Storage files by
    /// id exactly as for a fresh upload; `savedSnap` reads it back through
    /// `storagePrefix`.
    private func restoreIfNeeded() {
        guard !hasRestored else { return }
        hasRestored = true
        defer { autosaver?.enabled = true }
        guard let userId = currentUserId, let saved = drafts.load(ownerId: userId) else { return }
        model.restore(saved.draft, hasEditedDate: saved.hasEditedDate)
        if let snap = saved.snap {
            snaps?.seedExisting(.pending(AttachedSnap(id: snap.id, localThumb: Data(), state: .uploaded)))
        }
    }
}

extension CaptureFlowView {
    init(onMomentChanged: @escaping (UUID) -> Void, chainsComplete: Bool = false, startsAtMomentPage: Bool = false) {
        self.onMomentChanged = onMomentChanged
        self.chainsComplete = chainsComplete
        self.startsAtMomentPage = startsAtMomentPage
        _model = State(initialValue: CaptureFlowModel(startingAt: startsAtMomentPage ? .moment : .photo))
    }
}
