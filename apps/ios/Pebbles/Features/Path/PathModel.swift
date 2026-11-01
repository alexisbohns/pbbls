import Foundation
import Observation
import os

/// The Path tab's state (#1130 §Data flow), the Path's first `@Observable` view
/// model (new pattern, named in the D3 PR). It takes over `PathView`'s inline
/// load, its refocus rule and its delete path, unchanged in behaviour, and
/// builds one `SpinePage` per week, memoised per load. `focusedPage` is the one
/// source of truth for the strip, the pager and the links.
///
/// Since #1226 it loads the server's strip (`path_strip`) beside the feed and
/// pages over the strip's focusable items, weeks and months (D2). The strip
/// comes through `wraps`, which the view sets and the tests fake; a strip
/// failure never fails the timeline, and a failed refresh keeps the last strip.
/// The feed's fetch and the delete are passed in as closures.
///
/// It holds each week's and month's wrap (`path_week_wrap`, `path_month_wrap`)
/// the way spec §7.1 asks: a page shows its wrap at once whenever one ever
/// loaded (this session, or the device's kept copy), and revalidates behind it.
/// `weekWraps` and `monthWraps` hold only what the pages show, and are written
/// only when a payload differs, so an unchanged revalidation invalidates no view; which
/// pages must be fetched again (a write since, a failure) is unobserved. Each
/// page's fetch is a task the model owns (Android's `wrapJobs`), so a swipe that
/// moves the focus never cancels one half-way.
@Observable @MainActor
final class PathModel {
    enum Phase: Equatable { case loading, failed, content }
    /// A delete ran (`done`), failed and was logged (`failed`), or was ignored
    /// because another was still running (`ignored`).
    enum DeleteOutcome: Equatable { case done, failed, ignored }

    private(set) var phase: Phase = .loading
    private(set) var moments: [MomentSummary] = []
    /// The feed's moments by lowercase id, built once per load: what the wraps' ids point at.
    private(set) var momentsById: [String: MomentSummary] = [:]
    /// The souls' names the pebbles carry, by lowercase id, for a soul the reference cache lacks.
    private(set) var pebbleSoulNames: [String: String] = [:]
    private(set) var entries: [WeekRollEntry] = []
    /// The server's strip; nil until it first loads, and the feed's weeks stand in.
    private(set) var strip: Strip?
    /// The strip as drawn: the server's items, with any week the feed holds and the strip does not.
    private(set) var stripItems: [StripItem] = []
    /// The pager's pages, the strip's focusable items in order.
    private(set) var pages: [PathPage] = []
    /// Each week's wrap as last fetched, by Monday: what its page shows.
    private(set) var weekWraps: [Date: WeekWrap] = [:]
    /// Each month's page as last fetched.
    private(set) var monthWraps: [CalendarMonth: MonthWrap] = [:]
    private(set) var focusedPage: PathPage
    /// weekStart → the reading day's index (D12), written only on a change.
    private(set) var readingDay: [Date: Int] = [:]
    let pressRequests = StonePressRequests()
    /// The slot VoiceOver returns to; stack E sets it when a held card closes.
    var returnFocus: UUID?
    /// What the month pages' intros have played this launch (#1241 D3).
    let introLedger = IntroLedger()
    private(set) var isDeleting = false
    /// Bumped whenever the memoised pages are dropped (a load, the reference
    /// cache's souls). `page(for:)` reads it, so a view that read a page before
    /// the souls arrived is invalidated; the pages themselves are not tracked.
    private(set) var pagesRevision = 0

    @ObservationIgnored private var spinePages: [Date: SpinePage] = [:]
    /// Pages whose shown wrap must be fetched again: a write since it loaded, or a failed fetch.
    @ObservationIgnored private var wrapsToRefetch: Set<PathPage> = []
    /// One running fetch per page.
    @ObservationIgnored private var wrapTasks: [PathPage: Task<Void, Never>] = [:]
    /// Bumped by every load: a fetch that started before a write lands stale.
    @ObservationIgnored private var wrapGeneration = 0
    /// Pages showing the device's kept copy: any fetched result replaces it.
    @ObservationIgnored private var keptPages: Set<PathPage> = []
    /// Each load takes the next number; only the latest one applies its result.
    @ObservationIgnored private var loadGeneration = 0
    @ObservationIgnored private var referenceNames: [String: String] = [:]
    @ObservationIgnored private var soulGlyphs: [String: [GlyphStroke]] = [:]
    @ObservationIgnored let calendar: Calendar
    /// The wraps' RPCs (`PathWrapService`); set by the view before the first load.
    @ObservationIgnored var wraps: (any PathWrapServicing)?
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private let locale: () -> Locale
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    init(calendar: Calendar = SpineCalendar.current, now: @escaping () -> Date = { Date() },
         locale: @escaping () -> Locale = { SpineStrings.dateLocale }) {
        self.calendar = calendar
        self.now = now
        self.locale = locale
        focusedPage = .week(WeekRollBuilder.weekStart(for: now(), calendar: calendar))
    }

