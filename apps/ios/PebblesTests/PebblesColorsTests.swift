import SwiftUI
import Testing
import UIKit
@testable import Pebbles

/// The asset catalog's appearance keys are only proven by resolving real trait
/// collections: a misspelt key compiles silently and falls back to light
/// (spec 2026-10-20-ios-m3-colour §13). Expected hexes are ColorSchemes.kt at
/// the commit that generated the colorsets.
@Suite("PebblesColors")
struct PebblesColorsTests {

    enum Look: String, CaseIterable, Sendable {
        case light, dark, lightHigh, darkHigh

        var traits: UITraitCollection {
            UITraitCollection { traits in
                traits.userInterfaceStyle = (self == .dark || self == .darkHigh) ? .dark : .light
                traits.accessibilityContrast = (self == .lightHigh || self == .darkHigh) ? .high : .normal
            }
        }
    }

    private func hex(_ color: UIColor) -> String {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return String(
            format: "#%02X%02X%02X",
            Int((red * 255).rounded()), Int((green * 255).rounded()), Int((blue * 255).rounded())
        )
    }

    @Test("every role has a colorset in the app bundle")
    func everyRoleResolves() {
        for role in PebblesColorRole.allCases {
            #expect(UIColor(named: role.assetName, in: .main, compatibleWith: nil) != nil, "\(role.assetName)")
        }
    }

    @Test("roles resolve to the Kotlin hexes under the four trait collections", arguments: [
        (PebblesColorRole.surface, ["#FEF9EF", "#131315", "#FEF9EF", "#131315"]),
        (PebblesColorRole.error, ["#9B4500", "#FFB68E", "#4D1E00", "#FFECE4"]),
        (PebblesColorRole.primary, ["#8E4955", "#FFB2BC", "#501824", "#FFEBED"])
    ])
    func resolvesPerAppearance(role: PebblesColorRole, expected: [String]) {
        let got = Look.allCases.map { hex(UIColor.pebbles(role).resolvedColor(with: $0.traits)) }
        #expect(got == expected, "\(role.rawValue): \(got)")
    }

    @Test("Color.pebbles and UIColor.pebbles name the same asset")
    func swiftUIAndUIKitAgree() {
        for look in Look.allCases {
            let viaSwiftUI = UIColor(Color.pebbles.error).resolvedColor(with: look.traits)
            #expect(hex(viaSwiftUI) == hex(UIColor.pebbles(.error).resolvedColor(with: look.traits)), "\(look)")
        }
    }

    @Test("rule is onSurface at 0.14 Paper and 0.12 Night")
    func ruleAlpha() {
        var alphas: [Double] = []
        for look in Look.allCases {
            var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
            UIColor.pebbles(.rule).resolvedColor(with: look.traits)
                .getRed(&red, green: &green, blue: &blue, alpha: &alpha)
            alphas.append((Double(alpha) * 100).rounded() / 100)
        }
        #expect(alphas == [0.14, 0.12, 0.14, 0.12])
    }

    @Test("googleCapsuleInk ignores appearance and contrast")
    func googleInkIsPinned() {
        let resolved = Set(Look.allCases.map { look in
            hex(UIColor.pebbles(.googleCapsuleInk).resolvedColor(with: look.traits))
        })
        #expect(resolved == ["#4A3639"])
    }
}
