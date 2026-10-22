import Foundation

/// SplitMix64: a small deterministic generator, the same bytes on every device
/// and run (Swift's own generators are seeded per process).
struct SplitMix64 {
    private var state: UInt64

    init(seed: UInt64) {
        state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// A double in [0, 1), from the top 53 bits.
    mutating func nextUnit() -> Double {
        Double(next() >> 11) * 0x1.0p-53
    }
}

/// The postmark ink's texture as an alpha mask (#1146 §Postmark, D4): a smooth
/// drift on an 8 px lattice that thins the ink to at most 1 − `minDensity`,
/// plus rare pits (3 %) at `pitAlpha` and above, where the panel shows through.
/// Deterministic per seed, so each stamp keeps its own pattern. A port of
/// Android's `PostmarkNoise.kt` on SplitMix64.
enum PostmarkNoise {
    static let minDensity = 0.83
    static let pitAlpha: UInt8 = 200
    static let pitRate = 0.03
    static let cell = 8

    static func alphas(size: Int, seed: UInt64) -> [UInt8] {
        var random = SplitMix64(seed: seed)
        let grid = size / cell + 2
        let lattice = (0..<(grid * grid)).map { _ in random.nextUnit() }
        let maxDrift = 255 * (1 - minDensity)
        func at(_ x: Int, _ y: Int) -> Double { lattice[y * grid + x] }
        func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }
        return (0..<(size * size)).map { index in
            if random.nextUnit() < pitRate {
                return pitAlpha + UInt8(random.next() % UInt64(256 - Int(pitAlpha)))
            }
            let x = Double(index % size) / Double(cell)
            let y = Double(index / size) / Double(cell)
            let x0 = Int(x.rounded(.down))
            let y0 = Int(y.rounded(.down))
            let tx = smooth(x - Double(x0))
            let ty = smooth(y - Double(y0))
            let top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * tx
            let bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * tx
            return UInt8((top + (bottom - top) * ty) * maxDrift)
        }
    }
}