    /// The focused week's Monday, nil while a month is focused.
    var focusedWeekStart: Date? { focusedPage.weekStart }

    var currentWeekStart: Date {
        WeekRollBuilder.weekStart(for: now(), calendar: calendar)
    }

    // MARK: - Load

    /// A cancelled load, or one a newer load overtook, changes nothing. The
    /// strip loads alongside, in the zone the feed buckets its days in.
    func load(_ fetch: () async throws -> [MomentSummary]) async {
        loadGeneration += 1
        let generation = loadGeneration
        let wraps = wraps, zone = calendar.timeZone, logger = logger
        async let loadedStrip = Self.fetchStrip(wraps, zone: zone, logger: logger)
        do {
            let result = try await fetch()
            let fresh = await loadedStrip
            guard !Task.isCancelled, generation == loadGeneration else { return }
            if let fresh {
                strip = fresh
            } else if strip == nil, let kept = await wraps?.cachedStrip(zone: zone) {
                // The device's last strip stands in for one that never loaded (spec §7.1).
                guard !Task.isCancelled, generation == loadGeneration else { return }
                strip = kept
            }
            apply(result)
            // A load follows a write or a return: every wrap fetched may be out of date.
            wrapGeneration += 1
            wrapsToRefetch.formUnion(weekWraps.keys.map(PathPage.week) + monthWraps.keys.map(PathPage.month))
            phase = .content
        } catch {
            // The strip's fetch is cancelled with the scope; the error need not wait for it.
            guard !Task.isCancelled, generation == loadGeneration else { return }
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            phase = .failed
        }
    }

    /// The strip, or nil (logged) when it fails or no service is set.
    private nonisolated static func fetchStrip(_ wraps: (any PathWrapServicing)?, zone: TimeZone,
                                               logger: Logger) async -> Strip? {
        guard let wraps else { return nil }
        do {
            return try await wraps.strip(zone: zone)
        } catch {
            // A newer load overtook this one: nothing failed.
            if !error.isCancellation {
                logger.error("path_strip failed: \(error.localizedDescription, privacy: .private)")
            }
            return nil
        }
    }

    /// Stack C's `MomentsRevision`: a moment changed on another tab or in a
    /// sheet. The Path's own changes reload through their own callbacks.
    func reloadIfChangedOutside(_ revision: MomentsRevision, _ fetch: () async throws -> [MomentSummary]) async {
        guard revision.changedOutside(.path) else { return }
        await load(fetch)
    }

    private func apply(_ result: [MomentSummary]) {
        moments = result
        momentsById = Dictionary(result.map { ($0.id.uuidString.lowercased(), $0) },
                                 uniquingKeysWith: { first, _ in first })
        pebbleSoulNames = Dictionary(result.compactMap(\.pebble).flatMap(\.souls).map {
            ($0.id.uuidString.lowercased(), $0.name)
        }, uniquingKeysWith: { first, _ in first })
        let today = now()
        entries = WeekRollBuilder.build(moments: result, calendar: calendar, today: today)
        stripItems = PathPages.items(strip: strip, entries: entries, today: today, calendar: calendar)
        pages = PathPages.pages(stripItems, calendar: calendar)
        spinePages = [:]
        pagesRevision += 1
        // Keep the focused page if it is still there, else the current week, else the closest.
        // A refocus is a page change, so it drops a pending press as `focus(_:)` does.
        guard let refocused = PathPages.refocused(pages, focused: focusedPage, today: today, calendar: calendar),
              refocused != focusedPage else { return }
        pressRequests.cancel()
        focusedPage = refocused
    }

