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
