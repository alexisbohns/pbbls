import Supabase
import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: (UUID) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var selectedGlyph: Glyph?

    @State private var isSaving = false
    @State private var saveError: String?

    @State private var isPhotoPickerPresented = false

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
                .navigationTitle("New pebble")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") {
                            Task { await cancelAndCleanup() }
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            Button("Save") {
                                Task { await save() }
                            }
                            .disabled(!draft.isValid)
                        }
                    }
                }
                .pebblesScreen()
        }
        .task {
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
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
            onCreated(response.pebbleId)
            dismiss()
        } catch let functionsError as FunctionsError {
            if let pebbleId = softSuccessPebbleId(from: functionsError) {
                logger.warning("compose-pebble returned 5xx but pebble_id found — advancing to detail sheet")
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
