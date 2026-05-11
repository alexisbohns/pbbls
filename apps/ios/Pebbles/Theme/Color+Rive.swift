import RiveRuntime
import SwiftUI
import UIKit

extension SwiftUI.Color {
    /// Resolves this SwiftUI `Color` against `colorScheme` and applies its
    /// RGBA components to a Rive Data Binding color property.
    ///
    /// `RiveDataBindingViewModel.Instance.ColorProperty.set(red:green:blue:alpha:)`
    /// accepts values in the 0–255 range (CGFloat). We resolve the named
    /// asset-catalog color via `UITraitCollection` so light/dark variants
    /// produce the correct channel values.
    ///
    /// API surface used (RiveDataBindingViewModelInstanceProperty.h:160–173):
    ///   `set(red:green:blue:alpha:)` — sets all four channels in 0-255 range.
    /// Property access (RiveDataBindingViewModelInstance.h:108–109):
    ///   `colorProperty(fromPath:)` — returns the cached ColorProperty.
    ///
    /// - Parameters:
    ///   - property: The Rive color property to update.
    ///   - colorScheme: The active SwiftUI `ColorScheme`; read from
    ///     `@Environment(\.colorScheme)` in the calling view.
    func applyToRiveColorProperty(
        _ property: RiveDataBindingViewModel.Instance.ColorProperty,
        in colorScheme: ColorScheme
    ) {
        let uiStyle: UIUserInterfaceStyle = colorScheme == .dark ? .dark : .light
        let traits = UITraitCollection(userInterfaceStyle: uiStyle)
        // Resolve via UIColor so asset-catalog named colors honour the trait.
        let uiColor = UIColor(self).resolvedColor(with: traits)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        uiColor.getRed(&r, green: &g, blue: &b, alpha: &a)
        property.set(
            red:   r * 255,
            green: g * 255,
            blue:  b * 255,
            alpha: a * 255
        )
    }
}
