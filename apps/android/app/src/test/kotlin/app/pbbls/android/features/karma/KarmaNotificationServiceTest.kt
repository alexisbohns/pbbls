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
 * Covers the karma-flash entry point (D9/D10): only positive credits celebrate,
 * a shown capsule auto-dismisses after [KarmaNotificationService.CAPSULE_DURATION_MS],
 * `dismiss` clears it immediately, and a fresh credit replaces the current one
 * and resets the timer. The service takes an injectable scope backed by an
 * [UnconfinedTestDispatcher] so the auto-dismiss is exercised on the virtual
 * clock without a device.
 */
class KarmaNotificationServiceTest {
    @Test
    fun `non-positive amounts never surface a capsule`() =
        runTest {
            val service = KarmaNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyEarned(0, KarmaReason.PEBBLE_CREATED)
            assertNull(service.activeCapsule)

            service.notifyEarned(-3, KarmaReason.PEBBLE_ENRICHED)
            assertNull(service.activeCapsule)
        }

    @Test
    fun `a positive credit shows the capsule and auto-dismisses after the duration`() =
        runTest {
            val service = KarmaNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyEarned(5, KarmaReason.PEBBLE_CREATED)
            assertEquals(KarmaEarnedContent(5, KarmaReason.PEBBLE_CREATED), service.activeCapsule)

            advanceTimeBy(KarmaNotificationService.CAPSULE_DURATION_MS + 1)
            runCurrent()
            assertNull(service.activeCapsule)
        }

    @Test
    fun `dismiss clears the capsule immediately`() =
        runTest {
            val service = KarmaNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyEarned(7, KarmaReason.PEBBLE_ENRICHED)
            assertEquals(KarmaEarnedContent(7, KarmaReason.PEBBLE_ENRICHED), service.activeCapsule)

            service.dismiss()
            assertNull(service.activeCapsule)
        }

    @Test
    fun `a fresh credit replaces the previous capsule and resets the timer`() =
        runTest {
            val service = KarmaNotificationService(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

            service.notifyEarned(5, KarmaReason.PEBBLE_CREATED)
            advanceTimeBy(KarmaNotificationService.CAPSULE_DURATION_MS - 500)

            service.notifyEarned(9, KarmaReason.PEBBLE_ENRICHED)
            assertEquals(KarmaEarnedContent(9, KarmaReason.PEBBLE_ENRICHED), service.activeCapsule)

            // The original timer would have fired here; the reset keeps it visible.
            advanceTimeBy(600)
            runCurrent()
            assertEquals(KarmaEarnedContent(9, KarmaReason.PEBBLE_ENRICHED), service.activeCapsule)

            // The new timer dismisses it on its own schedule.
            advanceTimeBy(KarmaNotificationService.CAPSULE_DURATION_MS)
            runCurrent()
            assertNull(service.activeCapsule)
        }
}
