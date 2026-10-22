import SwiftUI
import Testing
@testable import Pebbles

/// #1146 §Verso: which palette slot inks a postmark, how strong the band's
/// paper is, and the Highlight word's ramp (decision 2), by scheme. Colours in
/// this test are fixtures (`ThemeLiteralsTests` scans `Features/` only).
@Suite("CardColors")
struct CardColorsTests {
    @Test("postmark ink: the emotion's dark on paper, its light at Night")
    func postmarkInk() {
        #expect(CardColors.postmarkInkSlot(scheme: .light) == .dark)
        #expect(CardColors.postmarkInkSlot(scheme: .dark) == .light)
    }

    @Test("the band's paper takes 19 % of the spot on paper, 24 % at Night")
    func bandPaper() {
        #expect(CardColors.bandPaperK(scheme: .light) == 0.19)
        #expect(CardColors.bandPaperK(scheme: .dark) == 0.24)
    }

    @Test("a mix moves the first colour toward the second by k")
    func mix() {
        let environment = EnvironmentValues()
        let a = Color(.sRGB, red: 0, green: 0, blue: 0)
        let b = Color(.sRGB, red: 1, green: 1, blue: 1)
        let half = CardColors.mix(a, toward: b, 0.5, in: environment).resolve(in: environment)
        let resolvedA = a.resolve(in: environment)
        let resolvedB = b.resolve(in: environment)
        #expect(abs(half.linearRed - (resolvedA.linearRed + resolvedB.linearRed) / 2) < 0.001)
        let none = CardColors.mix(a, toward: b, 0, in: environment).resolve(in: environment)
        let all = CardColors.mix(a, toward: b, 1, in: environment).resolve(in: environment)
        #expect(abs(none.linearRed - resolvedA.linearRed) < 0.001)
        #expect(abs(all.linearRed - resolvedB.linearRed) < 0.001)
    }

    @Test("the Highlight ramp has three stops at 0, 0.4 and 1 in both schemes")
    func ramp() {
        for scheme in [ColorScheme.light, .dark] {
            var environment = EnvironmentValues()
            environment.colorScheme = scheme
            let stops = CardColors.highlightRamp(.brand, scheme: scheme, in: environment)
            #expect(stops.map(\.location) == [0, 0.4, 1])
        }
    }
}
