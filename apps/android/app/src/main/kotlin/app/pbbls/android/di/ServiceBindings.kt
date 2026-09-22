package app.pbbls.android.di

import app.pbbls.android.core.data.AchievementsService
import app.pbbls.android.core.data.AchievementsServicing
import app.pbbls.android.core.data.CollectionsService
import app.pbbls.android.core.data.CollectionsServicing
import app.pbbls.android.core.data.ConnectionsService
import app.pbbls.android.core.data.ConnectionsServicing
import app.pbbls.android.core.data.GlyphMarketService
import app.pbbls.android.core.data.GlyphMarketServicing
import app.pbbls.android.core.data.GlyphService
import app.pbbls.android.core.data.GlyphServicing
import app.pbbls.android.core.data.LogsService
import app.pbbls.android.core.data.LogsServicing
import app.pbbls.android.core.data.PathService
import app.pbbls.android.core.data.PathServicing
import app.pbbls.android.core.data.PathStatsService
import app.pbbls.android.core.data.PathStatsServicing
import app.pbbls.android.core.data.PebbleDetailService
import app.pbbls.android.core.data.PebbleDetailServicing
import app.pbbls.android.core.data.PebbleDraftsService
import app.pbbls.android.core.data.PebbleDraftsServicing
import app.pbbls.android.core.data.PebbleWriteService
import app.pbbls.android.core.data.PebbleWriteServicing
import app.pbbls.android.core.data.ProfileService
import app.pbbls.android.core.data.ProfileServicing
import app.pbbls.android.core.data.ReferenceDataService
import app.pbbls.android.core.data.ReferenceDataServicing
import app.pbbls.android.core.data.SoulsService
import app.pbbls.android.core.data.SoulsServicing
import app.pbbls.android.core.data.SupabaseService
import app.pbbls.android.core.data.SupabaseServicing
import app.pbbls.android.features.path.AndroidComposerMedia
import app.pbbls.android.features.path.ComposerMedia
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

    @Binds
    @Singleton
    fun bindSoulsServicing(impl: SoulsService): SoulsServicing

    @Binds
    @Singleton
    fun bindCollectionsServicing(impl: CollectionsService): CollectionsServicing

    @Binds
    @Singleton
    fun bindConnectionsServicing(impl: ConnectionsService): ConnectionsServicing

    @Binds
    @Singleton
    fun bindGlyphServicing(impl: GlyphService): GlyphServicing

    @Binds
    @Singleton
    fun bindGlyphMarketServicing(impl: GlyphMarketService): GlyphMarketServicing

    @Binds
    @Singleton
    fun bindLogsServicing(impl: LogsService): LogsServicing
}
