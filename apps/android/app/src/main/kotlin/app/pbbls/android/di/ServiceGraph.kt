package app.pbbls.android.di

import app.pbbls.android.features.glyph.services.GlyphMarketServicing
import app.pbbls.android.features.karma.AchievementNotificationService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.services.AchievementsServicing
import app.pbbls.android.services.ConnectionsServicing
import app.pbbls.android.services.EmotionPaletteService
import app.pbbls.android.services.PathStatsServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SupabaseServicing
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TEMPORARY, and now half the size it was.
 *
 * This is, honestly, still a service locator — and it is kept on purpose with
 * its eyes open. #849 migrated every screen and took 11 of its 21 entries with
 * them; the 10 below are what is left, and each has a named reason:
 *
 * - **`palettes` (10 reads) and `referenceData` (6)** are read by *leaf*
 *   components — `PathPebbleRow`, `ValenceGlyph`, `WeekHeader`, the pickers —
 *   not by screens. They are ambient reference data every leaf needs, which is
 *   the one shape a `CompositionLocal` is actually for, and they stay
 *   deliberately (see `docs/decisions/log.md`, 2026-09-20). `snapUrls` (3) is
 *   the same category.
 * - **`glyphMarket` and `pathStats`** are read by `GlyphPickerSheet`, which
 *   keeps its own duplicated copy of the glyph store's state and is a migration
 *   of its own.
 * - **`supabase`, `connectionsService`, `karma` and `achievementNotify`** are
 *   read by `RootScreen`, the one stateful surface without a ViewModel; the two
 *   overlay hosts take the service object rather than its state.
 * - **`achievements`** is three scattered `fireCheck()` calls.
 *
 * So this file dies in **#852**, which owns all three: it rewrites `RootScreen`
 * (auth becomes a condition, not a branch), promotes `GlyphCarve` to a nav entry
 * and carries the `GlyphPickerSheet` migration and the `fireCheck()` lambdas as
 * folded-in scope. Not before — and the three ambient locals above are meant to
 * outlive it.
 *
 * **Do not copy this pattern.** New code takes its dependencies by constructor
 * (`@Inject`) or through `hiltViewModel()`. Nothing should ever inject
 * `ServiceGraph` except `MainActivity`, and nothing should add an entry to it.
 */
@Singleton
class ServiceGraph
    @Inject
    constructor(
        val supabase: SupabaseServicing,
        val palettes: EmotionPaletteService,
        val pathStats: PathStatsServicing,
        val snapUrls: SnapURLCache,
        val referenceData: ReferenceDataServicing,
        val connectionsService: ConnectionsServicing,
        val glyphMarket: GlyphMarketServicing,
        val karma: KarmaNotificationService,
        val achievementNotify: AchievementNotificationService,
        val achievements: AchievementsServicing,
    )
