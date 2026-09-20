package app.pbbls.android.features.lab

import app.pbbls.android.R
import app.pbbls.android.features.lab.models.Log
import app.pbbls.android.features.lab.models.LogPlatform
import app.pbbls.android.features.lab.models.LogSpecies
import app.pbbls.android.features.lab.models.LogStatus
import app.pbbls.android.testing.FakeLogsService
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.OffsetDateTime

/**
 * The announcement detail's by-id load (#852).
 *
 * Unlike the Lab page's content swap this replaces, the screen no longer
 * receives its [Log] and cover URL from a parent already holding them — it
 * loads both itself, the same deviation from iOS that
 * [app.pbbls.android.features.profile.SoulDetailViewModel] already made for
 * the same reason (a Nav3 key can only carry an id).
 */
class AnnouncementDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun log(id: String) =
        Log(
            id = id,
            species = LogSpecies.ANNOUNCEMENT,
            platform = LogPlatform.ALL,
            status = LogStatus.SHIPPED,
            titleEn = "Log $id",
            summaryEn = "Summary $id",
            coverImagePath = "covers/$id.png",
            published = true,
            createdAt = OffsetDateTime.parse("2026-09-19T12:00:00Z"),
            reactionCount = 0,
        )

    private fun viewModel(logs: FakeLogsService = FakeLogsService()) = AnnouncementDetailViewModel(logs)

    private fun fakeLogsService(log: Log?) = FakeLogsService().apply { this.log = log }

    @Test
    fun `starts Loading and resolves to Content with a cover url`() =
        runTest {
            val logs = fakeLogsService(log("a"))
            val viewModel = viewModel(logs)

            viewModel.start("a")
            assertEquals(AnnouncementDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            val state = viewModel.uiState.value as AnnouncementDetailUiState.Content
            assertEquals("a", state.log.id)
            assertEquals("${logs.coverBase}/covers/a.png", state.coverUrl)
            assertEquals(listOf("a"), logs.logCalls)
        }

    @Test
    fun `start is idempotent for the same id`() =
        runTest {
            val logs = fakeLogsService(log("a"))
            val viewModel = viewModel(logs)

            viewModel.start("a")
            advanceUntilIdle()
            repeat(3) { viewModel.start("a") }
            advanceUntilIdle()

            assertEquals(1, logs.logCalls.size)
        }

    @Test
    fun `a missing row is Error`() =
        runTest {
            val logs = fakeLogsService(null)
            val viewModel = viewModel(logs)

            viewModel.start("gone")
            advanceUntilIdle()

            assertEquals(
                R.string.lab_announcement_load_error,
                (viewModel.uiState.value as AnnouncementDetailUiState.Error).messageRes,
            )
        }

    @Test
    fun `a failed load is Error, and retry recovers`() =
        runTest {
            val logs = fakeLogsService(log("a"))
            logs.logFailure = IOException("offline")
            val viewModel = viewModel(logs)

            viewModel.start("a")
            advanceUntilIdle()
            assertEquals(
                R.string.error_offline,
                (viewModel.uiState.value as AnnouncementDetailUiState.Error).messageRes,
            )

            logs.logFailure = null
            viewModel.retry()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is AnnouncementDetailUiState.Content)
        }

    @Test
    fun `start on a different id reloads`() =
        runTest {
            val logs = fakeLogsService(log("a"))
            val viewModel = viewModel(logs)

            viewModel.start("a")
            advanceUntilIdle()

            logs.log = log("b")
            viewModel.start("b")
            advanceUntilIdle()

            assertEquals("b", (viewModel.uiState.value as AnnouncementDetailUiState.Content).log.id)
            assertEquals(listOf("a", "b"), logs.logCalls)
        }
}
