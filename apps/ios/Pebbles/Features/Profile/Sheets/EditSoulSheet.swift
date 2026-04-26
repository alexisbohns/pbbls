import SwiftUI
import os

/// Sheet for renaming an existing soul. One text field, save/cancel toolbar.
/// UPDATE goes directly to `public.souls` — RLS scopes to the owner.
struct EditSoulSheet: View {
    let soul: Soul
    let onSaved: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var name: String
    @State private var isSaving = false
    @State private var saveError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    init(soul: Soul, onSaved: @escaping () -> Void) {
        self.soul = soul
        self.onSaved = onSaved
        self._name = State(initialValue: soul.name)
    }

    private var trimmedName: String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var canSave: Bool {
        !trimmedName.isEmpty && trimmedName != soul.name
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
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Edit soul")
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
            .pebblesScreen()
        }
    }

    private func save() async {
        guard canSave else { return }
        isSaving = true
        saveError = nil
        do {
            let payload = SoulUpdatePayload(name: trimmedName)
            try await supabase.client
                .from("souls")
                .update(payload)
                .eq("id", value: soul.id)
                .execute()
            onSaved()
            dismiss()
        } catch {
            logger.error("update soul failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
}

#Preview {
    EditSoulSheet(soul: Soul(id: UUID(), name: "Preview", glyphId: UUID()), onSaved: {})
        .environment(SupabaseService())
}
