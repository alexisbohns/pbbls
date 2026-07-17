import SwiftUI
import Testing
@testable import Pebbles

@Suite("EmotionPalette.petroglyphColors")
struct EmotionPalettePetroglyphColorsTests {

    // Distinct opaque hexes so each role is unambiguous in assertions.
    private func palette() -> EmotionPalette {
        EmotionPalette(
            primaryHex:   "#111111FF",
            secondaryHex: "#222222FF",
            lightHex:     "#333333FF",
            surfaceHex:   "#4444441A",
            shadedHex:    "#555555FF",
            darkHex:      "#666666FF"
        )!
    }

    // MARK: small / medium — theme-dependent

    @Test("small/medium light: stroke=primary, backfill=light")
    func smallMediumLight() {
        let colors = palette().petroglyphColors(forIntensity: 1, scheme: .light)
        #expect(colors.strokeHex == "#111111")
        #expect(colors.fillHex == "#333333")
        #expect(colors.fillOpacity == 1)
    }

    @Test("small/medium dark: stroke=secondary, backfill=dark")
    func smallMediumDark() {
        let colors = palette().petroglyphColors(forIntensity: 2, scheme: .dark)
        #expect(colors.strokeHex == "#222222")
        #expect(colors.fillHex == "#666666")
        #expect(colors.fillOpacity == 1)
    }

    // MARK: large — theme-independent

    @Test("large light: stroke=light, backfill=primary")
    func largeLight() {
        let colors = palette().petroglyphColors(forIntensity: 3, scheme: .light)
        #expect(colors.strokeHex == "#333333")
        #expect(colors.fillHex == "#111111")
        #expect(colors.fillOpacity == 1)
    }

    @Test("large dark: stroke=light, backfill=primary (same as light)")
    func largeDark() {
        let colors = palette().petroglyphColors(forIntensity: 3, scheme: .dark)
        #expect(colors.strokeHex == "#333333")
        #expect(colors.fillHex == "#111111")
        #expect(colors.fillOpacity == 1)
    }
}
