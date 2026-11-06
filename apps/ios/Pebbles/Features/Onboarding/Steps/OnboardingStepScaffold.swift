import SwiftUI

/// Shared geometry for the onboarding steps: a left-aligned title and lead, a content slot and a
/// footer pinned below it. `RecordStepScaffold` centres its title in another rank, so this is the
/// mock's left-aligned sibling.
struct OnboardingStepScaffold<Content: View, Footer: View>: View {
    let title: LocalizedStringResource
    var lead: LocalizedStringResource?
    /// Reference renders pass false: `ImageRenderer` draws no `ScrollView` content.
    var scrolls = true
    @ViewBuilder let content: () -> Content
    @ViewBuilder let footer: () -> Footer

    /// The scroll region's margin at either end, also the depth of its fades.
    private let edgeFade: CGFloat = Spacing.lg

    var body: some View {
        VStack(spacing: 0) {
            scrollingContent
            footer()
                .padding(.horizontal, Spacing.xl)
                .padding(.bottom, Spacing.sm)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var column: some View {
        VStack(alignment: .leading, spacing: Spacing.lg) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(title)
                    .spineType(.stepTitle)
                    .foregroundStyle(Color.pebbles.onSurface)
                    .accessibilityAddTraits(.isHeader)
                if let lead {
                    Text(lead)
                        .spineType(.greetingLine)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
            }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.xl)
    }

    @ViewBuilder
    private var scrollingContent: some View {
        if scrolls {
            ScrollView { column }
                .contentMargins(.vertical, edgeFade, for: .scrollContent)
                .scrollBounceBehavior(.basedOnSize)
                .mask(fadeMask)
        } else {
            column
                .padding(.vertical, edgeFade)
                .frame(maxHeight: .infinity, alignment: .top)
        }
    }

    private var fadeMask: some View {
        VStack(spacing: 0) {
            LinearGradient(colors: [.clear, Color.pebbles.onSurface], startPoint: .top, endPoint: .bottom)
                .frame(height: edgeFade)
            Color.pebbles.onSurface
            LinearGradient(colors: [Color.pebbles.onSurface, .clear], startPoint: .top, endPoint: .bottom)
                .frame(height: edgeFade)
        }
    }
}
