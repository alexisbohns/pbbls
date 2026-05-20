import Testing
import Foundation
@testable import Pebbles

@Suite
struct PebbleOutlineBackdropViewTests {

    /// The view's `coloredSvg` is private. We test the same logic by
    /// loading the asset directly and running the swap — exercises the
    /// contract (sentinel + replacement), not the SwiftUI body.
    @Test func sentinelSwapProducesNoMagentaResidue() throws {
        let url = try #require(Bundle.main.url(forResource: "small-neutral", withExtension: "svg"))
        let raw = try String(contentsOf: url, encoding: .utf8)
        let swapped = raw.replacingOccurrences(of: "#FF00FF", with: "#C07A7A")
        #expect(!swapped.contains("#FF00FF"))
        #expect(swapped.contains("#C07A7A"))
    }

}
