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
    let initialHandle: String?
    let initialPublicProfile: Bool
    let email: String?
    let onSaved: (_ displayName: String, _ glyph: Glyph?, _ handle: String?, _ isPublic: Bool) -> Void

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.dismiss) private var dismiss

    @State private var displayName: String
    @State private var pickedGlyph: Glyph?
    /// The "Confirm it's you" password (#977). Never persisted, cleared after every attempt.
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var handle: String
    @State private var isPublicProfile: Bool
    @State private var handleError: String?
    @State private var isSaving = false
    @State private var saveError: String?
    @State private var presentedLegalDoc: LegalDoc?
    @State private var isPresentingGlyphPicker = false
    @State private var isPresentingDeleteConfirm = false
    #if DEBUG
    @State private var isPresentingStoneLab = false
    #endif
    @State private var isDeleting = false
    @State private var deleteError: String?
    /// The action waiting on a recent sign-in, or nil.
    @State private var reauthPurpose: ReauthPurpose?
    @State private var isPresentingReauth = false
    /// Set after a failed attempt; the alert re-presents with it.
    @State private var reauthError: String?
    @FocusState private var focusedField: Field?

    private enum Field: Hashable { case displayName, newPassword, handle }
    private enum ReauthPurpose { case save, delete }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "settings-sheet")

    init(
        initialDisplayName: String,
        initialGlyphId: UUID?,
        initialGlyphStrokes: [GlyphStroke]?,
        initialHandle: String? = nil,
        initialPublicProfile: Bool = false,
        email: String?,
        onSaved: @escaping (_ displayName: String, _ glyph: Glyph?, _ handle: String?, _ isPublic: Bool) -> Void
    ) {
        self.initialDisplayName = initialDisplayName
        self.initialGlyphId = initialGlyphId
        self.initialGlyphStrokes = initialGlyphStrokes
        self.initialHandle = initialHandle
        self.initialPublicProfile = initialPublicProfile
        self.email = email
        self.onSaved = onSaved
        self._displayName = State(initialValue: initialDisplayName)
        self._handle = State(initialValue: initialHandle ?? "")
        self._isPublicProfile = State(initialValue: initialPublicProfile)
    }

    private var trimmedDisplayName: String {
        displayName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Handles are stored normalized (see the DB CHECK), so every comparison
    /// and write uses the lowercased, trimmed form.
    private var normalizedHandle: String {
        handle.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private var handleChanged: Bool {
        normalizedHandle != (initialHandle ?? "")
    }

    private var publicProfileChanged: Bool {
        isPublicProfile != initialPublicProfile
    }

    /// A password change and going public need a recent sign-in (#976). Every
    /// other edit saves without a prompt.
    private var saveNeedsRecentAuth: Bool {
        !newPassword.isEmpty || (publicProfileChanged && isPublicProfile)
    }

    private var isSignInFresh: Bool {
        RecentAuth.isFresh(accessToken: supabase.session?.accessToken)
    }

    private var reauthMethod: ReauthMethod {
        ReauthMethod(providers: (supabase.session?.user.identities ?? []).map(\.provider))
    }

    private var isDirty: Bool {
        let nameChanged = trimmedDisplayName != initialDisplayName && !trimmedDisplayName.isEmpty
        let glyphChanged = pickedGlyph != nil && pickedGlyph?.id != initialGlyphId
        let passwordSet = !newPassword.isEmpty
        return nameChanged || glyphChanged || passwordSet || handleChanged || publicProfileChanged
    }

    /// The share row reflects what is actually live on the server, not staged
    /// edits — a link to an unsaved handle would 404.
    private var shareURL: URL? {
        guard initialPublicProfile, let saved = initialHandle, !saved.isEmpty else { return nil }
        return URL(string: "https://www.pbbls.app/u/\(saved)")
    }

    private var currentStrokes: [GlyphStroke]? {
        pickedGlyph?.strokes ?? initialGlyphStrokes
    }

    var body: some View {
        NavigationStack {
            List {
                headerSection
                informationsSection
                publicProfileSection
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
                            .pebblesListRow(position: .only)
                    }
                }
                legalSection
                #if DEBUG
                developerSection
                #endif
                deleteAccountSection
            }
            .pebblesList()
            .scrollDismissesKeyboard(.interactively)
            .pebblesToolbarTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if isSaving {
                        ProgressView()
                    } else {
                        PebbleToolbarButton("Save") { Task { await save() } }
                            .disabled(!isDirty)
                    }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button {
                        focusedField = nil
                    } label: {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel(Text("Close keyboard"))
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
            #if DEBUG
            .fullScreenCover(isPresented: $isPresentingStoneLab) {
                StoneLabView()
            }
            #endif
            .confirmationDialog(
                "Delete your account?",
                isPresented: $isPresentingDeleteConfirm,
                titleVisibility: .visible
            ) {
                Button("Delete forever", role: .destructive) {
                    Task { await confirmDelete() }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This permanently deletes your account and everything in it: pebbles, snaps, souls, collections, glyphs and karma. Glyphs other pebblers bought stay available to them, without your name attached. This cannot be undone.")
            }
            .alert(
                "Couldn't delete",
                isPresented: Binding(
                    get: { deleteError != nil },
                    set: { if !$0 { deleteError = nil } }
                ),
                presenting: deleteError
            ) { _ in
                Button("OK", role: .cancel) { deleteError = nil }
            } message: { message in
                Text(message)
            }
            .alert("Confirm it's you", isPresented: $isPresentingReauth) {
                switch reauthMethod {
                case .password:
                    SecureField("Password", text: $currentPassword)
                        .textContentType(.password)
                    Button("Confirm") { Task { await submitReauth() } }
                        .disabled(currentPassword.isEmpty)
                case .google:
                    Button("Continue with Google") { Task { await submitReauth() } }
                case .apple:
                    Button("Continue with Apple") { Task { await submitReauth() } }
                }
                Button("Cancel", role: .cancel) { cancelReauth() }
            } message: {
                if let reauthError {
                    Text(reauthError)
                } else {
                    switch reauthMethod {
                    case .password: Text("Enter your password to continue.")
                    case .google: Text("Continue with Google to confirm it's you.")
                    case .apple: Text("Continue with Apple to confirm it's you.")
                    }
                }
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
            .listRowSeparator(.hidden)
        }
    }

    @ViewBuilder
    private var glyphView: some View {
        if let strokes = currentStrokes, !strokes.isEmpty {
            GlyphView(case: .profile, strokes: strokes, side: 120)
        } else {
            GlyphView(case: .carve, side: 120)
        }
    }

    private struct LinkedProvider: Identifiable {
        let id: String       // provider raw value: "apple" | "google" | …
        let label: String    // brand name; rendered verbatim, not localized.
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
        Section {
            ForEach(Array(linkedProviders.enumerated()), id: \.element.id) { index, provider in
                HStack(spacing: 12) {
                    Image(systemName: provider.systemImage)
                        .foregroundStyle(Color.system.secondary)
                    Text(verbatim: provider.label)
                    Spacer()
                }
                .pebblesListRow(position: pebblesRowPosition(index: index, count: linkedProviders.count))
            }
        } header: {
            Text("Providers").pebblesSectionHeader()
        }
    }

    private var informationsSection: some View {
        Section {
            HStack {
                Text("Name")
                    .foregroundStyle(Color.system.secondary)
                Spacer()
                TextField("Your name", text: $displayName)
                    .multilineTextAlignment(.trailing)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled(false)
                    .focused($focusedField, equals: .displayName)
                    .submitLabel(.done)
                    .onSubmit { focusedField = nil }
            }
            .pebblesListRow(position: .top)
            HStack {
                Text("Email")
                    .foregroundStyle(Color.system.secondary)
                Spacer()
                Text(email ?? "—")
                    .foregroundStyle(Color.system.secondary)
            }
            .pebblesListRow(position: .bottom)
        } header: {
            Text("Informations").pebblesSectionHeader()
        }
    }

    /// Public profile (M50): claim a handle, opt in, then share the link.
    /// The toggle stays disabled until a handle is live on the server — the DB
    /// CHECK rejects `public_profile = true` with no handle.
    private var publicProfileSection: some View {
        Section {
            HStack {
                Text("Handle")
                    .foregroundStyle(Color.system.secondary)
                Spacer()
                Text(verbatim: "@")
                    .foregroundStyle(Color.system.secondary)
                TextField("your_handle", text: $handle)
                    .multilineTextAlignment(.trailing)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .focused($focusedField, equals: .handle)
                    .submitLabel(.done)
                    .onSubmit { focusedField = nil }
                    .onChange(of: handle) { _, newValue in
                        handleError = nil
                        // Emptying the field means "release my handle", which the
                        // server pairs with dropping the public flag. Mirror it so
                        // a save can never carry "public, no handle".
                        if newValue.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            isPublicProfile = false
                        }
                    }
            }
            .pebblesListRow(position: .top)

            Toggle(isOn: $isPublicProfile) {
                Text("Make my profile public")
                    .foregroundStyle(
                        initialHandle == nil ? Color.system.secondary : Color.system.foreground
                    )
            }
            .tint(Color.accent.primary)
            .disabled(initialHandle == nil)
            .pebblesListRow(position: shareURL == nil ? .bottom : .middle)

            if let shareURL {
                ShareLink(item: shareURL) {
                    HStack {
                        Label("Share my profile", systemImage: "square.and.arrow.up")
                        Spacer()
                        Text(verbatim: shareURL.absoluteString)
                            .font(.footnote)
                            .foregroundStyle(Color.system.secondary)
                            .lineLimit(1)
                            .truncationMode(.middle)
                    }
                }
                .pebblesListRow(position: .bottom)
            }
        } header: {
            Text("Public profile").pebblesSectionHeader()
        } footer: {
            if let handleError {
                Text(handleError).foregroundStyle(.red)
            } else if initialHandle == nil {
                Text("Claim a handle first to go public.")
            } else {
                Text("3 to 30 characters: lowercase letters, numbers, underscores. Leave empty to release your handle.")
            }
        }
    }

    private var passwordSection: some View {
        Section {
            SecureField("New password", text: $newPassword)
                .textContentType(.newPassword)
                .autocorrectionDisabled(true)
                .textInputAutocapitalization(.never)
                .focused($focusedField, equals: .newPassword)
                .submitLabel(.done)
                .onSubmit { focusedField = nil }
                .pebblesListRow(position: .only)
        } header: {
            Text("Password").pebblesSectionHeader()
        } footer: {
            Text("Leave blank to keep your current password.")
        }
    }

    private var legalSection: some View {
        Section {
            Button { presentedLegalDoc = .terms } label: {
                Label("Terms of Service", systemImage: "doc.text")
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .top)
            Button { presentedLegalDoc = .privacy } label: {
                Label("Privacy Policy", systemImage: "hand.raised")
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .bottom)
        } header: {
            Text("Legal").pebblesSectionHeader()
        }
    }

    #if DEBUG
    /// Debug builds only: the stone material lab (#974). Verbatim text so the
    /// row never reaches the string catalog.
    private var developerSection: some View {
        Section {
            Button {
                // Start building the nine carvings off the main actor now, so
                // the lab has less to wait for when it opens.
                Task.detached(priority: .userInitiated) { StoneCarvingArt.prewarm() }
                isPresentingStoneLab = true
            } label: {
                Label {
                    Text(verbatim: "Stone lab")
                } icon: {
                    Image(systemName: "sparkles")
                }
            }
            .buttonStyle(.plain)
            .pebblesListRow(position: .only)
        } header: {
            Text(verbatim: "Developer").pebblesSectionHeader()
        }
    }
    #endif

    /// Store-mandated account deletion entry (Apple 5.1.1(v): easy to find).
    private var deleteAccountSection: some View {
        Section {
            Button(role: .destructive) {
                isPresentingDeleteConfirm = true
            } label: {
                HStack {
                    Label("Delete account", systemImage: "trash")
                        .foregroundStyle(.red)
                    Spacer()
                    if isDeleting {
                        ProgressView()
                    }
                }
            }
            .buttonStyle(.plain)
            .disabled(isDeleting)
            .pebblesListRow(position: .only)
        } header: {
            Text("Account").pebblesSectionHeader()
        }
    }

    /// Deletion needs a recent sign-in (#976): a stale session goes through
    /// "Confirm it's you" between the confirmation and the call.
    private func confirmDelete() async {
        guard !isDeleting, reauthPurpose == nil else { return }
        guard isSignInFresh else {
            requestReauth(for: .delete)
            return
        }
        await deleteAccount()
    }

    /// Full erasure via the delete-account edge function (purge + storage +
    /// auth user), then a local sign-out: the server session is already gone,
    /// and the session stream flipping to signed-out swaps RootView to Welcome.
    private func deleteAccount() async {
        guard !isDeleting else { return }
        isDeleting = true
        do {
            try await supabase.client.functions.invoke("delete-account")
            await supabase.signOut()
            dismiss()
        } catch let error where RecentAuth.isReauthRequired(error) {
            // The server's clock disagrees with ours (a 428): confirm, then retry.
            logger.info("delete-account wants a recent sign-in")
            isDeleting = false
            requestReauth(for: .delete)
        } catch {
            logger.error("account deletion failed: \(error.localizedDescription, privacy: .private)")
            deleteError = String(localized: "We couldn't delete your account. Nothing was removed. Please try again.")
            isDeleting = false
        }
    }

    /// Maps a `set_handle` rejection to its user-facing reason. The RPC raises
    /// stable codes (`invalid_handle` / `handle_taken` / `handle_reserved`);
    /// anything else — `not_found`, a timeout, a dropped connection — is not a
    /// verdict on the handle, so it falls through to the generic save error.
    private func handleErrorMessage(for error: Error) -> String? {
        let description = "\(error)"
        if description.contains("handle_taken") {
            return String(localized: "That handle is already taken.")
        }
        if description.contains("handle_reserved") {
            return String(localized: "That handle is reserved.")
        }
        if description.contains("invalid_handle") {
            return String(localized: "That handle isn't valid. Use 3 to 30 lowercase letters, numbers or underscores, starting and ending with a letter or number.")
        }
        return nil
    }

    // MARK: - Recent sign-in (#976, #977)

    private func requestReauth(for purpose: ReauthPurpose) {
        reauthPurpose = purpose
        reauthError = nil
        currentPassword = ""
        isPresentingReauth = true
    }

    private func cancelReauth() {
        reauthPurpose = nil
        reauthError = nil
        currentPassword = ""
    }

    /// Password: signs in again as the same user. Google and Apple: re-run the
    /// provider sheet. On success the pending action runs at once; a failure
    /// re-presents the alert with the reason.
    private func submitReauth() async {
        guard let purpose = reauthPurpose else { return }
        let method = reauthMethod
        if method == .password && currentPassword.isEmpty {
            cancelReauth()
            return
        }
        // The row and toolbar spinners double as the alert's working state.
        switch purpose {
        case .save: isSaving = true
        case .delete: isDeleting = true
        }
        do {
            try await reauthenticate(with: method)
        } catch {
            isSaving = false
            isDeleting = false
            reauthFailed(with: error)
            return
        }
        cancelReauth()
        isSaving = false
        isDeleting = false
        switch purpose {
        case .save: await performSave()
        case .delete: await deleteAccount()
        }
    }

    private func reauthenticate(with method: ReauthMethod) async throws {
        switch method {
        case .password: try await supabase.reauthenticate(password: currentPassword)
        case .google: try await supabase.reauthenticateWithGoogle()
        case .apple: try await supabase.reauthenticateWithApple()
        }
    }

    private func reauthFailed(with error: Error) {
        currentPassword = ""
        // Closing the provider sheet abandons the action. A mismatch already
        // signed that other account out, and RootView swaps to Welcome.
        if error is CancellationError || error is ReauthAccountMismatchError {
            cancelReauth()
            return
        }
        logger.error("re-auth failed: \(error.localizedDescription, privacy: .private)")
        reauthError = RecentAuth.isWrongPassword(error)
            ? String(localized: "That password doesn't match. Try again.")
            : String(localized: "We couldn't confirm it's you. Please try again.")
        isPresentingReauth = true
    }

    private func save() async {
        guard isDirty, !isSaving, reauthPurpose == nil else { return }
        if saveNeedsRecentAuth && !isSignInFresh {
            requestReauth(for: .save)
            return
        }
        await performSave()
    }

    private func performSave() async {
        guard !isSaving else { return }
        isSaving = true
        saveError = nil
        handleError = nil

        let nameToSend: String? = {
            let trimmed = trimmedDisplayName
            return (trimmed != initialDisplayName && !trimmed.isEmpty) ? trimmed : nil
        }()
        let glyphIdToSend: String? = {
            guard let picked = pickedGlyph, picked.id != initialGlyphId else { return nil }
            return picked.id.uuidString
        }()
        let passwordToSend: String? = newPassword.isEmpty ? nil : newPassword

        // Handle first: claiming and going public in one save needs the handle
        // stored before the public_profile write can pass the DB CHECK.
        var savedHandle = initialHandle
        if handleChanged {
            do {
                let claimed = normalizedHandle.isEmpty ? nil : normalizedHandle
                try await supabase.client
                    .rpc("set_handle", params: SetHandleParams(p_handle: claimed))
                    .execute()
                savedHandle = claimed
            } catch {
                logger.error("set_handle failed: \(error.localizedDescription, privacy: .private)")
                if let message = handleErrorMessage(for: error) {
                    handleError = message
                } else {
                    saveError = String(localized: "Couldn't save your changes. Please try again.")
                }
                isSaving = false
                return
            }
        }

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

            // Single-column toggle: the sanctioned direct-update case (root
            // AGENTS.md). Releasing the handle already cleared the flag
            // server-side, so only write it while a handle exists.
            if publicProfileChanged, savedHandle != nil, let userId = supabase.session?.user.id {
                try await supabase.client
                    .from("profiles")
                    .update(["public_profile": isPublicProfile])
                    .eq("user_id", value: userId)
                    .execute()
            }

            if let passwordToSend {
                _ = try await supabase.client.auth.update(
                    user: UserAttributes(password: passwordToSend)
                )
            }

            onSaved(
                nameToSend ?? initialDisplayName,
                pickedGlyph,
                savedHandle,
                savedHandle == nil ? false : isPublicProfile
            )
            dismiss()
        } catch let error where RecentAuth.isReauthRequired(error) {
            // The server's clock disagrees with ours. A stored handle is a
            // no-op to resend, and the password is written last, so re-running
            // the whole save after the re-auth is safe.
            logger.info("settings save wants a recent sign-in")
            isSaving = false
            requestReauth(for: .save)
        } catch {
            logger.error("settings save failed: \(error.localizedDescription, privacy: .private)")
            saveError = String(localized: "Couldn't save your changes. Please try again.")
            isSaving = false
        }
    }
}

/// Wire shape for `set_handle`. A null handle releases it (and drops the
/// public flag) server-side.
private struct SetHandleParams: Encodable {
    let p_handle: String?
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
        initialHandle: "alexis",
        initialPublicProfile: true,
        email: "hello@bohns.design",
        onSaved: { _, _, _, _ in }
    )
    .environment(SupabaseService())
}
