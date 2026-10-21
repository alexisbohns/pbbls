import SwiftUI

/// Renders a `Label`'s icon in the Pebbles accent while letting the title
/// inherit the ambient `foregroundStyle`. Cascades via the SwiftUI environment
/// when applied through `.labelStyle(_:)` on a parent view.
private struct PebblesLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        Label {
            configuration.title
        } icon: {
            configuration.icon
                .foregroundStyle(Color.pebbles.primary)
        }
    }
}

private struct PebblesScreen: ViewModifier {
    func body(content: Content) -> some View {
        content
            .tint(Color.pebbles.primary)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .labelStyle(PebblesLabelStyle())
            .scrollContentBackground(.hidden)
            .background(Color.pebbles.surface)
            .toolbarBackground(Color.pebbles.surface, for: .navigationBar)
    }
}

extension View {
    /// Applies the Pebbles design-system styling: tint, foreground,
    /// background, hidden scroll-content background, and nav-toolbar
    /// background. Background is always the `surface` role — there is
    /// no override knob, screens should not deviate.
    ///
    /// Apply inside a `NavigationStack` so the toolbar modifier attaches
    /// to the correct bar. Modifiers that don't apply to the current
    /// context (e.g. `.toolbarBackground` when there is no toolbar)
    /// are inert.
    func pebblesScreen() -> some View {
        modifier(PebblesScreen())
    }
}
