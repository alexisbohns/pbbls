import SwiftUI
import Testing
@testable import Pebbles

@Suite("StonePalette.blank")
struct StonePaletteBlankTests {

    private func hex(_ color: Color) -> String {
        let resolved = color.resolve(in: EnvironmentValues())
        func byte(_ c: Float) -> Int { Int((c * 255).rounded()) }
        return String(format: "#%02X%02X%02X", byte(resolved.red), byte(resolved.green), byte(resolved.blue))
    }

    @Test("the web BLANK_PALETTE grey ramp, slot for slot (R4)")
    func ramp() {
        let blank = StonePalette.blank
        #expect(hex(blank.primary) == "#868686")
        #expect(hex(blank.secondary) == "#B9B9B9")
        #expect(hex(blank.light) == "#F3F3F3")
        #expect(hex(blank.shaded) == "#5E5E5E")
        #expect(hex(blank.dark) == "#1B1B1B")
        #expect(hex(blank.surface) == "#868686")
        #expect(abs(blank.surface.resolve(in: EnvironmentValues()).opacity - 0.1) < 0.01)
    }
}
