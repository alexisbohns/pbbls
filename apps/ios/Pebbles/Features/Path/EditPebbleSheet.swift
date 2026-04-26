import SwiftUI
import Supabase
import os

/// Sheet for editing an existing pebble.
///
/// Flow:
/// 1. `.task` loads the pebble detail + the four reference lists concurrently.
/// 2. On load success, `draft` is prefilled via `PebbleDraft(from: detail)` and
///    `PebbleFormView` renders the form.
/// 3. Save calls the `compose-pebble-update` edge function which updates the row
///    (via `update_pebble` RPC internally) and returns a fresh `render_svg`.
/// 4. `onSaved()` notifies the parent (`PathView`) so it can refetch the list.
// swiftlint:disable:next type_body_length
struct EditPebbleSheet: View {
    let pebbleId: UUID
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var emotions: [Emotion] = []
    @State private var domains: [Domain] = []
    @State private var souls: [Soul] = []
    @State private var collections: [PebbleCollection] = []
    @State private var renderSvg: String?
    @State private var strokeColor: String?
    @State private var sizeGroup: ValenceSizeGroup = .medium

    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var saveError: String?

    @State private var isPhotoPickerPresented = false
    @State private var processedForRetry: ProcessedImage?
    @State private var isRemovingExistingSnap = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-pebble")

    private var currentUserId: UUID? {
        supabase.session?.user.id
    }

