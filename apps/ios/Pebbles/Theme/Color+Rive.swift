import RiveRuntime
import SwiftUI
import UIKit

extension SwiftUI.Color {
    /// Resolves this SwiftUI `Color` against `colorScheme` and applies its
    /// RGBA components to a Rive Data Binding color property.
    ///
    /// **SDK gotcha (RiveRuntime 6.19.2):** the header for
    /// `RiveDataBindingViewModelInstance.ColorProperty.set(red:green:blue:alpha:)`
    /// documents the channel range as 0–255, but the implementation in
    /// `RiveDataBindingViewModelInstanceProperty.mm` clamps each channel
    /// with `fmax(0, fmin(value, 1.0))` and then multiplies by 255
    /// internally. Passing 0–255 makes every channel saturate to 1.0 → 255
    /// (white). We must pass the 0–1 normalized values returned by
    /// `UIColor.getRed(_:green:blue:alpha:)` directly.
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
        // The SDK clamps these to 0–1 — pass UIColor's native 0–1 range
        // (NOT 0–255 as the SDK header claims).
        property.set(red: r, green: g, blue: b, alpha: a)
    }
}
