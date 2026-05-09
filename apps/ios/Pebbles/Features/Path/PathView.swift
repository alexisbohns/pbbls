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

    /// First-week reveal cascade. Stages:
    ///   0 → cairn playing, title hidden, all pebbles hidden
    ///   1 → title visible, pebbles still hidden
    ///   N (N≥2) → first (N-1) pebbles visible
    /// Set to `Int.max` once the cascade has completed (or the user has
    /// since added/removed a pebble) so subsequent re-renders don't
    /// re-animate.
    @State private var firstWeekRevealStage: Int = 0
    @State private var firstWeekHasCascaded = false

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    /// Stagger between consecutive pebble reveals in the first week.
    private static let pebbleRevealStagger: Duration = .milliseconds(80)
    /// Delay after the cairn stops before the title fades in.
    private static let titleRevealDelay: Duration = .milliseconds(120)

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

                ForEach(Array(groupedPebbles.enumerated()), id: \.element.key) { weekIndex, group in
                    let isFirstWeek = (weekIndex == 0)
                    Section {
                        ForEach(Array(group.value.enumerated()), id: \.element.id) { pebbleIndex, pebble in
                            let visible = !isFirstWeek
                                || firstWeekHasCascaded
                                || firstWeekRevealStage >= pebbleIndex + 2
                            PathPebbleRow(
                                pebble: pebble,
                                onTap: { selectedPebbleId = pebble.id },
                                onDelete: { pendingDeletion = pebble }
                            )
                            .opacity(visible ? 1 : 0)
                            .offset(y: visible ? 0 : 8)
                            .animation(.easeOut(duration: 0.25), value: visible)
                            .listRowBackground(Color.pebblesListRow)
                            .listRowSeparator(.hidden)
                        }
                    } header: {
                        WeekSectionHeader(
                            weekStart: group.key,
                            calendar: isoCalendar,
                            titleVisible: !isFirstWeek
                                || firstWeekHasCascaded
                                || firstWeekRevealStage >= 1,
                            onCairnFinished: isFirstWeek ? { runFirstWeekCascade() } : nil
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

    /// Drives the first-week reveal cascade once the cairn animation
    /// has finished. Idempotent — guarded by `firstWeekHasCascaded` so
    /// `RiveViewModel` callbacks fired after a re-render do not restart
    /// the sequence.
    private func runFirstWeekCascade() {
        guard !firstWeekHasCascaded else { return }
        Task { @MainActor in
            try? await Task.sleep(for: Self.titleRevealDelay)
            withAnimation(.easeOut(duration: 0.25)) {
                firstWeekRevealStage = 1
            }
            let pebbleCount = groupedPebbles.first?.value.count ?? 0
            for index in 0..<pebbleCount {
                try? await Task.sleep(for: Self.pebbleRevealStagger)
                withAnimation(.easeOut(duration: 0.25)) {
                    firstWeekRevealStage = index + 2
                }
            }
            firstWeekHasCascaded = true
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
