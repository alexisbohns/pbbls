package app.pbbls.android.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.EmotionPaletteService
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PathStatsServicing
import app.pbbls.android.services.PebbleDraftsServicing
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SignedUrlProviding
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SupabaseService
import app.pbbls.android.services.SupabaseServicing
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope

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
 * These fields back ViewModel constructors directly in most tests; only
 * [referenceData] is also handed to [PebblesTestHarness] below, since it is one
 * of the three permanent CompositionLocals.
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
    val pathStats: PathStatsServicing = FakePathStatsService(),
    val drafts: PebbleDraftsServicing = FakePebbleDraftsService(),
)

/**
 * A real [EmotionPaletteService] over a syntactically valid but fake project —
 * the same recipe `ServiceGraphFakesTest.servicesConstructWithoutSecrets` uses,
 * because [EmotionPaletteService] is concrete with no extracted interface
 * (nothing calls `load()` through this harness, so `createSupabaseClient`'s
 * client never leaves the JVM — see `di/SupabaseModule`'s KDoc). The `@OptIn`
 * is `autoSetupPlatform`'s, documented there as "For testing."
 */
@OptIn(SupabaseInternal::class)
private fun fakeEmotionPaletteService(): EmotionPaletteService {
    val client =
        createSupabaseClient(
            supabaseUrl = "https://example.supabase.co",
            supabaseKey = "not-a-real-key",
        ) {
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                autoSetupPlatform = false
            }
            install(Postgrest)
        }
    return EmotionPaletteService(SupabaseService(client, TestScope()))
}

/** A real [SnapURLCache] whose provider is never expected to be called from this harness. */
private fun fakeSnapURLCache(): SnapURLCache =
    SnapURLCache(
        provider =
            object : SignedUrlProviding {
                override suspend fun signedUrls(storagePrefix: String) =
                    error("SnapURLCache.signedUrls should not be reached through PebblesTestHarness")
            },
        scope = CoroutineScope(SupervisorJob()),
        nowMillis = { 0L },
    )

/**
 * Provides the three permanent CompositionLocals in one place, so a screen
 * test is one wrap rather than three nested `CompositionLocalProvider`s.
 *
 * Nothing composes this yet: driving a composable on the JVM needs Robolectric,
 * which is #857. It is written now because a harness that arrives with the
 * seams is one that gets used.
 *
 * This file COMPILING is itself the gate: if one of the three reverts to a
 * concrete type this fake cannot supply, or a screen re-acquires a dependency
 * through a fourth local, this stops compiling and `testDebugUnitTest` goes
 * red (#852: `di/ServiceGraph` and every other `Local…Service` are gone —
 * `apps/android/CLAUDE.md`).
 */
@Composable
fun PebblesTestHarness(
    graph: FakeServiceGraph = FakeServiceGraph(),
    palettes: EmotionPaletteService = fakeEmotionPaletteService(),
    snapUrls: SnapURLCache = fakeSnapURLCache(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalEmotionPaletteService provides palettes,
        LocalReferenceDataService provides graph.referenceData,
        LocalSnapURLCache provides snapUrls,
        content = content,
    )
}
