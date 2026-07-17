import Testing
import SwiftUI
@testable import Pebbles

@Suite("Color(hex:)")
struct ColorHexTests {
    @Test("parses 6-digit hex with #")
    func parsesSixDigitWithHash() {
        let color = Color(hex: "#7B5E99")
        #expect(color != nil)
    }

    @Test("parses 6-digit hex without #")
    func parsesSixDigitNoHash() {
        let color = Color(hex: "7B5E99")
        #expect(color != nil)
    }

    @Test("parses 8-digit hex with alpha")
    func parsesEightDigitWithAlpha() {
        let color = Color(hex: "#7B5E991A")
        #expect(color != nil)
    }

    @Test("parses 8-digit fully opaque")
    func parsesEightDigitOpaque() {
        let color = Color(hex: "#7B5E99FF")
        #expect(color != nil)
    }

    @Test("rejects 5-digit input")
    func rejectsFiveDigit() {
        #expect(Color(hex: "#7B5E9") == nil)
    }

    @Test("rejects 7-digit input")
    func rejectsSevenDigit() {
        #expect(Color(hex: "#7B5E991") == nil)
    }

    @Test("rejects non-hex input")
    func rejectsNonHex() {
        #expect(Color(hex: "#ZZZZZZ") == nil)
    }

    @Test("trims surrounding whitespace")
    func trimsWhitespace() {
        let color = Color(hex: "  #7B5E99  ")
        #expect(color != nil)
    }
}

@Suite("EmotionPalette")
struct EmotionPaletteTests {
    private static func makePalette() -> EmotionPalette? {
        EmotionPalette(
            primaryHex: "#7B5E99FF",
            secondaryHex: "#AE91CCFF",
            lightHex: "#F2EFF5FF",
            surfaceHex: "#7B5E991A",
            shadedHex: "#AE91CCFF",
            darkHex: "#7B5E99FF"
        )
    }

    @Test("init succeeds with well-formed 8-digit hex")
    func initSucceeds() {
        #expect(Self.makePalette() != nil)
    }

    @Test("init returns nil when any hex is malformed")
    func initFailsOnBadHex() {
        let palette = EmotionPalette(
            primaryHex: "not-hex",
            secondaryHex: "#AE91CCFF",
            lightHex: "#F2EFF5FF",
            surfaceHex: "#7B5E991A",
            shadedHex: "#AE91CCFF",
            darkHex: "#7B5E99FF"
        )
        #expect(palette == nil)
    }

    @Test("strokeHex returns 6-digit primary in light mode")
    func strokeHexLight() {
        let palette = Self.makePalette()
        #expect(palette?.strokeHex(for: .light) == "#7B5E99")
    }

    @Test("strokeHex returns 6-digit secondary in dark mode")
    func strokeHexDark() {
        let palette = Self.makePalette()
        #expect(palette?.strokeHex(for: .dark) == "#AE91CC")
    }

    @Test("primaryHex and secondaryHex are preserved verbatim (8-digit)")
    func hexPreserved() {
        let palette = Self.makePalette()
        #expect(palette?.primaryHex == "#7B5E99FF")
        #expect(palette?.secondaryHex == "#AE91CCFF")
    }

    @Test("trims surrounding whitespace from palette hex before storing")
    func trimsWhitespaceFromHex() {
        let palette = EmotionPalette(
            primaryHex:   "#7B5E99FF  ",
            secondaryHex: "  #AE91CCFF",
            lightHex:     "#F2EFF5FF\n",
            surfaceHex:   "#7B5E991A ",
            shadedHex:    "#AE91CCFF",
            darkHex:      "#7B5E99FF"
        )
        #expect(palette?.primaryHex == "#7B5E99FF")
        #expect(palette?.surfaceHex == "#7B5E991A")
        #expect(palette?.strokeHex(for: .light) == "#7B5E99")
    }
}
