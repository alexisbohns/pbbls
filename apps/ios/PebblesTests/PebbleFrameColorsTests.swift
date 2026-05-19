import Testing
@testable import Pebbles

@Suite
struct PebbleFrameColorsTests {

    private let palette = EmotionPalette(
        primaryHex:   "#C07A7A",
        secondaryHex: "#9B5C5C",
        lightHex:     "#E8B8B8",
        surfaceHex:   "#F4DCDC"
    )!

    @Test func intensity3UsesLightStrokeAndPrimaryFill() {
        let colors = palette.pebbleFrameColors(forIntensity: 3)
        #expect(colors.strokeHex == "#E8B8B8")
        #expect(colors.fillHex   == "#C07A7A")
    }

    @Test func intensity2UsesSecondaryStrokeAndSurfaceFill() {
        let colors = palette.pebbleFrameColors(forIntensity: 2)
        #expect(colors.strokeHex == "#9B5C5C")
        #expect(colors.fillHex   == "#F4DCDC")
    }

    @Test func intensity1UsesSecondaryStrokeAndSurfaceFill() {
        let colors = palette.pebbleFrameColors(forIntensity: 1)
        #expect(colors.strokeHex == "#9B5C5C")
        #expect(colors.fillHex   == "#F4DCDC")
    }

    @Test func unexpectedIntensityFallsBackToSmallMediumRule() {
        // Defensive: clamp anything outside 1/2/3 to the small/medium rule.
        // DB CHECK guarantees 1–3; this is belt-and-braces for decode drift.
        let colors = palette.pebbleFrameColors(forIntensity: 99)
        #expect(colors.strokeHex == "#9B5C5C")
        #expect(colors.fillHex   == "#F4DCDC")
    }
}
