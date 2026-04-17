import SwiftUI
import os

/// Sheet for creating a new collection: name + optional mode.
/// INSERT goes directly to `public.collections` — RLS scopes to the owner.
struct CreateCollectionSheet: View {
    let onCreated: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var mode: CollectionMode?
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.collections")

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
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
            .navigationTitle("New collection")
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
                        .disabled(trimmedName.isEmpty)
                    }
                }
            }
            .pebblesScreen()
        }
    }

    private func save() async {
        guard !trimmedName.isEmpty else { return }
        guard let userId = supabase.session?.user.id else {
            logger.error("create collection: no session")
            saveError = "You're signed out. Please sign in again."
            return
        }
        isSaving = true
        saveError = nil
        do {
            let payload = CollectionInsertPayload(
                userId: userId,
                name: trimmedName,
                mode: mode?.rawValue
            )
            try await supabase.client
                .from("collections")
                .insert(payload)
                .execute()
            onCreated()
            dismiss()
        } catch {
            logger.error("create collection failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save the collection. Please try again."
            isSaving = false
        }
    }
}

/// Wire shape for `POST /collections`. Snake-case keys match the DB columns.
/// `user_id` is explicit because the RLS `with check` compares it to `auth.uid()`.
/// `mode` is explicitly encoded so that `nil` becomes JSON null, which Postgres
/// stores as SQL NULL (matching the nullable `mode` column).
struct CollectionInsertPayload: Encodable {
    let userId: UUID
    let name: String
    let mode: String?

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
        case mode
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(userId, forKey: .userId)
        try container.encode(name, forKey: .name)
        try container.encode(mode, forKey: .mode) // forces JSON null when nil
    }
}

#Preview {
    CreateCollectionSheet(onCreated: {})
        .environment(SupabaseService())
}
