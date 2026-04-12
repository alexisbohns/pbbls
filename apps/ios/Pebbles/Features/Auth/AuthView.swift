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
        .onChange(of: mode) { _, _ in
            supabase.authError = nil
        }
        .onChange(of: email) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
        }
        .onChange(of: password) { _, _ in
            if supabase.authError != nil { supabase.authError = nil }
        }
    }

    private var canSubmit: Bool {
        guard !isSubmitting,
              email.contains("@"),
              password.count >= 6
        else { return false }
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
