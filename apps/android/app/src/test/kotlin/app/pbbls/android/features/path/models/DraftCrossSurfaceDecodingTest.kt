package app.pbbls.android.features.path.models

import app.pbbls.android.services.PebbleDraftRecord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * A draft written by ANOTHER surface must decode here. iOS shipped a
 * fixed-precision ISO-8601 formatter and silently nil'd every web- and
 * Postgres-written `happened_at` (#651); an Android-only round-trip test could
 * not have caught it either, so these use real foreign payloads verbatim.
 *
 * The precisions in play all differ: web `toISOString()` emits milliseconds,
 * Postgres `timestamptz` emits microseconds, iOS emits whole seconds.
 */
class DraftCrossSurfaceDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `a web-written happened_at with milliseconds parses`() {
        // Exactly what `new Date().toISOString()` produces.
        val decoded =
            json.decodeFromString<PebbleDraftPayload>(
                """{"name":"From web","happened_at":"2026-07-30T01:23:45.123Z"}""",
            )
        assertNotNull("a web draft's timestamp must survive", decoded.happenedAt)
        assertEquals(2026, decoded.happenedAt?.year)
    }

    @Test
    fun `a Postgres-style happened_at with microseconds and an offset parses`() {
        val decoded =
            json.decodeFromString<PebbleDraftPayload>(
                """{"name":"x","happened_at":"2026-07-30T01:23:45.123456+00:00"}""",
            )
        assertNotNull(decoded.happenedAt)
    }

    @Test
    fun `an iOS-written happened_at with whole seconds parses`() {
        val decoded =
            json.decodeFromString<PebbleDraftPayload>(
                """{"name":"From iOS","happened_at":"2026-07-30T01:23:45Z"}""",
            )
        assertEquals(
            OffsetDateTime.of(2026, 7, 30, 1, 23, 45, 0, ZoneOffset.UTC).toInstant(),
            decoded.happenedAt?.toInstant(),
        )
    }

    @Test
    fun `a full iOS-shaped payload decodes, explicit nulls and all`() {
        val decoded =
            json.decodeFromString<PebbleDraftPayload>(
                """
                {"name":"From iOS","description":null,"happened_at":"2026-07-30T01:23:45Z",
                 "intensity":3,"positiveness":-1,"visibility":"private",
                 "emotion_id":null,"domain_ids":[],"soul_ids":[],"collection_ids":[],
                 "glyph_id":null,
                 "snaps":[{"id":"snap-1","storage_path":"user-1/snap-1","sort_order":0}]}
                """.trimIndent(),
            )
        assertEquals("From iOS", decoded.name)
        assertEquals(Visibility.PRIVATE, decoded.visibility)
        assertEquals(Valence.LOWLIGHT_LARGE, Valence.orNull(decoded.positiveness, decoded.intensity))
        assertEquals("user-1/snap-1", decoded.existingSnap?.storagePath)
    }

    @Test
    fun `a pebble_drafts row decodes with a microsecond updated_at`() {
        // The shape PostgREST returns for `select id, payload, updated_at`.
        val record =
            json.decodeFromString<PebbleDraftRecord>(
                """
                {"id":"3f2504e0-4f89-11d3-9a0c-0305e82c3301",
                 "payload":{"name":"Quick capture"},
                 "updated_at":"2026-07-30T01:23:45.123456+00:00"}
                """.trimIndent(),
            )
        assertEquals("Quick capture", record.payload.name)
        assertEquals(2026, record.updatedAt.year)
    }

    @Test
    fun `a payload from a newer build with unknown keys still decodes`() {
        val decoded =
            json.decodeFromString<PebbleDraftPayload>(
                """{"name":"Forward compatible","whispers":["not a thing yet"]}""",
            )
        assertEquals("Forward compatible", decoded.name)
    }
}
