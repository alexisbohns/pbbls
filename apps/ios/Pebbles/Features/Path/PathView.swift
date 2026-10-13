import SwiftUI
import os

private enum PathRoute: Hashable {
    case profile
}

struct PathView: View {
    /// Fired when the initial timeline load settles (success or failure), so
    /// `RootView` can keep the launch loader up until the home feed is ready
    /// instead of flashing this view's own spinner.
    var onFirstLoad: () -> Void = {}

    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(PathStatsService.self) private var stats
    @Environment(PebbleDraftsService.self) private var draftsService

    @State private var moments: [MomentSummary] = []
    @State private var entries: [WeekRollEntry] = []
    @State private var focusedWeekStart: Date = Date()
    @State private var navPath = NavigationPath()
    /// The record flow (M58) — what `+` opens.
    @State private var isPresentingFlow = false
    /// The classic composer, reachable by long-pressing `+` (D1).
    @State private var isPresentingCreate = false
    @State private var isPresentingDrafts = false
    @State private var selectedMomentId: UUID?
    @State private var pendingDeletion: MomentSummary?
    @State private var deleteError: String?
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
        NavigationStack(path: $navPath) {
            content
                .navigationDestination(for: PathRoute.self) { route in
                    switch route {
                    case .profile: ProfileView()
                    }
                }
                .toolbar(.hidden, for: .navigationBar)
                .pebblesScreen()
        }
        .task {
            await load()
            onFirstLoad()
        }
        .task { await stats.load() }
        .task { await draftsService.refreshCount() }
        .fullScreenCover(isPresented: $isPresentingFlow) {
            RecordFlowView(onPublished: { newPebbleId in
                // No detail sheet: the user just spent ten screens on this
                // pebble and the success step already showed it (D10).
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                    focusWeek(containing: newPebbleId)
                }
            })
        }
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                    selectedMomentId = moment(matching: newPebbleId)?.id
                }
            })
        }
        .sheet(isPresented: $isPresentingDrafts) {
            DraftsListSheet(onPebbleCreated: { newPebbleId in
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                    selectedMomentId = moment(matching: newPebbleId)?.id
                }
            })
        }
        .sheet(item: $selectedMomentId) { id in
            PebbleDetailSheet(momentId: id, onPebbleUpdated: {
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                }
            })
        }
        .confirmationDialog(
            pendingDeletion.map { "Delete \(MomentName.display($0.name) ?? String(localized: "Untitled"))?" } ?? "",
            isPresented: Binding(
                get: { pendingDeletion != nil },
                set: { if !$0 { pendingDeletion = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingDeletion
        ) { moment in
            Button("Delete", role: .destructive) {
                TapHaptics.play(.warning)
                Task { await delete(moment) }
            }
            Button("Cancel", role: .cancel) {
                pendingDeletion = nil
            }
        } message: { _ in
            Text("This can't be undone.")
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
            Color.system.background
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
                            onDelete: { moment in pendingDeletion = moment },
                            onCreate: { isPresentingFlow = true }
                        )
                        .tag(entry.weekStart)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 12) {
                    if draftsService.count > 0 {
                        Button {
                            isPresentingDrafts = true
                        } label: {
                            Label {
                                // Count is verbatim so this needs no plural
                                // entry in the catalog.
                                Text("Drafts") + Text(verbatim: " (\(draftsService.count))")
                            } icon: {
                                Image(systemName: "square.dashed")
                            }
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                        }
                    }
                    NewPebbleButton(
                        onTap: { isPresentingFlow = true },
                        onLongPress: { isPresentingCreate = true }
                    )
                    PathBottomBar(
                        karma: stats.karma,
                        ripple: rippleWithLocalActiveToday,
                        onProfile: { navPath.append(PathRoute.profile) }
                    )
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 8)
            }
        }
    }

    /// Focus the week the new pebble landed in, so exiting the flow puts the
    /// user where their pebble is rather than wherever they happened to be.
    ///
    /// Asks which entry actually holds the pebble rather than recomputing its
    /// week: `WeekRollBuilder` owns the bucketing, and a second implementation
    /// here could disagree with it. Silent no-op if the reload has not yet
    /// surfaced the pebble. `id` may be a moment id or a pebble id.
    private func focusWeek(containing id: UUID) {
        guard let entry = entries.first(where: { entry in
            entry.moments.contains { $0.id == id || $0.pebble?.id == id }
        }) else { return }
        focusedWeekStart = entry.weekStart
    }

    /// The moment holding `id`, which may be a moment id or a pebble id
    /// (the old composers report pebble ids; equal only for pre-split rows).
    private func moment(matching id: UUID) -> MomentSummary? {
        moments.first { $0.id == id || $0.pebble?.id == id }
    }

    private func load() async {
        do {
            let result = try await MomentsService(client: supabase.client).pathMoments()
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
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load your pebbles."
            self.isLoading = false
        }
    }

    private func delete(_ moment: MomentSummary) async {
        pendingDeletion = nil
        do {
            try await MomentDeletion.deleteMoment(moment.id, client: supabase.client)
            async let timeline: Void = load()
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
        } catch {
            logger.error("delete moment failed: \(error.localizedDescription, privacy: .private)")
            deleteError = String(localized: "Something went wrong. Please try again.")
        }
    }
}

#Preview {
    PathView()
        .environment(SupabaseService())
}
