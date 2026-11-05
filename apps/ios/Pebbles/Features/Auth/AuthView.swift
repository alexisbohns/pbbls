import SwiftUI

/// Email + password auth screen. Switcher toggles between Login and Signup
/// modes. Signup mode adds four consent checkboxes (terms, privacy, the
/// Art. 9 health-data consent, the 16+ attestation): all four gate the email
/// submit and the Apple and Google buttons alike (#821). All state lives view-locally,
/// including `authError` — the auth service throws on failure and this view
/// catches and renders the message inline.
struct AuthView: View {
    enum Mode: String, CaseIterable, Identifiable {
        case login
        case signup
        var id: String { rawValue }

        var label: LocalizedStringResource {
            switch self {
            case .login:  return "Log In"
            case .signup: return "Sign Up"
            }
        }
    }

    @Environment(SupabaseService.self) private var supabase

    @State private var mode: Mode
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false
    @State private var termsAccepted = false
    @State private var privacyAccepted = false
    @State private var healthDataAccepted = false
    @State private var ageAttested = false
    @State private var presentedLegalDoc: LegalDoc?
    @State private var authError: String?
    @State private var emailError: LocalizedStringResource?

    init(initialMode: Mode = .login) {
        _mode = State(initialValue: initialMode)
    }

    var body: some View {
        VStack(spacing: 24) {
            PebblesAuthSwitcher(mode: $mode)
                .padding(.top, 24)

            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    PebblesTextInput(
                        placeholder: "Email",
                        text: $email,
                        contentType: .emailAddress,
                        keyboard: .emailAddress,
                        autocapitalization: .never,
                        autocorrection: false
                    )

                    if let emailError {
                        Text(emailError)
                            .font(.footnote)
                            .foregroundStyle(Color.pebbles.error)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }

                PebblesTextInput(
                    placeholder: "Password",
                    text: $password,
                    isSecure: true,
                    contentType: mode == .login ? .password : .newPassword,
                    autocapitalization: .never,
                    autocorrection: false
                )
            }

            if mode == .signup {
                VStack(alignment: .leading, spacing: 12) {
                    PebblesCheckbox(
                        isChecked: $termsAccepted,
                        prefix: "I accept the ",
                        linkText: "Terms of Service",
                        onLinkTap: { presentedLegalDoc = .terms }
                    )
                    PebblesCheckbox(
                        isChecked: $privacyAccepted,
                        prefix: "I accept the ",
                        linkText: "Privacy Policy",
                        onLinkTap: { presentedLegalDoc = .privacy }
                    )
                    PebblesCheckbox(
                        isChecked: $healthDataAccepted,
                        // Android `auth_consent_health`, verbatim: one Art. 9 statement on every surface.
                        // swiftlint:disable:next line_length
                        label: "I agree that Pebbles records how I feel: my moods, the emotions I pick and what I write. This is sensitive data about my mental well-being."
                    )
                    PebblesCheckbox(
                        isChecked: $ageAttested,
                        label: "I am 16 years old or over."
                    )
                }
            }

            if let error = authError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(Color.pebbles.error)
                    .multilineTextAlignment(.leading)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }

            Button(action: submit) {
                Text(mode == .login ? "Connect" : "Create an account")
            }
            .buttonStyle(PebblesPrimaryButtonStyle(isLoading: isSubmitting))
            .disabled(!canSubmit)

            Spacer()

