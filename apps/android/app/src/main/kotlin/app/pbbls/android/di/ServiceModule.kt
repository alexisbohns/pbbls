package app.pbbls.android.di

import android.content.Context
import app.pbbls.android.services.ComposerSnapshotStore
import app.pbbls.android.services.PebbleSnapRepository
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SupabaseService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * The two graph singletons that do not carry an `@Inject` constructor (#848).
 * Everything else is `@Singleton class X @Inject constructor(…)`; if you are
 * adding a service, prefer that and do not grow this file.
 *
 * Neither of these is here because Dagger *cannot* infer it — both *could*
 * carry `@Inject` and deliberately do not. That distinction matters, because
 * "needs a qualifier" is NOT a reason to add a provider: a qualifier annotation
 * on a constructor parameter is inferred like any other binding, which is how
 * `SupabaseService`, `KarmaNotificationService` and `AchievementsService` take
 * their `@ApplicationScope` scope with no provider at all.
 *
 * - [ComposerSnapshotStore] keeps the `@ApplicationContext` qualifier out of a
 *   service class that is otherwise framework-free.
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
    fun provideSnapURLCache(
        supabase: SupabaseService,
        @IoDispatcher io: CoroutineDispatcher,
    ): SnapURLCache =
        SnapURLCache(
            provider = PebbleSnapRepository(supabase),
            scope = CoroutineScope(SupervisorJob() + io),
            nowMillis = System::currentTimeMillis,
        )

    @Provides
    @Singleton
    fun provideComposerSnapshotStore(
        @ApplicationContext context: Context,
    ): ComposerSnapshotStore = ComposerSnapshotStore(context)
}
