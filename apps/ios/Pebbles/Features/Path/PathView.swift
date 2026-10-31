import SwiftUI

/// The Path tab's root (#1130): the Spine. The strip (#1226) over a pager of
/// Spine weeks and months (`SpinePager`); `PathModel` holds the weeks, the
/// strip, the focused page and the pages. This view hands the environment's services to the model, and owns
/// Capture, the detail sheet (until stack E's held card), the delete dialog and
/// the floating + button. Its `NavigationStack` lives in `PebblesTabView`.
struct PathView: View {
    /// Fired when the first load settles (success or failure), so `RootView`
    /// keeps the launch loader up until the home feed is ready.
    var onFirstLoad: () -> Void = {}

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(PathStatsService.self) private var stats
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(ReferenceDataService.self) private var references
    @Environment(TabNavigation.self) private var nav
    @Environment(MomentsRevision.self) private var revision
    @Environment(HeldCardHostModel.self) private var heldCard
    @Environment(CardFrameRegistry.self) private var cardFrames
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var model = PathModel()
    /// The Capture flow, what `+` opens.
    @State private var isPresentingCapture = false
    @State private var pendingDeletion: MomentDeleteTarget?
    /// The latest reload asked for; a newer one cancels it, so a slow first
    /// fetch cannot land after the second.
    @State private var reloadTask: Task<Void, Never>?
    @State private var deleteError: String?

    private var service: MomentsService { MomentsService(client: supabase.client) }

    /// Peeked until the pages exist, so a cold Path does not drop Home's week.
    private func takeWeekIntent() {
        guard case let .focusWeek(day)? = nav.peekIntent(for: .path), !model.pages.isEmpty else { return }
        _ = nav.takeIntent(for: .path)
        model.focus(weekStarting: day)
    }

    var body: some View {
        content
            .toolbar(.hidden, for: .navigationBar)
            .pebblesScreen()
            .task {
                model.wraps = PathWrapService(client: supabase.client, disk: .standard,
                                              owner: supabase.session?.user.id.uuidString.lowercased())
                await model.load { try await service.pathMoments() }
                onFirstLoad()
            }
            .task { await stats.load() }
            .task(id: references.souls) { syncSouls() }
            // A held card closed: VoiceOver returns to its slot (stack D3's focus state).
            .onChange(of: heldCard.returnFocus) { _, id in
                if let id { model.returnFocus = id }
            }
            // Path reselected at its root (#903 §4): "top of Path" means now.
            .onChange(of: nav.scrollToTop[.path, default: 0]) { model.focusCurrentWeek() }
            // Home asked for a week (#1382 D9): taken on arrival, or once the pages land.
            .onAppear { takeWeekIntent() }
            .onChange(of: model.pages.isEmpty) { takeWeekIntent() }
            // #903 D5: a moment changed from another tab or a sheet.
            .onChange(of: revision.value) {
                guard revision.changedOutside(.path) else { return }
                Task { await stats.refresh() }
                reloadTask?.cancel()
                reloadTask = Task { await model.reloadIfChangedOutside(revision) { try await service.pathMoments() } }
            }
            .fullScreenCover(isPresented: $isPresentingCapture) {
                // Fired after the save and again after a chained completion.
                CaptureFlowView(onMomentChanged: { momentId in
                    revision.bump(from: .path)
                    Task { await stats.refresh() }
                    reload(focusing: momentId)
                })
            }
            .momentDeleteDialog(target: $pendingDeletion) { target, option in
                Task { await delete(option, on: target) }
            }
            .alert(
                "Couldn't delete",
                isPresented: Binding(get: { deleteError != nil }, set: { if !$0 { deleteError = nil } }),
                presenting: deleteError
            ) { _ in
                Button("OK", role: .cancel) { deleteError = nil }
            } message: { message in
                Text(message)
            }
    }

    @ViewBuilder
    private var content: some View {
        switch model.phase {
        case .loading:
            // No spinner: the launch loader owns the loading visual (#598).
            Color.pebbles.surface
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .failed:
            // Unchanged from the row Path: an English line (follow-up in the D5a PR).
            Text(verbatim: "Couldn't load your pebbles.")
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .content:
            VStack(spacing: 0) {
                PathStrip(items: model.stripItems, focus: model.focusedPage, calendar: model.calendar) { page in
                    if reduceMotion { model.focus(page) } else { withAnimation { model.focus(page) } }
                }
                SpinePager(
                    model: model,
                    palette: palette,
                    onOpen: open,
                    onDelete: { pendingDeletion = MomentDeleteTarget(summary: $0) },
                    onCreate: { isPresentingCapture = true },
                    lookups: lookups,
                    scrollToTopToken: nav.scrollToTop[.path, default: 0],
                    intro: IntroContext(ledger: model.introLedger, haptics: IntroHaptics.shared)
                )
                // The focused page's wrap and its neighbours', fetched on each focus and after each load.
                .task(id: WrapDemand(page: model.focusedPage, revision: model.pagesRevision)) {
                    await model.refreshWraps()
                }
            }
            // The + floats over the tab bar's trailing end (#903 §5). The page
            // scrolls under it; the Spine's 96 pt feed bottom clears it.
            .safeAreaInset(edge: .bottom) {
                NewPebbleFab(onTap: { isPresentingCapture = true })
                    .placed()
            }
        }
    }

