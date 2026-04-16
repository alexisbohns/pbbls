import SwiftUI
import os

/// Sheet for creating a new soul. One text field, save/cancel toolbar.
/// INSERT goes directly to `public.souls` — RLS scopes to the current user.
struct CreateSoulSheet: View {
    let onCreated: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Name", text: $name)
                        .textInputAutocapitalization(.words)
                        .autocorrectionDisabled(false)
                }
                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("New soul")
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
        }
    }

    private func save() async {
        guard !trimmedName.isEmpty else { return }
        guard let userId = supabase.session?.user.id else {
            logger.error("create soul: no session")
            saveError = "You're signed out. Please sign in again."
            return
        }
        isSaving = true
        saveError = nil
        do {
            let payload = SoulInsertPayload(userId: userId, name: trimmedName)
            try await supabase.client
                .from("souls")
                .insert(payload)
                .execute()
            onCreated()
            dismiss()
        } catch {
            logger.error("create soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save the soul. Please try again."
            isSaving = false
        }
    }
}

/// Matches the `souls` row shape required for insert.
/// `user_id` is explicit because the RLS policy still requires it in the row
/// (the policy's `with check` compares it to `auth.uid()`).
private struct SoulInsertPayload: Encodable {
    let userId: UUID
    let name: String

    enum CodingKeys: String, CodingKey {
        case userId = "user_id"
        case name
    }
}

#Preview {
    CreateSoulSheet(onCreated: {})
        .environment(SupabaseService())
}
