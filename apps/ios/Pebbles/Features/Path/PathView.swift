import SwiftUI
import os

private enum PathRoute: Hashable {
    case profile
}

struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(PathStatsService.self) private var stats

    @State private var pebbles: [Pebble] = []
    @State private var entries: [WeekRollEntry] = []
    @State private var focusedWeekStart: Date = Date()
    @State private var navPath = NavigationPath()
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?
    @State private var pendingDeletion: Pebble?
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
        let activeToday = pebbles.contains { Calendar.current.isDateInToday($0.createdAt) }
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
                .pebblesScreen(background: Color.pebblesPathBackground)
        }
        .task { await load() }
        .task { await stats.load() }
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                selectedPebbleId = newPebbleId
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                }
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            PebbleDetailSheet(pebbleId: id, onPebbleUpdated: {
                Task {
                    async let timeline: Void = load()
                    async let statsReload: Void = stats.refresh()
                    _ = await (timeline, statsReload)
                }
            })
        }
        .confirmationDialog(
            pendingDeletion.map { "Delete \($0.name)?" } ?? "",
            isPresented: Binding(
                get: { pendingDeletion != nil },
                set: { if !$0 { pendingDeletion = nil } }
            ),
            titleVisibility: .visible,
            presenting: pendingDeletion
        ) { pebble in
            Button("Delete", role: .destructive) {
                Task { await delete(pebble) }
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
            ProgressView()
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
                            onTap: { pebble in selectedPebbleId = pebble.id },
                            onDelete: { pebble in pendingDeletion = pebble },
                            onCreate: { isPresentingCreate = true }
                        )
                        .tag(entry.weekStart)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
            .safeAreaInset(edge: .bottom) {
                VStack(spacing: 12) {
                    NewPebbleButton(onTap: { isPresentingCreate = true })
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

    private func load() async {
        do {
            let result: [Pebble] = try await supabase.client
                .rpc("path_pebbles")
                .execute()
                .value
            self.pebbles = result
            self.entries = WeekRollBuilder.build(
                pebbles: result, calendar: isoCalendar, today: today
            )
            // First load (or after a deletion that removed the focused week):
            // pick the current-week entry if present, else the closest entry.
            if !entries.contains(where: { $0.weekStart == focusedWeekStart }) {
                let currentWeekStart = WeekRollBuilder.build(
                    pebbles: [], calendar: isoCalendar, today: today
                ).first?.weekStart ?? today
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

    private func delete(_ pebble: Pebble) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            async let timeline: Void = load()
            async let statsReload: Void = stats.refresh()
            _ = await (timeline, statsReload)
        } catch {
            logger.error("delete pebble failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

#Preview {
    PathView()
        .environment(SupabaseService())
}
