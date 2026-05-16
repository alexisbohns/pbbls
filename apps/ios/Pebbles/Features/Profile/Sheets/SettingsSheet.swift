import Supabase
import SwiftUI
import os

/// Edit-mode sheet for profile management. Presented from `ProfileView`'s gear button.
///
/// Sections shown depend on whether the account is SSO (Apple/Google) or email-only:
/// SSO sees a read-only Providers list; email-only sees a Password change form.
struct SettingsSheet: View {
    let initialDisplayName: String
    let initialGlyphId: UUID?
    let initialGlyphStrokes: [GlyphStroke]?
    let email: String?
    let onSaved: (_ displayName: String, _ glyph: Glyph?) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var displayName: String
    @State private var pickedGlyph: Glyph?
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var presentedLegalDoc: LegalDoc?
    @State private var isPresentingGlyphPicker = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-sheet")

    init(
        initialDisplayName: String,
        initialGlyphId: UUID?,
        initialGlyphStrokes: [GlyphStroke]?,
        email: String?,
        onSaved: @escaping (_ displayName: String, _ glyph: Glyph?) -> Void
    ) {
        self.initialDisplayName = initialDisplayName
        self.initialGlyphId = initialGlyphId
        self.initialGlyphStrokes = initialGlyphStrokes
        self.email = email
        self.onSaved = onSaved
        self._displayName = State(initialValue: initialDisplayName)
    }

    private var trimmedDisplayName: String {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private var isDirty: Bool {
        let nameChanged = trimmedDisplayName != initialDisplayName && !trimmedDisplayName.isEmpty
        let glyphChanged = pickedGlyph != nil && pickedGlyph?.id != initialGlyphId
        let passwordSet = !newPassword.isEmpty
        return nameChanged || glyphChanged || passwordSet
    }

    private var currentStrokes: [GlyphStroke]? {
        pickedGlyph?.strokes ?? initialGlyphStrokes
    }

    var body: some View {
        NavigationStack {
            Form {
                headerSection
                informationsSection
                if isSSO {
                    providersSection
                } else {
                    passwordSection
                }
                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.footnote)
                            .foregroundStyle(.red)
                    }
                }
                legalSection
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        Button("Save") { Task { await save() } }
                            .disabled(!isDirty)
                    }
                }
            }
            .pebblesScreen()
            .sheet(isPresented: $isPresentingGlyphPicker) {
                GlyphPickerSheet(currentGlyphId: pickedGlyph?.id ?? initialGlyphId) { glyph in
                    pickedGlyph = glyph
                }
            }
            .sheet(item: $presentedLegalDoc) { doc in
                LegalDocumentSheet(url: doc.url)
                    .ignoresSafeArea()
            }
        }
    }

    private var headerSection: some View {
        Section {
            Button {
                isPresentingGlyphPicker = true
            } label: {
                HStack {
                    Spacer()
                    glyphView
                    Spacer()
                }
                .padding(.vertical, 8)
            }
            .buttonStyle(.plain)
            .listRowBackground(Color.clear)
        }
    }

    @ViewBuilder
    private var glyphView: some View {
        if let strokes = currentStrokes, !strokes.isEmpty {
            GlyphThumbnail(strokes: strokes, side: 120)
        } else {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color.secondary.opacity(0.08))
                .frame(width: 120, height: 120)
                .overlay {
                    Image(systemName: "scribble")
                        .font(.title)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
        }
    }

    private struct LinkedProvider: Identifiable {
        let id: String       // provider raw value: "apple" | "google" | …
        let label: LocalizedStringResource
        let systemImage: String
    }

    private var linkedProviders: [LinkedProvider] {
        let identities = supabase.session?.user.identities ?? []
        return identities.compactMap { identity in
            switch identity.provider {
            case "apple":
                return LinkedProvider(id: "apple", label: "Apple", systemImage: "apple.logo")
            case "google":
                return LinkedProvider(id: "google", label: "Google", systemImage: "g.circle")
            case "email":
                return nil
            default:
                return nil
            }
        }
    }

    private var isSSO: Bool { !linkedProviders.isEmpty }

    private var providersSection: some View {
        Section("Providers") {
            ForEach(linkedProviders) { provider in
                HStack(spacing: 12) {
                    Image(systemName: provider.systemImage)
                        .foregroundStyle(Color.pebblesMutedForeground)
                    Text(provider.label)
                    Spacer()
                }
            }
        }
    }

    private var informationsSection: some View {
        Section("Informations") {
            HStack {
                Text("Name")
                    .foregroundStyle(Color.pebblesMutedForeground)
                Spacer()
                TextField("Your name", text: $displayName)
                    .multilineTextAlignment(.trailing)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled(false)
            }
            HStack {
                Text("Email")
                    .foregroundStyle(Color.pebblesMutedForeground)
                Spacer()
                Text(email ?? "—")
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
        }
    }

    private var passwordSection: some View {
        Section {
            SecureField("New password", text: $newPassword)
                .textContentType(.newPassword)
                .autocorrectionDisabled(true)
                .textInputAutocapitalization(.never)
        } header: {
            Text("Password")
        } footer: {
            Text("Leave blank to keep your current password.")
        }
    }

    private var legalSection: some View {
        Section("Legal") {
            Button { presentedLegalDoc = .terms } label: {
                Label("Terms of Service", systemImage: "doc.text")
            }
            .buttonStyle(.plain)
            Button { presentedLegalDoc = .privacy } label: {
                Label("Privacy Policy", systemImage: "hand.raised")
            }
            .buttonStyle(.plain)
        }
    }

    private func save() async {
        guard isDirty, !isSaving else { return }
        isSaving = true
        saveError = nil

        let nameToSend: String? = {
            let trimmed = trimmedDisplayName
            return (trimmed != initialDisplayName && !trimmed.isEmpty) ? trimmed : nil
        }()
        let glyphIdToSend: String? = {
            guard let picked = pickedGlyph, picked.id != initialGlyphId else { return nil }
            return picked.id.uuidString
        }()
        let passwordToSend: String? = newPassword.isEmpty ? nil : newPassword

        do {
            if nameToSend != nil || glyphIdToSend != nil {
                let params = UpdateProfileParams(
                    p_display_name: nameToSend,
                    p_glyph_id: glyphIdToSend
                )
                try await supabase.client
                    .rpc("update_profile", params: params)
                    .execute()
            }

            if let passwordToSend {
                _ = try await supabase.client.auth.update(
                    user: UserAttributes(password: passwordToSend)
                )
            }

            onSaved(nameToSend ?? initialDisplayName, pickedGlyph)
            dismiss()
        } catch {
            logger.error("settings save failed: \(error.localizedDescription, privacy: .private)")
            saveError = "Couldn't save your changes. Please try again."
            isSaving = false
        }
    }
}

/// Wire shape for `update_profile` RPC. Null fields tell Postgres "don't change".
private struct UpdateProfileParams: Encodable {
    let p_display_name: String?
    let p_glyph_id: String?
}

#Preview("Email user") {
    SettingsSheet(
        initialDisplayName: "Alexis",
        initialGlyphId: nil,
        initialGlyphStrokes: nil,
        email: "hello@bohns.design",
        onSaved: { _, _ in }
    )
    .environment(SupabaseService())
}
