package app.pbbls.android.di

import app.pbbls.android.features.path.AndroidComposerMedia
import app.pbbls.android.features.path.ComposerMedia
import app.pbbls.android.services.AchievementsService
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.PathService
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PathStatsService
import app.pbbls.android.services.PathStatsServicing
import app.pbbls.android.services.PebbleDetailService
import app.pbbls.android.services.PebbleDetailServicing
import app.pbbls.android.services.PebbleDraftsService
import app.pbbls.android.services.PebbleDraftsServicing
import app.pbbls.android.services.PebbleWriteService
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileService
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataService
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SupabaseService
import app.pbbls.android.services.SupabaseServicing
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Interface → implementation for the services a test needs to fake (#848).
 *
 * The standing rule is "extract a `…Servicing` interface for a fake only when a
 * test needs one" (`apps/android/CLAUDE.md`), so this list grows one entry at a
 * time: #848 landed the first five, and #849 adds one per screen as that screen
 * gets a ViewModel and a test — `AchievementsServicing` is the first of them.
 * Adding an interface here with no fake and no test behind it is the thing that
 * rule exists to prevent.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ServiceBindings {
    @Binds
    @Singleton
    fun bindSupabaseServicing(impl: SupabaseService): SupabaseServicing

    @Binds
    @Singleton
    fun bindPathServicing(impl: PathService): PathServicing

    @Binds
    @Singleton
    fun bindProfileServicing(impl: ProfileService): ProfileServicing

    @Binds
    @Singleton
    fun bindPebbleWriteServicing(impl: PebbleWriteService): PebbleWriteServicing

    @Binds
    @Singleton
    fun bindReferenceDataServicing(impl: ReferenceDataService): ReferenceDataServicing

    @Binds
    @Singleton
    fun bindAchievementsServicing(impl: AchievementsService): AchievementsServicing

    @Binds
    @Singleton
    fun bindPathStatsServicing(impl: PathStatsService): PathStatsServicing

    @Binds
    @Singleton
    fun bindPebbleDraftsServicing(impl: PebbleDraftsService): PebbleDraftsServicing

    @Binds
    @Singleton
    fun bindComposerMedia(impl: AndroidComposerMedia): ComposerMedia

    @Binds
    @Singleton
    fun bindPebbleDetailServicing(impl: PebbleDetailService): PebbleDetailServicing
}
