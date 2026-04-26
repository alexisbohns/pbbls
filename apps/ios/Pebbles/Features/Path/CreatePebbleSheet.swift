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

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    private var snapRepo: PebbleSnapRepository {
        PebbleSnapRepository(client: supabase.client)
    }

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
        .task { await loadReferences() }
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
                onPhotoPicked: { picked in
                    Task { await handlePicked(picked) }
                },
                onSnapRetry: {
                    Task { await retryUpload() }
                },
                onSnapRemoved: {
                    Task { await deleteAttachedSnapFiles() }
                }
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
            saveError = userMessage(for: error)
            return
        }

        let snapId = UUID()
        draft.attachedSnap = AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading)
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

    private func retryUpload() async {
        guard let userId = currentUserId,
              let processed = processedForRetry,
              var snap = draft.attachedSnap else { return }
        snap.state = .uploading
        draft.attachedSnap = snap
        await uploadCurrentSnap(processed: processed, userId: userId)
    }

    private func uploadCurrentSnap(processed: ProcessedImage, userId: UUID) async {
        guard var snap = draft.attachedSnap else { return }
        logger.notice("uploadCurrentSnap: started snap=\(snap.id, privacy: .public)")

        do {
            try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
            logger.notice("uploadCurrentSnap: success snap=\(snap.id, privacy: .public)")
            snap.state = .uploaded
            draft.attachedSnap = snap
        } catch {
            logger.warning("snap upload failed (first attempt): \(error.localizedDescription, privacy: .private)")
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            do {
                try await snapRepo.uploadProcessed(processed, snapId: snap.id, userId: userId)
                snap.state = .uploaded
                draft.attachedSnap = snap
            } catch {
                logger.error("snap upload failed (retry): \(error.localizedDescription, privacy: .private)")
                snap.state = .failed
                draft.attachedSnap = snap
            }
        }
    }

    /// Removes the attached snap from the draft and best-effort deletes its files.
    private func deleteAttachedSnapFiles() async {
        guard let userId = currentUserId,
              let snap = draft.attachedSnap else { return }
        draft.attachedSnap = nil
        processedForRetry = nil
        await snapRepo.deleteFiles(snapId: snap.id, userId: userId)
    }

    private func cancelAndCleanup() async {
        await deleteAttachedSnapFiles()
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

        if let snap = draft.attachedSnap, snap.state == .uploading {
            logger.notice("save blocked: snap still uploading")
            saveError = "Photo is still uploading."
            return
        }
        if let snap = draft.attachedSnap, snap.state == .failed {
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
        if let userId = currentUserId, let snap = draft.attachedSnap {
            await snapRepo.deleteFiles(snapId: snap.id, userId: userId)
        }
        saveError = userMessage(for: error)
        isSaving = false
    }

    private func userMessage(for error: Error) -> String {
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
