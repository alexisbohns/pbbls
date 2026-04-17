import SwiftUI

/// Email + password auth screen. Segmented control toggles between Login and
/// Signup modes. Signup mode adds consent checkboxes (wired in Task 9).
///
/// State lives here (view-local) except for `authError`, which is read from
/// `SupabaseService` so errors from `signIn` / `signUp` surface inline.
struct AuthView: View {
    enum Mode: String, CaseIterable, Identifiable {
        case login = "Log In"
        case signup = "Sign Up"
        var id: String { rawValue }
    }

    @Environment(SupabaseService.self) private var supabase

    @State private var mode: Mode = .login
    @State private var email = ""
    @State private var password = ""
    @State private var isSubmitting = false
    @State private var termsAccepted = false
    @State private var privacyAccepted = false
    @State private var presentedLegalDoc: LegalDoc?

    var body: some View {
        VStack(spacing: 24) {
            Text("Pebbles")
                .font(.largeTitle.weight(.semibold))
                .padding(.top, 48)

            Picker("Mode", selection: $mode) {
                ForEach(Mode.allCases) { mode in
                    Text(mode.rawValue).tag(mode)
                }
            }
            .pickerStyle(.segmented)

            VStack(spacing: 12) {
                TextField("Email", text: $email)
                    .textContentType(.emailAddress)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .textFieldStyle(.roundedBorder)

                SecureField("Password", text: $password)
                    .textContentType(mode == .login ? .password : .newPassword)
                    .textFieldStyle(.roundedBorder)
            }

            if mode == .signup {
                VStack(alignment: .leading, spacing: 12) {
                    ConsentCheckbox(
                        isChecked: $termsAccepted,
                        prefix: "I accept the ",
                        linkText: "Terms of Service",
                        onLinkTap: { presentedLegalDoc = .terms }
                    )
                    ConsentCheckbox(
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

            Button {
                submit()
            } label: {
                Text("Continue")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent)
            .disabled(!canSubmit)

            Spacer()
        }
        .padding(.horizontal, 24)
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
}

#Preview {
    AuthView()
        .environment(SupabaseService())
}
