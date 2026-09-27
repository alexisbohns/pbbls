package app.pbbls.android.core.data

import app.pbbls.android.core.model.PebbleDraftPayload
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The owner stamp on the composer crash snapshot (GDP-07) — the pure half of
 * [ComposerSnapshotStore], which needs a `Context` and so is not JVM-testable.
 *
 * The snapshot is someone's unpublished content on a device that can change
 * hands: it must only ever be handed back to the user who wrote it, and a
 * snapshot from a build that did not stamp an owner must not be trusted.
 */
class OwnedComposerSnapshotTest {
    /** The store's own configuration. */
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `the owner gets their snapshot back`() {
        val stored = OwnedComposerSnapshot("u1", PebbleDraftPayload(name = "mine"))

        assertEquals("mine", stored.restorableFor("u1")?.name)
    }

    @Test
    fun `anyone else gets nothing`() {
        val stored = OwnedComposerSnapshot("u1", PebbleDraftPayload(name = "not yours"))

        assertNull(stored.restorableFor("u2"))
    }

    @Test
    fun `an empty payload is nothing to restore, even for its owner`() {
        assertNull(OwnedComposerSnapshot("u1", PebbleDraftPayload()).restorableFor("u1"))
    }

    @Test
    fun `the owner survives the round trip through storage`() {
        val encoded = json.encodeToString(OwnedComposerSnapshot("u1", PebbleDraftPayload(name = "kept")))
        val decoded = json.decodeFromString<OwnedComposerSnapshot>(encoded)

        assertEquals("u1", decoded.ownerId)
        assertEquals("kept", decoded.restorableFor("u1")?.name)
    }

    /**
     * What a pre-GDP-07 build wrote under the same key: a bare payload. It has
     * no owner, so decoding it as an owned snapshot must fail — the store then
     * discards it rather than guessing whose it is.
     */
    @Test
    fun `a bare payload from an older build does not decode as owned`() {
        val legacy = json.encodeToString(PebbleDraftPayload(name = "whose?"))

        assertThrows(SerializationException::class.java) {
            json.decodeFromString<OwnedComposerSnapshot>(legacy)
        }
    }
}
