package app.pbbls.android.features.home.quick

import app.pbbls.android.R
import app.pbbls.android.core.model.MomentSavePayload
import app.pbbls.android.features.path.capture.CaptureFlowModel
import app.pbbls.android.testing.FakeAchievementsService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.postgrestException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Home's quick moment (#1383 D7; iOS `QuickMomentSheet` and `QuickMomentDraftTests`). */
class QuickMomentTest {
    private val writes = FakeMomentWriteService()
    private val achievements = FakeAchievementsService()
    private val saved = mutableListOf<Pair<String, QuickMomentExit>>()
    private val opened = OffsetDateTime.parse("2026-10-31T21:04:09.75+01:00")

    private fun TestScope.quick() =
        QuickMoment(
            scope = this,
            writes = writes,
            achievements = achievements,
            onSaved = { id, exit -> saved += id to exit },
            now = { opened },
        )

    @Test
    fun `opening starts a blank form at now, and a closed sheet edits nothing`() =
        runTest {
            val quick = quick()
            quick.setName("ignored")
            assertNull(quick.sheet.value)

            quick.open()

            assertEquals(QuickMomentUiState(happenedAt = opened), quick.sheet.value)
        }

    @Test
    fun `a blank or whitespace name saves nothing`() =
        runTest {
            val quick = quick().apply { open() }
            assertFalse(requireNotNull(quick.sheet.value).canSave)

            quick.setName("   ")
            assertFalse(requireNotNull(quick.sheet.value).canSave)
            quick.save(QuickMomentExit.CLOSE)
            advanceUntilIdle()

            assertTrue(writes.saved.isEmpty())
            assertNotNull(quick.sheet.value)
        }

    @Test
    fun `the name is clamped to capture's limit`() =
        runTest {
            assertEquals(CaptureFlowModel.NAME_LIMIT, QuickMoment.NAME_LIMIT)
            val quick = quick().apply { open() }

            quick.setName("x".repeat(QuickMoment.NAME_LIMIT + 5))

            assertEquals(QuickMoment.NAME_LIMIT, requireNotNull(quick.sheet.value).name.length)
        }

    @Test
    fun `save and close sends the trimmed name at whole seconds, checks achievements and closes`() =
        runTest {
            writes.nextMomentId = "m-1"
            val quick = quick().apply { open() }
            quick.setName("  Coffee with Mo ")

            quick.save(QuickMomentExit.CLOSE)
            advanceUntilIdle()

            assertEquals(
                MomentSavePayload(name = "Coffee with Mo", happenedAt = OffsetDateTime.parse("2026-10-31T21:04:09+01:00")),
                writes.saved.single(),
            )
            assertEquals(1, achievements.fireCheckCount)
            assertEquals(listOf("m-1" to QuickMomentExit.CLOSE), saved)
            assertNull(quick.sheet.value)
        }

    @Test
    fun `save and open hands the id over for complete, at the picked time`() =
        runTest {
            writes.nextMomentId = "m-2"
            val quick = quick().apply { open() }
            quick.setName("Walk")
            quick.setHappenedAt(OffsetDateTime.parse("2026-10-30T08:15:00+01:00"))

            quick.save(QuickMomentExit.OPEN)
            advanceUntilIdle()

            assertEquals(OffsetDateTime.parse("2026-10-30T08:15:00+01:00"), writes.saved.single().happenedAt)
            assertEquals(listOf("m-2" to QuickMomentExit.OPEN), saved)
        }

    @Test
    fun `both saves are off while one runs, and a second tap sends nothing`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            writes.gate = gate
            val quick = quick().apply { open() }
            quick.setName("Walk")

            quick.save(QuickMomentExit.OPEN)
            runCurrent()
            val saving = requireNotNull(quick.sheet.value)
            assertTrue(saving.isSaving)
            assertFalse(saving.canSave)
            quick.save(QuickMomentExit.CLOSE)
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, writes.saved.size)
            assertEquals(1, saved.size)
        }

    @Test
    fun `a failed save keeps the sheet and its name, with the error`() =
        runTest {
            writes.failNext = postgrestException("boom")
            val quick = quick().apply { open() }
            quick.setName("Walk")

            quick.save(QuickMomentExit.OPEN)
            advanceUntilIdle()

            val form = requireNotNull(quick.sheet.value)
            assertEquals(R.string.home_quick_error, form.errorRes)
            assertEquals("Walk", form.name)
            assertFalse(form.isSaving)
            assertTrue(saved.isEmpty())
            assertEquals(0, achievements.fireCheckCount)

            // Typing again clears the error; the retry goes through.
            quick.setName("Walk home")
            assertNull(requireNotNull(quick.sheet.value).errorRes)
            quick.save(QuickMomentExit.CLOSE)
            advanceUntilIdle()
            assertEquals("Walk home", writes.saved.single().name)
        }

    @Test
    fun `a save still lands and reports after the sheet is swiped away`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            writes.gate = gate
            val quick = quick().apply { open() }
            quick.setName("Walk")

            quick.save(QuickMomentExit.CLOSE)
            runCurrent()
            quick.dismiss()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, saved.size)
            assertNull(quick.sheet.value)
        }
}
