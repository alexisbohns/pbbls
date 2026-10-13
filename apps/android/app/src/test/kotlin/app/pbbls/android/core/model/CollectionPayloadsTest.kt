package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Collection write payloads — mirrors iOS `CollectionInsertPayloadEncodingTests`
 * + `CollectionUpdatePayloadEncodingTests`. The load-bearing assertions: the
 * keysets are exact, and a null mode or glyph is an explicit JSON null (absent
 * would silently keep the old value on update — PostgREST only touches present
 * keys).
 */
class CollectionPayloadsTest {
    private val userId = "11111111-1111-1111-1111-111111111111"

    @Test
    fun `insert encodes the exact keyset with snake_case user_id`() {
        val payload = collectionInsertPayload(userId = userId, name = "Summer", mode = CollectionMode.PACK, glyphId = null)
        assertEquals(setOf("user_id", "name", "mode", "glyph_id"), payload.keys)
        assertEquals(userId, payload.getValue("user_id").jsonPrimitive.content)
        assertEquals("Summer", payload.getValue("name").jsonPrimitive.content)
    }

    @Test
    fun `insert encodes a null mode as JSON null, not absent`() {
        val payload = collectionInsertPayload(userId = userId, name = "Modeless", mode = null, glyphId = null)
        assertEquals(setOf("user_id", "name", "mode", "glyph_id"), payload.keys)
        assertEquals(JsonNull, payload.getValue("mode"))
    }

    @Test
    fun `update encodes the exact keyset`() {
        val payload = collectionUpdatePayload(name = "Summer", mode = CollectionMode.PACK, glyphId = null)
        assertEquals(setOf("name", "mode", "glyph_id"), payload.keys)
        assertEquals("Summer", payload.getValue("name").jsonPrimitive.content)
        assertEquals("pack", payload.getValue("mode").jsonPrimitive.content)
    }

    @Test
    fun `update encodes a null mode as JSON null, not absent`() {
        val payload = collectionUpdatePayload(name = "Modeless", mode = null, glyphId = null)
        assertEquals(setOf("name", "mode", "glyph_id"), payload.keys)
        assertEquals(JsonNull, payload.getValue("mode"))
    }

    @Test
    fun `each mode round-trips its wire name`() {
        val expected = mapOf(CollectionMode.STACK to "stack", CollectionMode.PACK to "pack", CollectionMode.TRACK to "track")
        for ((mode, wire) in expected) {
            val payload = collectionUpdatePayload(name = "x", mode = mode, glyphId = null)
            assertEquals(wire, payload.getValue("mode").jsonPrimitive.content)
        }
    }

    @Test
    fun `insert carries the glyph, or an explicit null`() {
        assertEquals(
            Json.parseToJsonElement("""{"user_id":"u-1","name":"Sea days","mode":null,"glyph_id":"g-1"}"""),
            collectionInsertPayload(userId = "u-1", name = "Sea days", mode = null, glyphId = "g-1"),
        )
        assertEquals(
            Json.parseToJsonElement("""{"user_id":"u-1","name":"Sea days","mode":"stack","glyph_id":null}"""),
            collectionInsertPayload(userId = "u-1", name = "Sea days", mode = CollectionMode.STACK, glyphId = null),
        )
    }

    @Test
    fun `update clears the glyph with an explicit null`() {
        assertEquals(
            Json.parseToJsonElement("""{"name":"Sea days","mode":null,"glyph_id":null}"""),
            collectionUpdatePayload(name = "Sea days", mode = null, glyphId = null),
        )
    }

    @Test
    fun `update carries a picked glyph`() {
        assertEquals(
            Json.parseToJsonElement("""{"name":"Sea days","mode":"pack","glyph_id":"g-1"}"""),
            collectionUpdatePayload(name = "Sea days", mode = CollectionMode.PACK, glyphId = "g-1"),
        )
    }
}