    /// The reference cache's souls (`ReferenceDataService.souls`), keyed by lowercase id.
    func updateSouls(names: [String: String], glyphs: [String: [GlyphStroke]]) {
        referenceNames = names
        soulGlyphs = glyphs
        spinePages = [:]
        pagesRevision += 1
    }

    // MARK: - Focus

    /// A strip tap, a link, a settled swipe. An equal page is ignored, so the
    /// pager's binding cannot loop; a page change drops a pending press.
    func focus(_ page: PathPage) {
        guard page != focusedPage else { return }
        pressRequests.cancel()
        focusedPage = page
    }

    /// A week link or a week's own focus.
    func focus(_ weekStart: Date) {
        focus(.week(weekStart))
    }

    /// After Capture: the week that holds the moment, asked of the roll's own
    /// bucketing rather than recomputed. A silent no-op until the reload has it.
    func focusWeek(containing momentId: UUID) {
        guard let entry = entries.first(where: { $0.moments.contains { $0.id == momentId } }) else { return }
        focus(entry.weekStart)
    }

    /// Home's week columns (#1382 D9): the Monday's page, or now when the Path has none.
    func focus(weekStarting day: CalendarDay) {
        guard let start = day.date(in: calendar.timeZone), pages.contains(.week(start)) else {
            focusCurrentWeek()
            return
        }
        focus(.week(start))
    }

    /// The Path tab reselected at its root: "top of Path" means now.
    func focusCurrentWeek() {
        let current = PathPage.week(currentWeekStart)
        guard pages.contains(current) else { return }
        focus(current)
    }

    /// Nil when no day has reached the reading line (the page reads its first
    /// day again, as Android does); written only on a change.
    func setReadingDay(_ index: Int?, for weekStart: Date) {
        guard readingDay[weekStart] != index else { return }
        readingDay[weekStart] = index
    }

    // MARK: - Pages

    /// A week page; a week the strip focuses and the feed does not hold yet
    /// (the feed lags it by one fetch) draws with no moments.
    func page(for weekStart: Date) -> SpinePage? {
        _ = pagesRevision
        if let cached = spinePages[weekStart] { return cached }
        let entry = entries.first(where: { $0.weekStart == weekStart })
        guard entry != nil || pages.contains(.week(weekStart)) else { return nil }
        let moments = entry?.moments ?? []
        let locale = locale()
        let names = names(for: moments)
        let week = SpineWeekBuilder.spineWeek(weekStart: weekStart, moments: moments, today: now(),
                                              calendar: calendar)
        let page = SpinePage(
            weekStart: weekStart,
            week: week,
            words: WeekOpening.weekWords(weekStart: weekStart, calendar: calendar, locale: locale),
            contents: SpineWeekBuilder.readingOrder(week),
            next: WeekRollBuilder.next(of: weekStart, in: entries).map(neighbour),
            // Memoised until the next load: across the Sunday → Monday roll it stays stale without one.
            isCurrent: weekStart == currentWeekStart,
            soulNames: names,
            soulGlyphs: soulGlyphs
        )
        spinePages[weekStart] = page
        return page
    }

    /// The photos of a page's weeks and its pager neighbours', for the ratio
    /// prefetch: a month's neighbours are weeks too.
    func photoPaths(around page: PathPage) -> [String] {
        guard let index = pages.firstIndex(of: page) else { return [] }
        let weeks = Set(pages[max(0, index - 1)...min(pages.count - 1, index + 1)].compactMap(\.weekStart))
        return entries.filter { weeks.contains($0.weekStart) }.flatMap(\.moments).compactMap(\.firstSnapPath)
    }

    private func neighbour(_ entry: WeekRollEntry) -> WeekNeighbour {
        WeekNeighbour(number: WeekOpening.isoWeekNumber(entry.weekStart, timeZone: calendar.timeZone),
                      weekStart: entry.weekStart)
    }

    /// The reference cache's names, then the names `path_moments` carries on each pebble.
    private func names(for moments: [MomentSummary]) -> [String: String] {
        var names = referenceNames
        for soul in moments.compactMap(\.pebble).flatMap(\.souls) {
            let key = soul.id.uuidString.lowercased()
            names[key] = names[key] ?? soul.name
        }
        return names
    }