    private var snapRepo: PebbleSnapRepository {
        PebbleSnapRepository(client: supabase.client)
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
                .navigationTitle("Edit pebble")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            Button("Save") {
                                Task { await save() }
                            }
                            .disabled(!draft.isValid || isLoading)
                        }
                    }
                }
                .pebblesScreen()
        }
        .task { await load() }
        .sheet(isPresented: $isPhotoPickerPresented) {
            PhotoPickerView { picked in
                isPhotoPickerPresented = false
                if let picked {
                    Task { await handlePicked(picked) }
                }
            }
        }
        .onChange(of: pendingSnapState) { oldState, newState in
            if oldState == .failed,
               newState == .uploading,
               let processed = processedForRetry,
               let userId = currentUserId {
                Task { await uploadCurrentSnap(processed: processed, userId: userId) }
            }
        }
        .onChange(of: pendingSnapId) { oldId, newId in
            guard let oldId, newId == nil, let userId = currentUserId else { return }
            processedForRetry = nil
            Task { await snapRepo.deleteFiles(snapId: oldId, userId: userId) }
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            ProgressView()
        } else if let loadError {
            VStack(spacing: 12) {
                Text(loadError).foregroundStyle(.secondary)
                Button("Retry") {
                    Task { await load() }
                }
            }
            .padding()
        } else {
            PebbleFormView(
                draft: $draft,
                emotions: emotions,
                domains: domains,
                souls: souls,
                collections: collections,
                saveError: saveError,
                renderSvg: renderSvg,
                strokeColor: strokeColor,
                renderHeight: sizeGroup.renderHeight,
                showsPhotoSection: true,
                photoPickerPresented: $isPhotoPickerPresented,
                isRemovingExistingSnap: isRemovingExistingSnap,
                onRemoveExistingSnap: {
                    Task { await removeExistingSnap() }
                }
            )
        }
    }

    // Five parallel queries (detail + four reference lists) push this just past the default limit.
    // swiftlint:disable:next function_body_length
    private func load() async {
        isLoading = true
        loadError = nil
        do {
            async let detailQuery: PebbleDetail = supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    emotion:emotions(id, slug, name, color),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id)),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value

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

            let (detail, loadedEmotions, loadedDomains, loadedSouls, loadedCollections) =
                try await (detailQuery, emotionsQuery, domainsQuery, soulsQuery, collectionsQuery)

            self.emotions = loadedEmotions
            self.domains = loadedDomains
            self.souls = loadedSouls
            self.collections = loadedCollections
            self.draft = PebbleDraft(from: detail)
            self.renderSvg = detail.renderSvg
            self.strokeColor = detail.emotion.color
            self.sizeGroup = detail.valence.sizeGroup
            self.isLoading = false
        } catch {
            logger.error("edit pebble load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }

    private func handlePicked(_ picked: PhotoPickerView.PickedItem) async {
        logger.notice("handlePicked: started uti=\(picked.uti, privacy: .public)")

        guard let userId = currentUserId else {
            logger.error("handlePicked: no current user id")
            return
        }

        let data: Data
        do {
            data = try await loadData(from: picked.itemProvider, uti: picked.uti)
            logger.notice("handlePicked: loaded \(data.count, privacy: .public) bytes")
        } catch {
            logger.error("picker data load failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't read the image."
            return
        }

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
        draft.formSnap = .pending(
            AttachedSnap(id: snapId, localThumb: processed.thumb, state: .uploading)
        )
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

    /// Tap-X handler for an `.existing` snap row. Calls `delete_pebble_media`
    /// to commit the removal in the DB, then fires fire-and-forget Storage
    /// cleanup using the returned `storage_path`. Cancel does not undo this —
    /// see the design spec.
    private func removeExistingSnap() async {
        guard case .existing(let id, _) = draft.formSnap else { return }
        isRemovingExistingSnap = true
        defer { isRemovingExistingSnap = false }

        do {
            let storagePath: String = try await supabase.client
                .rpc("delete_pebble_media", params: ["p_snap_id": id.uuidString])
                .execute()
                .value
            // Fire-and-forget Storage cleanup. Logged on failure inside the helper.
            await snapRepo.deleteFiles(storagePrefix: storagePath)
            draft.formSnap = nil
        } catch {
            logger.error("delete_pebble_media failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't remove the photo. Please try again."
        }
    }

    // swiftlint:disable:next function_body_length
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

        isSaving = true
        saveError = nil

        guard let userId = currentUserId else {
            logger.error("save: no current user id")
            self.saveError = "You must be signed in to save."
            self.isSaving = false
            return
        }
        let payload = PebbleUpdatePayload(from: draft, userId: userId)
        let requestBody = ComposePebbleUpdateRequest(pebbleId: pebbleId, payload: payload)

        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let response: ComposePebbleResponse = try await supabase.client.functions
                .invoke(
                    "compose-pebble-update",
                    options: FunctionInvokeOptions(body: requestBody),
                    decoder: decoder
                )
            self.renderSvg = response.renderSvg ?? self.renderSvg
            onSaved()
            dismiss()
        } catch let functionsError as FunctionsError {
            // Soft-success: update succeeded but compose failed — the row was
            // updated, so advance as if saved and rely on the parent's list
            // refetch to eventually pick up a freshly composed render.
            if case .httpError(let status, let data) = functionsError, status >= 500 {
                let bodyString = String(data: data, encoding: .utf8) ?? "<non-utf8 body>"
                logger.warning("compose-pebble-update returned \(status, privacy: .public) — advancing on soft-success. body=\(bodyString, privacy: .private)")
                onSaved()
                dismiss()
            } else {
                let bodyString: String
                if case .httpError(_, let data) = functionsError {
                    bodyString = String(data: data, encoding: .utf8) ?? "<non-utf8 body>"
                } else {
                    bodyString = "<non-http error>"
                }
                logger.error("compose-pebble-update failed: \(functionsError.localizedDescription, privacy: .private) body=\(bodyString, privacy: .private)")
                self.saveError = userMessageForPebbleSaveError(functionsError)
                self.isSaving = false
            }
        } catch {
            logger.error("update pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = userMessageForPebbleSaveError(error)
            self.isSaving = false
        }
    }
}

/// Body for the `compose-pebble-update` edge function.
/// Shape: `{ "pebble_id": "...", "payload": { ... update_pebble payload ... } }`.
private struct ComposePebbleUpdateRequest: Encodable {
    let pebbleId: UUID
    let payload: PebbleUpdatePayload

    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case payload
    }
}

#Preview {
    EditPebbleSheet(pebbleId: UUID(), onSaved: {})
        .environment(SupabaseService())
}
