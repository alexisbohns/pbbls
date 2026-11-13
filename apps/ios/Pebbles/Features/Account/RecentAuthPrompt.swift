import SwiftUI
import os

/// "Confirm it's you" (#976, #977): the recent sign-in an action waits on.
///
/// One instance per action that can ask for it (Settings' save, account
/// deletion), so each caller's spinner reflects only its own re-sign-in.
/// `recentAuthPrompt(_:)` draws the alert.
@MainActor
@Observable
final class RecentAuthPrompt {
    var isPresented = false
    /// The password for an email account. Never persisted, cleared after every attempt.
    var password = ""
    /// Set after a failed attempt; the alert re-presents with it.
    private(set) var error: String?
    /// The re-sign-in is running. Callers show it on their own spinner.
    private(set) var isWorking = false
    /// The action waiting on a recent sign-in. It receives Apple's
    /// authorization code after an Apple re-sign-in, nil otherwise.
    private var pending: ((String?) async -> Void)?

    var isPending: Bool { pending != nil }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "recent-auth")

    static func method(for supabase: SupabaseService) -> ReauthMethod {
        ReauthMethod(providers: (supabase.session?.user.identities ?? []).map(\.provider))
    }

    func request(then action: @escaping (String?) async -> Void) {
        pending = action
        error = nil
        password = ""
        isPresented = true
    }

    func cancel() {
        pending = nil
        error = nil
        password = ""
    }

    /// Password: signs in again as the same user. Google and Apple: re-run the
    /// provider sheet. On success the pending action runs at once; a failure
    /// re-presents the alert with the reason.
    func submit(with supabase: SupabaseService) async {
        guard let action = pending else { return }
        let method = Self.method(for: supabase)
        if method == .password && password.isEmpty {
            cancel()
            return
        }
        isWorking = true
        let appleCode: String?
        do {
            appleCode = try await reauthenticate(supabase, with: method)
        } catch {
            isWorking = false
            failed(with: error)
            return
        }
        cancel()
        isWorking = false
        await action(appleCode)
    }

    /// Returns Apple's authorization code for an Apple re-auth, nil otherwise.
    private func reauthenticate(_ supabase: SupabaseService, with method: ReauthMethod) async throws -> String? {
        switch method {
        case .password:
            try await supabase.reauthenticate(password: password)
            return nil
        case .google:
            try await supabase.reauthenticateWithGoogle()
            return nil
        case .apple:
            return try await supabase.reauthenticateWithApple()
        }
    }

    private func failed(with error: Error) {
        password = ""
        // Closing the provider sheet abandons the action. A mismatch already
        // signed that other account out, and RootView swaps to Welcome.
        if error is CancellationError || error is ReauthAccountMismatchError {
            cancel()
            return
        }
        logger.error("re-auth failed: \(error.localizedDescription, privacy: .private)")
        self.error = RecentAuth.isWrongPassword(error)
            ? String(localized: "That password doesn't match. Try again.")
            : String(localized: "We couldn't confirm it's you. Please try again.")
        isPresented = true
    }
}

private struct RecentAuthPromptModifier: ViewModifier {
    @Bindable var prompt: RecentAuthPrompt
    @Environment(SupabaseService.self) private var supabase

    func body(content: Content) -> some View {
        content.alert("Confirm it's you", isPresented: $prompt.isPresented) {
            switch RecentAuthPrompt.method(for: supabase) {
            case .password:
                SecureField("Password", text: $prompt.password)
                    .textContentType(.password)
                Button("Confirm") { Task { await prompt.submit(with: supabase) } }
                    .disabled(prompt.password.isEmpty)
            case .google:
                Button("Continue with Google") { Task { await prompt.submit(with: supabase) } }
            case .apple:
                Button("Continue with Apple") { Task { await prompt.submit(with: supabase) } }
            }
            Button("Cancel", role: .cancel) { prompt.cancel() }
        } message: {
            if let error = prompt.error {
                Text(error)
            } else {
                switch RecentAuthPrompt.method(for: supabase) {
                case .password: Text("Enter your password to continue.")
                case .google: Text("Continue with Google to confirm it's you.")
                case .apple: Text("Continue with Apple to confirm it's you.")
                }
            }
        }
    }
}

extension View {
    /// Draws `prompt`'s "Confirm it's you" alert.
    func recentAuthPrompt(_ prompt: RecentAuthPrompt) -> some View {
        modifier(RecentAuthPromptModifier(prompt: prompt))
    }
}
