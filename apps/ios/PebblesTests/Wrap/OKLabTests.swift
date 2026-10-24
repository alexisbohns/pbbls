import SwiftUI
import Testing
@testable import Pebbles

/// The gem's colour mixing (#1226), CSS `color-mix(in oklab, …)`. Android:
/// `StoneTonesTest`'s mixOklab vector.
@Suite("OKLab")
struct OKLabTests {
    private let white = Color.Resolved(red: 1, green: 1, blue: 1)
    private let black = Color.Resolved(red: 0, green: 0, blue: 0)

    @Test("the midpoint of white and black is OKLab L one half, darker than the sRGB midpoint")
    func midpoint() {
        // L = .5 is linear .125, which encodes to sRGB ≈ .389 (#636363), as the browser draws it.
        let mid = OKLab.mix(white, 0.5, black)
        for channel in [mid.red, mid.green, mid.blue] {
            #expect(abs(channel - 0.389) < 0.01)
            #expect(channel < Float(0x80) / 255)
        }
    }

    @Test("the endpoints return the colours themselves")
    func endpoints() {
        let red = Color.Resolved(red: 0.8, green: 0.2, blue: 0.1)
        #expect(OKLab.mix(red, 1, black) == red)
        #expect(OKLab.mix(red, 0, black) == black)
    }
}
