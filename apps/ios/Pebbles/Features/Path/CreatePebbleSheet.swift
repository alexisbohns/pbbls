import Supabase
import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: (UUID) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var emotions: [Emotion] = []
    @State private var domains: [Domain] = []
    @State private var souls: [Soul] = []
    @State private var collections: [PebbleCollection] = []

    @State private var isLoadingReferences = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var saveError: String?

    /// In-flight processed bytes kept around so the user can retry an upload
    /// without re-picking the photo.
    @State private var processedForRetry: ProcessedImage?

    @State private var isPhotoPickerPresented = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    private var snapRepo: PebbleSnapRepository {
        PebbleSnapRepository(client: supabase.client)
    }

    private var currentUserId: UUID? {
        supabase.session?.user.id
    }

    private var pendingSnapState: AttachedSnap.UploadState? {
        if case .pending(let snap) = draft.formSnap { return snap.state }
        return nil
    }

    private var pendingSnapId: UUID? {
        if case .pending(let snap) = draft.formSnap { return snap.id }
        return nil
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
        .task { await loadReferences() }
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked {
                    Task { await handlePicked(picked) }
                }
            }
        }
        // Retry: chip mutates snap.state from .failed to .uploading; re-run upload.
        .onChange(of: pendingSnapState) { oldState, newState in
            if oldState == .failed,
               newState == .uploading,
               let processed = processedForRetry,
               let userId = currentUserId {
                Task { await uploadCurrentSnap(processed: processed, userId: userId) }
            }
        }
        // Remove: chip clears the snap; fire compensating Storage delete for the
        // files that were already uploaded under that id.
        .onChange(of: pendingSnapId) { oldId, newId in
            guard let oldId, newId == nil, let userId = currentUserId else { return }
            processedForRetry = nil
            Task { await snapRepo.deleteFiles(snapId: oldId, userId: userId) }
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoadingReferences {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") {
                    Task { await loadReferences() }
                }
            }
        } else {
            PebbleFormView(
                draft: $draft,
                emotions: emotions,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                showsPhotoSection: true,
                photoPickerPresented: $isPhotoPickerPresented
            )
        }
    }

    // MARK: - upload orchestration

    private func handlePicked(_ picked: PhotoPickerView.PickedItem) async {
        logger.notice("handlePicked: started uti=\(picked.uti, privacy: .public)")

        guard let userId = currentUserId else {
            logger.error("handlePicked: no current user id")
            return
        }

        // Load bytes from the item provider. PHPicker's loadDataRepresentation
        // is callback-based; wrap in a continuation.
        let data: Data
        do {
            data = try await loadData(from: picked.itemProvider, uti: picked.uti)
            logger.notice("handlePicked: loaded \(data.count, privacy: .public) bytes")
        } catch {
            logger.error("picker data load failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't read the image."
            return
        }

        // Process off the main actor — re-encode is CPU-bound.
        let processed: ProcessedImage
        let uti = picked.uti
        do {
            processed = try await Task.detached(priority: .userInitiated) {
                try ImagePipeline.process(data, uti: uti)
            }.value
        } catch {
            logger.error("image pipeline failed: \(String(describing: error), privacy: .public)")
            saveError = userMessageForPebbleSaveError(error)
            return
        }

        let snapId = UUID()
        draft.formSnap = .pending(AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading))
        processedForRetry = processed

        await uploadCurrentSnap(processed: processed, userId: userId)
    }

    private func loadData(from provider: NSItemProvider, uti: String) async throws -> Data {
        try await withCheckedThrowingContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: uti) { data, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: URLError(.cannotDecodeContentData))
                }
            }
        }
    }

    private func uploadCurrentSnap(processed: ProcessedImage, userId: UUID) async {
        guard case .pending(var snap) = draft.formSnap else { return }
        logger.notice("uploadCurrentSnap: started snap=\(snap.id, privacy: .public)")

        do {
            try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
            logger.notice("uploadCurrentSnap: success snap=\(snap.id, privacy: .public)")
            snap.state = .uploaded
            draft.formSnap = .pending(snap)
        } catch {
            logger.warning("snap upload failed (first attempt): \(error.localizedDescription, privacy: .private)")
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            do {
                try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
                snap.state = .uploaded
                draft.formSnap = .pending(snap)
            } catch {
                logger.error("snap upload failed (retry): \(error.localizedDescription, privacy: .private)")
                snap.state = .failed
                draft.formSnap = .pending(snap)
            }
        }
    }

    private func cancelAndCleanup() async {
        // Clearing the snap triggers the .onChange(of: pendingSnapId)
        // observer, which fires the compensating Storage delete.
        draft.formSnap = nil
        // Give the .onChange handler a tick to fire before dismissing.
        try? await Task.sleep(nanoseconds: 50_000_000)
        dismiss()
    }

    // MARK: - load references

    private func loadReferences() async {
        isLoadingReferences = true
        loadError = nil
        do {
            async let emotionsQuery: [Emotion] = supabase.client
                .from("emotions")
                .select()
                .order("name")
                .execute()
                .value
            async let domainsQuery: [Domain] = supabase.client
                .from("domains")
                .select()
                .order("name")
                .execute()
                .value
            async let soulsQuery: [Soul] = supabase.client
                .from("souls")
                .select("id, name, glyph_id")
                .order("name")
                .execute()
                .value
            async let collectionsQuery: [PebbleCollection] = supabase.client
                .from("collections")
                .select("id, name")
                .order("name")
                .execute()
                .value

            let (loadedEmotions, loadedDomains, loadedSouls, loadedCollections) =
                try await (emotionsQuery, domainsQuery, soulsQuery, collectionsQuery)

            self.emotions = loadedEmotions
            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.isLoadingReferences = false
        } catch {
            logger.error("reference load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load the form data."
            self.isLoadingReferences = false
        }
    }

    // MARK: - save

    private func save() async {
        guard draft.isValid else { return }

        if case .pending(let snap) = draft.formSnap, snap.state == .uploading {
            logger.notice("save blocked: snap still uploading")
            saveError = "Photo is still uploading."
            return
        }
        if case .pending(let snap) = draft.formSnap, snap.state == .failed {
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

        let payload = PebbleCreatePayload(from: draft, userId: userId)
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
        if let userId = currentUserId,
           case .pending(let snap) = draft.formSnap {
            await snapRepo.deleteFiles(snapId: snap.id, userId: userId)
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
    CreatePebbleSheet(onCreated: { _ in })
        .environment(SupabaseService())
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
