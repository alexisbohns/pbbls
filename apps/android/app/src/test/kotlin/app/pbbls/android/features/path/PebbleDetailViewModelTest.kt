package app.pbbls.android.features.path

import app.pbbls.android.core.data.SignedUrlProviding
import app.pbbls.android.core.data.SnapAspectCache
import app.pbbls.android.core.data.SnapBoundsDecoder
import app.pbbls.android.core.data.SnapURLCache
import app.pbbls.android.core.data.SnapUrls
import app.pbbls.android.core.model.MomentDeleteChoice
import app.pbbls.android.testing.FakeMomentReadService
import app.pbbls.android.testing.FakeMomentWriteService
import app.pbbls.android.testing.MainDispatcherRule
import app.pbbls.android.testing.UiFixtures
import app.pbbls.android.testing.recordEffects
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** The moment read page's load, resume-refresh and delete contract (#849, #852, #1048). */
class PebbleDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** Never reached from a ViewModel test: the ratio probe belongs to the card. */
    private fun snapAspect(): SnapAspectCache {
        val scope = CoroutineScope(SupervisorJob())
        val provider =
            object : SignedUrlProviding {
                override suspend fun signedUrls(storagePrefix: String) = SnapUrls(original = "", thumb = "")
            }
        return SnapAspectCache(
            urls = SnapURLCache(provider, scope, nowMillis = { 0L }),
            scope = scope,
            decoder = SnapBoundsDecoder { null },
        )
    }

    @Test
    fun `loads the moment`() =
        runTest {
            val moment = UiFixtures.momentDetail()
            val service = FakeMomentReadService(moments = mapOf("moment-1" to moment))
            val viewModel = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())

            viewModel.start("moment-1")
            assertEquals(PebbleDetailUiState.Loading, viewModel.uiState.value)
            advanceUntilIdle()

            assertEquals(moment, (viewModel.uiState.value as PebbleDetailUiState.Content).detail)
        }

    /** A photo-only capture with no pebble yet is a moment the page must open (D3). */
    @Test
    fun `loads a moment without a pebble or a name`() =
        runTest {
            val bare = UiFixtures.momentDetail(pebble = null, name = null)
            val service = FakeMomentReadService(moments = mapOf("moment-1" to bare))
            val viewModel = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())

            viewModel.start("moment-1")
            advanceUntilIdle()

            val detail = (viewModel.uiState.value as PebbleDetailUiState.Content).detail
            assertNull(detail.pebble)
            assertNull(detail.name)
        }

    @Test
    fun `a failed load is the error state and retry recovers`() =
        runTest {
            val service = FakeMomentReadService(moments = mapOf("moment-1" to UiFixtures.momentDetail()))
            service.failNext = IOException("offline")
            val viewModel = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())

            viewModel.start("moment-1")
            advanceUntilIdle()
            assertEquals(PebbleDetailUiState.Error, viewModel.uiState.value)

            viewModel.retry()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is PebbleDetailUiState.Content)
        }

    /** Rotation, as far as a JVM test carries it: re-running `start` with the same id does not refetch. */
    @Test
    fun `starting with the same id does not refetch`() =
        runTest {
            val service = FakeMomentReadService(moments = mapOf("moment-1" to UiFixtures.momentDetail()))
            val viewModel = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())

            viewModel.start("moment-1")
            advanceUntilIdle()
            viewModel.start("moment-1")
            advanceUntilIdle()

            assertEquals(1, service.loadedMomentIds.size)
        }

    /**
     * Edit moment and Edit pebble are separate entries with no callback back
     * into this instance (#852) — an edit saved there has to be picked up on resume.
     */
    @Test
    fun `resuming after the first time refetches the same moment`() =
        runTest {
            val service = FakeMomentReadService(moments = mapOf("moment-1" to UiFixtures.momentDetail(name = "A walk")))
            val viewModel = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())
            viewModel.start("moment-1")
            advanceUntilIdle()

            // First resume: `start` already loaded, so this is a no-op.
            viewModel.onResumed()
            advanceUntilIdle()
            assertEquals(1, service.loadedMomentIds.size)

            service.moments = mapOf("moment-1" to UiFixtures.momentDetail(name = "A longer walk"))
            viewModel.onResumed()
            advanceUntilIdle()

            assertEquals(listOf("moment-1", "moment-1"), service.loadedMomentIds)
            assertEquals(
                "A longer walk",
                (viewModel.uiState.value as PebbleDetailUiState.Content).detail.name,
            )
        }

    @Test
    fun `opening a different moment loads it`() =
        runTest {
            val service =
                FakeMomentReadService(
                    moments =
                        mapOf(
                            "moment-1" to UiFixtures.momentDetail(id = "moment-1"),
                            "moment-2" to UiFixtures.momentDetail(id = "moment-2", name = "Another"),
                        ),
                )
            val viewModel = PebbleDetailViewModel(service, FakeMomentWriteService(), snapAspect())
            viewModel.start("moment-1")
            advanceUntilIdle()

            viewModel.start("moment-2")
            advanceUntilIdle()

            assertEquals(listOf("moment-1", "moment-2"), service.loadedMomentIds)
        }

    // MARK: - Delete (R7, R8)

    @Test
    fun `deleting the moment from the read page closes it`() =
        runTest {
            val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1")))
            val write = FakeMomentWriteService()
            val vm = PebbleDetailViewModel(read, write, snapAspect())
            val effects = recordEffects(vm.effects)
            vm.start("m-1")
            advanceUntilIdle()

            vm.requestDelete()
            assertTrue(vm.pendingDelete.value)
            vm.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()

            assertEquals(listOf("m-1"), write.deletedMoments)
            assertFalse(vm.pendingDelete.value)
            assertEquals(listOf(PebbleDetailEffect.MomentDeleted), effects.values)
            effects.stop()
        }

    @Test
    fun `deleting only the pebble reloads the page as a bare moment`() =
        runTest {
            val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1")))
            val write = FakeMomentWriteService()
            val vm = PebbleDetailViewModel(read, write, snapAspect())
            val effects = recordEffects(vm.effects)
            vm.start("m-1")
            advanceUntilIdle()

            read.moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1", pebble = null))
            vm.requestDelete()
            vm.confirmDelete(MomentDeleteChoice.PEBBLE_ONLY)
            advanceUntilIdle()

            assertEquals(listOf("pebble-1"), write.deletedPebbles)
            assertTrue(write.deletedMoments.isEmpty())
            assertNull((vm.uiState.value as PebbleDetailUiState.Content).detail.pebble)
            assertTrue(effects.values.isEmpty())
            effects.stop()
        }

    /** R15: a failed delete keeps the page and says "Couldn't delete". */
    @Test
    fun `a failed delete keeps the page and reports`() =
        runTest {
            val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1")))
            val write = FakeMomentWriteService()
            val vm = PebbleDetailViewModel(read, write, snapAspect())
            val effects = recordEffects(vm.effects)
            vm.start("m-1")
            advanceUntilIdle()

            write.failNext = IOException("offline")
            vm.requestDelete()
            vm.confirmDelete(MomentDeleteChoice.MOMENT)
            advanceUntilIdle()

            assertTrue(vm.didDeleteFail.value)
            assertTrue(vm.uiState.value is PebbleDetailUiState.Content)
            assertTrue(effects.values.isEmpty())

            vm.dismissDeleteError()
            assertFalse(vm.didDeleteFail.value)
            effects.stop()
        }

    @Test
    fun `cancelling a delete writes nothing`() =
        runTest {
            val read = FakeMomentReadService(moments = mapOf("m-1" to UiFixtures.momentDetail(id = "m-1")))
            val write = FakeMomentWriteService()
            val vm = PebbleDetailViewModel(read, write, snapAspect())
            vm.start("m-1")
            advanceUntilIdle()

            vm.requestDelete()
            vm.cancelDelete()
            advanceUntilIdle()

            assertFalse(vm.pendingDelete.value)
            assertTrue(write.deletedMoments.isEmpty())
            assertTrue(write.deletedPebbles.isEmpty())
        }
}
