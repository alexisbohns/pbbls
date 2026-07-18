import Testing
import SwiftUI
@testable import Pebbles

/// `EmotionPalette.petroglyphColors(forSize:scheme:)` — the #599 per-size,
/// per-scheme colour table, mirroring Android `PetroglyphColorsTest`. Fixture
/// hex matches the shared `EmotionPaletteTests` palette (opaque primary /
/// secondary / light / dark, 10%-alpha surface).
@Suite("PetroglyphColors (#599)")
struct PetroglyphColorsTests {
    private let palette = EmotionPalette(
        primaryHex:   "#7B5E99FF",
        secondaryHex: "#AE91CCFF",
        lightHex:     "#F2EFF5FF",
        surfaceHex:   "#7B5E991A",
        darkHex:      "#2A2138FF",
        shadedHex:    "#4A3A5CFF"
    )!

    @Test("large is light stroke over opaque primary backfill in both schemes")
    func largeBothSchemes() {
        for scheme in [ColorScheme.light, .dark] {
            let colors = palette.petroglyphColors(forSize: .large, scheme: scheme)
            #expect(colors.strokeHex == "#F2EFF5", "scheme \(scheme)")
            #expect(colors.fillHex == "#7B5E99", "scheme \(scheme)")
            #expect(abs(colors.fillOpacity - 1.0) < 0.001, "scheme \(scheme)")
        }
    }

    @Test("small/medium in light mode use primary stroke over solid light backfill")
    func smallMediumLight() {
        for size in [ValenceSizeGroup.small, .medium] {
            let colors = palette.petroglyphColors(forSize: size, scheme: .light)
            #expect(colors.strokeHex == "#7B5E99", "size \(size)")
            #expect(colors.fillHex == "#F2EFF5", "size \(size)")
            #expect(abs(colors.fillOpacity - 1.0) < 0.001, "size \(size)")
        }
    }

    @Test("small/medium in dark mode use secondary stroke over the solid dark backfill")
    func smallMediumDark() {
        for size in [ValenceSizeGroup.small, .medium] {
            let colors = palette.petroglyphColors(forSize: size, scheme: .dark)
            #expect(colors.strokeHex == "#AE91CC", "size \(size)")
            // "palette.dark" == the dedicated opaque dark_color slot.
            #expect(colors.fillHex == "#2A2138", "size \(size)")
            #expect(abs(colors.fillOpacity - 1.0) < 0.001, "size \(size)")
        }
    }

    @Test("every hex is sanitized to 6-digit for SVG injection")
    func everyHexIsSixDigit() {
        for size in [ValenceSizeGroup.small, .medium, .large] {
            for scheme in [ColorScheme.light, .dark] {
                let colors = palette.petroglyphColors(forSize: size, scheme: scheme)
                #expect(colors.strokeHex.count == 7, "size \(size) scheme \(scheme) strokeHex")
                #expect(colors.fillHex.count == 7, "size \(size) scheme \(scheme) fillHex")
            }
        }
    }
}
