import SwiftUI
import os

/// The Path tab's root (#903). Its `NavigationStack` lives in
/// `PebblesTabView`; Path pushes nothing yet.
struct PathView: View {
    /// Fired when the initial timeline load settles (success or failure), so
    /// `RootView` can keep the launch loader up until the home feed is ready
    /// instead of flashing this view's own spinner.
    var onFirstLoad: () -> Void = {}

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(PathStatsService.self) private var stats
    @Environment(CompleteDraftStore.self) private var completeDrafts
    @Environment(TabNavigation.self) private var nav
    @Environment(MomentsRevision.self) private var revision

    @State private var moments: [MomentSummary] = []
    @State private var entries: [WeekRollEntry] = []
    @State private var focusedWeekStart: Date = Date()
    /// The Capture flow — what `+` opens.
    @State private var isPresentingCapture = false
    @State private var selectedMomentId: UUID?
    @State private var pendingDeletion: MomentDeleteTarget?
    /// The latest reload Capture asked for. Capture can fire twice (after the
    /// save, after a chained completion); the newer one cancels the older so a
    /// slow first fetch cannot land after the second.
    @State private var reloadTask: Task<Void, Never>?
    @State private var deleteError: String?
    /// Ignores a second delete confirmed while the first is still running.
    @State private var isDeleting = false
    @State private var isLoading = true
    @State private var loadError: String?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    private var isoCalendar: Calendar { Calendar(identifier: .iso8601) }
    private var today: Date { Date() }

    /// Overrides the server's `active_today` flag (which compares against
    /// UTC `current_date`) with a device-local check so users in non-UTC
    /// timezones don't see "active today" while local-midnight has passed.
    /// See M22 follow-up for proper server-side timezone handling.
    private var rippleWithLocalActiveToday: RippleSummary? {
        guard let server = stats.ripple else { return nil }
        let activeToday = moments.contains { $0.isCompleted && Calendar.current.isDateInToday($0.createdAt) }
        return RippleSummary(
            rippleLevel: server.rippleLevel,
            pebbles28d: server.pebbles28d,
            activeToday: activeToday
        )
    }

