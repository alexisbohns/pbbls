import Foundation
import Observation
import os

/// The Path tab's state (#1130 §Data flow), the Path's first `@Observable` view
/// model (new pattern, named in the D3 PR). It takes over `PathView`'s inline
/// load, its refocus rule and its delete path, unchanged in behaviour, and
/// builds one `SpinePage` per week, memoised per load. `focusedWeekStart` is the
/// one source of truth for the roll, the pager and the links.
///
/// The fetch and the delete are passed in as closures, so the model needs no
/// Supabase client and the tests need no fake service.
@Observable @MainActor
final class PathModel {
    enum Phase: Equatable { case loading, failed, content }

    private(set) var phase: Phase = .loading
    private(set) var moments: [MomentSummary] = []
    private(set) var entries: [WeekRollEntry] = []
    private(set) var focusedWeekStart: Date
    /// weekStart → the reading day's index (D12), written only on a change.
    private(set) var readingDay: [Date: Int] = [:]
    let pressRequests = StonePressRequests()
    /// The slot VoiceOver returns to; stack E sets it when a held card closes.
    var returnFocus: UUID?
    private(set) var isDeleting = false
    /// Bumped whenever the memoised pages are dropped (a load, the reference
    /// cache's souls). `page(for:)` reads it, so a view that read a page before
    /// the souls arrived is invalidated; the pages themselves are not tracked.
    private(set) var pagesRevision = 0

    @ObservationIgnored private var pages: [Date: SpinePage] = [:]
    @ObservationIgnored private var referenceNames: [String: String] = [:]
    @ObservationIgnored private var soulGlyphs: [String: [GlyphStroke]] = [:]
    @ObservationIgnored let calendar: Calendar
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private let locale: () -> Locale
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "path")

    init(calendar: Calendar = SpineCalendar.current, now: @escaping () -> Date = { Date() },
         locale: @escaping () -> Locale = { SpineStrings.dateLocale }) {
        self.calendar = calendar
        self.now = now
        self.locale = locale
        focusedWeekStart = WeekRollBuilder.weekStart(for: now(), calendar: calendar)
    }

    var currentWeekStart: Date {
        WeekRollBuilder.weekStart(for: now(), calendar: calendar)
    }

    // MARK: - Load

    /// A cancelled load (a newer one replaced it) changes nothing.
    func load(_ fetch: () async throws -> [MomentSummary]) async {
        do {
            let result = try await fetch()
            guard !Task.isCancelled else { return }
            apply(result)
            phase = .content
        } catch {
            guard !Task.isCancelled else { return }
            logger.error("path fetch failed: \(error.localizedDescription, privacy: .private)")
            phase = .failed
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
        entries = WeekRollBuilder.build(moments: result, calendar: calendar, today: now())
        pages = [:]
        pagesRevision += 1
        // Keep the focused week if it is still there, else the current week, else the closest.
        guard !entries.contains(where: { $0.weekStart == focusedWeekStart }) else { return }
        let current = currentWeekStart
        if entries.contains(where: { $0.weekStart == current }) {
            focusedWeekStart = current
        } else if let closest = entries.min(by: { lhs, rhs in
            let focused = focusedWeekStart
            return abs(lhs.weekStart.timeIntervalSince(focused)) < abs(rhs.weekStart.timeIntervalSince(focused))
        }) {
            focusedWeekStart = closest.weekStart
        }
    }

    /// The reference cache's souls (`ReferenceDataService.souls`), keyed by lowercase id.
    func updateSouls(names: [String: String], glyphs: [String: [GlyphStroke]]) {
        referenceNames = names
        soulGlyphs = glyphs
        pages = [:]
        pagesRevision += 1
    }

    // MARK: - Focus

    /// A roll tap, a link, a settled swipe. An equal week is ignored, so the
    /// pager's binding cannot loop; a page change drops a pending press.
    func focus(_ weekStart: Date) {
        guard weekStart != focusedWeekStart else { return }
        pressRequests.cancel()
        focusedWeekStart = weekStart
    }

    /// After Capture: the week that holds the moment, asked of the roll's own
    /// bucketing rather than recomputed. A silent no-op until the reload has it.
    func focusWeek(containing momentId: UUID) {
        guard let entry = entries.first(where: { $0.moments.contains { $0.id == momentId } }) else { return }
        focus(entry.weekStart)
    }

    /// The Path tab reselected at its root: "top of Path" means now.
    func focusCurrentWeek() {
        let current = currentWeekStart
        guard entries.contains(where: { $0.weekStart == current }) else { return }
        focus(current)
    }

    func setReadingDay(_ index: Int, for weekStart: Date) {
        guard readingDay[weekStart] != index else { return }
        readingDay[weekStart] = index
    }

    // MARK: - Pages

    func page(for weekStart: Date) -> SpinePage? {
        _ = pagesRevision
        if let cached = pages[weekStart] { return cached }
        guard let index = entries.firstIndex(where: { $0.weekStart == weekStart }) else { return nil }
        let entry = entries[index]
        let locale = locale()
        let names = names(for: entry.moments)
        let week = SpineWeekBuilder.spineWeek(weekStart: weekStart, moments: entry.moments, today: now(),
                                              calendar: calendar)
        let page = SpinePage(
            weekStart: weekStart,
            week: week,
            words: WeekOpening.weekWords(weekStart: weekStart, calendar: calendar, locale: locale),
            persons: PersonsOfWeek.personsOfWeek(entry.moments, names: names, weekStart: weekStart,
                                                 calendar: calendar, locale: locale),
            contents: SpineWeekBuilder.readingOrder(week),
            previous: index > 0 ? neighbour(entries[index - 1]) : nil,
            next: index + 1 < entries.count ? neighbour(entries[index + 1]) : nil,
            isCurrent: weekStart == currentWeekStart,
            soulNames: names,
            soulGlyphs: soulGlyphs
        )
        pages[weekStart] = page
        return page
    }

    /// The photos of the focused week and its pager neighbours, for the ratio prefetch.
    func photoPaths(around weekStart: Date) -> [String] {
        guard let index = entries.firstIndex(where: { $0.weekStart == weekStart }) else { return [] }
        return entries[max(0, index - 1)...min(entries.count - 1, index + 1)]
            .flatMap(\.moments).compactMap(\.firstSnapPath)
    }

    private func neighbour(_ entry: WeekRollEntry) -> WeekNeighbour {
        WeekNeighbour(number: calendar.component(.weekOfYear, from: entry.weekStart), weekStart: entry.weekStart)
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

    /// Runs `perform` (today's `MomentDeletion.perform`); false, logged, on a
    /// failure. A second confirm while one runs is ignored, as today.
    func delete(_ perform: () async throws -> Void) async -> Bool {
        guard !isDeleting else { return true }
        isDeleting = true
        defer { isDeleting = false }
        do {
            try await perform()
            return true
        } catch {
            logger.error("delete failed: \(error.localizedDescription, privacy: .private)")
            return false
        }
    }
}
