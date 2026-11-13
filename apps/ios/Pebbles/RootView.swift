import SwiftUI
import os

/// Top-level auth gate. The loader and pre-login welcome are merged into
/// a single `WelcomeView`, which hosts `HandcraftedLogoView` as its splash:
/// the logo plays a draw-on, then boils in place until the app is ready.
///
/// At cold launch `WelcomeView` is rendered with `contentRevealed: false`
/// — only the logo is visible, centered, drawing on then boiling. It boils
/// against `dataReady` (auth resolved AND both reference-data services
/// settled — success or failure — OR the safety ceiling elapsed) and, once it
/// has also boiled its minimum, emits `onLoaderSettled`. That event sets
/// `loaderSettled`, making `canProceed` true, and one of two things happens:
///   - if the user is unauthenticated, `contentRevealed` flips true and
///     `WelcomeView` slides the carousel + sign-in buttons + disclaimer
///     in from the bottom, pushing the logo up to its header position;
///   - if the user is authenticated, the stack swaps to `PebblesTabView`, with the
///     loader held over it (via `startSettled`) until the first timeline load
///     settles — so `PathView`'s own spinner never flashes. A launch restored
///     onto another tab skips the loader: `restoreTabs()` marks the feed as
///     handed off, since `TabView` only builds Path when it is first shown.
struct RootView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs
    @Environment(PathStatsService.self) private var stats
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(CaptureDraftStore.self) private var captureDrafts
    @Environment(SnapURLCache.self) private var snapURLs
    @Environment(ConnectionsService.self) private var connections
    @Environment(AchievementNotificationService.self) private var achievementNotify
    /// The first-run gate and its cover (#1510 D10). Owned here, like the consent gate, because the
    /// two decide together what stands above the tabs.
    @State private var onboarding = OnboardingPresentation()
    @State private var authPath = NavigationPath()
    @State private var loaderSettled = false
    @State private var loaderCeilingReached = false
    /// For authed launches: the loader stays over the first root (Home or
    /// Path) until its first load settles, so its own spinner never shows.
    @State private var firstRootLoaded = false
    /// Invite token lifted out of a universal link, presented as a sheet once
    /// there is a session to accept with (M49, design D12).
    @State private var acceptingInvite: InviteToken?
    /// Authed navigation (#903 §3). Lives here, not in a child, so this view
    /// can settle `holdsLoader` at restore and reset it on sign-out.
    @State private var nav = TabNavigation()
    /// Sign-out reset contract (#903 §6): every authed `@SceneStorage` key is
    /// declared here and cleared in `resetAuthedSceneState()`. A later stack
    /// that adds one (stack E's `heldCard`) registers it in both places, in
    /// the PR that adds it.
    @SceneStorage("tabs.navigation") private var storedTabs: Data?
    /// Where each Spine slot sits on screen, for stack E's held card (#1130
    /// §Frame registry). Created here, above the tabs, so the overlay E adds
    /// beside `PebblesTabView` reads the same one.
    @State private var cardFrames = CardFrameRegistry()
    /// The held card's one host (#1146 D1, D7), above the tabs.
    @State private var heldCard = HeldCardHostModel()
    /// Stack C §6: an authed scene key, declared here and cleared in
    /// `resetAuthedSceneState()`. Restored once the launch loader is down.
    @SceneStorage("heldCard") private var storedHeldCard: Data?
    @State private var heldCardRestored = false
    /// The consent gate (#821): an overlay above everything in the authed
    /// branch, held until the ledger carries every act at a current version.
    @State private var consentGate = ConsentGateModel()
    @Environment(\.scenePhase) private var scenePhase

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "root")

    /// Safety ceiling: if reference data never settles (e.g. a wedged
    /// network beyond the client's own timeout), open the app anyway rather
    /// than boiling indefinitely. Normal launches settle in well under this.
    private static let loaderCeilingSeconds: TimeInterval = 8

    private enum AuthRoute: Hashable {
        case auth(AuthView.Mode)
    }

    /// `sheet(item:)` needs an `Identifiable` payload; the token is the id.
    struct InviteToken: Identifiable, Hashable {
        let value: String
        var id: String { value }
    }

    /// Accepts only `https://www.pbbls.app/invite/<token>` — the one canonical
    /// invite URL (design D11). Returns nil for anything else.
    static func inviteToken(from url: URL) -> String? {
        guard url.scheme == "https", url.host() == "www.pbbls.app" else { return nil }
        let parts = url.pathComponents.filter { $0 != "/" }
        guard parts.count == 2, parts[0] == "invite" else { return nil }
        let token = parts[1]
        return token.isEmpty ? nil : token
    }

    /// Auth resolved AND both reference-data load attempts settled (success or
    /// failure), OR the safety ceiling elapsed.
    private var dataReady: Bool {
        (!supabase.isInitializing && palettes.didFinishLoading && refs.didFinishLoading)
            || loaderCeilingReached
    }

    /// The app is shown only once the loader has fully settled — drawn on AND
    /// boiled the minimum AND `dataReady`. The handcrafted logo IS the loader,
    /// so its own settle event (not a computed time) drives the transition.
    private var canProceed: Bool { loaderSettled }

    private var canShowAuthedTabs: Bool {
        supabase.session != nil && canProceed
    }

    private var welcomeContentRevealed: Bool {
        supabase.session == nil && canProceed
    }

    /// Auth has resolved and nobody is signed in. Distinct from
    /// `session == nil`, which is also true while the persisted session is
    /// still being read on every cold start.
    private var isResolvedSignedOut: Bool {
        !supabase.isInitializing && supabase.session == nil
    }

    /// Decided once, at restore: a scene restored onto People, Collections or You sets
    /// `firstRootLoaded` there, because `TabView` builds Home and Path lazily and their
    /// `onFirstLoad` would otherwise fire mid-session, under a full-screen
    /// loader on the first tap on Path. It also holds while the onboarding flag is unknown, so
    /// neither Home nor a restored card shows before the promise (#1510 D2).
    private var holdsLoader: Bool {
        !firstRootLoaded || onboarding.gate.isPending
    }

    /// The session's user has passed the consent gate. Keyed on the session, not on the gate's last
    /// `start`, so a change of user can't open the app on the previous user's pass for a frame.
    private var consentPassed: Bool {
        consentGate.isPassed(for: OnboardingGate.userKey(supabase.session?.user.id))
    }

    /// Past every gate, with the flow's cover gone: an invite may open, and the composer.
    private var isClearOfGates: Bool {
        supabase.session != nil && consentPassed && !onboarding.invitesWait
    }

    var body: some View {
        @Bindable var onboarding = onboarding
        ZStack {
            if canShowAuthedTabs {
                ZStack {
                    PebblesTabView(onFirstLoad: { firstRootLoaded = true })
                        .environment(nav)
                        .environment(cardFrames)
                        .environment(heldCard)
                        .environment(onboarding.handoff)
                        #if DEBUG
                        // Settings' replay row.
                        .environment(onboarding.gate)
                        #endif
                        // Nothing under the held card or the consent gate is reachable (stack C §7's slot).
                        .accessibilityHidden(heldCard.request != nil || !consentPassed)
                        .environment(\.spineSnapSource, .signed(snapURLs))
                        // `onDismiss`, not the flow's end: anything opened after it can't be
                        // dropped by a dismissal still in progress.
                        .fullScreenCover(isPresented: $onboarding.coverBinding, onDismiss: {
                            onboarding.coverDismissed()
                        }, content: {
                            OnboardingFlowHost(
                                slot: onboarding.slot,
                                onLogOut: signOut,
                                onCoverUp: onboarding.coverAppeared
                            ) { note in
                                onboarding.finish(
                                    note: note,
                                    consentGate: consentGate,
                                    onClosed: handOffAfterOnboarding
                                )
                            }
                        })

                    if holdsLoader {
                        // Hold the loader over the home feed load so its own
                        // spinner never flashes. `startSettled` skips a second
                        // draw-on — the logo just boils, then fades out.
                        HandcraftedLogoView(shouldSettle: false, startSettled: true)
                            .containerRelativeFrame(.horizontal) { width, _ in width * 0.33 }
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .background(Color.pebbles.surface.ignoresSafeArea())
                            .transition(.opacity)
                    }

                    // The held card (#1160 D1): one layer above the tabs for every tab.
                    HeldCardHost()
                        .environment(heldCard)
                        .environment(cardFrames)
                        .environment(\.spineSnapSource, .signed(snapURLs))
                        .accessibilityHidden(!consentPassed)

                    // The consent gate (#821), or the loader while the onboarding collects
                    // the consent itself. Invites and the composer wait for a pass.
                    ConsentLayerView(
                        layer: onboarding.consentLayer(isPassed: consentPassed),
                        gate: consentGate,
                        onSignOut: signOut
                    )
                }
                .animation(.easeInOut(duration: 0.25), value: consentPassed)
                .animation(.easeInOut(duration: 0.35), value: holdsLoader)
                .sheet(item: $acceptingInvite) { invite in
                    InviteAcceptSheet(token: invite.value)
                }
            } else {
                NavigationStack(path: $authPath) {
                    WelcomeView(
                        contentRevealed: welcomeContentRevealed,
                        appReady: dataReady,
                        onLoaderSettled: { loaderSettled = true },
                        onCreateAccount: { authPath.append(AuthRoute.auth(.signup)) },
                        onLogin: { authPath.append(AuthRoute.auth(.login)) }
                    )
                    .navigationDestination(for: AuthRoute.self) { route in
                        switch route {
                        case .auth(let mode):
                            AuthView(initialMode: mode)
                        }
                    }
                }
            }
        }
        .task {
            await supabase.start()
        }
        .task {
            try? await Task.sleep(for: .seconds(Self.loaderCeilingSeconds))
            loaderCeilingReached = true
        }
        .task { await palettes.load() }
        // Restore once, before the authed branch first renders: the loader
        // keeps that branch hidden for well over one runloop.
        .task { restoreTabs() }
        // Restore the held card once the launch loader is down (#1146 D7).
        .task(id: holdsLoader) {
            guard !holdsLoader, !heldCardRestored else { return }
            heldCardRestored = true
            restoreHeldCard()
        }
        .onChange(of: heldCard.snapshot) { _, snapshot in
            // Signed out, the state is being reset, not navigated.
            guard supabase.session != nil else { return }
            storedHeldCard = snapshot?.encoded
        }
        .onChange(of: nav.snapshot) { _, snapshot in
            // Signed out, the state is being reset, not navigated.
            guard supabase.session != nil else { return }
            storedTabs = snapshot.encoded
        }
        // Keyed on the user: souls and collections are theirs, so each sign-in
        // loads its own lists instead of keeping whatever the launch fetched.
        .task(id: supabase.session?.user.id) { await refs.load() }
        // Keyed on the user: a sign-out or a different account re-runs both gates. In one turn, so
        // the consent form can't show between them to an account the promise is about to greet.
        .task(id: supabase.session?.user.id) {
            let userId = OnboardingGate.userKey(supabase.session?.user.id)
            onboarding.gate.service = OnboardingService(client: supabase.client)
            onboarding.gate.start(userId: userId)
            consentGate.service = ConsentService(client: supabase.client)
            consentGate.start(userId: userId)
        }
        .onChange(of: canShowAuthedTabs && onboarding.gate.shouldPresent, initial: true) { _, present in
            if present {
                onboarding.bind(cards: achievementNotify)
                onboarding.presentCover()
            }
        }
        .onChange(of: scenePhase) { _, phase in
            // A gate that failed closed tries again when the app comes back.
            if phase == .active, case .failed = consentGate.state { consentGate.retry() }
        }
        // The pass is false until the gate's first verdict for the session's user, so a launch
        // already signed in still sees a false→true flip. Keyed on all of it, not on the pass alone:
        // an invite parked while the flag was unknown must still open when the flag says no flow.
        .onChange(of: isClearOfGates) { _, clear in
            if clear { openWhatWasWaiting() }
        }
        .onOpenURL { url in
            // The app's only URL entry point. OAuth returns are absorbed by
            // ASWebAuthenticationSession, so nothing else lands here.
            guard let token = Self.inviteToken(from: url) else { return }
            if isClearOfGates {
                acceptingInvite = InviteToken(value: token)
            } else {
                // Park it: an invite can arrive before sign-in completes, before
                // the consent gate passes, or before the onboarding ends.
                connections.pendingInviteToken = token
            }
        }
        .onChange(of: supabase.session == nil) { wasSignedOut, isSignedOut in
            if !wasSignedOut && isSignedOut {
                // Re-arm the home-feed loader cover for the next sign-in.
                firstRootLoaded = false
                // A real sign-out only, never a cold launch that resolves signed out: an invite parked
                // for this account must not open for the next one, while a link that launched the app
                // signed out has to survive to the sign-in it is waiting for (#1568). Android clears its
                // own on sign-out as well.
                connections.pendingInviteToken = nil
            }
        }
        .onChange(of: isResolvedSignedOut) { _, signedOut in
            guard signedOut else { return }
            resetAuthedSceneState()
            SignedOutPurge(
                completeDrafts: completeDrafts,
                captureDrafts: captureDrafts,
                refs: refs,
                stats: stats,
                snapURLs: snapURLs,
                urlCache: .shared
            ).run()
        }
    }

    private func signOut() {
        Task { await supabase.signOut() }
    }

    /// The cover is gone, and that lets `isClearOfGates` turn on, now if the gate has passed or when
    /// it does. Home first, and the people named in the flow into the lists Complete reads. Called
    /// once per flow's end, by `OnboardingPresentation`.
    private func handOffAfterOnboarding() {
        nav.popToRoot(.home)
        nav.go(.home)
        Task { await refs.load() }
    }

    private func openWhatWasWaiting() {
        guard let token = onboarding.openWaiting(parkedInvite: connections.pendingInviteToken) else { return }
        connections.pendingInviteToken = nil
        acceptingInvite = InviteToken(value: token)
    }

    private func restoreTabs() {
        guard let storedTabs else { return }
        guard let snapshot = TabNavigation.Snapshot.decoded(from: storedTabs) else {
            logger.error("tabs.navigation scene storage did not decode; keeping defaults")
            return
        }
        nav.restore(snapshot)
        if ![.home, .path].contains(snapshot.selected) {
            firstRootLoaded = true
        }
    }

    /// The one place authed scene state is cleared (#903 §6).
    private func resetAuthedSceneState() {
        nav.reset()
        storedTabs = nil
        // The `session == nil` re-arm never fires on a cold launch that
        // resolves signed out, so re-arm the loader for the next sign-in here.
        firstRootLoaded = false
        // No slot of the last account survives into the next one, and none
        // stays hidden by a flight the sign-out cut short (`hidden` starts nil).
        cardFrames = CardFrameRegistry()
        heldCard.reset()
        storedHeldCard = nil
        heldCardRestored = false
        // Also clears what the last account's flow held back, which must not show for the next.
        onboarding.bind(cards: achievementNotify)
        onboarding.reset()
        // Left set, the next sign-in would present it above the consent layer and drop the flow's cover.
        // Not `connections.pendingInviteToken`: this also runs on a cold launch that resolves signed
        // out, and that token may be the link that launched the app.
        acceptingInvite = nil
    }

    private func restoreHeldCard() {
        guard let storedHeldCard else { return }
        guard let snapshot = HeldCardSnapshot.decoded(from: storedHeldCard) else {
            logger.error("heldCard scene storage did not decode; no card reopens")
            self.storedHeldCard = nil
            return
        }
        heldCard.restore(snapshot)
    }
}

#Preview {
    let supabase = SupabaseService()
    let snapURLs = SnapURLCache(client: supabase.client)
    return RootView()
        .environment(supabase)
        .environment(EmotionPaletteService(client: supabase.client))
        .environment(ReferenceDataService(client: supabase.client))
        .environment(PathStatsService(supabase: supabase))
        .environment(CompleteDraftStore())
        .environment(CaptureDraftStore())
        .environment(snapURLs)
        .environment(KarmaNotificationService())
        .environment(AchievementNotificationService())
        .environment(ConnectionsService(supabase: supabase))
        .environment(MomentsRevision())
        .environment(SnapAspectCache(urls: snapURLs))
}
