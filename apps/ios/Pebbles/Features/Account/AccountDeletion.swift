import Supabase
import SwiftUI
import os

/// Full account erasure (Apple 5.1.1(v)), shared by Settings and the consent
/// gate (#1533): the confirmation, a recent sign-in when the session is stale
/// (#976), the Apple grant revocation (#1456) and the `delete-account` edge
/// function. `accountDeletion(_:message:onDeleted:)` draws its dialogs.
@MainActor
@Observable
final class AccountDeletion {
    var isConfirming = false
    private(set) var isDeleting = false
    var error: String?
    let recentAuth = RecentAuthPrompt()

    /// Deleting, or confirming it's you on the way there. Drives the caller's spinner.
    var isBusy: Bool { isDeleting || recentAuth.isWorking }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "account-deletion")

    func begin() {
        isConfirming = true
    }

    /// Deletion needs a recent sign-in (#976): a stale session goes through
    /// "Confirm it's you" between the confirmation and the call.
    func confirm(with supabase: SupabaseService, onDeleted: @escaping () -> Void) async {
        guard !isDeleting, !recentAuth.isPending else { return }
        guard RecentAuth.isFresh(accessToken: supabase.session?.accessToken) else {
            requestRecentAuth(supabase, onDeleted: onDeleted)
            return
        }
        await delete(with: supabase, onDeleted: onDeleted)
    }

    private func requestRecentAuth(_ supabase: SupabaseService, onDeleted: @escaping () -> Void) {
        recentAuth.request { [weak self] appleCode in
            await self?.delete(with: supabase, appleCode: appleCode, onDeleted: onDeleted)
        }
    }

    /// Full erasure via the delete-account edge function (purge + storage +
    /// auth user), then a local sign-out: the server session is already gone,
    /// and the session stream flipping to signed-out swaps RootView to Welcome.
    ///
    /// An Apple account also sends a fresh Apple authorization code so the
    /// server revokes the grant (#1456). An Apple re-auth already produced one;
    /// otherwise the Apple sheet runs once here, and closing it abandons the
    /// deletion.
    private func delete(
        with supabase: SupabaseService,
        appleCode: String? = nil,
        onDeleted: @escaping () -> Void
    ) async {
        guard !isDeleting else { return }
        isDeleting = true
        do {
            var code = appleCode
            if code == nil && Self.hasAppleIdentity(supabase) {
                code = try await supabase.appleAuthorizationCodeForDeletion()
            }
            if let code {
                try await supabase.client.functions.invoke(
                    "delete-account",
                    options: FunctionInvokeOptions(body: ["apple_authorization_code": code])
                )
            } else {
                try await supabase.client.functions.invoke("delete-account")
            }
            await supabase.signOut()
            onDeleted()
        } catch let error where RecentAuth.isReauthRequired(error) {
            // The server's clock disagrees with ours (a 428): confirm, then retry.
            logger.info("delete-account wants a recent sign-in")
            isDeleting = false
            requestRecentAuth(supabase, onDeleted: onDeleted)
        } catch is CancellationError {
            isDeleting = false
        } catch {
            logger.error("account deletion failed: \(error.localizedDescription, privacy: .private)")
            self.error = String(localized: "We couldn't delete your account. Nothing was removed. Please try again.")
            isDeleting = false
        }
    }

    /// Deleting an account with an Apple identity revokes the Apple grant
    /// (#1456), which needs a fresh authorization code from the Apple sheet.
    private static func hasAppleIdentity(_ supabase: SupabaseService) -> Bool {
        (supabase.session?.user.identities ?? []).contains { $0.provider == "apple" }
    }
}

private struct AccountDeletionModifier: ViewModifier {
    @Bindable var deletion: AccountDeletion
    let message: LocalizedStringResource
    let onDeleted: () -> Void
    @Environment(SupabaseService.self) private var supabase

    func body(content: Content) -> some View {
        content
            .confirmationDialog(
                "Delete your account?",
                isPresented: $deletion.isConfirming,
                titleVisibility: .visible
            ) {
                Button("Delete forever", role: .destructive) {
                    Task { await deletion.confirm(with: supabase, onDeleted: onDeleted) }
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text(message)
            }
            .alert(
                "Couldn't delete",
                isPresented: Binding(
                    get: { deletion.error != nil },
                    set: { if !$0 { deletion.error = nil } }
                ),
                presenting: deletion.error
            ) { _ in
                Button("OK", role: .cancel) { deletion.error = nil }
            } message: { message in
                Text(message)
            }
            .recentAuthPrompt(deletion.recentAuth)
    }
}

extension View {
    /// Draws `deletion`'s confirmation (with `message`), its error and its
    /// "Confirm it's you" alert. `onDeleted` runs after the local sign-out.
    func accountDeletion(
        _ deletion: AccountDeletion,
        message: LocalizedStringResource,
        onDeleted: @escaping () -> Void = {}
    ) -> some View {
        modifier(AccountDeletionModifier(deletion: deletion, message: message, onDeleted: onDeleted))
    }
}
