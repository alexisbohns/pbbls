import RiveRuntime
import SwiftUI
import UIKit

extension SwiftUI.Color {
    /// Resolves this SwiftUI `Color` against the appearance *and* Increase
    /// Contrast, then applies its RGBA to a Rive Data Binding colour property.
    /// Read `colorScheme` and `colorSchemeContrast` from the environment in the
    /// calling view, and re-apply when either changes, so a bound role follows
    /// High Contrast like every other role (D6, spec 2026-10-20-ios-m3-colour).
    /// Colours baked into the .riv artwork are not touched.
    ///
    /// **SDK gotcha (RiveRuntime 6.19.2):** the header for
    /// `RiveDataBindingViewModelInstance.ColorProperty.set(red:green:blue:alpha:)`
    /// documents the channel range as 0–255, but the implementation in
    /// `RiveDataBindingViewModelInstanceProperty.mm` clamps each channel
    /// with `fmax(0, fmin(value, 1.0))` and then multiplies by 255
    /// internally. Passing 0–255 makes every channel saturate to 1.0 → 255
    /// (white). We must pass the 0–1 normalized values returned by
    /// `UIColor.getRed(_:green:blue:alpha:)` directly.
    func applyToRiveColorProperty(
        _ property: RiveDataBindingViewModel.Instance.ColorProperty,
        in colorScheme: ColorScheme,
        contrast: ColorSchemeContrast
    ) {
        let uiColor = resolvedForRive(in: colorScheme, contrast: contrast)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        uiColor.getRed(&r, green: &g, blue: &b, alpha: &a)
        // The SDK clamps these to 0–1 — pass UIColor's native 0–1 range
        // (NOT 0–255 as the SDK header claims).
        property.set(red: r, green: g, blue: b, alpha: a)
    }

    /// The UIColor Rive receives, resolved through UIKit so asset-catalog
    /// colours honour both traits.
    func resolvedForRive(in colorScheme: ColorScheme, contrast: ColorSchemeContrast) -> UIColor {
        let traits = UITraitCollection { traits in
            traits.userInterfaceStyle = colorScheme == .dark ? .dark : .light
            traits.accessibilityContrast = contrast == .increased ? .high : .normal
        }
        return UIColor(self).resolvedColor(with: traits)
    }
}
