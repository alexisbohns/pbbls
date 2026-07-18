import Testing
import SwiftUI
@testable import Pebbles

/// `EmotionPalette.pebblePageColors(for:)` — the #605 whole-page emotion-palette
/// colour table, mirroring Android `PebblePageColorsTest`. Fixture hex matches
/// the shared `EmotionPaletteTests` palette (opaque primary / secondary / light /
/// dark / shaded, 10%-alpha surface). Each element must resolve to the exact
/// palette role per scheme; roles are compared against the palette's own stored
/// `Color`s so the mapping is verified without reconstructing colors from hex.
@Suite("PebblePageColors (#605)")
struct PebblePageColorsTests {
    private let palette = EmotionPalette(
        primaryHex:   "#7B5E99FF",
        secondaryHex: "#AE91CCFF",
        lightHex:     "#F2EFF5FF",
        surfaceHex:   "#7B5E991A",
        darkHex:      "#2A2138FF",
        shadedHex:    "#4A3A5CFF"
    )!

    @Test("light mode maps each element to its role")
    func lightMode() {
        let colors = palette.pebblePageColors(for: .light)
        #expect(colors.background == palette.light)
        #expect(colors.title == palette.shaded)
        #expect(colors.date == palette.secondary)
        #expect(colors.tileBackground == palette.surface)
        #expect(colors.tileIcon == palette.secondary)
        #expect(colors.tileLabel == palette.shaded)
        #expect(colors.description == palette.shaded)
        #expect(colors.soulGlyph == palette.secondary)
        #expect(colors.soulName == palette.primary)
    }

    @Test("dark mode maps each element to its role")
    func darkMode() {
        let colors = palette.pebblePageColors(for: .dark)
        #expect(colors.background == palette.dark)
        #expect(colors.title == palette.light)
        #expect(colors.date == palette.primary)
        #expect(colors.tileBackground == palette.surface)
        #expect(colors.tileIcon == palette.primary)
        #expect(colors.tileLabel == palette.light)
        #expect(colors.description == palette.light)
        #expect(colors.soulGlyph == palette.primary)
        #expect(colors.soulName == palette.light)
    }

    @Test("tile background is the surface wash in both schemes")
    func tileBackgroundIsSurface() {
        #expect(palette.pebblePageColors(for: .light).tileBackground == palette.surface)
        #expect(palette.pebblePageColors(for: .dark).tileBackground == palette.surface)
    }
}
