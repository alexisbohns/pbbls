import SwiftUI
import Testing
@testable import Pebbles

/// D5: the brand stone keeps the #456 accent ramp verbatim, in both appearances,
/// because the lit-stone shader is tuned to it and a stone never follows the theme.
@Suite("StonePalette.brand")
struct StonePaletteBrandTests {

    private func hex(_ rgb: SIMD3<Float>) -> String {
        String(
            format: "#%02X%02X%02X",
            Int((rgb.x * 255).rounded()),
            Int((rgb.y * 255).rounded()),
            Int((rgb.z * 255).rounded())
        )
    }

    @Test("every slot keeps the old accent value in light and dark", arguments: [
        (StonePalette.Slot.dark, "#341B1B"),
        (StonePalette.Slot.shaded, "#8C4949"),
        (StonePalette.Slot.primary, "#C07A7A"),
        (StonePalette.Slot.secondary, "#EAD3D3"),
        (StonePalette.Slot.light, "#FAF4F4"),
        (StonePalette.Slot.surface, "#C07A7A")
    ])
    func ramp(slot: StonePalette.Slot, expected: String) {
        #expect(hex(StonePalette.brand.rgb(slot, scheme: .light)) == expected)
        #expect(hex(StonePalette.brand.rgb(slot, scheme: .dark)) == expected)
    }

    @Test("surface is the primary at 10% alpha")
    func surfaceAlpha() {
        let resolved = StonePalette.brand.surface.resolve(in: EnvironmentValues())
        #expect(abs(resolved.opacity - 0.1) < 0.005)
    }
}
