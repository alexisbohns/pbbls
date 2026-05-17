import SwiftUI

/// Toolbar button wrapper that renders as a native iOS button (Liquid Glass
/// capsule on iOS 26) with its label color pinned to `system.secondary`.
///
/// Exists so toolbar buttons render in the branded "secondary" color rather
/// than the ambient accent set by `pebblesScreen()`, and so the rule has one
/// grep target. Adds no custom shape, padding, or `.buttonStyle` — the system
/// renders the capsule.
struct PebbleToolbarButton<Label: View>: View {
    let role: ButtonRole?
    let action: () -> Void
    @ViewBuilder let label: () -> Label

    init(role: ButtonRole? = nil,
         action: @escaping () -> Void,
         @ViewBuilder label: @escaping () -> Label) {
        self.role = role
        self.action = action
        self.label = label
    }

    var body: some View {
        Button(role: role, action: action) { label() }
            .tint(Color.system.secondary)
    }
}

extension PebbleToolbarButton where Label == Text {
    /// Convenience for the common `PebbleToolbarButton("Cancel") { … }` case.
    init(_ titleKey: LocalizedStringKey,
         role: ButtonRole? = nil,
         action: @escaping () -> Void) {
        self.init(role: role, action: action) { Text(titleKey) }
    }
}

#Preview {
    NavigationStack {
        Color.system.background
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    PebbleToolbarButton("Cancel") {}
                }
                ToolbarItem(placement: .confirmationAction) {
                    PebbleToolbarButton("Save") {}
                }
            }
    }
}
