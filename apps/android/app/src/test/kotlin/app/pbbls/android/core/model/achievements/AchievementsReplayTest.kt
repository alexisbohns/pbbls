package app.pbbls.android.core.model.achievements

import app.pbbls.android.core.model.Valence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page mapped from the server's own payloads (#1307, spec §4). The expected values are the
 * ones `verify-achievements.ts` asserts for the same users, or the dump read raw beside the model,
 * so a refreshed dump changes no test: when one fails, the client and the server disagree.
 */
class AchievementsReplayTest {
    private fun page(name: String) = decodeAchievementsPage(AchievementPayloads.read(name))

    private val wholeSecond = { at: java.time.Instant -> at.nano == 0 }

    @Test
    fun `the explorer's centre is the first, heavy, small pebble, at whole seconds`() {
        val first = page(AchievementPayloads.PAGE).explorer!!.firstPebble!!
        assertEquals(Valence.LOWLIGHT_SMALL, first.valence)
        assertTrue(wholeSecond(first.happenedAt!!))
    }

    @Test
    fun `the first five and the six firsts come in the server's order, the six done and dated`() {
        val explorer = page(AchievementPayloads.PAGE).explorer!!
        assertEquals(FirstStep.entries, explorer.firstFive.map { it.step })
        assertEquals(StoneKind.entries, explorer.sixFirsts.map { it.kind })
        assertTrue(explorer.sixFirsts.all { it.done && it.pebble != null && it.on != null })
    }

    @Test
    fun `an unnamed moment keeps its snapshot, without a name`() {
        val medium = page(AchievementPayloads.PAGE).explorer!!.sixFirsts.first { it.kind == StoneKind.MEDIUM }
        assertNotNull(medium.pebble)
        assertNull(medium.pebble!!.name)
    }

    @Test
    fun `the six firsts ring is done and its completion, with its stone, is not ready before tomorrow 06 00`() {
        val explorer = page(AchievementPayloads.PAGE).explorer!!
        assertTrue(ExplorerRing.SIX_FIRSTS in explorer.ringsDone)
        val completion = explorer.completion!!
        assertFalse(completion.ready)
        assertNull(completion.pulledAt)
        assertNotNull(completion.pebble)
        assertTrue(wholeSecond(completion.completedAt!!))
        assertTrue(completion.offeredFrom!!.isAfter(completion.completedAt))
    }

    @Test
    fun `emotions seat 38 with one met and dated, and seven families in the sheet's order`() {
        val emotions = page(AchievementPayloads.PAGE).emotions!!
        assertEquals(38, emotions.seats.size)
        assertEquals((0 until 38).toList(), emotions.seats.map { it.seat })
        assertEquals(1, emotions.count)
        assertEquals(1, emotions.seats.count { it.met })
        assertNotNull(emotions.seats.single { it.met }.on)
        assertEquals(7, emotions.families.size)
        assertEquals(emotions.seats.filter { it.met }, emotions.families.flatMap { it.met })
        assertEquals(38, emotions.families.sumOf { it.met.size + it.notYet.size })
        assertNotNull(emotions.since)
    }

    @Test
    fun `domains report one entered, the rest not yet, and the few tier`() {
        val domains = page(AchievementPayloads.PAGE).domains!!
        assertEquals(1, domains.count)
        assertEquals(Coverage.FEW, domains.coverage)
        assertEquals(1, domains.entered.size)
        assertNotNull(domains.entered.single().on)
        assertEquals(domains.of - 1, domains.notYet.size)
    }

    @Test
    fun `pebbles - three kept, the next is 10 with 7 left at 0 222, then every 500`() {
        val p = page(AchievementPayloads.PAGE)
        val pebbles = p.pebbles!!
        assertEquals(3, pebbles.count)
        assertEquals(NextLevel(threshold = 10, left = 7, progress = 0.222f), pebbles.next)
        assertEquals(500, pebbles.thenEvery)
        assertNotNull(pebbles.since)
        assertNull(p.glyphsCarved!!.thenEvery)
        assertNotNull(p.glyphSales)
    }

    @Test
    fun `nothing is new before the first look, and no card has been seen`() {
        val p = page(AchievementPayloads.PAGE)
        assertTrue(p.explorer!!.sixFirsts.none { it.isNew })
        assertTrue(p.pebbles!!.levels.none { it.isNew })
        assertTrue(p.emotions!!.seats.none { it.isNew })
        assertTrue(p.seen.isEmpty())
    }

    @Test
    fun `the shelf leaves out the private families and is counted`() {
        val p = page(AchievementPayloads.PAGE)
        val private = setOf("emotion_first", "domain_first", "first_stone", "set_complete")
        assertTrue(p.shelf.isNotEmpty() && p.shelf.size <= 6)
        assertTrue(p.shelf.none { it.family in private })
        assertTrue(p.shelfCount >= p.shelf.size)
        assertNull(p.shelf.first { it.slug == "first-moment" }.threshold)
    }

