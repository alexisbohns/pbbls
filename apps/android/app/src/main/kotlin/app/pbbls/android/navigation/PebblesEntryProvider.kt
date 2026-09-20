package app.pbbls.android.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pbbls.android.features.connections.ConnectionsScreen
import app.pbbls.android.features.connections.InviteScreen
import app.pbbls.android.features.glyph.carve.GlyphCarveScreen
import app.pbbls.android.features.glyph.store.GlyphsListScreen
import app.pbbls.android.features.lab.LabScreen
import app.pbbls.android.features.path.PathScreen
import app.pbbls.android.features.profile.AchievementsScreen
import app.pbbls.android.features.profile.CollectionDetailScreen
import app.pbbls.android.features.profile.CollectionFormScreen
import app.pbbls.android.features.profile.CollectionsListScreen
import app.pbbls.android.features.profile.ProfileScreen
import app.pbbls.android.features.profile.SettingsScreen
import app.pbbls.android.features.profile.SoulDetailScreen
import app.pbbls.android.features.profile.SoulFormScreen
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
            onOpenSettings = { navigator.navigate(PebblesKey.Settings) },
            onCreateCollection = { navigator.navigate(PebblesKey.CollectionForm()) },
        )
    }

    entry<PebblesKey.People>(metadata = NavTransitions.forKey(PebblesKey.People)) {
        SoulsListScreen(
            onBack = navigator::goBack,
            onOpenSoul = { navigator.navigate(PebblesKey.SoulDetail(it.id)) },
            onCreateSoul = { navigator.navigate(PebblesKey.SoulForm()) },
        )
    }

    entry<PebblesKey.Collections>(metadata = NavTransitions.forKey(PebblesKey.Collections)) {
        CollectionsListScreen(
            onBack = navigator::goBack,
            onOpenCollection = { navigator.navigate(PebblesKey.CollectionDetail(it.id)) },
            onCreateCollection = { navigator.navigate(PebblesKey.CollectionForm()) },
        )
    }

    entry<PebblesKey.SoulDetail>(metadata = NavTransitions.forKey(PebblesKey.SoulDetail(""))) { key ->
        SoulDetailScreen(
            soulId = key.soulId,
            onBack = navigator::goBack,
            onEditSoul = { navigator.navigate(PebblesKey.SoulForm(key.soulId)) },
        )
    }

    entry<PebblesKey.CollectionDetail>(metadata = NavTransitions.forKey(PebblesKey.CollectionDetail(""))) { key ->
        CollectionDetailScreen(
            collectionId = key.collectionId,
            onBack = navigator::goBack,
            onEditCollection = { navigator.navigate(PebblesKey.CollectionForm(key.collectionId)) },
        )
    }

    entry<PebblesKey.Connections>(metadata = NavTransitions.forKey(PebblesKey.Connections)) {
        ConnectionsScreen(
            onDismiss = navigator::goBack,
            onOpenInvite = { navigator.navigate(PebblesKey.Invite) },
        )
    }

    entry<PebblesKey.Glyphs>(metadata = NavTransitions.forKey(PebblesKey.Glyphs)) {
        GlyphsListScreen(
            onBack = navigator::goBack,
            onCarve = { navigator.navigate(PebblesKey.GlyphCarve) },
        )
    }

    entry<PebblesKey.Lab>(metadata = NavTransitions.forKey(PebblesKey.Lab)) {
        LabScreen(onBack = navigator::goBack)
    }

    entry<PebblesKey.Achievements>(metadata = NavTransitions.forKey(PebblesKey.Achievements)) {
        AchievementsScreen(onBack = navigator::goBack)
    }

    // ---- Modal covers promoted to entries (#852 Task 17) ----

    entry<PebblesKey.SoulForm>(metadata = NavTransitions.forKey(PebblesKey.SoulForm())) { key ->
        SoulFormScreen(soulId = key.soulId, onDismiss = navigator::goBack, onSaved = navigator::goBack)
    }

    entry<PebblesKey.CollectionForm>(metadata = NavTransitions.forKey(PebblesKey.CollectionForm())) { key ->
        CollectionFormScreen(
            collectionId = key.collectionId,
            onDismiss = navigator::goBack,
            onSaved = navigator::goBack,
        )
    }

    entry<PebblesKey.Settings>(metadata = NavTransitions.forKey(PebblesKey.Settings)) {
        SettingsScreen(
            onDismiss = navigator::goBack,
            // The saved values used to be folded straight into ProfileViewModel's
            // state; an entry has no such handle back to its parent, so the
            // parent picks them up on its own resume refresh instead (#852).
            onSaved = { _, _, _, _ -> navigator.goBack() },
        )
    }

    entry<PebblesKey.GlyphCarve>(metadata = NavTransitions.forKey(PebblesKey.GlyphCarve)) {
        GlyphCarveScreen(onSaved = { navigator.goBack() }, onCancel = navigator::goBack)
    }

    entry<PebblesKey.Invite>(metadata = NavTransitions.forKey(PebblesKey.Invite)) {
        InviteScreen(onDismiss = navigator::goBack)
    }
}
