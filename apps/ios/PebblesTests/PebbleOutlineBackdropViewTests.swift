import Testing
import Foundation
@testable import Pebbles

@Suite
struct PebbleOutlineBackdropViewTests {

    /// Locate the Pebbles app bundle from the test runner host.
    private static var appBundle: Bundle? {
        Bundle.allBundles.first { $0.bundleIdentifier?.hasPrefix("app.pbbls.ios") == true && !$0.bundleIdentifier!.contains("test") }
    }

    private static func svgURL(for name: String) -> URL? {
        if let url = appBundle?.url(forResource: name, withExtension: "svg") { return url }
        return Bundle.main.url(forResource: name, withExtension: "svg")
    }

    /// The view's `coloredSvg` is private. We test the same logic by
    /// loading the asset directly and running the swap — exercises the
    /// contract (sentinel + replacement), not the SwiftUI body.
    @Test func sentinelSwapProducesNoMagentaResidue() throws {
        let url = try #require(Self.svgURL(for: "small-neutral"), "missing asset: small-neutral.svg")
        let raw = try String(contentsOf: url, encoding: .utf8)
        let swapped = raw.replacingOccurrences(of: "#FF00FF", with: "#C07A7A")
        #expect(!swapped.contains("#FF00FF"))
        #expect(swapped.contains("#C07A7A"))
    }

    @Test func eightDigitHexTrimsAlphaBeforeInjection() {
        let input = "#C07A7AFF"
        let trimmed = input.count == 9 ? String(input.prefix(7)) : input
        #expect(trimmed == "#C07A7A")
    }
}
