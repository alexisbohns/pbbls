package app.pbbls.android.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pbbls.android.features.connections.ConnectionsScreen
import app.pbbls.android.features.glyph.store.GlyphsListScreen
import app.pbbls.android.features.lab.LabScreen
import app.pbbls.android.features.path.PathScreen
import app.pbbls.android.features.profile.AchievementsScreen
import app.pbbls.android.features.profile.CollectionDetailScreen
import app.pbbls.android.features.profile.CollectionsListScreen
import app.pbbls.android.features.profile.ProfileScreen
import app.pbbls.android.features.profile.SoulDetailScreen
import app.pbbls.android.features.profile.SoulsListScreen

/**
 * Key → screen (#852).
 *
 * Part 1 wires exactly what the two NavHosts reached, with the same IA, so this
 * PR is a move and not a redesign (D11). The modal keys in [PebblesKey] are
 * declared but not yet wired: their covers still live inside their parent
 * screens until Parts 3–5 promote them.
 *
 * Each `entry` carries its transition metadata via [NavTransitions.forKey], so
 * the animation travels with the key rather than living in a `when` at the
 * display.
 */
fun EntryProviderScope<NavKey>.pebblesEntries(
    navigator: Navigator,
    onSignOut: () -> Unit,
) {
    entry<PebblesKey.Path>(metadata = NavTransitions.forKey(PebblesKey.Path)) {
        PathScreen(onProfile = { navigator.navigate(PebblesKey.You) })
    }

    entry<PebblesKey.You>(metadata = NavTransitions.forKey(PebblesKey.You)) {
        ProfileScreen(
            onBack = navigator::goBack,
            onSignOut = onSignOut,
            onOpenSouls = { navigator.navigate(PebblesKey.People) },
            onOpenCollections = { navigator.navigate(PebblesKey.Collections) },
            onOpenCollection = { navigator.navigate(PebblesKey.CollectionDetail(it.id)) },
            onOpenGlyphs = { navigator.navigate(PebblesKey.Glyphs) },
            onOpenConnections = { navigator.navigate(PebblesKey.Connections) },
            onOpenLab = { navigator.navigate(PebblesKey.Lab) },
            onOpenAchievements = { navigator.navigate(PebblesKey.Achievements) },
        )
    }

    entry<PebblesKey.People>(metadata = NavTransitions.forKey(PebblesKey.People)) {
        SoulsListScreen(
            onBack = navigator::goBack,
            onOpenSoul = { navigator.navigate(PebblesKey.SoulDetail(it.id)) },
        )
    }

    entry<PebblesKey.Collections>(metadata = NavTransitions.forKey(PebblesKey.Collections)) {
        CollectionsListScreen(
            onBack = navigator::goBack,
            onOpenCollection = { navigator.navigate(PebblesKey.CollectionDetail(it.id)) },
        )
    }

    entry<PebblesKey.SoulDetail>(metadata = NavTransitions.forKey(PebblesKey.SoulDetail(""))) { key ->
        SoulDetailScreen(soulId = key.soulId, onBack = navigator::goBack)
    }

    entry<PebblesKey.CollectionDetail>(metadata = NavTransitions.forKey(PebblesKey.CollectionDetail(""))) { key ->
        CollectionDetailScreen(collectionId = key.collectionId, onBack = navigator::goBack)
    }

    entry<PebblesKey.Connections>(metadata = NavTransitions.forKey(PebblesKey.Connections)) {
        ConnectionsScreen(onDismiss = navigator::goBack)
    }

    entry<PebblesKey.Glyphs>(metadata = NavTransitions.forKey(PebblesKey.Glyphs)) {
        GlyphsListScreen(onBack = navigator::goBack)
    }

    entry<PebblesKey.Lab>(metadata = NavTransitions.forKey(PebblesKey.Lab)) {
        LabScreen(onBack = navigator::goBack)
    }

    entry<PebblesKey.Achievements>(metadata = NavTransitions.forKey(PebblesKey.Achievements)) {
        AchievementsScreen(onBack = navigator::goBack)
    }
}
