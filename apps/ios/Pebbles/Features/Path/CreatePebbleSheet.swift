import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: () -> Void

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

        do {
            let payload = PebbleCreatePayload(from: draft)

            _ = try await supabase.client
                .rpc("create_pebble", params: CreatePebbleParams(payload: payload))
                .execute()

            onCreated()
            dismiss()
        } catch {
            logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your pebble. Please try again."
            self.isSaving = false
        }
    }
}

/// Wrapper matching the `create_pebble(payload jsonb)` RPC signature.
private struct CreatePebbleParams: Encodable {
    let payload: PebbleCreatePayload
}

#Preview {
    CreatePebbleSheet(onCreated: {})
        .environment(SupabaseService())
}
