package app.pbbls.android.di

import android.content.Context
import app.pbbls.android.features.karma.AchievementNotificationService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.services.AchievementsService
import app.pbbls.android.services.ComposerSnapshotStore
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SupabaseService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The four graph singletons that do not carry an `@Inject` constructor (#848).
 * Everything else is `@Singleton class X @Inject constructor(…)`; if you are
 * adding a service, prefer that and do not grow this file.
 *
 * Two of these genuinely cannot be inferred, and two are a deliberate choice —
 * the distinction matters, because "needs a qualifier" is NOT a reason to add a
 * provider:
 *
 * - [KarmaNotificationService] and [AchievementsService] take a `scope` with a
 *   Kotlin default, and Dagger ignores defaults. These two Dagger genuinely
 *   cannot infer. Both stop needing a provider in #848 Part 2, once
 *   `@ApplicationScope` is a real binding — delete them from here then rather
 *   than leaving a provider that only restates the constructor.
 * - [ComposerSnapshotStore] and [SnapURLCache] *could* carry `@Inject` and
 *   deliberately do not. The first keeps the `@ApplicationContext` qualifier out
 *   of a service class that is otherwise framework-free; the second's real
 *   injection point is its `internal` primary constructor, which Part 2 hands a
 *   scope and a dispatcher.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideSnapURLCache(supabase: SupabaseService): SnapURLCache = SnapURLCache(supabase)

    @Provides
    @Singleton
    fun provideComposerSnapshotStore(
        @ApplicationContext context: Context,
    ): ComposerSnapshotStore = ComposerSnapshotStore(context)

    @Provides
    @Singleton
    fun provideKarmaNotificationService(): KarmaNotificationService = KarmaNotificationService()

    @Provides
    @Singleton
    fun provideAchievementsService(
        supabase: SupabaseService,
        notify: AchievementNotificationService,
    ): AchievementsService = AchievementsService(supabase, notify)
}
