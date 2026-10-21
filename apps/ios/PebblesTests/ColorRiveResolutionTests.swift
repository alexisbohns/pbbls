import SwiftUI
import Testing
import UIKit
@testable import Pebbles

@Suite("Color+Rive")
struct ColorRiveResolutionTests {

    private func hex(_ color: UIColor) -> String {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(format: "#%02X%02X%02X", Int((red * 255).rounded()), Int((green * 255).rounded()), Int((blue * 255).rounded()))
    }

    @Test("the bound colour resolves against appearance and Increase Contrast", arguments: [
        (ColorScheme.light, ColorSchemeContrast.standard, "#8E4955"),
        (ColorScheme.dark, ColorSchemeContrast.standard, "#FFB2BC"),
        (ColorScheme.light, ColorSchemeContrast.increased, "#501824"),
        (ColorScheme.dark, ColorSchemeContrast.increased, "#FFEBED")
    ])
    func resolves(scheme: ColorScheme, contrast: ColorSchemeContrast, expected: String) {
        #expect(hex(Color.pebbles.primary.resolvedForRive(in: scheme, contrast: contrast)) == expected)
    }
}
