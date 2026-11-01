package app.pbbls.android.features.home.checklist

import app.pbbls.android.core.data.AchievementRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Your first five's state (#1383 D11): iOS `HomeChecklistTests`, case for case. */
class HomeChecklistTest {
    private fun catalog(
        slugs: List<String>,
        inactive: Set<String> = emptySet(),
    ): List<AchievementRecord> =
        slugs.mapIndexed { index, slug ->
            AchievementRecord(
                id = "id-$slug",
                slug = slug,
                family = if (slug == HomeChecklist.BLUE_SLUG) "first_five" else slug.replace("-", "_"),
                sortOrder = index,
                karmaReward = 0,
                isActive = slug !in inactive,
            )
        }

    private val taskSlugs = ChecklistTask.entries.map { it.slug }
    private val allSlugs = taskSlugs + HomeChecklist.BLUE_SLUG

    @Test
    fun `rows follow the fixed order and tick from unlocks`() {
        val c = HomeChecklist.make(catalog(allSlugs), unlocked = setOf("first-moment", "first-soul"))

        assertEquals(
            listOf(ChecklistTask.MOMENT, ChecklistTask.PEBBLE, ChecklistTask.COLLECTION, ChecklistTask.SOUL, ChecklistTask.GLYPH),
            c.rows.map { it.task },
        )
        assertEquals(listOf(true, false, false, true, false), c.rows.map { it.isDone })
        assertEquals(2, c.doneCount)
        assertEquals(ChecklistState.IN_PROGRESS, c.state)
    }

    @Test
    fun `the slugs are the server's`() {
        assertEquals(listOf("first-moment", "first-pebble", "first-collection", "first-soul", "first-glyph"), taskSlugs)
        assertEquals("blue-pebble", HomeChecklist.BLUE_SLUG)
    }

    @Test
    fun `five done offers the claim`() {
        val c = HomeChecklist.make(catalog(allSlugs), unlocked = taskSlugs.toSet())

        assertEquals(ChecklistState.CLAIMABLE, c.state)
        assertEquals(5, c.doneCount)
    }

    @Test
    fun `a claimed blue pebble removes the section`() {
        val c = HomeChecklist.make(catalog(allSlugs), unlocked = allSlugs.toSet())

        assertEquals(ChecklistState.DONE, c.state)
        assertTrue(!c.isShown)
    }

    @Test
    fun `a missing or retired catalog row hides the section`() {
        assertEquals(ChecklistState.UNAVAILABLE, HomeChecklist.make(emptyList(), emptySet()).state)
        val retired = catalog(allSlugs, inactive = setOf("first-glyph"))
        assertEquals(ChecklistState.UNAVAILABLE, HomeChecklist.make(retired, emptySet()).state)
        val noBlue = catalog(taskSlugs)
        assertEquals(ChecklistState.UNAVAILABLE, HomeChecklist.make(noBlue, emptySet()).state)
    }

    @Test
    fun `an unlocked retired row still counts`() {
        // A retired row the user already unlocked keeps its tick (unlocks are permanent).
        val retired = catalog(allSlugs, inactive = setOf("first-glyph"))
        val c = HomeChecklist.make(retired, unlocked = setOf("first-glyph"))

        assertNotEquals(ChecklistState.UNAVAILABLE, c.state)
        assertTrue(c.rows[4].isDone)
    }

    @Test
    fun `unlocks by achievement id resolve to slugs through the catalog`() {
        val rows = catalog(allSlugs)
        val c = HomeChecklist.fromUnlockIds(rows, unlockedIds = setOf("id-first-pebble", "id-unknown"))

        assertEquals(listOf(false, true, false, false, false), c.rows.map { it.isDone })
        assertEquals(ChecklistState.IN_PROGRESS, c.state)
    }

    @Test
    fun `only in progress and claimable are shown`() {
        assertTrue(HomeChecklist.make(catalog(allSlugs), emptySet()).isShown)
        assertTrue(HomeChecklist.make(catalog(allSlugs), taskSlugs.toSet()).isShown)
        assertTrue(!HomeChecklist.make(emptyList(), emptySet()).isShown)
    }
}
