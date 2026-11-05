import SwiftUI

/// The consent gate, drawn by `RootView` above everything in the authed
/// branch. Mirrors Android's `ConsentGateScreen` (design §5.3).
///
/// An opaque layer that swallows every touch aimed at the app beneath it. It
/// asks only for the missing kinds, and never offers to withdraw anything:
/// `terms`, `privacy` and `age_assurance` are non-withdrawable, and
/// `health_data` is withdrawn from Settings, never here. Declining is Log out.
struct ConsentGateView: View {
    let model: ConsentGateModel
    let onSignOut: () -> Void

    @State private var presentedLegalDoc: LegalDoc?

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
                }
            }
        }
        .contentShape(Rectangle())
        .sheet(item: $presentedLegalDoc) { doc in
            LegalDocumentSheet(url: doc.url)
                .ignoresSafeArea()
        }
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
