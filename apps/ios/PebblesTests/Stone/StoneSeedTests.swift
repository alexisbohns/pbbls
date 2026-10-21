import Testing
@testable import Pebbles

/// #1130 D1: the Spine passes `StableHash.seed(pebble.id)`; the shader reads the
/// seed through a Float, so it is folded to 24 bits, where a Float is exact.
@Suite("Stone seed")
struct StoneSeedTests {
    @Test("a pebble's seed folds to 24 bits and survives the Float the shader reads")
    func fold() {
        let folded = StoneView.shaderSeed(3_118_864_542)
        #expect(folded == 15_079_582)
        #expect(Int(Float(folded)) == folded)
        #expect(StoneView.shaderSeed(.max) < 1 << 24)
    }
}
