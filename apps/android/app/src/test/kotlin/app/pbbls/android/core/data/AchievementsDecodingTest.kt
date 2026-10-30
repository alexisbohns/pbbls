package app.pbbls.android.core.data

import app.pbbls.android.core.ui.achievementOverride
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Achievements row contracts (M48) — foreign payloads in the shapes PostgREST
 * actually emits, mirroring iOS `AchievementsDecodingTests.swift`. The unlock
 * rows' timestamp variants left with the last `achievement_unlocks` read (#1307).
 */
class AchievementsDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `catalog row decodes with nulls and overrides`() {
        val rows =
            json.decodeFromString<List<AchievementRecord>>(
                """
                [{ "id": "a1", "slug": "pebble-count-10", "family": "pebble_count",
                   "threshold": 10, "emotion_id": null, "domain_id": null,
                   "sort_order": 110, "glyph_id": null, "karma_reward": 0,
                   "is_active": true, "title_en": null, "title_fr": null,
                   "description_en": null, "description_fr": null },
                 { "id": "a2", "slug": "emotion-first-joyful", "family": "emotion_first",
                   "threshold": null, "emotion_id": "e1", "domain_id": null,
                   "sort_order": 200, "glyph_id": null, "karma_reward": 5,
                   "is_active": true, "title_en": "Custom title", "title_fr": "Titre perso",
                   "description_en": null, "description_fr": null }]
                """.trimIndent(),
            )
        assertEquals(2, rows.size)
        assertEquals(10, rows[0].threshold)
        assertNull(rows[0].emotionId)
        assertEquals(5, rows[1].karmaReward)
        assertEquals("Titre perso", rows[1].titleFr)
    }

    @Test
    fun `check result decodes slug and granted karma`() {
        val rows =
            json.decodeFromString<List<AchievementCheckResult>>(
                """[{ "slug": "first-soul", "karma_granted": 7 },
                    { "slug": "pebble-count-1", "karma_granted": 0 }]""",
            )
        assertEquals("first-soul", rows[0].slug)
        assertEquals(7, rows[0].karmaGranted)
        assertEquals(0, rows[1].karmaGranted)
    }

    @Test
    fun `override pick prefers the active locale and falls back to the sibling`() {
        assertEquals("fr", achievementOverride(en = "en", fr = "fr", isFrench = true))
        assertEquals("en", achievementOverride(en = "en", fr = "fr", isFrench = false))
        assertEquals("en", achievementOverride(en = "en", fr = null, isFrench = true))
        assertEquals("fr", achievementOverride(en = null, fr = "fr", isFrench = false))
        assertNull(achievementOverride(en = null, fr = null, isFrench = true))
    }
}
