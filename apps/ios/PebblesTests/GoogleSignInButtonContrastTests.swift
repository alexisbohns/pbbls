import SwiftUI
import Testing
import UIKit
@testable import Pebbles

/// The Google capsule is a pinned light surface, so its label must not follow the
/// appearance. `UIColor(_:)` preserves an asset colour's dynamic behaviour, so
/// `resolvedColor(with:)` exercises the dark appearance the bug lived in.
@Suite("GoogleSignInButton contrast")
struct GoogleSignInButtonContrastTests {

    /// WCAG 2.x relative luminance. Test-local: the app ships no contrast utility.
    private func luminance(_ color: UIColor) -> Double {
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0
        color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        func channel(_ value: CGFloat) -> Double {
            let raw = Double(value)
            return raw <= 0.03928 ? raw / 12.92 : pow((raw + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }

    private func contrastRatio(_ ink: UIColor, _ ground: UIColor) -> Double {
        let inkLuminance = luminance(ink)
        let groundLuminance = luminance(ground)
        let lighter = max(inkLuminance, groundLuminance)
        let darker = min(inkLuminance, groundLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test("label meets AA on the capsule in both appearances")
    func labelIsLegible() {
        for style in [UIUserInterfaceStyle.light, .dark] {
            let traits = UITraitCollection(userInterfaceStyle: style)
            let ink = UIColor(GoogleSignInButton.labelColor).resolvedColor(with: traits)
            let ground = UIColor(GoogleSignInButton.surface).resolvedColor(with: traits)
            let ratio = contrastRatio(ink, ground)
            #expect(ratio >= 4.5, "\(style) appearance: \(ratio):1 fails WCAG AA (4.5:1)")
        }
    }

    @Test("onLight resolves identically in both appearances")
    func onLightIsAppearanceIndependent() {
        let token = UIColor(Color.system.onLight)
        let light = token.resolvedColor(with: UITraitCollection(userInterfaceStyle: .light))
        let dark = token.resolvedColor(with: UITraitCollection(userInterfaceStyle: .dark))
        #expect(light == dark)
    }
}
