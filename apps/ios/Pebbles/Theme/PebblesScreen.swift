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
                .foregroundStyle(Color.pebblesAccent)
        }
    }
}

private struct PebblesScreen: ViewModifier {
    let background: Color
    func body(content: Content) -> some View {
        content
            .tint(Color.pebblesAccent)
            .foregroundStyle(Color.pebblesMutedForeground)
            .labelStyle(PebblesLabelStyle())
            .scrollContentBackground(.hidden)
            .background(background)
            .toolbarBackground(background, for: .navigationBar)
    }
}

extension View {
    /// Applies the Pebbles design-system styling: tint, foreground, background,
    /// hidden scroll-content background, and nav/tab toolbar backgrounds.
    ///
    /// Apply inside a `NavigationStack` so the toolbar modifiers attach to the
    /// correct bar. Modifiers that don't apply to the current context (e.g.
    /// `.toolbarBackground` when there is no toolbar) are inert.
    ///
    /// Default background uses `Color.pebblesBackground`. Pass a custom
    /// `background` to override (e.g. PathView wants a pure-white root in
    /// light mode while keeping the dark theme color in dark).
    func pebblesScreen(background: Color = Color.pebblesBackground) -> some View {
        modifier(PebblesScreen(background: background))
    }
}
