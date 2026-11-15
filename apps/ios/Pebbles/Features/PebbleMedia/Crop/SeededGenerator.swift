import CoreGraphics
import Foundation

/// SplitMix64 seeded by `StableHash.fnv1a`: the hand-drawn parts of the crop
/// editor wobble the same way on every render (references record twice).
struct SeededGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: String) {
        state = UInt64(StableHash.fnv1a(seed)) | 0x9E37_79B9_0000_0000
    }

    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }

    /// A value in −1…1.
    mutating func unit() -> CGFloat {
        CGFloat(Double(next() >> 11) / Double(1 << 53)) * 2 - 1
    }
}
