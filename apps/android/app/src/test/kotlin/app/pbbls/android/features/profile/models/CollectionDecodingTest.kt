package app.pbbls.android.features.profile.models

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [CollectionRow] decoding — mirrors iOS `CollectionDecodingTests.swift`: the
 * `pebble_count:collection_pebbles(count)` aggregate unwraps `[{count: N}]`
 * to an Int, absent aggregates fall back to 0 (single-row detail fetches),
 * and the nullable mode decodes each constraint value.
 */
class CollectionDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the aggregate count and mode`() {
        val collection =
            json
                .decodeFromString<CollectionRow>(
                    """
                    { "id": "c1", "name": "Reading list", "mode": "pack",
                      "pebble_count": [{ "count": 7 }] }
                    """.trimIndent(),
                ).toCollection()
        assertEquals("c1", collection.id)
        assertEquals("Reading list", collection.name)
        assertEquals(CollectionMode.PACK, collection.mode)
        assertEquals(7, collection.pebbleCount)
    }

    @Test
    fun `an absent aggregate decodes to zero`() {
        val collection =
            json
                .decodeFromString<CollectionRow>("""{ "id": "c1", "name": "Trips" }""")
                .toCollection()
        assertEquals(0, collection.pebbleCount)
        assertNull(collection.mode)
    }

    @Test
    fun `a null mode stays null and every constraint value decodes`() {
        val modes =
            listOf("stack" to CollectionMode.STACK, "pack" to CollectionMode.PACK, "track" to CollectionMode.TRACK)
        modes.forEach { (wire, expected) ->
            val row =
                json.decodeFromString<CollectionRow>(
                    """{ "id": "c", "name": "n", "mode": "$wire", "pebble_count": [] }""",
                )
            assertEquals(expected, row.toCollection().mode)
        }
        val nullMode =
            json.decodeFromString<CollectionRow>(
                """{ "id": "c", "name": "n", "mode": null, "pebble_count": [] }""",
            )
        assertNull(nullMode.toCollection().mode)
    }
}
