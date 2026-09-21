package app.pbbls.android.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pbbls.android.features.auth.AuthMode
import app.pbbls.android.features.auth.AuthScreen
import app.pbbls.android.features.connections.AcceptInviteScreen
import app.pbbls.android.features.connections.ConnectionsScreen
import app.pbbls.android.features.connections.InviteScreen
import app.pbbls.android.features.glyph.carve.GlyphCarveScreen
import app.pbbls.android.features.glyph.store.GlyphsListScreen
import app.pbbls.android.features.lab.AnnouncementDetailScreen
import app.pbbls.android.features.lab.LabScreen
import app.pbbls.android.features.lab.LogListMode
import app.pbbls.android.features.lab.LogListScreen
import app.pbbls.android.features.onboarding.OnboardingScreen
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.path.DraftsScreen
import app.pbbls.android.features.path.EditPebbleScreen
import app.pbbls.android.features.path.PathScreen
import app.pbbls.android.features.path.PebbleDetailScreen
import app.pbbls.android.features.path.create.CreatePebbleScreen
import app.pbbls.android.features.path.record.RecordFlowScreen
import app.pbbls.android.features.profile.AchievementsScreen
import app.pbbls.android.features.profile.CollectionDetailScreen
import app.pbbls.android.features.profile.CollectionFormScreen
import app.pbbls.android.features.profile.CollectionsListScreen
import app.pbbls.android.features.profile.ProfileScreen
import app.pbbls.android.features.profile.SettingsScreen
import app.pbbls.android.features.profile.SoulDetailScreen
import app.pbbls.android.features.profile.SoulFormScreen
import app.pbbls.android.features.profile.SoulsListScreen
import app.pbbls.android.features.welcome.WelcomeScreen

/**
 * Key → screen (#852).
 *
 * Part 1 wired exactly what the two NavHosts reached, with the same IA, so
 * that PR was a move and not a redesign (D11). Part 3 promoted the
 * five write-path covers (detail, edit, both composers, drafts). * folds in the funnel (`Welcome`, `Auth`) that used to live in its own
 * `NavDisplay`, plus `Onboarding` and `AcceptInvite` — the last two were
 * previously composed outside any back stack as `RootScreen` overlays; now
 * they are ordinary entries `RootViewModel`-driven navigation pushes onto
 * this one stack (design §5, D8).
 *
 * Each `entry` carries its transition metadata via [NavTransitions.forKey], so
 * the animation travels with the key rather than living in a `when` at the
 * display.
 */
