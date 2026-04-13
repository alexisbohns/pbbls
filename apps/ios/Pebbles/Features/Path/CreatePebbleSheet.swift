import SwiftUI
import os

struct CreatePebbleSheet: View {
    let onCreated: (Pebble) -> Void

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
            Form {
                Section {
                    DatePicker(
                        "When",
                        selection: $draft.happenedAt,
                        displayedComponents: [.date, .hourAndMinute]
                    )

                    TextField("Name", text: $draft.name)

                    TextField("Description (optional)", text: $draft.description, axis: .vertical)
                        .lineLimit(1...5)
                }

                Section("Mood") {
                    Picker("Emotion", selection: $draft.emotionId) {
                        Text("Choose…").tag(UUID?.none)
                        ForEach(emotions) { emotion in
                            Text(emotion.name).tag(UUID?.some(emotion.id))
                        }
                    }

                    Picker("Domain", selection: $draft.domainId) {
                        Text("Choose…").tag(UUID?.none)
                        ForEach(domains) { domain in
                            Text(domain.name).tag(UUID?.some(domain.id))
                        }
                    }

                    Picker("Valence", selection: $draft.valence) {
                        Text("Choose…").tag(Valence?.none)
                        ForEach(Valence.allCases) { valence in
                            Text(valence.label).tag(Valence?.some(valence))
                        }
                    }
                }

                Section("Optional") {
                    Picker("Soul", selection: $draft.soulId) {
                        Text("None").tag(UUID?.none)
                        ForEach(souls) { soul in
                            Text(soul.name).tag(UUID?.some(soul.id))
                        }
                    }

                    Picker("Collection", selection: $draft.collectionId) {
                        Text("None").tag(UUID?.none)
                        ForEach(collections) { collection in
                            Text(collection.name).tag(UUID?.some(collection.id))
                        }
                    }
                }

                Section("Privacy") {
                    Picker("Privacy", selection: $draft.visibility) {
                        ForEach(Visibility.allCases) { visibility in
                            Text(visibility.label).tag(visibility)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                if let saveError {
                    Section {
                        Text(saveError)
                            .foregroundStyle(.red)
                            .font(.callout)
                    }
                }
            }
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
        guard let userId = supabase.session?.user.id else {
            logger.error("create pebble aborted: no current session")
            self.saveError = "You're signed out. Please sign in again."
            return
        }
        isSaving = true
        saveError = nil

        do {
            let payload = PebbleInsert(from: draft, userId: userId)

            let inserted: Pebble = try await supabase.client
                .from("pebbles")
                .insert(payload)
                .select()
                .single()
                .execute()
                .value

            try await insertJoinRows(for: inserted.id)

            onCreated(inserted)
            dismiss()
        } catch {
            logger.error("create pebble failed: \(error.localizedDescription, privacy: .private)")
            self.saveError = "Couldn't save your pebble. Please try again."
            self.isSaving = false
        }
    }

    private func insertJoinRows(for pebbleId: UUID) async throws {
        // Domain is mandatory — always one row.
        async let domainInsert: Void = insertPebbleDomain(pebbleId: pebbleId, domainId: draft.domainId!)

        // Soul is optional.
        async let soulInsert: Void = {
            if let soulId = draft.soulId {
                try await insertPebbleSoul(pebbleId: pebbleId, soulId: soulId)
            }
        }()

        // Collection is optional.
        async let collectionInsert: Void = {
            if let collectionId = draft.collectionId {
                try await insertCollectionPebble(collectionId: collectionId, pebbleId: pebbleId)
            }
        }()

        _ = try await (domainInsert, soulInsert, collectionInsert)
    }

    private func insertPebbleDomain(pebbleId: UUID, domainId: UUID) async throws {
        _ = try await supabase.client
            .from("pebble_domains")
            .insert(PebbleDomainRow(pebbleId: pebbleId, domainId: domainId))
            .execute()
    }

    private func insertPebbleSoul(pebbleId: UUID, soulId: UUID) async throws {
        _ = try await supabase.client
            .from("pebble_souls")
            .insert(PebbleSoulRow(pebbleId: pebbleId, soulId: soulId))
            .execute()
    }

    private func insertCollectionPebble(collectionId: UUID, pebbleId: UUID) async throws {
        _ = try await supabase.client
            .from("collection_pebbles")
            .insert(CollectionPebbleRow(collectionId: collectionId, pebbleId: pebbleId))
            .execute()
    }
}

private struct PebbleDomainRow: Encodable {
    let pebbleId: UUID
    let domainId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case domainId = "domain_id"
    }
}

private struct PebbleSoulRow: Encodable {
    let pebbleId: UUID
    let soulId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
        case soulId = "soul_id"
    }
}

private struct CollectionPebbleRow: Encodable {
    let collectionId: UUID
    let pebbleId: UUID
    enum CodingKeys: String, CodingKey {
        case collectionId = "collection_id"
        case pebbleId = "pebble_id"
    }
}

#Preview {
    CreatePebbleSheet { _ in }
        .environment(SupabaseService())
}
