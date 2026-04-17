import SwiftUI
import os

/// Sheet for editing an existing collection: name + mode.
/// UPDATE goes directly to `public.collections` — RLS scopes to the owner.
struct EditCollectionSheet: View {
    let collection: Collection
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var mode: CollectionMode?
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    init(collection: Collection, onSaved: @escaping () -> Void) {
        self.collection = collection
        self.onSaved = onSaved
        self._name = State(initialValue: collection.name)
        self._mode = State(initialValue: collection.mode)
    }

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        guard !trimmedName.isEmpty else { return false }
        return trimmedName != collection.name || mode != collection.mode
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("Name", text: $name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(false)
                }
                Section("Mode") {
                    Picker("Mode", selection: $mode) {
                        Text("None").tag(CollectionMode?.none)
                        Text("Stack").tag(CollectionMode?.some(.stack))
                        Text("Pack").tag(CollectionMode?.some(.pack))
                        Text("Track").tag(CollectionMode?.some(.track))
                    }
                    .pickerStyle(.segmented)
                }
                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Edit collection")
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
                        .disabled(!canSave)
                    }
                }
            }
        }
    }

    private func save() async {
        guard canSave else { return }
        isSaving = true
        saveError = nil
        do {
            let payload = CollectionUpdatePayload(name: trimmedName, mode: mode?.rawValue)
            try await supabase.client
                .from("collections")
                .update(payload)
                .eq("id", value: collection.id)
                .execute()
            onSaved()
            dismiss()
        } catch {
            logger.error("update collection failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
}

/// Wire shape for `PATCH /collections/:id`. Snake-case keys match the DB columns.
/// `mode` is explicitly encoded so that `nil` clears the column server-side.
struct CollectionUpdatePayload: Encodable {
    let name: String
    let mode: String?

    enum CodingKeys: String, CodingKey {
        case name
        case mode
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(mode, forKey: .mode) // encodes nil as JSON null
    }
}