fun EntryProviderScope<NavKey>.pebblesEntries(
    navigator: Navigator,
    onSignOut: () -> Unit,
    welcomeContentRevealed: Boolean,
    onOnboardingFinished: () -> Unit,
) {
    entry<PebblesKey.Path>(metadata = NavTransitions.forKey(PebblesKey.Path)) {
        PathScreen(
            onOpenDetail = { pebbleId -> navigator.navigate(PebblesKey.PebbleDetail(pebbleId)) },
            onOpenDrafts = { navigator.navigate(PebblesKey.Drafts) },
            onCreatePebble = { navigator.navigate(PebblesKey.RecordFlow()) },
            onCreatePebbleLongPress = { navigator.navigate(PebblesKey.CreatePebble()) },
        )
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
        LabScreen(
            onBack = navigator::goBack,
            onOpenAnnouncement = { navigator.navigate(PebblesKey.LabAnnouncement(it)) },
            onSeeAll = { navigator.navigate(PebblesKey.LabLogList(it.name)) },
        )
    }

    entry<PebblesKey.Achievements>(metadata = NavTransitions.forKey(PebblesKey.Achievements)) {
        AchievementsScreen(onBack = navigator::goBack)
    }

    // ---- Modal covers promoted to entries (#852) ----

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

    // ---- Lab's content swaps promoted to entries (#852) ----

    entry<PebblesKey.LabAnnouncement>(metadata = NavTransitions.forKey(PebblesKey.LabAnnouncement(""))) { key ->
        AnnouncementDetailScreen(logId = key.logId, onBack = navigator::goBack)
    }

    entry<PebblesKey.LabLogList>(
        metadata = NavTransitions.forKey(PebblesKey.LabLogList(LogListMode.CHANGELOG.name)),
    ) { key ->
        LogListScreen(mode = key.mode, onBack = navigator::goBack)
    }

    // ---- The write path promoted to entries (#852) ----

    entry<PebblesKey.PebbleDetail>(metadata = NavTransitions.forKey(PebblesKey.PebbleDetail(""))) { key ->
        PebbleDetailScreen(
            pebbleId = key.pebbleId,
            onDismiss = navigator::goBack,
            onEditRequested = { navigator.navigate(PebblesKey.EditPebble(key.pebbleId)) },
        )
    }

    entry<PebblesKey.EditPebble>(metadata = NavTransitions.forKey(PebblesKey.EditPebble(""))) { key ->
        EditPebbleScreen(
            pebbleId = key.pebbleId,
            onDismiss = navigator::goBack,
            // Popping back to the still-open detail entry is the reveal — its
            // own resume refresh (mirroring PathViewModel.onResumed) is what
            // re-reads the pebble (M58 D5, #852).
            onSaved = navigator::goBack,
        )
    }

    entry<PebblesKey.RecordFlow>(metadata = NavTransitions.forKey(PebblesKey.RecordFlow())) { key ->
        RecordFlowScreen(
            resumeDraftId = key.resumeDraftId,
            // The flow deliberately does NOT reveal the pebble through the
            // detail entry: the user has just spent ten screens on it and the
            // success step already showed it (M58 D10). Just pop; Path's own
            // resume refresh picks up the new pebble.
            onPublished = { navigator.goBack() },
            onDismiss = navigator::goBack,
        )
    }

    entry<PebblesKey.CreatePebble>(metadata = NavTransitions.forKey(PebblesKey.CreatePebble())) { key ->
        CreatePebbleScreen(
            resumeDraftId = key.resumeDraftId,
            // The form DOES reveal the new pebble through the detail entry
            // (M58 D10) — pop the composer, then push detail on what is left.
            onCreated = { pebbleId ->
                navigator.goBack()
                navigator.navigate(PebblesKey.PebbleDetail(pebbleId))
            },
            onCancel = navigator::goBack,
        )
    }

    entry<PebblesKey.Drafts>(metadata = NavTransitions.forKey(PebblesKey.Drafts)) {
        DraftsScreen(
            // Resuming a draft leaves the drafts list rather than stacking the
            // flow over it (#852 supersedes the cover-stacking of M47/M58): pop
            // Drafts, then enter the flow already seeded with this draft's id.
            onResume = { record ->
                navigator.goBack()
                navigator.navigate(PebblesKey.RecordFlow(resumeDraftId = record.id))
            },
            onDismiss = navigator::goBack,
        )
    }

    // ---- The unauthenticated funnel promoted to entries (#852) ----

    entry<PebblesKey.Welcome>(metadata = NavTransitions.forKey(PebblesKey.Welcome)) {
        WelcomeScreen(
            contentRevealed = welcomeContentRevealed,
            onCreateAccount = { navigator.navigate(PebblesKey.Auth(AuthMode.SIGNUP)) },
            onLogin = { navigator.navigate(PebblesKey.Auth(AuthMode.LOGIN)) },
        )
    }

    entry<PebblesKey.Auth>(metadata = NavTransitions.forKey(PebblesKey.Auth(AuthMode.LOGIN))) { key ->
        AuthScreen(initialMode = key.mode)
    }

    // ---- Onboarding + the pending-invite accept surface (#852) ----
    // Both used to be `RootScreen` overlays composed outside any back stack;
    // now `RootViewModel`'s destination/pendingInvite state pushes them onto
    // this one stack instead (design §5, D8).

    entry<PebblesKey.Onboarding>(metadata = NavTransitions.forKey(PebblesKey.Onboarding)) {
        OnboardingScreen(steps = OnboardingSteps.all, onFinish = onOnboardingFinished)
    }

    entry<PebblesKey.AcceptInvite>(metadata = NavTransitions.forKey(PebblesKey.AcceptInvite(""))) { key ->
        AcceptInviteScreen(token = key.token, onDismiss = navigator::goBack)
    }
}
