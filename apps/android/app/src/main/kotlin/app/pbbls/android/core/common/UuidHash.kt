package app.pbbls.android.core.common

/**
 * FNV-1a over the UUID's UTF-16 code units, as the web's `hashUUID`
 * (`apps/web/lib/engine/seed.ts`): the same pebble seeds the same stone on
 * every surface.
 */
object UuidHash {
    private const val OFFSET = 0x811c9dc5L
    private const val PRIME = 0x01000193L

    fun fnv1a(uuid: String): Long {
        var hash = OFFSET
        for (ch in uuid) {
            hash = hash xor ch.code.toLong()
            hash = (hash * PRIME) and 0xffffffffL
        }
        return hash
    }
}
