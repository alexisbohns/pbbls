import SwiftUI

/// The single action a step may offer beneath its content.
///
/// Top-level rather than nested inside the generic scaffold: `RecordFlowView`
/// builds these in a `switch` that never names a `Content` type, and
/// `RecordStepScaffold<AnyView>.Action` would be a placeholder standing in for
/// nothing.
enum RecordStepAction {
    /// Full-width prominent button — `Continue` on `when` / `name`,
    /// `Skip` / `Done` on the optional steps (D3), `Publish` on `privacy`.
    case primary(LocalizedStringResource, enabled: Bool, loading: Bool, () -> Void)
}

/// Shared geometry for every step: a title, a content slot, and one optional
/// button beneath it.
///
/// Steps supply content and a button role and never their own layout, so the
/// title baseline and button position do not drift between screens as the user
/// moves through the flow — which is the whole reason the flow reads as one
/// motion rather than eleven pages.
struct RecordStepScaffold<Content: View>: View {
    let title: LocalizedStringResource
    var subtitle: LocalizedStringResource?
    var action: RecordStepAction?
    /// When true the content fills the space beneath the title instead of
    /// scrolling, and the action floats over its bottom edge. The photo step
    /// uses it so the library grid runs to the bottom of the screen — only
    /// steps whose action is always enabled should, since a disabled pill is
    /// an outline and would not read over a photo.
    var contentFillsHeight: Bool = false
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: Spacing.lg) {
            VStack(spacing: Spacing.xs) {
                Text(title)
                    .pebblesFont(.title)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)

                if let subtitle {
                    Text(subtitle)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                        .multilineTextAlignment(.center)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.horizontal, Spacing.lg)

            if contentFillsHeight {
                content()
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .overlay(alignment: .bottom) { floatingAction }
            } else {
                ScrollView {
                    content()
                        .padding(.horizontal, Spacing.lg)
                        .padding(.bottom, Spacing.lg)
                }
                .scrollBounceBehavior(.basedOnSize)

                if let action {
                    actionView(action)
                        .padding(.horizontal, Spacing.lg)
                        .padding(.bottom, Spacing.sm)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// The same pill, laid over the content's bottom edge rather than pushing
    /// it up. The shadow is what separates it from whatever it covers.
    @ViewBuilder
    private var floatingAction: some View {
        if let action {
            actionView(action)
                .shadow(color: .black.opacity(0.22), radius: 14, y: 4)
                .padding(.horizontal, Spacing.xl)
                .padding(.bottom, Spacing.lg)
        }
    }

    @ViewBuilder
    private func actionView(_ action: RecordStepAction) -> some View {
        switch action {
        case let .primary(label, enabled, loading, perform):
            Button(action: perform) {
                Text(label)
            }
            .buttonStyle(PebblesPrimaryButtonStyle(isLoading: loading))
            .disabled(!enabled || loading)
        }
    }
}
