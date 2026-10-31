package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.LocalListDetailSceneScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import app.pbbls.android.core.model.AuthMode
import app.pbbls.android.core.model.GlyphShelf
import app.pbbls.android.core.ui.GlyphPickerSlot
import app.pbbls.android.core.ui.HomeMonthSlot
import app.pbbls.android.core.ui.HomeWrapLineSlot
import app.pbbls.android.core.ui.HomeWrapSlot
import app.pbbls.android.features.achievements.AchievementsScreen
import app.pbbls.android.features.auth.AuthScreen
import app.pbbls.android.features.connections.AcceptInviteScreen
import app.pbbls.android.features.connections.ConnectionsScreen
import app.pbbls.android.features.connections.InviteScreen
import app.pbbls.android.features.glyph.carve.GlyphCarveScreen
import app.pbbls.android.features.glyph.picker.GlyphPickerContent
import app.pbbls.android.features.glyph.store.GlyphDetailScreen
import app.pbbls.android.features.glyph.store.GlyphsListScreen
import app.pbbls.android.features.home.HomeScreen
import app.pbbls.android.features.home.guides.GuideScreen
import app.pbbls.android.features.home.wraps.HomeWrapScreen
import app.pbbls.android.features.lab.AnnouncementDetailScreen
import app.pbbls.android.features.lab.LabScreen
import app.pbbls.android.features.lab.LogListMode
import app.pbbls.android.features.lab.LogListScreen
import app.pbbls.android.features.onboarding.OnboardingScreen
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.path.EditMomentScreen
import app.pbbls.android.features.path.EditPebbleScreen
import app.pbbls.android.features.path.PathScreen
import app.pbbls.android.features.path.PebbleDetailScreen
import app.pbbls.android.features.path.capture.CaptureFlowScreen
import app.pbbls.android.features.path.complete.CompleteFlowScreen
import app.pbbls.android.features.path.wrap.month.HomeMonthGrid
import app.pbbls.android.features.path.wrap.month.HomeWrapPage
import app.pbbls.android.features.path.wrap.month.homeWrapLine
import app.pbbls.android.features.profile.CollectionDetailScreen
import app.pbbls.android.features.profile.CollectionFormScreen
import app.pbbls.android.features.profile.CollectionsListScreen
import app.pbbls.android.features.profile.ProfileScreen
import app.pbbls.android.features.profile.SettingsScreen
import app.pbbls.android.features.profile.SoulDetailScreen
import app.pbbls.android.features.profile.SoulFormScreen
import app.pbbls.android.features.profile.SoulsListScreen
import app.pbbls.android.features.welcome.WelcomeScreen
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

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
    welcomeContentRevealed: () -> Boolean,
    onOnboardingFinished: () -> Unit,
) {
    entry<PebblesKey.Home>(metadata = NavTransitions.forKey(PebblesKey.Home)) {
        val reselected = remember(navigator) { navigator.reselectEvents.filter { it == PebblesKey.Home }.map {} }
        // The camera and quick moments are Home's own; New pebble chains Capture into Complete (#1383 D7).
        HomeScreen(
            reselected = reselected,
            monthSlot = homeMonth,
            onOpenPath = { navigator.navigate(PebblesKey.Path) },
            onPebble = { navigator.navigate(PebblesKey.Capture(chainsComplete = true)) },
            onOpenComplete = { navigator.navigate(PebblesKey.CompletePebble(it)) },
            onNameMoment = { navigator.navigate(PebblesKey.EditMoment(it)) },
            // The first five's tab tasks (D11): their intent is taken by the tab's root, so it opens there.
            onOpenCollections = { navigator.openAtRoot(PebblesKey.Collections) },
            onOpenPeople = { navigator.openAtRoot(PebblesKey.People) },
            onOpenGlyphs = {
                navigator.openAtRoot(PebblesKey.You)
                navigator.navigate(PebblesKey.Glyphs(GlyphShelf.MINE))
            },
            onOpenWrap = { month -> navigator.navigate(PebblesKey.HomeWrap(month.toString())) },
            onOpenGuide = { slug -> navigator.navigate(PebblesKey.Guide(slug)) },
        )
    }

    // A page pushed over Home, the bar kept (#1383 D13).
    entry<PebblesKey.Guide>(metadata = NavTransitions.forKey(PebblesKey.Guide(""))) { key ->
        GuideScreen(slug = key.slug, onBack = navigator::goBack)
    }

    // A docked sheet over Home on every window (#1383 D12): Home has no list-detail pair to make it a pane.
    entry<PebblesKey.HomeWrap>(
        metadata = NavTransitions.push + BottomSheetSceneStrategy.bottomSheet(),
    ) { key ->
        HomeWrapScreen(
            month = key.month,
            wrapSlot = homeWrapSlot,
            wrapLine = homeWrapLineSlot,
            onClose = navigator::goBack,
            onOpenPath = {
                navigator.goBack()
                navigator.navigate(PebblesKey.Path)
            },
        )
    }

    entry<PebblesKey.Path>(
        metadata = NavTransitions.forKey(PebblesKey.Path) + PanePairs.metadataFor(PebblesKey.Path),
    ) {
        PathScreen(
            onOpenDetail = { momentId, face -> navigator.navigateToDetail(PebblesKey.PebbleDetail(momentId, face)) },
            onCreatePebble = { navigator.navigate(PebblesKey.Capture()) },
            onDeleteConfirmed = { navigator.closeMomentDetail(it) },
        )
    }

    entry<PebblesKey.You>(metadata = NavTransitions.forKey(PebblesKey.You)) {
        ProfileScreen(
            onOpenGlyphs = { navigator.navigate(PebblesKey.Glyphs(it)) },
            onOpenLab = { navigator.navigate(PebblesKey.Lab) },
            onOpenAnnouncement = { navigator.navigate(PebblesKey.LabAnnouncement(it)) },
            onOpenAchievements = { navigator.navigate(PebblesKey.Achievements) },
            onOpenSettings = { navigator.navigate(PebblesKey.Settings) },
        )
    }

    entry<PebblesKey.People>(
        metadata = NavTransitions.forKey(PebblesKey.People) + PanePairs.metadataFor(PebblesKey.People),
    ) {
        SoulsListScreen(
            onBack = navigator::goBack,
            onOpenSoul = { navigator.navigateToDetail(PebblesKey.SoulDetail(it.id)) },
            onCreateSoul = { navigator.navigate(PebblesKey.SoulForm()) },
            onOpenCircle = { navigator.navigate(PebblesKey.Connections) },
            onDeleteConfirmed = { navigator.closeDetail(PebblesKey.SoulDetail(it)) },
        )
    }

    entry<PebblesKey.Collections>(
        metadata = NavTransitions.forKey(PebblesKey.Collections) + PanePairs.metadataFor(PebblesKey.Collections),
    ) {
        CollectionsListScreen(
            onBack = navigator::goBack,
            onOpenCollection = { navigator.navigateToDetail(PebblesKey.CollectionDetail(it.id)) },
            onCreateCollection = { navigator.navigate(PebblesKey.CollectionForm()) },
            onDeleteConfirmed = { navigator.closeDetail(PebblesKey.CollectionDetail(it)) },
        )
    }

    entry<PebblesKey.SoulDetail>(
        metadata = NavTransitions.forKey(PebblesKey.SoulDetail("")) + PanePairs.metadataFor<PebblesKey.SoulDetail>(),
    ) { key ->
        SoulDetailScreen(
            soulId = key.soulId,
            onBack = navigator::goBack,
            onEditSoul = { navigator.navigate(PebblesKey.SoulForm(key.soulId)) },
            // The moment's read page, not the edit form (R9).
            onOpenMoment = { navigator.navigate(PebblesKey.PebbleDetail(it)) },
            showBack = !isInListDetailPane(),
        )
    }

    entry<PebblesKey.CollectionDetail>(
        metadata = NavTransitions.forKey(PebblesKey.CollectionDetail("")) + PanePairs.metadataFor<PebblesKey.CollectionDetail>(),
    ) { key ->
        CollectionDetailScreen(
            collectionId = key.collectionId,
            onBack = navigator::goBack,
            onEditCollection = { navigator.navigate(PebblesKey.CollectionForm(key.collectionId)) },
            // The moment's read page, not the edit form (R9).
            onOpenMoment = { navigator.navigate(PebblesKey.PebbleDetail(it)) },
            showBack = !isInListDetailPane(),
        )
    }

    entry<PebblesKey.Connections>(metadata = NavTransitions.forKey(PebblesKey.Connections)) {
        ConnectionsScreen(
            onDismiss = navigator::goBack,
            onOpenInvite = { navigator.navigate(PebblesKey.Invite) },
        )
    }

    entry<PebblesKey.Glyphs>(
        metadata = NavTransitions.forKey(PebblesKey.Glyphs()) + PanePairs.metadataFor<PebblesKey.Glyphs>(),
    ) { key ->
        GlyphsListScreen(
            initialTab = key.tab,
            onBack = navigator::goBack,
            onCarve = { navigator.navigate(PebblesKey.GlyphCarve) },
            onOpenGlyph = { navigator.navigateToDetail(PebblesKey.GlyphDetail(it)) },
            isInListPane = isInListDetailPane(),
        )
    }

    // A sheet on phones, a pane beside the store on large screens (#940). The
    // store and its placeholder are already a list-detail scene there, so
    // opening a glyph stays inside one scene and the push spec never plays;
    // it is the motion for the scene boundaries that remain.
    //
    // The content key is the glyph id. The default is the key's toString(),
    // which here is the whole grid item, every stroke's path data included, and
    // it keys the saved state, the view model store and the sheet scene.
    entry<PebblesKey.GlyphDetail>(
        clazzContentKey = { key -> "glyph-detail:${key.item.id}" },
        metadata = NavTransitions.push + PanePairs.metadataFor<PebblesKey.GlyphDetail>(),
    ) { key ->
        GlyphDetailScreen(item = key.item)
    }

    entry<PebblesKey.Lab>(metadata = NavTransitions.forKey(PebblesKey.Lab)) {
        LabScreen(
            onBack = navigator::goBack,
            onOpenAnnouncement = { navigator.navigate(PebblesKey.LabAnnouncement(it)) },
            onSeeAll = { navigator.navigate(PebblesKey.LabLogList(it.name)) },
        )
    }

    entry<PebblesKey.Achievements>(metadata = NavTransitions.forKey(PebblesKey.Achievements)) {
        AchievementsScreen(
            onBack = navigator::goBack,
            // A sheet's row opens its pebble: the held card, as the soul and collection details do.
            onOpenMoment = { navigator.navigate(PebblesKey.PebbleDetail(it)) },
        )
    }

    // ---- Modal covers promoted to entries (#852) ----

    entry<PebblesKey.SoulForm>(metadata = NavTransitions.forKey(PebblesKey.SoulForm())) { key ->
        SoulFormScreen(
            soulId = key.soulId,
            onDismiss = navigator::goBack,
            onSaved = navigator::goBack,
            glyphPicker = glyphPicker,
        )
    }

    entry<PebblesKey.CollectionForm>(metadata = NavTransitions.forKey(PebblesKey.CollectionForm())) { key ->
        CollectionFormScreen(
            collectionId = key.collectionId,
            onDismiss = navigator::goBack,
            onSaved = navigator::goBack,
            glyphPicker = glyphPicker,
        )
    }

    entry<PebblesKey.Settings>(metadata = NavTransitions.forKey(PebblesKey.Settings)) {
        SettingsScreen(
            onDismiss = navigator::goBack,
            // The saved values used to be folded straight into ProfileViewModel's
            // state; an entry has no such handle back to its parent, so the
            // parent picks them up on its own resume refresh instead (#852).
            onSaved = { _, _, _, _ -> navigator.goBack() },
            glyphPicker = glyphPicker,
            onSignOut = onSignOut,
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

    entry<PebblesKey.PebbleDetail>(
        // One moment is one entry whatever face was asked for, and however often: beside
        // Path, pressing the open moment's stone after its card replaces the key with the
        // verso (pressing either again, with a new request), and the default content key
        // (the whole key) would reload the page, a new ViewModel and a fade-through, where
        // the card should turn in place (pebble card spec §Behaviour).
        clazzContentKey = { key -> "PebbleDetail:${key.momentId}" },
        // Fade-through, not the push BarKeys get: beside Path this is a scene
        // change, not a lateral move (#940). It also carries the phone page
        // (#1135), while the card itself flies from its slot on the Spine
        // (`sharedCardBounds`, provided only on one pane by RootScreen).
        metadata = NavTransitions.split + PanePairs.metadataFor<PebblesKey.PebbleDetail>(),
    ) { key ->
        PebbleDetailScreen(
            momentId = key.momentId,
            requestedFace = key.face,
            faceRequest = key.request,
            onEditMoment = { navigator.navigate(PebblesKey.EditMoment(key.momentId)) },
            onEditPebble = { navigator.navigate(PebblesKey.EditPebble(key.momentId)) },
            onMomentDeleted = { navigator.closeMomentDetail(key.momentId) },
            onCompleteRequested = { navigator.navigate(PebblesKey.CompletePebble(key.momentId)) },
            showBack = !isInListDetailPane(),
            onBack = navigator::goBack,
        )
    }

    // Edit moment and Edit pebble (R5): popping back to the still-open read
    // page is the reveal — its own resume refresh (mirroring
    // PathViewModel.onResumed) is what re-reads the moment (M58 D5, #852).
    entry<PebblesKey.EditMoment>(metadata = NavTransitions.forKey(PebblesKey.EditMoment(""))) { key ->
        EditMomentScreen(momentId = key.momentId, onDismiss = navigator::goBack, onSaved = navigator::goBack)
    }

    entry<PebblesKey.EditPebble>(metadata = NavTransitions.forKey(PebblesKey.EditPebble(""))) { key ->
        EditPebbleScreen(momentId = key.momentId, onDismiss = navigator::goBack, onSaved = navigator::goBack)
    }

    entry<PebblesKey.Capture>(metadata = NavTransitions.forKey(PebblesKey.Capture())) { key ->
        CaptureFlowScreen(
            chainsComplete = key.chainsComplete,
            onDismiss = navigator::goBack,
            // Path's resume refresh shows the new blank stone; no detail is opened (A-14).
            onSaved = navigator::goBack,
            // Replace Capture with Complete rather than stacking it, so Complete's exit lands where
            // Capture was opened from: Path, or Home for its New pebble (#1383 D7).
            onSavedThenComplete = { momentId ->
                navigator.goBack()
                navigator.navigate(PebblesKey.CompletePebble(momentId))
            },
        )
    }

    entry<PebblesKey.CompletePebble>(metadata = NavTransitions.forKey(PebblesKey.CompletePebble(""))) { key ->
        // Exit returns to whatever opened it: the read page (which reloads on resume) or Path after a capture (A-14).
        CompleteFlowScreen(momentId = key.momentId, onDismiss = navigator::goBack)
    }

    // ---- The unauthenticated funnel promoted to entries (#852) ----

    entry<PebblesKey.Welcome>(metadata = NavTransitions.forKey(PebblesKey.Welcome)) {
        WelcomeScreen(
            // Read here, inside the entry, not captured by the caller: see
            // `RootScreen`'s class doc for why a plain Boolean froze Welcome.
            contentRevealed = welcomeContentRevealed(),
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

/**
 * The glyph picker's body, for the path and profile screens that host it
 * (#914). The picker is glyph's and a feature may not import another feature,
 * so the hosts take it as a slot and this file, which sees every feature, is
 * where it gets filled.
 */
private val glyphPicker: GlyphPickerSlot = { currentGlyphId, onSelected, state, modifier ->
    GlyphPickerContent(
        currentGlyphId = currentGlyphId,
        onSelected = onSelected,
        modifier = modifier,
        state = state,
    )
}

/**
 * Home's month grid (#1383 D8): the Path's week columns, for the Home tab that hosts them. Same reason as
 * [glyphPicker]: a feature may not import another, so this file fills the slot.
 */
private val homeMonth: HomeMonthSlot = { wrap, onWeek, modifier -> HomeMonthGrid(wrap, onWeek, modifier) }

/** Home's revealed wrap (#1383 D12): the Path's month page, intro once per launch. Same reason as [glyphPicker]. */
private val homeWrapSlot: HomeWrapSlot = { wrap, dayView, onDayView, hapticsOn, onWeek, modifier ->
    HomeWrapPage(wrap, dayView, onDayView, hapticsOn, onWeek, modifier)
}

/** A folded month's line in Home's wrap sheet (#1383 D12): the Path's words. */
private val homeWrapLineSlot: HomeWrapLineSlot = { pebbles, people -> homeWrapLine(pebbles, people) }

/**
 * Whether this entry is being shown as a pane of a list-detail scene (#940).
 * The strategy only builds that scene with two panes visible, so inside it a
 * detail always has its list beside it (no back arrow), and a list is only as
 * wide as its pane (the glyph store's toolbar goes back to the bottom).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun isInListDetailPane(): Boolean = LocalListDetailSceneScope.current != null
