import SwiftUI

private struct PebblesScreen: ViewModifier {
    func body(content: Content) -> some View {
        content
            .tint(Color.pebblesAccent)
            .foregroundStyle(Color.pebblesForeground)
            .scrollContentBackground(.hidden)
            .background(Color.pebblesBackground)
            .toolbarBackground(Color.pebblesBackground, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarBackground(Color.pebblesBackground, for: .tabBar)
            .toolbarBackground(.visible, for: .tabBar)
    }
}

extension View {
    /// Applies the Pebbles design-system styling: tint, foreground, background,
    /// hidden scroll-content background, and nav/tab toolbar backgrounds.
    ///
    /// Apply inside a `NavigationStack` so the toolbar modifiers attach to the
    /// correct bar. Modifiers that don't apply to the current context (e.g.
    /// `.toolbarBackground` when there is no toolbar) are inert.
    func pebblesScreen() -> some View {
        modifier(PebblesScreen())
    }
}
