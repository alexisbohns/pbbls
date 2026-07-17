package app.pbbls.android.features.lab.services

import app.pbbls.android.features.lab.models.Log
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.OffsetDateTime

/**
 * Ports the iOS `LossyLogArray` suite 1:1 (M44 design D2), plus the D1
 * platform-policy test: a `project`/`infra` row is dropped whole, exactly
 * like any other bad row.
 */
class LossyLogListTest {
    private val skips = mutableListOf<Pair<Int, Exception>>()

    private fun decode(raw: String): List<Log> = LossyLogList.decode(raw) { index, cause -> skips += index to cause }

    // The iOS fixture: a full row with every optional key present as JSON
    // null and NO released_at key.
    private fun row(
        id: String = VALID_ID,
        titleEn: String = "Shipped it",
        species: String = "feature",
        platform: String = "ios",
        status: String = "shipped",
        releasedAt: String? = null,
    ): JsonObject =
        buildJsonObject {
            put("id", id)
            put("species", species)
            put("platform", platform)
            put("status", status)
            put("title_en", titleEn)
            put("title_fr", JsonNull)
            put("summary_en", "One line.")
            put("summary_fr", JsonNull)
            put("body_md_en", JsonNull)
            put("body_md_fr", JsonNull)
            put("cover_image_path", JsonNull)
            put("external_url", JsonNull)
            put("published", true)
            put("published_at", "2026-04-20T12:00:00Z")
            releasedAt?.let { put("released_at", it) }
            put("created_at", "2026-04-20T12:00:00Z")
            put("reaction_count", 0)
        }

    // The iOS bad-row shape: required keys only, so every optional key is
    // ABSENT (not JSON null) — pins that absence alone never drops a row.
    private fun minimalRow(
        id: String = VALID_ID,
        species: String = "feature",
    ): JsonObject =
        buildJsonObject {
            put("id", id)
            put("species", species)
            put("platform", "ios")
            put("status", "shipped")
            put("title_en", "x")
            put("summary_en", "y")
            put("published", true)
            put("created_at", "2026-04-20T12:00:00Z")
            put("reaction_count", 0)
        }

    private fun feed(vararg rows: JsonObject): String = JsonArray(rows.toList()).toString()

    @Test
    fun decodesAllValidArrayUnchanged() {
        val logs = decode(feed(row(titleEn = "A"), row(id = OTHER_ID, titleEn = "B")))
        assertEquals(2, logs.size)
        assertEquals(listOf("A", "B"), logs.map { it.titleEn })
        assertTrue(skips.isEmpty())
    }

    @Test
    fun emptyArrayDecodesToEmpty() {
        assertTrue(decode("[]").isEmpty())
        assertTrue(skips.isEmpty())
    }

    @Test
    fun singleBadRowSkippedSurroundingRowsKept() {
        val logs = decode(feed(row(titleEn = "A"), row(id = OTHER_ID, status = "retired"), row(titleEn = "B")))
        assertEquals(listOf("A", "B"), logs.map { it.titleEn })
        assertEquals(listOf(1), skips.map { it.first })
    }

    @Test
    fun consecutiveBadRowsAllSkipped() {
        val logs = decode(feed(minimalRow(id = "not-a-uuid"), minimalRow(species = "mystery"), row(titleEn = "last")))
        assertEquals(1, logs.size)
        assertEquals("last", logs.first().titleEn)
        assertEquals(listOf(0, 1), skips.map { it.first })
    }

    @Test
    fun allRowsBadYieldsEmptyNotThrow() {
        val logs = decode(feed(minimalRow(id = "not-a-uuid"), minimalRow(id = "also-not-a-uuid")))
        assertTrue(logs.isEmpty())
        assertEquals(2, skips.size)
    }

    @Test
    fun nonArrayTopLevelStillThrows() {
        try {
            decode("""{ "error": "oops" }""")
            fail("expected a decoding exception for a non-array top level")
        } catch (expected: Exception) {
            assertTrue(skips.isEmpty())
        }
    }

    @Test
    fun releasedAtDecodesWhenPresent() {
        val logs = decode(feed(row(releasedAt = "2026-05-14T09:30:00Z")))
        assertEquals(OffsetDateTime.parse("2026-05-14T09:30:00Z"), logs.single().releasedAt)
    }

    @Test
    fun releasedAtAbsentDecodesToNull() {
        assertNull(decode(feed(row())).single().releasedAt)
    }

    @Test
    fun unknownPlatformRowsDroppedPerDesignD1() {
        // The check constraint admits project/infra; iOS's strict enum drops
        // those rows and Android deliberately matches (design D1).
        val logs =
            decode(
                feed(
                    row(titleEn = "A"),
                    row(id = OTHER_ID, platform = "project"),
                    row(id = THIRD_ID, platform = "infra"),
                    row(id = FOURTH_ID, titleEn = "B"),
                ),
            )
        assertEquals(listOf("A", "B"), logs.map { it.titleEn })
        assertEquals(listOf(1, 2), skips.map { it.first })
    }

    @Test
    fun minimalValidRowDecodesWithNullOptionals() {
        val log = decode(feed(minimalRow())).single()
        assertNull(log.titleFr)
        assertNull(log.summaryFr)
        assertNull(log.bodyMdEn)
        assertNull(log.coverImagePath)
        assertNull(log.externalUrl)
        assertNull(log.publishedAt)
        assertNull(log.releasedAt)
    }

    private companion object {
        const val VALID_ID = "11111111-1111-1111-1111-111111111111"
        const val OTHER_ID = "22222222-2222-2222-2222-222222222222"
        const val THIRD_ID = "33333333-3333-3333-3333-333333333333"
        const val FOURTH_ID = "44444444-4444-4444-4444-444444444444"
    }
}
