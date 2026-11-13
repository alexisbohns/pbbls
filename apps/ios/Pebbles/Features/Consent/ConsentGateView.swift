import SwiftUI

/// The consent gate, drawn by `RootView` above everything in the authed
/// branch. Mirrors Android's `ConsentGateScreen` (design §5.3).
///
/// An opaque layer that swallows every touch aimed at the app beneath it. It
/// asks only for the missing kinds, and never offers to withdraw anything:
/// `terms`, `privacy` and `age_assurance` are non-withdrawable, and
/// `health_data` is withdrawn from Settings, never here. Declining is Log out,
/// or Delete my account (#1533, Android #1030): erasure must not require
/// accepting anything first.
struct ConsentGateView: View {
    let model: ConsentGateModel
    let onSignOut: () -> Void

    @State private var presentedLegalDoc: LegalDoc?
    @State private var deletion = AccountDeletion()

    var body: some View {
        ZStack {
            Color.pebbles.surface.ignoresSafeArea()

            switch model.state {
            // Satisfied is never shown by RootView; a blank surface is the
            // safe render if it ever is.
            case .satisfied:
                EmptyView()
            // Idle shows for the frame between sign-in and the first start.
            case .idle, .checking:
                ProgressView()
                    .tint(Color.pebbles.primary)
            case .failed(let error):
                column {
                    Text(error.message)
                        .font(.body)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Button { model.retry() } label: { Text("Try again") }
                        .buttonStyle(PebblesPrimaryButtonStyle(isLoading: false))
                    signOutButton
                    deleteAccountButton
                }
            case .required(let form):
                column {
                    Text("Before you continue")
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(Color.pebbles.onSurface)
                        .accessibilityAddTraits(.isHeader)
                    // swiftlint:disable:next line_length
                    Text("Pebbles keeps a journal of how you feel, so we need your agreement on each point below. Tick them all to carry on.")
                        .font(.body)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(form.missing, id: \.self) { kind in
                            row(kind, isChecked: form.ticked.contains(kind))
                        }
                    }
                    if let error = form.error {
                        Text(error.message)
                            .font(.footnote)
                            .foregroundStyle(Color.pebbles.error)
                    }
                    Button { model.submit() } label: { Text("Continue") }
                        .buttonStyle(PebblesPrimaryButtonStyle(isLoading: form.isSubmitting))
                        .disabled(!form.canContinue)
                    signOutButton
                    deleteAccountButton
                }
            }
        }
        .contentShape(Rectangle())
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
        // A successful deletion signs out, and RootView drops the gate with the session.
        .accountDeletion(
            deletion,
            title: ConsentGateStrings.deleteTitle,
            message: ConsentGateStrings.deleteMessage,
            failure: ConsentGateStrings.deleteFailure
        )
    }

    private func column(@ViewBuilder _ content: () -> some View) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                content()
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 48)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollBounceBehavior(.basedOnSize)
    }

    private var signOutButton: some View {
        Button(action: onSignOut) { Text("Log out") }
            .font(.body.weight(.medium))
            .foregroundStyle(Color.pebbles.primary)
            .frame(maxWidth: .infinity)
            .frame(minHeight: 44)
    }

    private var deleteAccountButton: some View {
        Button { deletion.begin() } label: {
            if deletion.isBusy {
                ProgressView()
                    .tint(Color.pebbles.error)
            } else {
                Text(ConsentGateStrings.deleteButton)
            }
        }
        .font(.body.weight(.medium))
        .foregroundStyle(Color.pebbles.error)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 44)
        .disabled(deletion.isBusy)
    }

    @ViewBuilder
    private func row(_ kind: ConsentKind, isChecked: Bool) -> some View {
        let binding = Binding(get: { isChecked }, set: { model.toggle(kind, $0) })
        switch kind {
        case .terms:
            PebblesCheckbox(
                isChecked: binding,
                prefix: "I accept the ",
                linkText: "Terms of Service",
                onLinkTap: { presentedLegalDoc = .terms }
            )
        case .privacy:
            PebblesCheckbox(
                isChecked: binding,
                prefix: "I accept the ",
                linkText: "Privacy Policy",
                onLinkTap: { presentedLegalDoc = .privacy }
            )
        case .healthData:
            PebblesCheckbox(
                isChecked: binding,
                // Android `auth_consent_health`, verbatim: one Art. 9 statement on every surface.
                // swiftlint:disable:next line_length
                label: "I agree that Pebbles records how I feel: my moods, the emotions I pick and what I write. This is sensitive data about my mental well-being."
            )
        case .ageAssurance:
            PebblesCheckbox(isChecked: binding, label: "I am 16 years old or over.")
        }
    }
}

/// The gate's deletion copy (#1533). Dotted keys because Settings asks the same
/// English questions in French with "vous", and the gate speaks "tu" (Android
/// `consent_gate_delete_*`, verbatim).
enum ConsentGateStrings {
    static let deleteButton = LocalizedStringResource(
        "consent.gate.delete.button", defaultValue: "Delete my account",
        comment: "Consent gate: deletes the account without accepting anything")
    static let deleteTitle = LocalizedStringResource(
        "consent.gate.delete.title", defaultValue: "Delete your account?",
        comment: "Consent gate: deletion confirmation title")
    static let deleteMessage = LocalizedStringResource(
        "consent.gate.delete.message",
        // swiftlint:disable:next line_length
        defaultValue: "You don't have to accept anything above to do this. It permanently deletes your account and everything in it: pebbles, snaps, souls, collections, glyphs and karma. Glyphs other pebblers bought stay available to them, without your name attached. This cannot be undone.",
        comment: "Consent gate: deletion confirmation message")
    static let deleteFailure = LocalizedStringResource(
        "consent.gate.delete.failure",
        defaultValue: "We couldn't delete your account. Nothing was removed. Please try again.",
        comment: "Consent gate: deletion failed, nothing removed")
}
