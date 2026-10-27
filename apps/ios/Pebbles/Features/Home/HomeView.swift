import SwiftUI
import os

/// The Home tab root (spec §1): the greeting, then the sections the later
/// parts add (shortcuts, wraps, the first five, About). Built from system
/// parts in the Spine's type and paper (D2).
struct HomeView: View {
    var onFirstLoad: () -> Void = {}

    @Environment(SupabaseService.self) private var supabase
    @Environment(TabNavigation.self) private var nav
    @Environment(MomentsRevision.self) private var revision
    @State private var model = HomeModel()
    @State private var showsQuick = false
    @State private var showsCapture = false
    @Environment(AchievementsService.self) private var achievements
    @State private var showsCamera = false
    @State private var showsCameraUnavailable = false
    @State private var camera: CameraMomentCoordinator?
    @State private var pendingComplete: UUID?
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "home")
    @State private var cameraError: String?
    @State private var naming: MomentDetail?
    @State private var completing: CompleteTarget?
    @State private var openWrap: CalendarMonth?
    @State private var taskSheet: HomeChecklist.Task?
    /// The task whose flow opens once its sheet is gone, so the two never race.
    @State private var pendingTask: HomeChecklist.Task?
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var references

    private struct CompleteTarget: Identifiable, Equatable {
        let momentId: UUID
        let name: String?
        var id: UUID { momentId }
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    // The greeting re-reads the clock every minute, so the band flips on the hour.
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        HomeGreetingView(lines: greetingLines(at: context.date))
                    }
                    .id(TabNavigation.scrollTopAnchor)

                    HomeShortcuts(
                        onCamera: openCamera,
                        onQuick: { showsQuick = true },
                        onPebble: { showsCapture = true },
                        cameraDisabled: camera?.isSaving == true
                    )

                    if model.wraps != nil || model.wrapsError != nil {
                        VStack(alignment: .leading, spacing: Spacing.sm) {
                            HomeWrapsRow(months: model.wraps?.months ?? [], lookups: lookups,
                                         liveMonth: liveMonth, onOpen: { openWrap = $0.month })
                            if let wrapsError = model.wrapsError {
                                HStack(alignment: .firstTextBaseline) {
                                    Text(verbatim: wrapsError)
                                        .pebblesFont(.callout)
                                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                                    Spacer(minLength: 0)
                                    Button("home.wraps.retry") { Task { await model.loadWraps() } }
                                        .pebblesFont(.callout)
                                }
                            }
                        }
                    }

                    // HomeChecklistView draws nothing for a done or unavailable checklist.
                    if let checklist = model.checklist {
                        VStack(alignment: .leading, spacing: Spacing.sm) {
                            HomeChecklistView(checklist: checklist, isClaiming: model.isClaiming,
                                              onTask: { taskSheet = $0 },
                                              onClaim: { Task { await model.claim() } })
                            if let claimError = model.claimError {
                                Text(verbatim: claimError)
                                    .pebblesFont(.callout)
                                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            }
                        }
                    }

                    // Spec §1.6: the About header is static; the rows land with the read.
                    GuidesList(guides: model.guides)

                    if camera?.isSaving == true {
                        Text("home.camera.saving")
                            .spineType(.summary)
                            .italic()
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    }

                    if let cameraError {
                        HStack(alignment: .firstTextBaseline) {
                            Text(verbatim: cameraError)
                                .pebblesFont(.callout)
                                .foregroundStyle(Color.pebbles.error)
                            Spacer(minLength: 0)
                            Button { self.cameraError = nil } label: {
                                Image(systemName: "xmark").foregroundStyle(Color.pebbles.onSurfaceVariant)
                            }
                            .accessibilityLabel(Text("Close"))
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, Spacing.lg)
                .padding(.top, Spacing.md)
                .padding(.bottom, MonthLayout.pageBottom)
            }
            .onChange(of: nav.scrollToTop[.home, default: 0]) {
                withAnimation { proxy.scrollTo(TabNavigation.scrollTopAnchor, anchor: .top) }
            }
        }
        .pebblesScreen()
        // Like the Path root: the greeting is the title, so the bar stays hidden.
        .toolbar(.hidden, for: .navigationBar)
        .sheet(isPresented: $showsQuick, onDismiss: {
            // Presented once the sheet is gone, so the two presentations never race.
            if let id = pendingComplete {
                pendingComplete = nil
                completing = CompleteTarget(momentId: id, name: nil)
            }
        }) {
            QuickMomentSheet(onSaved: { id, exit in
                revision.bump(from: .home)
                reloadChecklist()
                if exit == .open { pendingComplete = id }
            })
        }
        .fullScreenCover(item: $completing) { target in
            CompleteFlowView(momentId: target.momentId, momentName: target.name,
                             onCompleted: { _ in
                                 revision.bump(from: .home)
                                 reloadChecklist()
                             })
        }
        .fullScreenCover(isPresented: $showsCapture) {
            CaptureFlowView(onMomentChanged: { _ in
                revision.bump(from: .home)
                reloadChecklist()
            }, chainsComplete: true)
        }
        .fullScreenCover(isPresented: $showsCamera) {
            CameraPicker(
                onCapture: { image, shutter in
                    showsCamera = false
                    Task { await capture(image, at: shutter) }
                },
                onCancel: { showsCamera = false }
            )
            .ignoresSafeArea()
        }
        .alert("home.camera.unavailable", isPresented: $showsCameraUnavailable) {
            Button("OK", role: .cancel) {}
        } message: {
            Text("home.camera.unavailableBody")
        }
        .sheet(item: $openWrap) { month in
            HomeWrapSheet(monthId: month, lookups: lookups, model: model, onWeek: { _ in nav.go(.path) })
        }
        .sheet(item: $taskSheet, onDismiss: {
            if let task = pendingTask {
                pendingTask = nil
                go(task)
            }
        }) { task in
            ChecklistTaskSheet(task: task, onGo: { pendingTask = task })
        }
        .sheet(item: $naming) { detail in
            EditMomentSheet(detail: detail, onSaved: { revision.bump(from: .home) })
        }
        .safeAreaInset(edge: .bottom) {
            if let landed = camera?.landed {
                MomentToast(landed: landed, onName: { name(landed) }, onDismiss: { dismissToast() })
                    .padding(.horizontal, Spacing.lg)
                    .padding(.bottom, Spacing.sm)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.snappy, value: camera?.landed)
        .navigationDestination(for: HomeRoute.self) { route in
            switch route {
            case .guide(let slug): guideDestination(slug)
            }
        }
        // Reselecting Home inside the TabView re-runs this task (the root re-appears), which
        // also ticks what the user did on a tab the checklist sent them to. `onFirstLoad()`
        // re-firing is harmless: its latch (`firstRootLoaded`) only sets once.
        .task {
            if model.service == nil { model.service = HomeService(client: supabase.client) }
            if model.achievements == nil { model.achievements = achievements }
            // Spec §1.6: the loader waits at most 1.5 s for the profile; the load
            // goes on in its own Task so the name still lands after the hand-off.
            let load = Task { await model.load() }
            let deadline = Task { try? await Task.sleep(for: .seconds(1.5)) }
            await withTaskGroup(of: Void.self) { group in
                group.addTask { await load.value }
                group.addTask { await deadline.value }
                await group.next()
                group.cancelAll()
            }
            deadline.cancel()
            onFirstLoad()
        }
    }

    /// A pushed guide, by slug: the list may not have landed yet when a snapshot
    /// restores the route, so the page waits on it rather than reporting a miss.
    /// A failed first read is a miss, not a spinner that never ends.
    private func guideResolution(_ slug: String) -> RouteResolution<Guide> {
        guard let guides = model.guides else { return model.guidesFailed ? .missing : .loading }
        return guides.first { $0.slug == slug }.map { .found($0) } ?? .missing
    }

    @ViewBuilder
    private func guideDestination(_ slug: String) -> some View {
        switch guideResolution(slug) {
        case .loading:
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .pebblesScreen()
        case .found(let guide):
            GuidePage(guide: guide)
        case .missing:
            ContentUnavailableView("home.about.missing", systemImage: "book.closed")
                .pebblesScreen()
        }
    }

    private var lookups: WrapLookups { HomeLookups.make(palettes: palettes, refs: references) }

    /// The month the clock is in, in the device's zone.
    private var liveMonth: CalendarMonth {
        let parts = SpineCalendar.current.dateComponents([.year, .month], from: .now)
        return CalendarMonth(year: parts.year ?? 1970, month: parts.month ?? 1)
    }

    private func openCamera() {
        if CameraPicker.isAvailable { showsCamera = true } else { showsCameraUnavailable = true }
    }

    private func capture(_ image: UIImage, at shutter: Date) async {
        cameraError = nil
        guard let userId else {
            logger.error("camera moment: no session")
            cameraError = String(localized: "home.quick.error")
            return
        }
        let coordinator = camera ?? CameraMomentCoordinator(client: supabase.client, userId: userId)
        camera = coordinator
        await coordinator.capture(image, at: shutter)
        if coordinator.landed != nil {
            achievements.fireCheck()
            revision.bump(from: .home)
            reloadChecklist()
        }
        cameraError = coordinator.error
    }

    private func name(_ landed: CameraMomentCoordinator.LandedMoment) {
        dismissToast()
        Task {
            do {
                naming = try await MomentsService(client: supabase.client).moment(id: landed.momentId)
            } catch {
                logger.error("moment(id:) failed: \(error.localizedDescription, privacy: .private)")
                cameraError = String(localized: "home.toast.openFailed")
            }
        }
    }

    private func reloadChecklist() {
        Task { await model.loadChecklist() }
    }

    /// A task sheet's call to action, run once the sheet is gone.
    private func go(_ task: HomeChecklist.Task) {
        switch task {
        case .moment: showsQuick = true
        case .pebble: Task { await completeNewestBare() }
        case .collection: nav.go(.collections, intent: .createCollection)
        case .soul: nav.go(.people, intent: .createSoul)
        case .glyph: nav.go(.you, intent: .carveGlyph)
        }
    }

    /// The pebble task completes the newest bare moment; with none (or an
    /// unreadable Path) it records one and chains into Complete. The row is only
    /// undone for a user who has never completed a pebble, so the first moment of
    /// the feed is the answer and the read is bounded in practice.
    private func completeNewestBare() async {
        do {
            let moments = try await MomentsService(client: supabase.client).pathMoments()
            if let bare = moments.first(where: { !$0.isCompleted }) {
                completing = CompleteTarget(momentId: bare.id, name: bare.name)
                return
            }
        } catch {
            logger.error("path_moments for the pebble task failed: \(error.localizedDescription, privacy: .private)")
        }
        showsCapture = true
    }

    private func dismissToast() { camera?.clearLanded() }

    private var userId: UUID? { supabase.session?.user.id }

    private func greetingLines(at now: Date) -> HomeGreeting.Lines {
        HomeGreeting.lines(at: now, name: model.name, zone: SpineCalendar.current.timeZone, locale: SpineStrings.appLocale)
    }
}

/// The greeting block alone, so the reference pins it without a service.
struct HomeGreetingView: View {
    let lines: HomeGreeting.Lines

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(verbatim: lines.dateline)
                .spineType(.kicker)
                .foregroundStyle(Color.pebbles.outline)
            Text(verbatim: lines.title)
                .spineType(.bareTitle)
                .foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: lines.line)
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(lines.title). \(lines.line). \(lines.dateline)"))
    }
}