    /// What the wraps' ids point at: the feed's moments, the reference data, the palettes.
    private var lookups: WrapLookups {
        let moments = model.momentsById
        let families = Dictionary(EmotionFamilies.of(palettes.byEmotionId.values).map { ($0.slug, $0) },
                                  uniquingKeysWith: { first, _ in first })
        let domains = Dictionary(references.domains.map { ($0.id.uuidString.lowercased(), $0) },
                                 uniquingKeysWith: { first, _ in first })
        let emotions = palettes.byEmotionId
        let souls = Dictionary(references.souls.map { ($0.id.uuidString.lowercased(), $0) },
                               uniquingKeysWith: { first, _ in first })
        let pebbleSouls = model.pebbleSoulNames
        return WrapLookups(
            moment: { moments[$0.lowercased()] },
            family: { families[$0] },
            domain: { domains[$0.lowercased()] },
            emotion: { UUID(uuidString: $0).flatMap { emotions[$0] } },
            palette: palette,
            soulName: { souls[$0.lowercased()]?.name ?? pebbleSouls[$0.lowercased()] },
            soulGlyph: { souls[$0.lowercased()]?.glyph.strokes }
        )
    }

    private func palette(_ moment: MomentSummary) -> EmotionPalette? {
        moment.pebble?.emotion.flatMap { palettes.palette(for: $0.id) }
    }

    /// A press opens the held card on the face it asked for (#1146): the stone
    /// the verso, after its dip; the card or the entry the recto.
    private func open(_ open: SpineOpen) {
        model.returnFocus = nil
        heldCard.open(HeldCardRequest(open, origin: .path, onChanged: { reloadAfterCard() }),
                      sourceRect: cardFrames.frames[open.momentId], reduceMotion: reduceMotion)
    }

    /// The card changed a moment: the Path and the stats reload.
    private func reloadAfterCard() {
        Task {
            async let timeline: Void = model.load { try await service.pathMoments() }
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
        }
    }

    private func syncSouls() {
        let souls = references.souls
        model.updateSouls(
            names: Dictionary(souls.map { ($0.id.uuidString.lowercased(), $0.name) },
                              uniquingKeysWith: { first, _ in first }),
            glyphs: Dictionary(souls.map { ($0.id.uuidString.lowercased(), $0.glyph.strokes) },
                               uniquingKeysWith: { first, _ in first })
        )
    }

    /// Focus the week the moment landed in once the reload has it.
    private func reload(focusing momentId: UUID? = nil) {
        reloadTask?.cancel()
        reloadTask = Task {
            await model.load { try await service.pathMoments() }
            guard !Task.isCancelled, let momentId else { return }
            model.focusWeek(containing: momentId)
        }
    }

    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        // The model logs a failure itself; this view only shows the alert. A
        // second confirm while one runs is ignored, as before.
        switch await model.delete({
            try await MomentDeletion.perform(option, on: target, client: supabase.client, drafts: completeDrafts)
        }) {
        case .ignored:
            return
        case .failed:
            deleteError = String(localized: "Something went wrong. Please try again.")
            return
        case .done:
            break
        }
        revision.bump(from: .path)
        async let timeline: Void = model.load { try await service.pathMoments() }
        async let statsReload: Void = stats.refresh()
        _ = await (timeline, statsReload)
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        PathView()
    }
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(ReferenceDataService(client: supabase.client))
    .environment(PathStatsService(supabase: supabase))
    .environment(CompleteDraftStore())
    .environment(TabNavigation())
    .environment(MomentsRevision())
    .environment(HeldCardHostModel())
    .environment(CardFrameRegistry())
    .environment(SnapAspectCache(urls: SnapURLCache(client: supabase.client)))
}

/// When the Path asks for wraps: on each focus, and after each load.
private struct WrapDemand: Equatable {
    let page: PathPage
    let revision: Int
}
