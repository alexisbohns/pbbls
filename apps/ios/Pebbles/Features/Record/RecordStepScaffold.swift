import SwiftUI

/// The single action a step may offer beneath its content.
///
/// Top-level rather than nested inside the generic scaffold: the Capture and
/// Complete flows build these in a `switch` that never names a `Content` type, and
/// `RecordStepScaffold<AnyView>.Action` would be a placeholder standing in for
/// nothing.
enum RecordStepAction {
    /// Full-width prominent button — `Continue` on `when` / `name`,
    /// `Skip` / `Done` on the optional steps (D3), `Publish` on `privacy`.
    case primary(LocalizedStringResource, enabled: Bool, loading: Bool, () -> Void)
}

/// A quieter text button under the primary one. Capture's name step uses it
/// for "Make it a pebble now" (A1).
struct RecordStepSecondaryAction {
    let label: LocalizedStringResource
    let enabled: Bool
    /// Shows a spinner in place of the label while the work it started runs.
    var isLoading = false
    let perform: () -> Void
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
    /// Shown under the primary action. Ignored when `contentFillsHeight`,
    /// whose floating pill has no room beneath it.
    var secondaryAction: RecordStepSecondaryAction?
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: Spacing.lg) {
            VStack(spacing: Spacing.xs) {
                Text(title)
                    .pebblesFont(.title)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .multilineTextAlignment(.center)

                if let subtitle {
                    Text(subtitle)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
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
                    VStack(spacing: Spacing.xs) {
                        actionView(action)
                        if let secondaryAction {
                            secondaryButton(secondaryAction)
                        }
                    }
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// A plain text button whose whole 44pt row takes the tap, not only the
    /// glyphs of its label.
    private func secondaryButton(_ secondary: RecordStepSecondaryAction) -> some View {
        Button(action: secondary.perform) {
            Group {
                if secondary.isLoading {
                    ProgressView().tint(Color.pebbles.primary)
                } else {
                    Text(secondary.label)
                        .pebblesFont(.bodyEmphasized)
                        .foregroundStyle(Color.pebbles.primary)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        // The spinner replaces the label on screen, not for VoiceOver.
        .accessibilityLabel(Text(secondary.label))
        .disabled(!secondary.enabled || secondary.isLoading)
        .opacity(secondary.enabled || secondary.isLoading ? 1 : 0.4)
    }

    /// The same pill, laid over the content's bottom edge rather than pushing
    /// it up. The shadow is what separates it from whatever it covers.
    @ViewBuilder
    private var floatingAction: some View {
        if let action {
            actionView(action)
                .shadow(color: Color.pebbles.scrim.opacity(0.22), radius: 14, y: 4)
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
