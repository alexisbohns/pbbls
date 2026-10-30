package app.pbbls.android.testing

import app.pbbls.android.core.model.achievements.AchievementCard
import app.pbbls.android.core.model.achievements.CeremonyPull
import app.pbbls.android.core.model.achievements.CompletionStack
import app.pbbls.android.core.model.achievements.decodeAchievementsPage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.ZoneId

/** The fake's page, cache, seen and pull behave as the ViewModel tests will rely on (#1307). */
class FakeAchievementsServiceTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val page = decodeAchievementsPage("{}")

    @Test
    fun `it serves the armed page, fails once when armed, and records calls`() =
        runTest {
            val fake = FakeAchievementsService(served = page)
            assertSame(page, fake.page(zone))
            fake.failNextPage = IOException("offline")
            try {
                fake.page(zone)
                fail("expected the armed failure")
            } catch (e: IOException) {
                assertEquals("offline", e.message)
            }
            assertSame(page, fake.page(zone))
            assertEquals(3, fake.pageCalls)
        }

    @Test
    fun `the check and the read are recorded in call order`() =
        runTest {
            val fake = FakeAchievementsService()
            fake.checkIgnoringFailure()
            fake.page(zone)
            assertEquals(listOf("check", "page"), fake.calls)
        }

    @Test
    fun `a gated read waits for its gate`() =
        runTest {
            val fake = FakeAchievementsService(served = page)
            val gate = CompletableDeferred<Unit>()
            fake.pageGate = gate
            val read = async { fake.page(zone) }
            runCurrent()
            assertFalse(read.isCompleted)
            gate.complete(Unit)
            assertSame(page, read.await())
        }

    @Test
    fun `the kept page is null until armed`() =
        runTest {
            val fake = FakeAchievementsService()
            assertNull(fake.cachedPage(zone))
            fake.kept = page
            assertSame(page, fake.cachedPage(zone))
        }

    @Test
    fun `seen marks and pulls are recorded in order, and a pull fails once when armed`() =
        runTest {
            val fake = FakeAchievementsService(pullResult = CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 10))
            fake.markSeen(AchievementCard.EXPLORER)
            fake.markSeen(AchievementCard.EMOTIONS)
            assertEquals(listOf(AchievementCard.EXPLORER, AchievementCard.EMOTIONS), fake.seen)
            assertEquals(CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 10), fake.pullCeremony(CompletionStack.SIX_FIRSTS, zone))
            fake.failNextPull = IOException("lost")
            try {
                fake.pullCeremony(CompletionStack.SIX_FIRSTS, zone)
                fail("expected the armed failure")
            } catch (e: IOException) {
                assertTrue(e.message == "lost")
            }
            assertEquals(listOf(CompletionStack.SIX_FIRSTS, CompletionStack.SIX_FIRSTS), fake.pulls)
        }

    @Test
    fun `the zone of every read, kept read and pull is recorded`() =
        runTest {
            val tokyo = ZoneId.of("Asia/Tokyo")
            val fake = FakeAchievementsService()
            fake.page(zone)
            fake.cachedPage(tokyo)
            fake.pullCeremony(CompletionStack.EMOTIONS, zone)
            assertEquals(listOf(zone, tokyo, zone), fake.zones)
        }
}
