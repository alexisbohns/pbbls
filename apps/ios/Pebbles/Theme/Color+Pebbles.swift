import SwiftUI
import UIKit

extension Color {
    static let pebblesBackground      = Color("Background")
    static let pebblesForeground      = Color("Foreground")
    static let pebblesSurface         = Color("Surface")
    static let pebblesSurfaceAlt      = Color("SurfaceAlt")
    static let pebblesMuted           = Color("Muted")
    static let pebblesMutedForeground = Color("MutedForeground")
    static let pebblesBorder          = Color("Border")
    static let pebblesAccent          = Color("AccentColor")
    static let pebblesAccentSoft      = Color("AccentSoft")

    /// Hex equivalent of `pebblesAccent` (light-mode value of `AccentColor`).
    /// Used as a fallback for SVG-text injection when the palette cache
    /// hasn't loaded yet. A single value covers both schemes — the fallback
    /// path is rare and brief, so dark-mode parity is not worth a second
    /// constant. If `AccentColor` is retuned, update here.
    static let pebblesAccentHex: String = "#C07A7A"

    /// Fill for list/form rows across the app. White in light mode,
    /// `SurfaceAlt` in dark — so rows read as a layered surface on top of
    /// `pebblesBackground` rather than the iOS default neutral grey.
    static let pebblesListRow = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(named: "SurfaceAlt") ?? .systemGray5
            : .white
    })

    /// Pure white in light mode, `pebblesBackground` (near-black) in dark.
    /// Used by PathView per the #388 design.
    static let pebblesPathBackground = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? (UIColor(named: "Background") ?? .black)
            : .white
    })
}
