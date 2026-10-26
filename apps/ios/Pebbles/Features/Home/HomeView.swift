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
    @State private var cameraError: String?
    @State private var naming: MomentDetail?
    @State private var completing: CompleteTarget?

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
                        onPebble: { showsCapture = true }
                    )

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
        .sheet(isPresented: $showsQuick) {
            QuickMomentSheet(onSaved: { id, exit in
                revision.bump(from: .home)
                if exit == .open { completing = CompleteTarget(momentId: id, name: nil) }
            })
        }
        .fullScreenCover(item: $completing) { target in
            CompleteFlowView(momentId: target.momentId, momentName: target.name,
                             onCompleted: { _ in revision.bump(from: .home) })
        }
        .fullScreenCover(isPresented: $showsCapture) {
            CaptureFlowView(onMomentChanged: { _ in revision.bump(from: .home) }, chainsComplete: true)
        }
        .fullScreenCover(isPresented: $showsCamera) {
            CameraPicker(
                onCapture: { data, shutter in
                    showsCamera = false
                    Task { await capture(data, at: shutter) }
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
            case .guide(let slug): Text(verbatim: slug) // Part 5 replaces this with GuidePage
            }
        }
        .task {
            if model.service == nil { model.service = HomeService(client: supabase.client) }
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

    private func openCamera() {
        if CameraPicker.isAvailable { showsCamera = true } else { showsCameraUnavailable = true }
    }

    private func capture(_ data: Data, at shutter: Date) async {
        cameraError = nil
        let coordinator = CameraMomentCoordinator(client: supabase.client, userId: userId)
        camera = coordinator
        await coordinator.capture(data, at: shutter)
        if coordinator.landed != nil {
            achievements.fireCheck()
            revision.bump(from: .home)
        }
        cameraError = coordinator.error
    }

    private func name(_ landed: CameraMomentCoordinator.LandedMoment) {
        dismissToast()
        Task {
            do {
                naming = try await MomentsService(client: supabase.client).moment(id: landed.momentId)
            } catch {
                Logger(subsystem: "app.pbbls.ios", category: "home.camera")
                    .error("moment(id:) failed: \(error.localizedDescription, privacy: .private)")
                cameraError = String(localized: "home.quick.error")
            }
        }
    }

    private func dismissToast() { camera = nil }

    private var userId: UUID { supabase.session?.user.id ?? UUID() }

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
