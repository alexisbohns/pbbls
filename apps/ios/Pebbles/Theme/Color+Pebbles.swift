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

    /// Fill for list/form rows across the app. White in light mode,
    /// `SurfaceAlt` in dark — so rows read as a layered surface on top of
    /// `pebblesBackground` rather than the iOS default neutral grey.
    static let pebblesListRow = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(named: "SurfaceAlt") ?? .systemGray5
            : .white
    })
}
