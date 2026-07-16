package app.pbbls.android.features.profile.models

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Collection write payloads — mirrors iOS `CollectionInsertPayloadEncodingTests`
 * + `CollectionUpdatePayloadEncodingTests`. The load-bearing assertions: the
 * keysets are exact, and a null mode is an explicit JSON null (absent would
 * silently keep the old mode on update — PostgREST only touches present keys).
 */
class CollectionPayloadsTest {
    private val userId = "11111111-1111-1111-1111-111111111111"

    @Test
    fun `insert encodes the exact keyset with snake_case user_id`() {
        val payload = collectionInsertPayload(userId = userId, name = "Summer", mode = CollectionMode.PACK)
        assertEquals(setOf("user_id", "name", "mode"), payload.keys)
        assertEquals(userId, payload.getValue("user_id").jsonPrimitive.content)
        assertEquals("Summer", payload.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `insert encodes a null mode as JSON null, not absent`() {
        val payload = collectionInsertPayload(userId = userId, name = "Modeless", mode = null)
        assertEquals(setOf("user_id", "name", "mode"), payload.keys)
        assertEquals(JsonNull, payload.getValue("mode"))
    }

    @Test
    fun `update encodes the exact keyset`() {
        val payload = collectionUpdatePayload(name = "Summer", mode = CollectionMode.PACK)
        assertEquals(setOf("name", "mode"), payload.keys)
        assertEquals("Summer", payload.getValue("name").jsonPrimitive.content)
        assertEquals("pack", payload.getValue("mode").jsonPrimitive.content)
    }

    @Test
    fun `update encodes a null mode as JSON null, not absent`() {
        val payload = collectionUpdatePayload(name = "Modeless", mode = null)
        assertEquals(setOf("name", "mode"), payload.keys)
        assertEquals(JsonNull, payload.getValue("mode"))
    }

    @Test
    fun `each mode round-trips its wire name`() {
        val expected = mapOf(CollectionMode.STACK to "stack", CollectionMode.PACK to "pack", CollectionMode.TRACK to "track")
        for ((mode, wire) in expected) {
            val payload = collectionUpdatePayload(name = "x", mode = mode)
            assertEquals(wire, payload.getValue("mode").jsonPrimitive.content)
        }
    }
}
