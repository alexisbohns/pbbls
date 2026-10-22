import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/PostmarkNoiseTest.kt
// at d53be9cf, plus SplitMix64's published first outputs (Kotlin's `Random(seed)` is
// not portable, so iOS draws from SplitMix64; the bytes differ from Android's, the
// statistics do not).
@Suite("PostmarkNoise (Android PostmarkNoiseTest.kt)")
struct PostmarkNoiseTests {
    @Test("SplitMix64 draws its reference sequence")
    func generator() {
        var random = SplitMix64(seed: 0)
        #expect(random.next() == 0xE220_A839_7B1D_CDAF)
        #expect(random.next() == 0x6E78_9E6A_A1B9_65F4)
    }

    @Test("the same seed gives the same mask")
    func deterministic() {
        let alphas = PostmarkNoise.alphas(size: 16, seed: 7)
        #expect(alphas == PostmarkNoise.alphas(size: 16, seed: 7))
        // Pinned bytes, so a refactor of the lattice is caught, not only a change between calls.
        #expect(Array(alphas.prefix(8)) == [16, 16, 14, 11, 8, 5, 3, 1])
    }

    @Test("different seeds give different masks")
    func seeded() {
        #expect(PostmarkNoise.alphas(size: 64, seed: 7) != PostmarkNoise.alphas(size: 64, seed: 8))
    }

    @Test("the mask is restrained: rare pits, a drift under 17 %")
    func restrained() throws {
        let alphas = PostmarkNoise.alphas(size: 128, seed: 3)
        #expect(alphas.count == 128 * 128)
        let pits = alphas.filter { $0 >= PostmarkNoise.pitAlpha }.count
        let drift = alphas.filter { $0 < PostmarkNoise.pitAlpha }
        let pitShare = Double(pits) / Double(alphas.count)
        #expect((0.01...0.06).contains(pitShare), "pits \(pitShare)")
        let maxDrift = try #require(drift.max())
        #expect(Int(maxDrift) <= Int(255 * (1 - PostmarkNoise.minDensity)) + 1, "drift max \(maxDrift)")
        let mean = Double(drift.map(Int.init).reduce(0, +)) / Double(drift.count) / 255
        #expect(mean <= 0.17, "mean drift \(mean)")
    }
}
