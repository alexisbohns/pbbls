package app.pbbls.android.features.path.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/**
 * The Android half of the cross-surface wire contract (D3). Encodes the payloads
 * built by [PebbleCreatePayload.from] / [PebbleUpdatePayload.from] through the same
 * `explicitNulls = true` Json the write service uses, then inspects the tree: the
 * exact snake_case key set, `snaps` present only on update, explicit-null encoding
 * of a cleared `description` / `glyph_id`, `happened_at` as an ISO-8601 **string**
 * (a `timestamptz` cast rejects epoch numbers), and the trimmed scalar mappings.
 */
class PebblePayloadTest {
    private val json = Json { explicitNulls = true }

    private val fixedTime = OffsetDateTime.parse("2026-07-08T14:23:45Z")

    /**
     * The canonical valid draft from the blueprint: padded name + whitespace-only
     * description (both trimmed by `from`), a highlight-medium valence (intensity
     * 2, positiveness 1), two souls, one collection, and no glyph.
     */
    private fun validDraft(): PebbleDraft =
        PebbleDraft(
            happenedAt = fixedTime,
            name = "  Morning walk  ",
            description = "  ",
            emotionId = "e1",
            domainId = "d1",
            valence = Valence.HIGHLIGHT_MEDIUM,
            soulIds = listOf("s1", "s2"),
            collectionId = "c1",
            glyphId = null,
            visibility = Visibility.PRIVATE,
        )

    private fun encode(payload: PebbleCreatePayload): JsonObject = json.parseToJsonElement(json.encodeToString(payload)).jsonObject

    private fun encode(payload: PebbleUpdatePayload): JsonObject = json.parseToJsonElement(json.encodeToString(payload)).jsonObject

    @Test
    fun `create payload emits exactly the eleven keys and no snaps`() {
        val obj = encode(PebbleCreatePayload.from(validDraft()))

        assertEquals(
            setOf(
                "name",
                "description",
                "happened_at",
                "intensity",
                "positiveness",
                "visibility",
                "emotion_id",
                "domain_ids",
                "soul_ids",
                "collection_ids",
                "glyph_id",
            ),
            obj.keys,
        )
        assertFalse("snaps" in obj.keys)
    }

    @Test
    fun `update payload adds snaps to the same twelve-key set`() {
        val snaps = listOf(PebbleSnapPayload(id = "snap-1", storagePath = "user/snap", sortOrder = 0))
        val obj = encode(PebbleUpdatePayload.from(validDraft(), snaps))

        assertEquals(
            setOf(
                "name",
                "description",
                "happened_at",
                "intensity",
                "positiveness",
                "visibility",
                "emotion_id",
                "domain_ids",
                "soul_ids",
                "collection_ids",
                "glyph_id",
                "snaps",
            ),
            obj.keys,
        )

        val snapsArray = obj.getValue("snaps").jsonArray
        val snap = snapsArray.single().jsonObject
        assertEquals(setOf("id", "storage_path", "sort_order"), snap.keys)
        assertEquals("snap-1", snap.getValue("id").jsonPrimitive.content)
        assertEquals("user/snap", snap.getValue("storage_path").jsonPrimitive.content)
        assertFalse(snap.getValue("sort_order").jsonPrimitive.isString)
    }

    @Test
    fun `a cleared description and glyph_id encode as literal JSON null`() {
        // Whitespace-only description trims to null; glyphId is null.
        val obj = encode(PebbleCreatePayload.from(validDraft()))
        assertEquals(JsonNull, obj["description"])
        assertEquals(JsonNull, obj["glyph_id"])
    }

    @Test
    fun `happened_at encodes as an ISO-8601 string and round-trips`() {
        val happenedAt = encode(PebbleCreatePayload.from(validDraft())).getValue("happened_at").jsonPrimitive
        assertTrue(happenedAt.isString)
        // Compare OffsetDateTime, not strings — toString() trims :00 seconds.
        assertEquals(fixedTime, OffsetDateTime.parse(happenedAt.content))
    }

    @Test
    fun `scalar and array mappings match the trimmed draft`() {
        val obj = encode(PebbleCreatePayload.from(validDraft()))

        assertEquals("Morning walk", obj.getValue("name").jsonPrimitive.content)
        assertEquals("private", obj.getValue("visibility").jsonPrimitive.content)
        assertEquals("2", obj.getValue("intensity").jsonPrimitive.content)
        assertEquals("1", obj.getValue("positiveness").jsonPrimitive.content)
        assertEquals("e1", obj.getValue("emotion_id").jsonPrimitive.content)
        // Numbers stay numbers, never stringified.
        assertFalse(obj.getValue("intensity").jsonPrimitive.isString)
        assertFalse(obj.getValue("positiveness").jsonPrimitive.isString)

        val domainIds = obj.getValue("domain_ids").jsonArray
        assertEquals(1, domainIds.size)
        assertEquals("d1", domainIds.single().jsonPrimitive.content)
        assertEquals(
            listOf("s1", "s2"),
            obj.getValue("soul_ids").jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("c1"),
            obj.getValue("collection_ids").jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `an unset collection encodes as a present empty array`() {
        val obj = encode(PebbleCreatePayload.from(validDraft().copy(collectionId = null)))
        assertEquals(0, obj.getValue("collection_ids").jsonArray.size)
    }

    // ---- create snaps (M42 D5): key absent without a photo, present with one ----

    @Test
    fun `create without a snap omits the snaps key entirely`() {
        val obj = encode(PebbleCreatePayload.from(validDraft()))
        assertFalse("snaps" in obj)
    }

    @Test
    fun `create with an uploaded snap encodes the snaps array`() {
        val snap = PebbleSnapPayload(id = "snap1", storagePath = "uid/snap1", sortOrder = 0)
        val obj = encode(PebbleCreatePayload.from(validDraft(), snaps = listOf(snap)))
        val snaps = obj.getValue("snaps").jsonArray
        assertEquals(1, snaps.size)
        val entry = snaps.single().jsonObject
        assertEquals("snap1", entry.getValue("id").jsonPrimitive.content)
        assertEquals("uid/snap1", entry.getValue("storage_path").jsonPrimitive.content)
        assertEquals("0", entry.getValue("sort_order").jsonPrimitive.content)
    }
}
