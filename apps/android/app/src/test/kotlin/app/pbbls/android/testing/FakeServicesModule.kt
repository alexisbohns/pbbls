package app.pbbls.android.testing

import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.CollectionsServicing
import app.pbbls.android.core.data.ComposerSnapshotStoring
import app.pbbls.android.core.data.ConnectionsServicing
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.data.GlyphServicing
import app.pbbls.android.core.data.LogsServicing
import app.pbbls.android.core.data.PathServicing
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.data.PebbleDetailServicing
import app.pbbls.android.core.data.PebbleDraftsServicing
import app.pbbls.android.core.data.PebbleWriteServicing
import app.pbbls.android.core.data.ProfileServicing
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SignedUrlProviding
import app.pbbls.android.core.data.SnapURLCache
import app.pbbls.android.core.data.SnapUrls
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SoulsServicing
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.di.IoApplicationScope
import app.pbbls.android.di.ServiceBindings
import app.pbbls.android.di.ServiceModule
import app.pbbls.android.di.SupabaseModule
import app.pbbls.android.features.path.ComposerMedia
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import java.util.Collections
import javax.inject.Singleton

/**
 * The Hilt graph every Robolectric UI test runs against (#857).
 *
 * It replaces the three production modules that reach a network or a
 * `BuildConfig` secret, and leaves the rest of the graph real:
 *
 * - [ServiceBindings]: every `…Servicing` seam is bound to its in-memory fake.
 *   Each fake is also provided under its own type, so a test injects
 *   `FakePathService` and arms it directly — no cast, and no second instance.
 * - [SupabaseModule]: the client is a real `SupabaseClient` over a Ktor
 *   [MockEngine], never `AppEnvironment`. The concrete services that still have
 *   no seam (`EmotionPaletteService`, `KarmaNotificationService`, …) are built
 *   by Hilt as in production, and whatever they send is answered here with an
 *   empty JSON array — so a test is deterministic with no network, and a fork
 *   PR with no secrets runs the same suite. [unexpectedRequests] records every
 *   URL, which is what to read when a screen seems to be waiting on nothing.
 * - [ServiceModule]: the snapshot store, snap writer and signed-URL cache are
 *   replaced with fakes; the real ones write to disk and Storage.
 *
 * Hilt builds a fresh component per test, so every fake starts clean.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [ServiceBindings::class, SupabaseModule::class, ServiceModule::class],
)
object FakeServicesModule {
    /** Every URL a concrete (unfaked) service requested, in order. Cleared per process, not per test. */
    val unexpectedRequests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    // ---- The client, for the services that have no seam yet ----

    @OptIn(SupabaseInternal::class)
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = "https://fake.supabase.test",
            supabaseKey = "not-a-real-key",
        ) {
            httpEngine =
                MockEngine { request ->
                    unexpectedRequests += request.url.toString()
                    respond(
                        content = "[]",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                autoSetupPlatform = false
            }
            install(Postgrest)
            install(Storage)
            install(Functions)
        }

    // ---- Fakes under their own type (what a test injects) ----

    @Provides @Singleton
    fun fakeSupabase() = FakeSupabaseService()

    @Provides @Singleton
    fun fakePath() = FakePathService()

    @Provides @Singleton
    fun fakeProfile() = FakeProfileService()

    @Provides @Singleton
    fun fakePebbleWrite() = FakePebbleWriteService()

    @Provides @Singleton
    fun fakeReferenceData() = FakeReferenceDataService()

    @Provides @Singleton
    fun fakeAchievements() = FakeAchievementsService()

    @Provides @Singleton
    fun fakePathStats() = FakePathStatsService()

    @Provides @Singleton
    fun fakeDrafts() = FakePebbleDraftsService()

    @Provides @Singleton
    fun fakeComposerMedia() = FakeComposerMedia()

    @Provides @Singleton
    fun fakePebbleDetail() = FakePebbleDetailService()

    @Provides @Singleton
    fun fakeSouls() = FakeSoulsService()

    @Provides @Singleton
    fun fakeCollections() = FakeCollectionsService()

    @Provides @Singleton
    fun fakeConnections() = FakeConnectionsService()

    @Provides @Singleton
    fun fakeGlyph() = FakeGlyphService()

    @Provides @Singleton
    fun fakeGlyphMarket() = FakeGlyphMarketService()

    @Provides @Singleton
    fun fakeLogs() = FakeLogsService()

    @Provides @Singleton
    fun fakeSnapshotStore() = FakeComposerSnapshotStore()

    @Provides @Singleton
    fun fakeSnapWrite() = FakeSnapWriteRepository()

    // ---- The seams, bound to the same instances ----

    @Provides
    fun supabase(fake: FakeSupabaseService): SupabaseServicing = fake

    @Provides
    fun path(fake: FakePathService): PathServicing = fake

    @Provides
    fun profile(fake: FakeProfileService): ProfileServicing = fake

    @Provides
    fun pebbleWrite(fake: FakePebbleWriteService): PebbleWriteServicing = fake

    @Provides
    fun referenceData(fake: FakeReferenceDataService): ReferenceDataServicing = fake

    @Provides
    fun achievements(fake: FakeAchievementsService): AchievementsServicing = fake

    @Provides
    fun pathStats(fake: FakePathStatsService): PathStatsServicing = fake

    @Provides
    fun drafts(fake: FakePebbleDraftsService): PebbleDraftsServicing = fake

    @Provides
    fun composerMedia(fake: FakeComposerMedia): ComposerMedia = fake

    @Provides
    fun pebbleDetail(fake: FakePebbleDetailService): PebbleDetailServicing = fake

    @Provides
    fun souls(fake: FakeSoulsService): SoulsServicing = fake

    @Provides
    fun collections(fake: FakeCollectionsService): CollectionsServicing = fake

    @Provides
    fun connections(fake: FakeConnectionsService): ConnectionsServicing = fake

    @Provides
    fun glyph(fake: FakeGlyphService): GlyphServicing = fake

    @Provides
    fun glyphMarket(fake: FakeGlyphMarketService): GlyphMarketServicing = fake

    @Provides
    fun logs(fake: FakeLogsService): LogsServicing = fake

    @Provides
    fun snapshotStore(fake: FakeComposerSnapshotStore): ComposerSnapshotStoring = fake

    @Provides
    fun snapWrite(fake: FakeSnapWriteRepository): SnapWriteRepositing = fake

    /** Nothing in a UI test is worth signing; an empty URL is a failed image load, which renders the placeholder. */
    @Provides
    @Singleton
    fun snapURLCache(
        @IoApplicationScope scope: CoroutineScope,
    ): SnapURLCache =
        SnapURLCache(
            provider =
                object : SignedUrlProviding {
                    override suspend fun signedUrls(storagePrefix: String) = SnapUrls(original = "", thumb = "")
                },
            scope = scope,
            nowMillis = { 0L },
        )
}
