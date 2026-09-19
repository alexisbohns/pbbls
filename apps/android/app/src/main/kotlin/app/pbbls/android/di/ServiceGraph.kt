package app.pbbls.android.di

import app.pbbls.android.features.glyph.services.GlyphMarketService
import app.pbbls.android.features.glyph.services.GlyphService
import app.pbbls.android.features.karma.AchievementNotificationService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.features.lab.services.LogsService
import app.pbbls.android.services.AchievementsService
import app.pbbls.android.services.CollectionsService
import app.pbbls.android.services.ComposerSnapshotStore
import app.pbbls.android.services.ConnectionsService
import app.pbbls.android.services.EmotionPaletteService
import app.pbbls.android.services.PathService
import app.pbbls.android.services.PathStatsService
import app.pbbls.android.services.PebbleDetailService
import app.pbbls.android.services.PebbleDraftsService
import app.pbbls.android.services.PebbleWriteService
import app.pbbls.android.services.ProfileService
import app.pbbls.android.services.ReferenceDataService
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SoulsService
import app.pbbls.android.services.SupabaseService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TEMPORARY. Deleted by #849.
 *
 * This is, honestly, still a service locator — and it is kept on purpose with
 * its eyes open. The screens still read `Local…Service.current` (87 call sites)
 * until #849 gives each one a ViewModel, so `MainActivity` still has to fill a
 * 20-entry `CompositionLocalProvider` from somewhere. The alternative is 20
 * `@Inject lateinit var` fields on the activity, which costs the same for the
 * same lifespan and leaves #849 unpicking twenty fields instead of deleting one
 * file.
 *
 * **Do not copy this pattern.** New code takes its dependencies by constructor
 * (`@Inject`) or, once #849 lands, through `hiltViewModel()`. Nothing should
 * ever inject `ServiceGraph` except `MainActivity`.
 */
@Singleton
class ServiceGraph
    @Inject
    constructor(
        val supabase: SupabaseService,
        val palettes: EmotionPaletteService,
        val pathService: PathService,
        val pathStats: PathStatsService,
        val profileService: ProfileService,
        val pebbleDetailService: PebbleDetailService,
        val snapUrls: SnapURLCache,
        val referenceData: ReferenceDataService,
        val pebbleWrite: PebbleWriteService,
        val soulsService: SoulsService,
        val collectionsService: CollectionsService,
        val draftsService: PebbleDraftsService,
        val connectionsService: ConnectionsService,
        val composerSnapshots: ComposerSnapshotStore,
        val glyphService: GlyphService,
        val glyphMarket: GlyphMarketService,
        val logsService: LogsService,
        val karma: KarmaNotificationService,
        val achievementNotify: AchievementNotificationService,
        val achievements: AchievementsService,
    )
