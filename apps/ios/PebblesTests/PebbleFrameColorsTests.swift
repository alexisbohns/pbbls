import Testing
@testable import Pebbles

@Suite
struct PebbleFrameColorsTests {

    // 8-digit #RRGGBBAA — the shape the DB actually stores. primary,
    // secondary, light are opaque (FF); surface is primary RGB at ~10%
    // alpha (0x1A), matching the emotion_categories seeding convention.
    private let palette = EmotionPalette(
        primaryHex:   "#C07A7AFF",
        secondaryHex: "#9B5C5CFF",
        lightHex:     "#E8B8B8FF",
        surfaceHex:   "#C07A7A1A"
    )!

    @Test func intensity3UsesLightStrokeAndOpaquePrimaryFill() {
        let colors = palette.pebbleFrameColors(forIntensity: 3)
        #expect(colors.strokeHex == "#E8B8B8")
        #expect(colors.fillHex   == "#C07A7A")
        #expect(abs(colors.fillOpacity - 1.0) < 0.001)
    }

    @Test func intensity1And2UseSecondaryStrokeAndFaintSurfaceFill() {
        for intensity in [1, 2] {
            let colors = palette.pebbleFrameColors(forIntensity: intensity)
            #expect(colors.strokeHex == "#9B5C5C", "intensity \(intensity)")
            #expect(colors.fillHex   == "#C07A7A", "intensity \(intensity)")
            // surface alpha 0x1A = 26 → 26/255 ≈ 0.102
            #expect(abs(colors.fillOpacity - 26.0 / 255.0) < 0.001, "intensity \(intensity)")
        }
    }

    @Test func everyHexIsSanitizedToSixDigitForSvgInjection() {
        // SVGView misparses 8-digit hex; PebbleFrameColors must never
        // hand a 9-char string to an SVG-injection consumer.
        for intensity in [1, 2, 3] {
            let colors = palette.pebbleFrameColors(forIntensity: intensity)
            #expect(colors.strokeHex.count == 7, "intensity \(intensity) strokeHex")
            #expect(colors.fillHex.count == 7, "intensity \(intensity) fillHex")
        }
    }

    @Test func unexpectedIntensityFallsBackToSmallMediumRule() {
        // DB CHECK guarantees 1–3; belt-and-braces for decode drift.
        let colors = palette.pebbleFrameColors(forIntensity: 99)
        #expect(colors.strokeHex == "#9B5C5C")
        #expect(colors.fillHex   == "#C07A7A")
    }

    @Test func sixDigitPaletteHexPassesThroughAtFullOpacity() {
        // Defensive: a palette already stored as 6-digit hex must not
        // be mangled, and its fill must default to fully opaque.
        let sixDigit = EmotionPalette(
            primaryHex:   "#112233",
            secondaryHex: "#445566",
            lightHex:     "#778899",
            surfaceHex:   "#AABBCC"
        )!
        let colors = sixDigit.pebbleFrameColors(forIntensity: 2)
        #expect(colors.fillHex   == "#AABBCC")
        #expect(colors.strokeHex == "#445566")
        #expect(abs(colors.fillOpacity - 1.0) < 0.001)
    }
}
