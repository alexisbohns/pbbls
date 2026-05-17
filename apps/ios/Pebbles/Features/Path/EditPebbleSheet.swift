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
struct EditPebbleSheet: View {
    let pebbleId: UUID
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.dismiss) private var dismiss

    @State private var draft = PebbleDraft()
    @State private var renderSvg: String?
    @State private var strokeColor: String?
    @State private var sizeGroup: ValenceSizeGroup = .medium
    @State private var selectedGlyph: Glyph?

    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isSaving = false
    @State private var saveError: String?

    @State private var isPhotoPickerPresented = false
    @State private var isRemovingExistingSnap = false

    /// Lazily constructed in `.task` so we have access to `supabase.client`.
    /// Seeded with the loaded snap (if any) after `load()` succeeds.
    @State private var snaps: SnapUploadCoordinator?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-pebble")

    private var currentUserId: UUID? {
        supabase.session?.user.id
    }

    var body: some View {
        NavigationStack {
            content
                .pebblesToolbarTitle("Edit pebble")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        PebbleToolbarButton("Cancel") {
                            Task { await cancelAndCleanup() }
                        }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        if isSaving {
                            ProgressView()
                        } else {
                            PebbleToolbarButton("Save") {
                                Task { await save() }
                            }
                            .disabled(!draft.isValid || isLoading)
                        }
                    }
                }
                .pebblesScreen()
        }
        .task {
            if snaps == nil {
                snaps = SnapUploadCoordinator(repo: PebbleSnapRepository(client: supabase.client))
            }
            await load()
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
                domains: refs.domains,
                souls: refs.souls,
                collections: refs.collections,
                saveError: saveError,
                renderSvg: renderSvg,
                strokeColor: strokeColor,
                renderHeight: sizeGroup.renderHeight,
                selectedGlyph: selectedGlyph,
                onGlyphPicked: { picked in selectedGlyph = picked },
                showsPhotoSection: true,
                photoPickerPresented: $isPhotoPickerPresented,
                isRemovingExistingSnap: isRemovingExistingSnap,
                onRemoveExistingSnap: {
                    Task { await removeExisting() }
                },
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
    }

    private func cancelAndCleanup() async {
        if let userId = currentUserId, let snaps {
            await snaps.cancelAndCleanup(userId: userId)
        }
        dismiss()
    }

    private func removeExisting() async {
        guard let snaps else { return }
        isRemovingExistingSnap = true
        defer { isRemovingExistingSnap = false }
        do {
            try await snaps.removeExisting()
        } catch {
            logger.error("delete_pebble_media failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't remove the photo. Please try again."
        }
    }

    private func load() async {
        isLoading = true
        loadError = nil
        do {
            let detail: PebbleDetail = try await supabase.client
                .from("pebbles")
                .select("""
                    id, name, description, happened_at, intensity, positiveness, visibility,
                    render_svg, render_version, glyph_id,
                    glyph:glyphs(id, name, strokes, view_box),
                    emotion:emotions(id, slug, name),
                    pebble_domains(domain:domains(id, slug, name)),
                    pebble_souls(soul:souls(id, name, glyph_id, glyphs(id, name, strokes, view_box))),
                    collection_pebbles(collection:collections(id, name)),
                    snaps(id, storage_path, sort_order)
                """)
                .eq("id", value: pebbleId)
                .single()
                .execute()
                .value

            self.draft = PebbleDraft(from: detail)
            self.selectedGlyph = detail.glyph
            self.renderSvg = detail.renderSvg
            self.strokeColor = palettes.palette(for: detail.emotion.id)?
                .strokeHex(for: colorScheme) ?? Color.accent.primaryHex
            self.sizeGroup = detail.valence.sizeGroup
            if let existing = detail.snaps.first {
                snaps?.seedExisting(.existing(id: existing.id, storagePath: existing.storagePath))
            }
            self.isLoading = false
        } catch {
            logger.error("edit pebble load failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load this pebble."
            self.isLoading = false
        }
    }

    // swiftlint:disable:next function_body_length
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

        isSaving = true
        saveError = nil

        guard let userId = currentUserId else {
            logger.error("save: no current user id")
            self.saveError = "You must be signed in to save."
            self.isSaving = false
            return
        }
        let payload = PebbleUpdatePayload(from: draft, formSnap: snaps?.formSnap, userId: userId)
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
                await handleSaveFailure(functionsError)
            }
        } catch {
            logger.error("update pebble failed: \(error.localizedDescription, privacy: .private)")
            await handleSaveFailure(error)
        }
    }

    private func handleSaveFailure(_ error: Error) async {
        if let userId = currentUserId, let snaps {
            await snaps.handleSaveFailure(userId: userId)
        }
        self.saveError = userMessageForPebbleSaveError(error)
        self.isSaving = false
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
    let supabase = SupabaseService()
    return EditPebbleSheet(pebbleId: UUID(), onSaved: {})
        .environment(supabase)
        .environment(ReferenceDataService(client: supabase.client))
}
