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

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "edit-pebble")

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
                renderHeight: sizeGroup.renderHeight
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
                    emotion:emotions(id, name, color),
                    pebble_domains(domain:domains(id, name)),
                    pebble_souls(soul:souls(id, name)),
                    collection_pebbles(collection:collections(id, name))
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
                .select("id, name")
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

    private func save() async {
        guard draft.isValid else { return }
        isSaving = true
        saveError = nil

        let payload = PebbleUpdatePayload(from: draft)
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
                self.saveError = "Couldn't save your changes. Please try again."
                self.isSaving = false
            }
        } catch {
            logger.error("update pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your changes. Please try again."
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
