package app.pbbls.android.core.designsystem.stone

/**
 * The exploration harness's seeded generator, bit for bit (a murmur-style
 * hash of the seed's UTF-16 units, then a three-step xorshift-multiply mixer),
 * ported from iOS `StoneRandom` (`ShortcutStones.swift`) so a seed draws the
 * same stone on every surface. Seeds are part of the cross-surface contract
 * (spec 2026-10-31 Android Home and You, D5).
 *
 * Never seed from `String.hashCode()`: the shapes must not depend on the
 * platform's hash.
 */
class StoneRandom(
    seed: String,
) {
    private var h: UInt

    init {
        var hash = 1_779_033_703u xor seed.length.toUInt()
        for (unit in seed) {
            hash = (hash xor unit.code.toUInt()) * 3_432_918_353u
            hash = hash.rotateLeft(13)
        }
        h = hash
    }

    /** The next draw in [0, 1). */
    fun next(): Double {
        h = (h xor (h shr 16)) * 2_246_822_507u
        h = (h xor (h shr 13)) * 3_266_489_909u
        h = h xor (h shr 16)
        return h.toDouble() / 4_294_967_296.0
    }
}
