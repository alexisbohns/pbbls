package app.pbbls.android.features.karma

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The achievement-unlock capsule entry point (M48): zero-count calls never
 * surface, a shown capsule auto-dismisses after
 * [AchievementNotificationService.CAPSULE_DURATION_MS], `dismiss` clears it
 * immediately, and a fresh unlock replaces the current one and resets the
 * timer — mirrors [KarmaNotificationServiceTest]'s virtual-clock approach.
 */
class AchievementNotificationServiceTest {
    @Test
    fun `a zero count never surfaces a capsule`() =
        runTest {
            val service = AchievementNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyUnlocked(count = 0, single = null, karmaTotal = 0)
            assertNull(service.activeCapsule)
        }

    @Test
    fun `an unlock shows the capsule and auto-dismisses after the duration`() =
        runTest {
            val service = AchievementNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyUnlocked(count = 2, single = null, karmaTotal = 5)
            assertEquals(
                AchievementUnlockedContent(count = 2, single = null, karmaTotal = 5),
                service.activeCapsule,
            )

            advanceTimeBy(AchievementNotificationService.CAPSULE_DURATION_MS + 1)
            runCurrent()
            assertNull(service.activeCapsule)
        }

    @Test
    fun `dismiss clears the capsule immediately`() =
        runTest {
            val service = AchievementNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyUnlocked(count = 1, single = null, karmaTotal = 0)
            service.dismiss()
            assertNull(service.activeCapsule)
        }

    @Test
    fun `a fresh unlock replaces the capsule and resets the timer`() =
        runTest {
            val service = AchievementNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyUnlocked(count = 1, single = null, karmaTotal = 0)
            advanceTimeBy(AchievementNotificationService.CAPSULE_DURATION_MS - 100)
            service.notifyUnlocked(count = 3, single = null, karmaTotal = 7)

            advanceTimeBy(200)
            runCurrent()
            assertEquals(3, service.activeCapsule?.count)

            advanceTimeBy(AchievementNotificationService.CAPSULE_DURATION_MS)
            runCurrent()
            assertNull(service.activeCapsule)
        }
}
