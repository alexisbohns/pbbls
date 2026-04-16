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

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "create-pebble")

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("New pebble")
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
                            .disabled(!draft.isValid)
                        }
                    }
                }
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
                saveError: saveError
            )
        }
    }

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

    private func save() async {
        guard draft.isValid else { return }
        isSaving = true
        saveError = nil

        let payload = PebbleCreatePayload(from: draft)
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
            // Attempt soft-success: edge function returned 5xx but pebble was
            // inserted — extract pebble_id from the error body so we can still
            // advance to the detail sheet.
            if let pebbleId = softSuccessPebbleId(from: functionsError) {
                logger.warning("compose-pebble returned 5xx but pebble_id found — advancing to detail sheet")
                onCreated(pebbleId)
                dismiss()
            } else {
                logger.error("compose-pebble failed: \(functionsError.localizedDescription, privacy: .private)")
                self.saveError = "Couldn't save your pebble. Please try again."
                self.isSaving = false
            }
        } catch {
            logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your pebble. Please try again."
            self.isSaving = false
        }
    }

    /// Tries to extract a `pebble_id` UUID from the raw error body of a
    /// `FunctionsError.httpError`. Returns `nil` if the body is absent,
    /// unparseable, or missing the `pebble_id` key.
    private func softSuccessPebbleId(from error: FunctionsError) -> UUID? {
        guard case let .httpError(_, data) = error, !data.isEmpty else { return nil }
        struct Partial: Decodable { let pebbleId: UUID; enum CodingKeys: String, CodingKey { case pebbleId = "pebble_id" } }
        return try? JSONDecoder().decode(Partial.self, from: data).pebbleId
    }
}

/// Wrapper matching the compose-pebble edge function body shape.
/// The function expects `{ "payload": {...} }` where `payload` mirrors
/// the create_pebble RPC payload.
private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}

#Preview {
    CreatePebbleSheet(onCreated: { _ in })
        .environment(SupabaseService())
}
