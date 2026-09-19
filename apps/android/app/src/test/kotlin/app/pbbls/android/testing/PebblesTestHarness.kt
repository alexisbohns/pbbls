package app.pbbls.android.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.LocalAchievementsService
import app.pbbls.android.services.LocalPathService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalProfileService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SupabaseServicing

/**
 * The fake graph a test drives (#848). Every field defaults to a fresh fake, so
 * a test names only the seam it cares about:
 *
 * ```
 * val graph = FakeServiceGraph(pathService = FakePathService(pebbles = fixture))
 * ```
 *
 * Only the extracted seams are here — the five from #848 plus one per screen
 * #849 has migrated. The rest still have no interface, on purpose
 * (`apps/android/CLAUDE.md`: extract a seam when a test needs one, not before).
 *
 * **Where the fakes live, and why.** Not one of them holds a `SupabaseClient` or
 * reads `BuildConfig` — that is the whole point: each screen's load, error and
 * save path becomes drivable without a live project. They sit in `src/test`
 * because that is their only consumer until #857 lands Robolectric; they move to
 * a real `core/testing` module with #851.
 */
class FakeServiceGraph(
    val supabase: SupabaseServicing = FakeSupabaseService(),
    val pathService: PathServicing = FakePathService(),
    val profileService: ProfileServicing = FakeProfileService(),
    val pebbleWrite: PebbleWriteServicing = FakePebbleWriteService(),
    val referenceData: ReferenceDataServicing = FakeReferenceDataService(),
    val achievements: AchievementsServicing = FakeAchievementsService(),
)

/**
 * Provides the whole graph of fakes in one place, so a screen test is one wrap
 * rather than five nested `CompositionLocalProvider`s.
 *
 * Nothing composes this yet: driving a composable on the JVM needs Robolectric,
 * which is #857. It is written now because #849 will move screens to ViewModels
 * against exactly this shape, and because a harness that arrives with the seams
 * is one that gets used.
 *
 * This file COMPILING is itself the gate: if any of the five `Local…` reverts to
 * a concrete type, or a screen re-acquires a dependency a fake cannot supply,
 * this stops compiling and `testDebugUnitTest` goes red.
 */
@Composable
fun PebblesTestHarness(
    graph: FakeServiceGraph = FakeServiceGraph(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSupabaseService provides graph.supabase,
        LocalPathService provides graph.pathService,
        LocalProfileService provides graph.profileService,
        LocalPebbleWriteService provides graph.pebbleWrite,
        LocalReferenceDataService provides graph.referenceData,
        LocalAchievementsService provides graph.achievements,
        content = content,
    )
}
