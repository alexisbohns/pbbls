package app.pbbls.android.di

import android.content.Context
import app.pbbls.android.core.data.ComposerSnapshotStore
import app.pbbls.android.core.data.ComposerSnapshotStoring
import app.pbbls.android.core.data.PebbleSnapRepository
import app.pbbls.android.core.data.SnapURLCache
import app.pbbls.android.core.data.SnapWriteRepositing
import app.pbbls.android.core.data.SupabaseService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

/**
 * The three of this app's own service classes that do not carry an `@Inject`
 * constructor (#848). Everything else is `@Singleton class X @Inject
 * constructor(…)`; if you are adding a service, prefer that and do not grow
 * this file.
 *
 * None of these is here because Dagger *cannot* infer it — all three *could* be
 * wired through the graph and deliberately are not. That distinction matters, because
 * "needs a qualifier" is NOT a reason to add a provider: a qualifier annotation
 * on a constructor parameter is inferred like any other binding, which is how
 * `SupabaseService`, `KarmaNotificationService` and `AchievementsService` take
 * their `@ApplicationScope` scope with no provider at all.
 *
 * - [ComposerSnapshotStore] keeps the `@ApplicationContext` qualifier out of a
 *   service class that is otherwise framework-free.
 * - [PebbleSnapRepository] is assembled here because three screens used to
 *   `new` one from a `SupabaseService` they read off a CompositionLocal. Once
 *   that local carries `SupabaseServicing` (#848) they cannot, and they should
 *   not have been building a repository anyway — they take
 *   [app.pbbls.android.features.path.create.LocalSnapWriteRepository] instead.
 *   The class is stateless, so one instance serves every form.
 * - [SnapURLCache]'s real injection point is its `internal` primary
 *   constructor, which `SnapURLCacheTest` drives with a fake provider, a test
 *   scope and a fake clock. Annotating it would make the test's seam part of
 *   the graph; assembling it here keeps the seam a test concern.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun providePebbleSnapRepository(supabase: SupabaseService): PebbleSnapRepository = PebbleSnapRepository(supabase)

    @Provides
    @Singleton
    fun provideSnapURLCache(
        snaps: PebbleSnapRepository,
        @IoApplicationScope scope: CoroutineScope,
    ): SnapURLCache =
        SnapURLCache(
            provider = snaps,
            scope = scope,
            nowMillis = System::currentTimeMillis,
        )

    @Provides
    @Singleton
    fun provideComposerSnapshotStore(
        @ApplicationContext context: Context,
    ): ComposerSnapshotStore = ComposerSnapshotStore(context)

    // The two seams #849 needed for `RecordFlowViewModel`'s test. They are
    // @Provides rather than @Binds in ServiceBindings because both
    // implementations are themselves assembled here by @Provides, so there is no
    // @Inject constructor for @Binds to point at.

    @Provides
    @Singleton
    fun provideComposerSnapshotStoring(impl: ComposerSnapshotStore): ComposerSnapshotStoring = impl

    @Provides
    @Singleton
    fun provideSnapWriteRepositing(impl: PebbleSnapRepository): SnapWriteRepositing = impl
}