    // MARK: - Delete

    /// Runs `perform` (today's `MomentDeletion.perform`), logged on a failure.
    /// A second confirm while one runs is ignored, as today, and says so.
    func delete(_ perform: () async throws -> Void) async -> DeleteOutcome {
        guard !isDeleting else { return .ignored }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await perform()
            return .done
        } catch {
            logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
            return .failed
        }
    }
}

extension PathModel {
    // MARK: - Wraps

    /// Starts the fetches the focused page and its neighbours need, weeks and
    /// months alike (never loaded, stale or failed), and waits for them, and for
    /// any already running. The view calls it on each focus and after each load;
    /// cancelling the caller stops the wait, never the fetches.
    func refreshWraps() async {
        for task in scheduleWraps() { await task.value }
    }

    @discardableResult
    private func scheduleWraps() -> [Task<Void, Never>] {
        guard let wraps, let index = pages.firstIndex(of: focusedPage) else { return [] }
        var running: [Task<Void, Never>] = []
        for page in pages[max(0, index - 1)...min(pages.count - 1, index + 1)] {
            if let task = wrapTasks[page] {
                running.append(task)
                continue
            }
            guard !isShown(page) || wrapsToRefetch.contains(page) else { continue }
            let generation = wrapGeneration
            let task = Task { await self.fetchWrap(page, wraps: wraps, generation: generation) }
            wrapTasks[page] = task
            running.append(task)
        }
        return running
    }

    private func fetchWrap(_ page: PathPage, wraps: any PathWrapServicing, generation: Int) async {
        let zone = calendar.timeZone
        let failure: Error?
        switch page {
        case let .week(start):
            let day = CalendarDay(start, in: zone)
            failure = await load(page, key: start, in: \.weekWraps, generation: generation,
                                 kept: { await wraps.cachedWeekWrap(day, zone: zone) },
                                 fetched: { try await wraps.weekWrap(day, zone: zone) })
        case let .month(month):
            failure = await load(page, key: month, in: \.monthWraps, generation: generation,
                                 kept: { await wraps.cachedMonthWrap(month, zone: zone) },
                                 fetched: { try await wraps.monthWrap(month, zone: zone) })
        }
        wrapTasks[page] = nil
        if let failure {
            // The last good wrap stays on screen; the next demand retries.
            wrapsToRefetch.insert(page)
            logger.error("\(page.rpcName, privacy: .public) failed: \(failure.localizedDescription, privacy: .private)")
        } else if generation == wrapGeneration {
            wrapsToRefetch.remove(page)
        } else {
            // It started before a write: still stale, asked for again.
            wrapsToRefetch.insert(page)
            scheduleWraps()
        }
    }

    // swiftlint:disable function_parameter_count
    /// One page's wrap, weeks and months alike (Android's `loadWrap`): the kept
    /// copy first when nothing shows yet, then the server's. A result that
    /// started before a write still beats a kept copy, never a fresher one.
    /// Returns the error, if any.
    private func load<Key: Hashable, Wrap: Equatable>(
        _ page: PathPage, key: Key, in shown: ReferenceWritableKeyPath<PathModel, [Key: Wrap]>, generation: Int,
        kept: () async -> Wrap?, fetched: () async throws -> Wrap
    ) async -> Error? {
        if self[keyPath: shown][key] == nil, let copy = await kept(), self[keyPath: shown][key] == nil {
            self[keyPath: shown][key] = copy
            keptPages.insert(page)
        }
        do {
            let wrap = try await fetched()
            if generation == wrapGeneration || self[keyPath: shown][key] == nil || keptPages.contains(page) {
                keptPages.remove(page)
                if self[keyPath: shown][key] != wrap { self[keyPath: shown][key] = wrap }
            }
            return nil
        } catch {
            return error
        }
    }
    // swiftlint:enable function_parameter_count

    private func isShown(_ page: PathPage) -> Bool {
        switch page {
        case let .week(start): return weekWraps[start] != nil
        case let .month(month): return monthWraps[month] != nil
        }
    }
}

extension Error {
    /// A cancelled task or request: a newer one overtook it, so nothing failed.
    var isCancellation: Bool {
        if self is CancellationError { return true }
        if let url = self as? URLError, url.code == .cancelled { return true }
        return false
    }
}
