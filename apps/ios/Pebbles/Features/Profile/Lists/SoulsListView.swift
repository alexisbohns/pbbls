import SwiftUI
import os

struct SoulsListView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(ReferenceDataService.self) private var refs
    @Environment(TabNavigation.self) private var nav
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(MomentsRevision.self) private var revision
    // The People tab is the chooser in browse mode (souls spec Summary): the
    // Usual tiles, everyone A to Z, the lens and the plus at the bottom.
    @State private var chooser = SoulChooserModel(mode: .browse, souls: [])
    @State private var isLoading = true
    @State private var loadError: String?
    @State private var pendingDeletion: SoulWithGlyph?
    @State private var deleteError: String?
    @State private var promptError: String?
    // The People onboarding (spec §2, D11): read only while the grid is short
    // (D-plan-3); the device's "Not now" ends it.
    @AppStorage(PeopleOnboardingState.storageKey) private var promptsDismissed = false
    @State private var prompts: PeoplePrompts?
    @State private var quickMoment = false
    @State private var editing: EditTarget?

    /// The pebble a prompt's "who?" opens, with its moment.
    struct EditTarget: Identifiable {
        let momentId: UUID
        let pebble: MomentDetail.Pebble
        var id: UUID { momentId }
    }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "profile.souls")

    var body: some View {
        content
            .pebblesToolbarTitle("Souls")
            .toolbar {
                // People reaches Connections from here (#903 §2): the hub
                // tile's icon and copy.
                ToolbarItem(placement: .topBarLeading) {
                    PebbleToolbarButton(action: { nav.people.append(.connections) }) {
                        PbblsIconImage(.people, .toolbar)
                    }
                    .accessibilityLabel("Circle")
                }
            }
            .navigationDestination(for: PeopleRoute.self) { route in
                switch route {
                case .soul(let id):
                    SoulRoute(id: id, known: chooser.souls, onChanged: {
                        Task {
                            await load()
                            await refs.refreshSouls()
                        }
                    })
                case .connections:
                    ConnectionsListView()
                }
            }
            .task { await load() }
            .sheet(isPresented: $quickMoment) {
                QuickMomentSheet(onSaved: { _, _ in
                    revision.bump(from: .people)
                    Task { await load() }
                })
            }
            .sheet(item: $editing) { target in
                EditPebbleSheet(momentId: target.momentId, pebble: target.pebble, scrollsToSouls: true,
                                onSaved: {
                                    revision.bump(from: .people)
                                    Task {
                                        await load()
                                        await refs.refreshSouls()
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
            ) { soul in
                Button("Delete", role: .destructive) {
                    TapHaptics.play(.warning)
                    Task { await delete(soul) }
                }
                Button("Cancel", role: .cancel) {
                    pendingDeletion = nil
                }
            } message: { _ in
                Text("Linked pebbles stay; only the soul and its links are removed.")
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
            .alert(
                "Couldn't open",
                isPresented: Binding(
                    get: { promptError != nil },
                    set: { if !$0 { promptError = nil } }
                ),
                presenting: promptError
            ) { _ in
                Button("OK", role: .cancel) { promptError = nil }
            } message: { message in
                Text(message)
            }
            .pebblesScreen()
            // Home's checklist sends the user here to add a first soul: the
            // chooser's field opens, typing a new name offers the Add row.
            .onAppear {
                if nav.takeIntent(for: .people) == .createSoul { chooser.isSearching = true }
            }
    }

    @ViewBuilder
    private var content: some View {
        // A tab root reloads on every return; keep the rows (and the scroll
        // position) on screen while it does.
        if isLoading && chooser.souls.isEmpty {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let loadError, chooser.souls.isEmpty {
            ContentUnavailableView {
                Label { Text("Couldn't load souls") } icon: { PbblsIconImage(.people, .hero) }
            } description: {
                Text(loadError)
            } actions: {
                Button("Retry") { Task { await load() } }
            }
        } else {
            ScrollViewReader { proxy in
                SoulChooser(
                    model: chooser,
                    title: nil,
                    onOpen: { nav.people.append(.soul($0.id)) },
                    onCreated: { nav.people.append(.soul($0.id)) },
                    onDeleteRequest: { pendingDeletion = $0 },
                    header: {
                        let state = prompts.map { PeopleOnboardingState.make($0, dismissed: promptsDismissed) }
                        if let state, state != .done {
                            onboarding(state)
                                .padding(.horizontal, Spacing.lg)
                                .padding(.bottom, Spacing.md)
                        } else if chooser.souls.isEmpty {
                            // Nothing to draw and nothing to list (the prompts
                            // failed, were dismissed, or are done): never a
                            // blank tab, never a spinner.
                            ContentUnavailableView {
                                Label { Text("No souls yet") } icon: { PbblsIconImage(.people, .hero) }
                            } description: {
                                Text("People and beings you tag on your pebbles will appear here.")
                            }
                        }
                    }
                )
                .id(TabNavigation.scrollTopAnchor)
                .onChange(of: nav.scrollToTop[.people, default: 0]) {
                    withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
                }
            }
        }
    }

    private func onboarding(_ state: PeopleOnboardingState) -> some View {
        PeopleOnboardingView(
            state: state,
            palette: { id in id.flatMap { palettes.palette(for: $0) } },
            onRecord: { quickMoment = true },
            onWho: { prompt in Task { await openPebble(of: prompt) } },
            onNotNow: { promptsDismissed = true }
        )
    }

    private func load() async {
        isLoading = true
        loadError = nil
        // A warm tab shows the cache while its own fetch (with counts) lands.
        if chooser.souls.isEmpty, !refs.souls.isEmpty { chooser.replaceSouls(refs.souls) }
        do {
            let result: [SoulWithGlyph] = try await supabase.client
                .from("souls")
                .select("id, name, glyph_id, glyphs(id, name, strokes, view_box), pebbles_count:pebble_souls(count)")
                .order("name", ascending: true)
                .execute()
                .value
            chooser.replaceSouls(result)
            // D-plan-3: a veteran's grid pays nothing for the onboarding.
            if result.count < 3 {
                await loadPrompts()
            } else {
                prompts = nil
            }
        } catch {
            // Cancelled by a tab switch, not failed.
            guard !Task.isCancelled else { return }
            logger.error("souls fetch failed: \(error.localizedDescription, privacy: .private)")
            self.loadError = "Something went wrong. Please try again."
        }
        self.isLoading = false
    }

    private func loadPrompts() async {
        do {
            prompts = try await PeopleService(client: supabase.client).prompts(limit: 3)
        } catch {
            // Cancelled, not failed: `.task` reruns the read on reappear.
            guard !Task.isCancelled else { return }
            logger.error("people_prompts failed: \(error.localizedDescription, privacy: .private)")
            // Keep the last prompts, if any; the list or the empty state stands.
        }
    }

    /// "Who?": the prompt's pebble opens for editing on its Souls section (D-plan-1).
    private func openPebble(of prompt: PeoplePrompts.Prompt) async {
        do {
            let detail = try await MomentsService(client: supabase.client).moment(id: prompt.momentId)
            if let pebble = detail.pebble {
                editing = EditTarget(momentId: detail.id, pebble: pebble)
            } else {
                logger.error("prompt \(prompt.pebbleId, privacy: .public) has no pebble on \(detail.id, privacy: .public)")
                promptError = String(localized: "people.prompt.openFailed")
            }
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("moment(id:) for a prompt failed: \(error.localizedDescription, privacy: .private)")
            promptError = String(localized: "people.prompt.openFailed")
        }
    }

    private func delete(_ soul: SoulWithGlyph) async {
        pendingDeletion = nil
        do {
            try await supabase.client
                .from("souls")
                .delete()
                .eq("id", value: soul.id)
                .execute()
            await load()
            await refs.refreshSouls()
        } catch {
            logger.error("delete soul failed: \(error.localizedDescription, privacy: .private)")
            deleteError = "Something went wrong. Please try again."
        }
    }
}

#Preview {
    let supabase = SupabaseService()
    return NavigationStack {
        SoulsListView()
            .environment(supabase)
            .environment(ReferenceDataService(client: supabase.client))
            .environment(TabNavigation())
            .environment(CompleteDraftStore())
            .environment(MomentsRevision())
            .environment(EmotionPaletteService(client: supabase.client))
            .environment(AchievementsService(client: supabase.client))
    }
}