    var body: some View {
        content
            .toolbar(.hidden, for: .navigationBar)
            .pebblesScreen()
        .task {
            await load()
            onFirstLoad()
        }
        .task { await stats.load() }
        .onChange(of: nav.scrollToTop[.path, default: 0]) { focusCurrentWeek() }
        // #903 D5: a moment changed from People or Collections. Path's own
        // changes already reloaded through their callbacks, and its `.task`
        // reloads whenever the tab reappears, so this reader only covers
        // changes made while Path stays visible. Stack D's
        // `PathModel` takes this over.
        .onChange(of: revision.value) {
            guard revision.changedOutside(.path) else { return }
            Task { await stats.refresh() }
            reloadTask?.cancel()
            reloadTask = Task { await load() }
        }
        .fullScreenCover(isPresented: $isPresentingCapture) {
            // Fired after the save and again after a chained completion (A5).
            CaptureFlowView(onMomentChanged: { momentId in
                revision.bump(from: .path)
                // Not cancelled with the path reload: `refresh()` no-ops while
                // one is running, so a cancelled refresh would drop the next.
                Task { await stats.refresh() }
                reloadTask?.cancel()
                reloadTask = Task {
                    await load()
                    guard !Task.isCancelled else { return }
                    focusWeek(containing: momentId)
                }
            })
        }
        .sheet(item: $selectedMomentId) { id in
            PebbleDetailSheet(momentId: id, origin: .path, onPebbleUpdated: {
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                }
            })
        }
        .momentDeleteDialog(target: $pendingDeletion) { target, option in
            Task { await delete(option, on: target) }
        }
        .alert(
            "Couldn't delete",
            isPresented: Binding(
                get: { deleteError != nil },
                set: { if !$0 { deleteError = nil } }
            ),
            presenting: deleteError
        ) { _ in
            Button("OK", role: .cancel) { deleteError = nil }
        } message: { message in
            Text(message)
        }
    }

    @ViewBuilder
    private var content: some View {
        if isLoading {
            // No spinner: the launch loader (RootView's handcrafted-logo cover)
            // owns the loading visual and stays up until `onFirstLoad`. Showing
            // the plain background here means the hand-off to the feed can't
            // flash a spinner even for a frame (#598).
            Color.pebbles.surface
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError {
            Text(loadError)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 0) {
                WeekRollView(
                    entries: entries,
                    focusedWeekStart: $focusedWeekStart,
                    calendar: isoCalendar
                )
                WeekHeaderView(
                    entries: entries,
                    focusedWeekStart: $focusedWeekStart,
                    calendar: isoCalendar,
                    today: today
                )
                .padding(.top, 16)
                TabView(selection: $focusedWeekStart) {
                    ForEach(entries) { entry in
                        WeekPathView(
                            entry: entry,
                            onTap: { moment in selectedMomentId = moment.id },
                            onDelete: { moment in pendingDeletion = MomentDeleteTarget(summary: moment) },
                            onCreate: { isPresentingCapture = true },
                            scrollToTopToken: nav.scrollToTop[.path, default: 0]
                        )
                        .tag(entry.weekStart)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            // One row, clear background, 8pt above the tab bar (#903 §5). The
            // week list scrolls under it; `WeekPathView`'s bottom padding lets
            // the last pebble clear it.
            .safeAreaInset(edge: .bottom) {
                HStack(alignment: .center, spacing: 16) {
                    PathStatusCluster(
                        karma: stats.karma,
                        ripple: rippleWithLocalActiveToday,
                        onKarma: { nav.select(.you) },
                        onRipple: { nav.select(.you) }
                    )
                    Spacer(minLength: 0)
                    NewPebbleFab(onTap: { isPresentingCapture = true })
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
    }

    /// Focus the week the moment landed in, so exiting the flow puts the
    /// user where their moment is rather than wherever they happened to be.
    ///
    /// Asks which entry actually holds the moment rather than recomputing its
    /// week: `WeekRollBuilder` owns the bucketing, and a second implementation
    /// here could disagree with it. Silent no-op if the reload has not yet
    /// surfaced the moment.
    private func focusWeek(containing momentId: UUID) {
        guard let entry = entries.first(where: { entry in
            entry.moments.contains { $0.id == momentId }
        }) else { return }
        focusedWeekStart = entry.weekStart
    }

    /// Path reselected at its root (#903 §4): "top of Path" means now. The
    /// same current-week lookup `load()` uses.
    private func focusCurrentWeek() {
        let current = WeekRollBuilder.weekStart(for: today, calendar: isoCalendar)
        guard entries.contains(where: { $0.weekStart == current }) else { return }
        withAnimation { focusedWeekStart = current }
    }

    private func load() async {
        do {
            let result = try await MomentsService(client: supabase.client).pathMoments()
            // A newer reload replaced this one: its result is already stale.
            guard !Task.isCancelled else { return }
            self.moments = result
            self.entries = WeekRollBuilder.build(
                moments: result, calendar: isoCalendar, today: today
            )
            // First load (or after a deletion that removed the focused week):
            // pick the current-week entry if present, else the closest entry.
            if !entries.contains(where: { $0.weekStart == focusedWeekStart }) {
                let currentWeekStart = WeekRollBuilder.weekStart(for: today, calendar: isoCalendar)
                if let cur = entries.first(where: { $0.weekStart == currentWeekStart }) {
                    focusedWeekStart = cur.weekStart
                } else if let closest = entries.min(by: {
                    abs($0.weekStart.timeIntervalSince(focusedWeekStart)) <
                    abs($1.weekStart.timeIntervalSince(focusedWeekStart))
                }) {
                    focusedWeekStart = closest.weekStart
                }
            }
            self.isLoading = false
        } catch {
            // Cancelled for a newer reload, not failed.
            guard !Task.isCancelled else { return }
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load your pebbles."
            self.isLoading = false
        }
    }

    private func delete(_ option: MomentDeleteOption, on target: MomentDeleteTarget) async {
        guard !isDeleting else { return }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await MomentDeletion.perform(
                option, on: target, client: supabase.client, drafts: completeDrafts
            )
            revision.bump(from: .path)
            async let timeline: Void = load()
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
        } catch {
            logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
            deleteError = String(localized: "Something went wrong. Please try again.")
        }
    }
}

#Preview {
    NavigationStack {
        PathView()
    }
    .environment(SupabaseService())
    .environment(CompleteDraftStore())
    .environment(TabNavigation())
    .environment(MomentsRevision())
}
