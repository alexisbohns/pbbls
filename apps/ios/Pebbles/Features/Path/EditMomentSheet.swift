import SwiftUI
import os

/// Edit moment (R5): when, name, description, photo → `save_moment` only.
/// The pebble's fields edit in `EditPebbleSheet`.
///
/// Removing the photo only detaches it (R6): Save sends the whole `snaps`
/// set (`[]` when removed) and the server checks `moment_empty` in that same
/// write. The detached files are deleted best-effort after a successful save;
/// a failed save or Cancel keeps them.
struct EditMomentSheet: View {
    let detail: MomentDetail
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft: EditMomentDraft
    /// Built in `.task` so it has `supabase.client`; seeded with the saved photo.
    @State private var snaps: SnapUploadCoordinator?
    @State private var isPhotoPickerPresented = false
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-moment")

    init(detail: MomentDetail, onSaved: @escaping () -> Void) {
        self.detail = detail
        self.onSaved = onSaved
        self._draft = State(initialValue: EditMomentDraft(from: detail))
    }

    private var currentUserId: UUID? { supabase.session?.user.id }
    private var hasSnap: Bool { snaps?.formSnap != nil }
    /// A newly picked photo, not yet saved: swipe-dismiss would skip its cleanup.
    private var hasPendingSnap: Bool {
        if case .pending = snaps?.formSnap { return true }
        return false
    }
    private var canSave: Bool { draft.canSave(hasSnap: hasSnap) && snaps?.isBlocking != true }

    /// Non-nil while the attached photo blocks saving.
    private var snapBlockedMessage: String? {
        if snaps?.isUploading == true { return String(localized: "Photo is still uploading.") }
        if snaps?.hasFailed == true { return String(localized: "Photo upload failed. Retry or remove it.") }
        return nil
    }

    var body: some View {
        NavigationStack {
            EditMomentForm(
                draft: $draft,
                hasSnap: hasSnap || snaps?.isProcessingPick == true,
                isSaving: isSaving,
                saveError: saveError
            ) {
                photoRow
            }
            .pebblesToolbarTitle("Edit moment")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { Task { await cancel() } }
                        .disabled(isSaving)
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        PebbleToolbarButton("Save") { Task { await save() } }
                            .disabled(!canSave)
                    }
                }
            }
            .pebblesScreen()
        }
        .interactiveDismissDisabled(isSaving || hasPendingSnap || snaps?.isProcessingPick == true)
        .onDisappear {
            // Safety net for any dismissal that skipped Cancel. Idempotent:
            // deletes only a pending photo, never the saved or committed one.
            guard let snaps, !snaps.isCommitted, let userId = currentUserId else { return }
            Task { await snaps.cancelAndCleanup(userId: userId) }
        }
        .task {
            guard snaps == nil else { return }
            // Single-photo assumption: iOS shows one photo per moment, so only
            // the first is seeded; save sends the whole set.
            let existing = detail.snaps.first.map { FormSnap.existing(id: $0.id, storagePath: $0.storagePath) }
            snaps = SnapUploadCoordinator(
                repo: PebbleSnapRepository(client: supabase.client),
                initialSnap: existing
            )
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
    private var photoRow: some View {
        Group {
            switch snaps?.formSnap {
            case .none:
                Button { isPhotoPickerPresented = true } label: {
                    HStack {
                        Label { Text("Add a photo") } icon: { PbblsIconImage(.camera, .row) }
                        if snaps?.isProcessingPick == true {
                            Spacer()
                            ProgressView()
                        }
                    }
                }
                .disabled(snaps?.isProcessingPick == true)
            case .existing(_, let storagePath):
                ExistingSnapRow(
                    storagePath: storagePath,
                    isRemoving: false,
                    onRemove: { snaps?.detachExisting() }
                )
            case .pending(let snap):
                AttachedPhotoView(
                    snap: snap,
                    onRetry: {
                        if let userId = currentUserId, let snaps { Task { await snaps.retryCurrent(userId: userId) } }
                    },
                    onRemove: {
                        if let userId = currentUserId, let snaps { Task { await snaps.removePending(userId: userId) } }
                    }
                )
            }
        }
        // The coordinator also refuses changes once the save began.
        .disabled(isSaving)
        .pebblesListRow(position: .only)
    }

    private func cancel() async {
        guard !isSaving else { return }
        if let userId = currentUserId, let snaps {
            // Deletes only a newly picked photo's files; the saved one stays.
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }

    private func save() async {
        guard !isSaving, let snaps else { return }
        guard let userId = currentUserId else {
            logger.error("save_moment (edit): no current user id")
            saveError = MomentWriteError.notAuthenticated.userMessage
            return
        }
        if let blocked = snapBlockedMessage {
            saveError = blocked
            return
        }
        isSaving = true
        saveError = nil
        // From here until the save settles, no pick or Remove can change the
        // photo the payload sends.
        snaps.beginCommit()
        let payload = SaveMomentPayload(edit: draft, momentId: detail.id, formSnap: snaps.formSnap, userId: userId)
        // Only these are purged on success.
        let detachedAtSave = snaps.detachedStoragePaths
        do {
            _ = try await MomentsService(client: supabase.client).saveMoment(payload)
            snaps.markCommitted()
            // R6: the rows are gone; now the files, best-effort and without
            // holding the sheet open.
            Task { await snaps.purgeDetached(detachedAtSave) }
            onSaved()
            dismiss()
        } catch {
            // R6: a failed save keeps every file so a retry can use them.
            logger.error("save_moment (edit) failed: \(error.localizedDescription, privacy: .private)")
            snaps.abortCommit()
            saveError = userMessageForMomentWriteError(error)
            isSaving = false
        }
    }
}

/// Edit moment's list: when, name, description, the photo row the sheet
/// passes in, the R6 hint and the save error. Pure UI.
struct EditMomentForm<PhotoRow: View>: View {
    @Binding var draft: EditMomentDraft
    let hasSnap: Bool
    let isSaving: Bool
    let saveError: String?
    @ViewBuilder let photoRow: () -> PhotoRow

    var body: some View {
        List {
            Section {
                DatePicker("When", selection: $draft.happenedAt, displayedComponents: [.date, .hourAndMinute])
                    .tint(Color.pebbles.primary)
                    .pebblesListRow(position: .top)
                TextField("Name", text: $draft.name)
                    // Clamp on screen like RecordNameStep: a binding setter
                    // leaves the extra characters in the field's buffer.
                    .onChange(of: draft.name) { _, newValue in
                        let clamped = String(newValue.prefix(CaptureFlowModel.nameLimit))
                        if clamped != newValue { draft.name = clamped }
                    }
                    .pebblesListRow(position: .middle)
                TextField("Description (optional)", text: $draft.description, axis: .vertical)
                    .lineLimit(1...5)
                    .pebblesListRow(position: .bottom)
            }
            .disabled(isSaving)

            Section {
                photoRow()
                // R6 hint. A plain row, not a `footer:`: a plain-style list
                // draws footers as opaque, separated cells.
                if !draft.canSave(hasSnap: hasSnap) {
                    Text("A moment needs a name or a photo.")
                        .font(.footnote)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .listRowBackground(Color.clear)
                        .listRowSeparator(.hidden)
                }
            } header: {
                Text("Photo").pebblesSectionHeader()
            }

            if let saveError {
                Section {
                    Text(saveError)
                        .foregroundStyle(Color.pebbles.error)
                        .font(.callout)
                        .pebblesListRow(position: .only)
                }
            }
        }
        .pebblesList()
    }
}
