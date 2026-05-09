import SwiftUI
import os

struct PathView: View {
    @Environment(SupabaseService.self) private var supabase
    @State private var pebbles: [Pebble] = []
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var isPresentingCreate = false
    @State private var selectedPebbleId: UUID?
    @State private var isPresentingOnboarding = false
    @State private var pendingDeletion: Pebble?
    @State private var deleteError: String?

    /// Per-week reveal cascade state. Each week's section only mounts
    /// once the previous week's cascade is complete, so its cairn
    /// animation doesn't start prematurely.
    ///
    /// - `revealedWeeksCount`: how many weeks are currently in the list
    ///   (starts at 1 — the topmost week — so its cairn begins playing
    ///   on appear). Incremented each time a week finishes its cascade.
    /// - `revealedPebblesPerWeek[i]`: how many of week `i`'s pebbles are
    ///   currently visible. Pebble rows beyond this count are not
    ///   emitted at all (so the card grows from zero height instead of
    ///   reserving space for hidden rows).
    /// - `titleVisiblePerWeek[i]`: whether week `i`'s title has faded in.
    /// - `cascadeStartedWeeks`: guards `runCascade(forWeek:)` against
    ///   duplicate invocation.
    /// - `cascadeFullyDone`: once true, the gating dictionaries are
    ///   ignored and the full list renders immediately. Prevents a
    ///   `load()` triggered by a new/deleted pebble from re-animating.
    @State private var revealedWeeksCount: Int = 1
    @State private var revealedPebblesPerWeek: [Int: Int] = [:]
    @State private var titleVisiblePerWeek: [Int: Bool] = [:]
    @State private var cascadeStartedWeeks: Set<Int> = []
    @State private var cascadeFullyDone = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    /// Stagger between consecutive pebble reveals in a single week.
    private static let pebbleRevealStagger: Duration = .milliseconds(80)
    /// Delay after a cairn stops before its week title fades in.
    private static let titleRevealDelay: Duration = .milliseconds(120)
    /// Pause after a week's last pebble before the next week's section
    /// is mounted (and its cairn starts).
    private static let nextWeekDelay: Duration = .milliseconds(180)

    /// ISO 8601 calendar — Mon-start, week-1-contains-first-Thursday.
    /// Locale-independent so all users see the same week boundaries.
    private var isoCalendar: Calendar {
        Calendar(identifier: .iso8601)
    }

    private var groupedPebbles: [(key: Date, value: [Pebble])] {
        groupPebblesByISOWeek(pebbles, calendar: isoCalendar)
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Path")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            isPresentingOnboarding = true
                        } label: {
                            Image(systemName: "info.circle")
                        }
                        .accessibilityLabel("Show how Pebbles works")
                    }
                }
                .pebblesScreen()
        }
        .task { await load() }
        .sheet(isPresented: $isPresentingCreate) {
            CreatePebbleSheet(onCreated: { newPebbleId in
                selectedPebbleId = newPebbleId
                Task { await load() }
            })
        }
        .sheet(item: $selectedPebbleId) { id in
            PebbleDetailSheet(pebbleId: id, onPebbleUpdated: {
                Task { await load() }
            })
        }
        .fullScreenCover(isPresented: $isPresentingOnboarding) {
            OnboardingView(steps: OnboardingSteps.all) {
                // Replay is idempotent — only RootView's initial-gate
                // closure writes @AppStorage("hasSeenOnboarding").
                isPresentingOnboarding = false
            }
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
        } else if let loadError {
            Text(loadError).foregroundStyle(.secondary)
        } else {
            List {
                Section {
                    Button {
                        isPresentingCreate = true
                    } label: {
                        Label("Record a pebble", systemImage: "plus.circle.fill")
                            .font(.headline)
                    }
                    .listRowBackground(Color.pebblesListRow)
                }

                let visibleWeekCount = cascadeFullyDone
                    ? groupedPebbles.count
                    : min(revealedWeeksCount, groupedPebbles.count)

                ForEach(0..<visibleWeekCount, id: \.self) { weekIndex in
                    let group = groupedPebbles[weekIndex]
                    let revealedPebbleCount = cascadeFullyDone
                        ? group.value.count
                        : (revealedPebblesPerWeek[weekIndex] ?? 0)
                    let visiblePebbles = Array(group.value.prefix(revealedPebbleCount))

                    Section {
                        ForEach(visiblePebbles) { pebble in
                            PathPebbleRow(
                                pebble: pebble,
                                onTap: { selectedPebbleId = pebble.id },
                                onDelete: { pendingDeletion = pebble }
                            )
                            .listRowBackground(Color.pebblesListRow)
                            .listRowSeparator(.hidden)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                        }
                    } header: {
                        WeekSectionHeader(
                            weekStart: group.key,
                            calendar: isoCalendar,
                            titleVisible: cascadeFullyDone
                                || (titleVisiblePerWeek[weekIndex] ?? false),
                            onCairnFinished: cascadeFullyDone
                                ? nil
                                : { runCascade(forWeek: weekIndex) }
                        )
                    }
                }
            }
        }
    }

    private func load() async {
        do {
            let result: [Pebble] = try await supabase.client
                .from("pebbles")
                .select("id, name, happened_at, render_svg, emotion:emotions(id, slug, name)")
                .order("happened_at", ascending: false)
                .execute()
                .value
            self.pebbles = result
            self.isLoading = false
        } catch {
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Couldn't load your pebbles."
            self.isLoading = false
        }
    }

    /// Drives one week's reveal cascade, then chains to the next week
    /// by mounting its section so that section's cairn animation can
    /// start. Idempotent — guarded by `cascadeStartedWeeks` so a stray
    /// `RiveViewModel` callback fired after a re-render does not
    /// re-trigger an in-flight or completed cascade.
    private func runCascade(forWeek weekIndex: Int) {
        guard !cascadeStartedWeeks.contains(weekIndex) else { return }
        cascadeStartedWeeks.insert(weekIndex)

        Task { @MainActor in
            try? await Task.sleep(for: Self.titleRevealDelay)
            withAnimation(.easeOut(duration: 0.25)) {
                titleVisiblePerWeek[weekIndex] = true
            }

            let groups = groupedPebbles
            let pebbleCount = weekIndex < groups.count ? groups[weekIndex].value.count : 0
            for index in 0..<pebbleCount {
                try? await Task.sleep(for: Self.pebbleRevealStagger)
                withAnimation(.easeOut(duration: 0.25)) {
                    revealedPebblesPerWeek[weekIndex] = index + 1
                }
            }

            try? await Task.sleep(for: Self.nextWeekDelay)
            if weekIndex + 1 < groupedPebbles.count {
                withAnimation(.easeOut(duration: 0.3)) {
                    revealedWeeksCount = weekIndex + 2
                }
            } else {
                cascadeFullyDone = true
            }
        }
    }

    private func delete(_ pebble: Pebble) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .rpc("delete_pebble", params: ["p_pebble_id": pebble.id.uuidString])
                .execute()
            await load()
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
