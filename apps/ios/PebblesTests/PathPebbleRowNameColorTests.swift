import SwiftUI
import Testing
@testable import Pebbles

@Suite("PathPebbleRow name color")
struct PathPebbleRowNameColorTests {

    private let palette = EmotionPalette(
        primaryHex:   "#C07A7AFF",
        secondaryHex: "#9B5C5CFF",
        lightHex:     "#E8B8B8FF",
        surfaceHex:   "#C07A7A1A"
    )!

    // Regression (#510): large pebble name/time were unreadable in light
    // mode because they rendered in `palette.light` (a pale tint) on the
    // white row background. The text sits on the row background, not the
    // pebble fill, so it must contrast the scheme background at every size.

    @Test("light scheme uses primary (dark ink) at every size")
    func lightScheme() {
        #expect(PathPebbleRow.nameColor(palette: palette, isLarge: false, colorScheme: .light) == palette.primary)
        #expect(PathPebbleRow.nameColor(palette: palette, isLarge: true, colorScheme: .light) == palette.primary)
    }

    @Test("dark scheme uses light (pale ink) at every size")
    func darkScheme() {
        #expect(PathPebbleRow.nameColor(palette: palette, isLarge: false, colorScheme: .dark) == palette.light)
        #expect(PathPebbleRow.nameColor(palette: palette, isLarge: true, colorScheme: .dark) == palette.light)
    }

    @Test("large and small resolve to the same color in a given scheme")
    func sizeDoesNotAffectColor() {
        for scheme in [ColorScheme.light, .dark] {
            #expect(
                PathPebbleRow.nameColor(palette: palette, isLarge: true, colorScheme: scheme)
                    == PathPebbleRow.nameColor(palette: palette, isLarge: false, colorScheme: scheme),
                "scheme \(scheme)"
            )
        }
    }
}
