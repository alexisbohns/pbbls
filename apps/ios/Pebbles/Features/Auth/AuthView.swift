import SwiftUI

/// Email + password auth screen. Switcher toggles between Login and Signup
/// modes. Signup mode adds two consent checkboxes. State lives view-locally
/// except for `authError`, which is read from `SupabaseService` so failures
/// from `signIn` / `signUp` surface inline.
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
    @State private var presentedLegalDoc: LegalDoc?

    init(initialMode: Mode = .login) {
        _mode = State(initialValue: initialMode)
    }

    var body: some View {
        VStack(spacing: 24) {
            PebblesAuthSwitcher(mode: $mode)
                .padding(.top, 24)

            VStack(spacing: 12) {
                PebblesTextInput(
                    placeholder: "Email",
                    text: $email,
                    contentType: .emailAddress,
                    keyboard: .emailAddress,
                    autocapitalization: .never,
                    autocorrection: false
                )

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
                }
            }

            if let error = supabase.authError {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
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
                    .disabled(isSubmitting)

                GoogleSignInButton(action: { Task { await runGoogle() } })
                    .disabled(isSubmitting)

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
            supabase.authError = nil
            if newMode == .login {
                termsAccepted = false
                privacyAccepted = false
            }
        }
        .onChange(of: email) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
        }
        .onChange(of: password) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
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
            isSubmitting: isSubmitting
        )
    }

    static func canSubmit(
        mode: Mode,
        email: String,
        password: String,
        termsAccepted: Bool,
        privacyAccepted: Bool,
        isSubmitting: Bool
    ) -> Bool {
        guard !isSubmitting,
              email.contains("@"),
              password.count >= 6
        else { return false }

        if mode == .signup {
            return termsAccepted && privacyAccepted
        }
        return true
    }

    private func submit() {
        isSubmitting = true
        Task {
            switch mode {
            case .login:
                await supabase.signIn(email: email, password: password)
            case .signup:
                await supabase.signUp(email: email, password: password)
            }
            isSubmitting = false
        }
    }

    private func runApple() async {
        guard !isSubmitting else { return }
        isSubmitting = true
        await supabase.signInWithApple()
        isSubmitting = false
    }

    private func runGoogle() async {
        guard !isSubmitting else { return }
        isSubmitting = true
        await supabase.signInWithGoogle()
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
