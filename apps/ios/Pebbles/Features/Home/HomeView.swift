import SwiftUI

/// The Home tab root (spec §1): the greeting, then the sections the later
/// parts add (shortcuts, wraps, the first five, About). Built from system
/// parts in the Spine's type and paper (D2).
struct HomeView: View {
    var onFirstLoad: () -> Void = {}

    @Environment(SupabaseService.self) private var supabase
    @Environment(TabNavigation.self) private var nav
    @State private var model = HomeModel()

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    // The greeting re-reads the clock every minute, so the band flips on the hour.
                    TimelineView(.periodic(from: .now, by: 60)) { context in
                        HomeGreetingView(lines: greetingLines(at: context.date))
                    }
                    .id(TabNavigation.scrollTopAnchor)
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