    @Test
    fun `the year payload lists the current year first, then the past years newest first, twelve months each`() {
        val years = page(AchievementPayloads.YEAR).years
        assertTrue(years.size >= 2)
        assertTrue(years[0].current)
        assertTrue(years.drop(1).none { it.current })
        assertEquals(years.map { it.year }.sortedDescending(), years.map { it.year })
        assertTrue(years.all { y -> y.months.map { it.month } == (1..12).toList() })
        assertTrue(years.all { it.daysTarget == 14 })
    }

    @Test
    fun `a past year has no next step and no upcoming month`() {
        val past = page(AchievementPayloads.YEAR).years.filterNot { it.current }
        assertTrue(past.isNotEmpty())
        assertTrue(past.all { it.nextStep == null })
        assertTrue(past.all { y -> y.months.none { it.state == MonthState.UPCOMING } })
    }

    @Test
    fun `every month reads exactly as served - state, family, counts, level, progress, done`() {
        val raw = Json.parseToJsonElement(AchievementPayloads.read(AchievementPayloads.YEAR)).jsonObject
        val years = page(AchievementPayloads.YEAR).years
        raw["years"]!!.jsonArray.forEachIndexed { y, rawYear ->
            val year = years[y]
            assertEquals(rawYear.jsonObject["listed"]!!.jsonArray.map { it.jsonPrimitive.int }, year.listed)
            rawYear.jsonObject["months"]!!.jsonArray.forEach { element ->
                val m = element.jsonObject
                val month = year.months[m.int("month") - 1]
                val label = "${year.year}-${month.month}"
                assertEquals(label, m["state"]!!.jsonPrimitive.content, month.state?.slug)
                assertEquals(label, m["family"]?.jsonPrimitive?.content, month.family)
                assertEquals(label, m.int("moment_days"), month.momentDays)
                assertEquals(label, m.int("pebble_days"), month.pebbleDays)
                assertEquals(label, m.int("full_weeks"), month.fullWeeks)
                assertEquals(label, m.int("weeks_filled"), month.weeksFilled)
                assertEquals(label, m.int("level"), month.level)
                assertEquals(label, m["progress"]!!.jsonPrimitive.double.toFloat(), month.progress)
                val done = m["done"]!!.jsonObject
                assertEquals(
                    label,
                    MonthDone(done.bool("moments"), done.bool("pebbles"), done.bool("weeks")),
                    month.done,
                )
            }
        }
    }

    @Test
    fun `every year's next step reads exactly as served, in every dump`() {
        for (name in listOf(AchievementPayloads.PAGE, AchievementPayloads.YEAR, AchievementPayloads.SEEN, AchievementPayloads.PULLED)) {
            val raw = Json.parseToJsonElement(AchievementPayloads.read(name)).jsonObject
            val years = page(name).years
            raw["years"]!!.jsonArray.forEachIndexed { y, rawYear ->
                val label = "$name ${years[y].year}"
                val step = rawYear.jsonObject["next_step"]?.jsonObject
                if (step == null) {
                    assertNull(label, years[y].nextStep)
                } else {
                    assertEquals(
                        label,
                        NextStep(
                            month = step.int("month"),
                            needs = Needs.of(step["needs"]!!.jsonPrimitive.content)!!,
                            daysLeft = step.int("days_left"),
                            reachable = step.bool("reachable"),
                        ),
                        years[y].nextStep,
                    )
                }
            }
        }
    }

    @Test
    fun `the page dump's current year carries its next step`() {
        val current = page(AchievementPayloads.PAGE).years.single { it.current }
        assertNotNull(current.nextStep)
        assertEquals(14, current.daysTarget)
    }

    @Test
    fun `after the explorer and emotions are seen, both looks are dated and exactly one emotion is new`() {
        val p = page(AchievementPayloads.SEEN)
        assertEquals(setOf(AchievementCard.EXPLORER, AchievementCard.EMOTIONS), p.seen.keys)
        assertTrue(p.seen.values.all(wholeSecond))
        assertEquals(1, p.emotions!!.seats.count { it.isNew })
        assertTrue(p.explorer!!.firstFive.none { it.isNew })
    }

    @Test
    fun `a pulled completion carries its pull and is no longer ready`() {
        val completion = page(AchievementPayloads.PULLED).explorer!!.completion!!
        assertNotNull(completion.pulledAt)
        assertTrue(wholeSecond(completion.pulledAt!!))
        assertFalse(completion.ready)
    }

    private fun JsonObject.int(key: String) = getValue(key).jsonPrimitive.int

    private fun JsonObject.bool(key: String) = getValue(key).jsonPrimitive.boolean
}