            VStack(spacing: 12) {
                AppleSignInButton(action: { Task { await runApple() } })
                    .disabled(!canUseProvider)

                GoogleSignInButton(action: { Task { await runGoogle() } })
                    .disabled(!canUseProvider)

                LegalDisclaimerText(
                    onTermsTap: { presentedLegalDoc = .terms },
                    onPrivacyTap: { presentedLegalDoc = .privacy }
                )
                .padding(.top, 8)
            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 32)
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
        .onChange(of: mode) { _, newMode in
            authError = nil
            if newMode == .login {
                termsAccepted = false
                privacyAccepted = false
                healthDataAccepted = false
                ageAttested = false
            }
        }
        .onChange(of: email) { oldValue, newValue in
            // Writing `email = normalized` below re-enters this closure with
            // the normalized string. Detect that and skip so we don't undo
            // the emailError we just set when stripping a '+'.
            if oldValue != newValue, Self.normalizeEmailInput(oldValue) == newValue {
                return
            }
            if authError != nil { authError = nil }
            // Stripping '+' is the only normalization that requires an inline
            // explanation — lowercasing is silent on purpose.
            if newValue.contains("+") {
                emailError = "The '+' character is not allowed in email addresses."
            } else if emailError != nil {
                emailError = nil
            }
            let normalized = Self.normalizeEmailInput(newValue)
            if normalized != newValue {
                email = normalized
            }
        }
        .onChange(of: password) { _, _ in
            if authError != nil { authError = nil }
        }
        .pebblesScreen()
    }

    private var canSubmit: Bool {
        Self.canSubmit(
            mode: mode,
            email: email,
            password: password,
            termsAccepted: termsAccepted,
            privacyAccepted: privacyAccepted,
            healthDataAccepted: healthDataAccepted,
            ageAttested: ageAttested,
            isSubmitting: isSubmitting
        )
    }

    private var canUseProvider: Bool {
        Self.canUseProvider(
            mode: mode,
            termsAccepted: termsAccepted,
            privacyAccepted: privacyAccepted,
            healthDataAccepted: healthDataAccepted,
            ageAttested: ageAttested,
            isSubmitting: isSubmitting
        )
    }

    // swiftlint:disable:next function_parameter_count
    static func canSubmit(
        mode: Mode,
        email: String,
        password: String,
        termsAccepted: Bool,
        privacyAccepted: Bool,
        healthDataAccepted: Bool,
        ageAttested: Bool,
        isSubmitting: Bool
    ) -> Bool {
        let trimmed = email.trimmingCharacters(in: .whitespaces)
        guard !isSubmitting,
              !trimmed.isEmpty,
              trimmed.contains("@"),
              trimmed.contains("."),
              !trimmed.contains("+"),
              password.count >= 6
        else { return false }

        if mode == .signup {
            return termsAccepted && privacyAccepted && healthDataAccepted && ageAttested
        }
        return true
    }

    // swiftlint:disable function_parameter_count
    /// Whether the Apple and Google buttons are live. In signup mode a
    /// provider creates the account just as the email form does, so it waits
    /// for the same four consents (web's `canSubmitRegistration`). In login
    /// mode an account may already exist; whatever it lacks is asked after
    /// sign-in, never here.
    static func canUseProvider(
        mode: Mode,
        termsAccepted: Bool,
        privacyAccepted: Bool,
        healthDataAccepted: Bool,
        ageAttested: Bool,
        isSubmitting: Bool
    ) -> Bool {
        guard !isSubmitting else { return false }
        if mode == .signup {
            return termsAccepted && privacyAccepted && healthDataAccepted && ageAttested
        }
        return true
    }
    // swiftlint:enable function_parameter_count

    /// Lowercases and strips disallowed characters from raw email input.
    /// Real-time scrubbing keeps the visible field, the submitted value, and
    /// the server's view of the address in sync.
    static func normalizeEmailInput(_ raw: String) -> String {
        var result = raw.lowercased()
        result.removeAll { $0 == "+" }
        return result
    }

    private func submit() {
        isSubmitting = true
        authError = nil
        let submittedEmail = email.trimmingCharacters(in: .whitespaces)
        Task {
            do {
                switch mode {
                case .login:
                    try await supabase.signIn(email: submittedEmail, password: password)
                case .signup:
                    try await supabase.signUp(email: submittedEmail, password: password)
                }
            } catch {
                authError = error.localizedDescription
            }
            isSubmitting = false
        }
    }

    private func runApple() async {
        guard canUseProvider else { return }
        isSubmitting = true
        authError = nil
        do {
            try await supabase.signInWithApple()
        } catch {
            authError = error.localizedDescription
        }
        isSubmitting = false
    }

    private func runGoogle() async {
        guard canUseProvider else { return }
        isSubmitting = true
        authError = nil
        do {
            try await supabase.signInWithGoogle()
        } catch {
            authError = error.localizedDescription
        }
        isSubmitting = false
    }
}

#Preview("Login") {
    AuthView(initialMode: .login)
        .environment(SupabaseService())
}

#Preview("Signup") {
    AuthView(initialMode: .signup)
        .environment(SupabaseService())
}
