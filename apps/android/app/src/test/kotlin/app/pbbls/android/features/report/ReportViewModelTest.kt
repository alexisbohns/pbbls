package app.pbbls.android.features.report

import app.pbbls.android.R
import app.pbbls.android.core.data.ReportReceipt
import app.pbbls.android.core.data.ReportServicing
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.OffsetDateTime

private data class Sent(
    val target: ReportTarget,
    val reason: ReportReason,
    val detail: String?,
    val removeAndBlock: Boolean,
)

private class FakeReports : ReportServicing {
    val sent = mutableListOf<Sent>()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun send(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Boolean,
    ): ReportReceipt {
        gate?.await()
        failure?.let { throw it }
        sent += Sent(target, reason, detail, removeAndBlock)
        return ReportReceipt("r-1", "open", OffsetDateTime.parse("2026-11-12T20:54:43Z"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val connection = ReportTarget.Connection("c-1", "Ana")

    private fun model(fake: FakeReports = FakeReports()) = ReportViewModel(fake)

    @Test
    fun `cannot send until a reason is picked`() {
        val vm = model().apply { start(connection) }
        assertFalse(vm.canSend)
        vm.reason = ReportReason.SPAM
        assertTrue(vm.canSend)
    }

    @Test
    fun `remove and block is on by default`() {
        val vm = model().apply { start(connection) }
        assertTrue(vm.removeAndBlock)
    }

    @Test
    fun `sends trimmed detail, null when blank, and the block for a connection`() =
        runTest {
            val fake = FakeReports()
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.HATE
            vm.onDetailChange("   ")
            vm.submit()
            advanceUntilIdle()
            assertEquals(Sent(connection, ReportReason.HATE, null, true), fake.sent.single())
            assertEquals(ReportPhase.Sent(didBlock = true), vm.phase.value)
        }

    @Test
    fun `an invite never sends the block`() =
        runTest {
            val fake = FakeReports()
            val invite = ReportTarget.Invite("tok", null)
            val vm = model(fake).apply { start(invite) }
            vm.reason = ReportReason.SPAM
            vm.onDetailChange("  rude  ")
            vm.submit()
            advanceUntilIdle()
            assertEquals(Sent(invite, ReportReason.SPAM, "rude", false), fake.sent.single())
            assertEquals(ReportPhase.Sent(didBlock = false), vm.phase.value)
        }

    @Test
    fun `turning the block off sends without it`() =
        runTest {
            val fake = FakeReports()
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.OTHER
            vm.removeAndBlock = false
            vm.submit()
            advanceUntilIdle()
            assertFalse(fake.sent.single().removeAndBlock)
            assertEquals(ReportPhase.Sent(didBlock = false), vm.phase.value)
        }

    @Test
    fun `sending refuses a second tap and blocks canSend`() =
        runTest {
            val fake = FakeReports().apply { gate = CompletableDeferred() }
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.SPAM
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportPhase.Sending, vm.phase.value)
            assertFalse(vm.canSend)
            vm.submit()
            fake.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, fake.sent.size)
        }

    @Test
    fun `a failure shows its message and allows a retry`() =
        runTest {
            val fake = FakeReports().apply { failure = java.io.IOException("offline") }
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.SPAM
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportPhase.Failed(R.string.report_error_generic), vm.phase.value)
            assertTrue(vm.canSend)
        }

    @Test
    fun `detail is capped at 1000 code points, not characters`() {
        val vm = model().apply { start(connection) }
        val family = "👨‍👩‍👧" // 5 code points
        vm.onDetailChange("a".repeat(998) + family)
        assertEquals(1000, vm.detail.codePointCount(0, vm.detail.length))
        vm.onDetailChange("b".repeat(1200))
        assertEquals(1000, vm.detail.length)
    }

    @Test
    fun `start is idempotent for the same target`() {
        val vm = model().apply { start(connection) }
        vm.reason = ReportReason.SPAM
        vm.start(connection)
        assertEquals(ReportReason.SPAM, vm.reason)
        assertNull(model().target)
    }
}
