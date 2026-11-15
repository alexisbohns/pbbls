package app.pbbls.android.core.common

/**
 * SplitMix64 seeded by FNV-1a (iOS `SeededGenerator`): the hand-drawn parts of the crop editor
 * wobble the same way on every render, and the same way as on iOS for the same seed.
 */
class SeededGenerator(
    seed: String,
) {
    private var state: ULong = UuidHash.fnv1a(seed).toULong() or 0x9E37_79B9_0000_0000uL

    fun next(): ULong {
        state += 0x9E37_79B9_7F4A_7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58_476D_1CE4_E5B9uL
        z = (z xor (z shr 27)) * 0x94D0_49BB_1331_11EBuL
        return z xor (z shr 31)
    }

    /** A value in −1…1. */
    fun unit(): Double = (next() shr 11).toDouble() / (1L shl 53).toDouble() * 2 - 1
}
