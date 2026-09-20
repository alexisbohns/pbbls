package app.pbbls.android.di

import app.pbbls.android.features.glyph.services.GlyphMarketService
import app.pbbls.android.features.glyph.services.GlyphService
import app.pbbls.android.features.karma.AchievementNotificationService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.features.lab.services.LogsService
import app.pbbls.android.features.path.valence.ValencePrewarmer
import app.pbbls.android.features.pebblemedia.SnapProcessor
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.ComposerSnapshotStoring
import app.pbbls.android.services.ConnectionsServicing
import app.pbbls.android.services.EmotionPaletteService
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PathStatsServicing
import app.pbbls.android.services.PebbleDetailServicing
import app.pbbls.android.services.PebbleDraftsServicing
import app.pbbls.android.services.PebbleSnapRepository
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SupabaseServicing
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TEMPORARY. Deleted by #849.
 *
 * This is, honestly, still a service locator — and it is kept on purpose with
 * its eyes open. The screens still read `Local…Service.current` (85 call sites,
 * down from 87) until #849 gives each one a ViewModel, so `MainActivity` still
 * has to fill a 21-entry `CompositionLocalProvider` from somewhere. The
 * alternative is as many `@Inject lateinit var` fields on the activity, which
 * costs the same for the same lifespan and leaves #849 unpicking twenty-odd
 * fields instead of deleting one file.
 *
 * It shrinks as screens migrate: #849's souls/collections part deleted
 * `LocalSoulsService` and `LocalCollectionsService` outright, because after the
 * migration nothing read them.
 *
 * **Do not copy this pattern.** New code takes its dependencies by constructor
 * (`@Inject`) or, once #849 lands, through `hiltViewModel()`. Nothing should
 * ever inject `ServiceGraph` except `MainActivity`.
 */
@Singleton
class ServiceGraph
    @Inject
    constructor(
        val supabase: SupabaseServicing,
        val palettes: EmotionPaletteService,
        val pathService: PathServicing,
        val pathStats: PathStatsServicing,
        val profileService: ProfileServicing,
        val pebbleDetailService: PebbleDetailServicing,
        val snapUrls: SnapURLCache,
        val snapWrites: PebbleSnapRepository,
        val referenceData: ReferenceDataServicing,
        val pebbleWrite: PebbleWriteServicing,
        val draftsService: PebbleDraftsServicing,
        val connectionsService: ConnectionsServicing,
        val composerSnapshots: ComposerSnapshotStoring,
        val glyphService: GlyphService,
        val glyphMarket: GlyphMarketService,
        val logsService: LogsService,
        val karma: KarmaNotificationService,
        val achievementNotify: AchievementNotificationService,
        val achievements: AchievementsServicing,
        val snapProcessor: SnapProcessor,
        val valencePrewarmer: ValencePrewarmer,
    )
