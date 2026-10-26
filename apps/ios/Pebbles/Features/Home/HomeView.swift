import SwiftUI

/// The Home tab root (spec §1): the greeting, then the sections the later
/// parts add (shortcuts, wraps, the first five, About). Built from system
/// parts in the Spine's type and paper (D2).
struct HomeView: View {
    var onFirstLoad: () -> Void = {}

    @Environment(SupabaseService.self) private var supabase
    @Environment(TabNavigation.self) private var nav
    @State private var model = HomeModel()
    @State private var now = Date()
    private let minute = Timer.publish(every: 60, on: .main, in: .common).autoconnect()

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.xl) {
                    HomeGreetingView(lines: greetingLines)
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
            await model.load()
            onFirstLoad()
        }
        .onReceive(minute) { now = $0 }
    }

    private var greetingLines: HomeGreeting.Lines {
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
                .font(.largeTitle.weight(.regular))
                .foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: lines.line)
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .accessibilityElement(children: .combine)
    }
}
