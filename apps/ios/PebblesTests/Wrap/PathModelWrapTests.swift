import Foundation
import Testing
@testable import Pebbles

/// #1226 W4: `PathModel` holds each week's wrap the way spec §7.1 asks: fetched
/// once for the focused page and its neighbours, kept on screen while stale after
/// a write and refetched behind it, kept after a failure and retried on the next
/// demand. Android: `PathViewModelTest`'s wrap cases.
@Suite("PathModel wraps")
@MainActor
struct PathModelWrapTests {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek

    private var thisWeek: CalendarDay { CalendarDay(F.weekStart, in: F.calendar.timeZone) }
    private let lastWeek = CalendarDay(year: 2026, month: 9, day: 28)
    private var older: MomentSummary {
        MomentSummary(id: UUID(), name: "Older", happenedAt: Vectors.at("2026-09-30T10:00:00Z"),
                      createdAt: Vectors.at("2026-09-30T10:00:00Z"), firstSnapPath: nil, pebble: nil)
    }

    private func wrap(_ week: CalendarDay, pebbles: Int) -> WeekWrap {
        WeekWrap(weekStart: week, isoWeek: 0, isoYear: 2026, isLive: false, pebbles: pebbles, people: 0, tiles: [],
                 persons: nil, inviteMomentIds: nil, families: nil, lead: nil, domains: nil, weight: nil, hours: nil,
                 kept: nil)
    }

    private func model(_ wraps: FakePathWrapService) -> PathModel {
        let model = PathModel(calendar: F.calendar, now: { F.today }, locale: { Vectors.en })
        model.wraps = wraps
        return model
    }

    @Test("the focused week and its neighbour load once each")
    func loadsOnce() async throws {
        let wraps = FakePathWrapService()
        wraps.answer(week: thisWeek, .success(F.weekWrap(live: true)))
        wraps.answer(week: lastWeek, .success(wrap(lastWeek, pebbles: 1)))
        let model = model(wraps)
        let older = older
        await model.load { F.moments + [older] }
        await model.refreshWraps()
        #expect(Set(wraps.weekCalls) == [thisWeek, lastWeek])
        #expect(model.weekWraps[F.weekStart] == F.weekWrap(live: true))
        await model.refreshWraps()
        #expect(wraps.weekCalls.count == 2)
    }

    @Test("after a load the wraps go stale, stay on screen, and are fetched again")
    func staleAfterWrite() async throws {
        let wraps = FakePathWrapService()
        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 13)))
        let model = model(wraps)
        await model.load { F.moments }
        await model.refreshWraps()
        #expect(model.weekWraps[F.weekStart]?.pebbles == 13)

        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 14)))
        await model.load { F.moments }
        // Stale, not cleared: the page keeps showing the last wrap until the refetch lands.
        #expect(model.weekWraps[F.weekStart]?.pebbles == 13)
        await model.refreshWraps()
        #expect(model.weekWraps[F.weekStart]?.pebbles == 14)
        #expect(wraps.weekCalls.count == 2)
    }

    @Test("a failed fetch keeps the last good wrap, and the next demand retries")
    func failureKeepsLastGood() async throws {
        let wraps = FakePathWrapService()
        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 13)))
        let model = model(wraps)
        await model.load { F.moments }
        await model.refreshWraps()

        wraps.answer(week: thisWeek, .failure(FakePathWrapService.Failure()))
        await model.load { F.moments }
        await model.refreshWraps()
        #expect(model.weekWraps[F.weekStart]?.pebbles == 13)

        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 15)))
        await model.refreshWraps()
        #expect(model.weekWraps[F.weekStart]?.pebbles == 15)
    }

    @Test("an unchanged revalidation writes nothing a view could observe")
    func unchangedWritesNothing() async throws {
        let wraps = FakePathWrapService()
        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 13)))
        let model = model(wraps)
        await model.load { F.moments }
        await model.refreshWraps()
        await model.load { F.moments }
        let changed = CardFrameRegistryTests.Flag()
        withObservationTracking { _ = model.weekWraps } onChange: { changed.raised = true }
        await model.refreshWraps()
        #expect(wraps.weekCalls.count == 2)
        #expect(!changed.raised)
    }

    @Test("a swipe that cancels the caller never cancels the fetch, and the page is not asked for twice")
    func cancelledCallerKeepsTheFetch() async throws {
        let wraps = FakePathWrapService()
        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 13)))
        wraps.weekDelay = .milliseconds(200)
        let model = model(wraps)
        await model.load { F.moments }
        let swiped = Task { await model.refreshWraps() }
        try await Task.sleep(for: .milliseconds(20))
        swiped.cancel()
        // The next demand joins the running fetch instead of starting another.
        await model.refreshWraps()
        #expect(model.weekWraps[F.weekStart]?.pebbles == 13)
        #expect(wraps.weekCalls == [thisWeek])
    }

    @Test("a fetch that started before a write lands stale and is asked for again")
    func fetchOvertakenByAWrite() async throws {
        let wraps = FakePathWrapService()
        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 13)))
        wraps.weekDelay = .milliseconds(200)
        let model = model(wraps)
        await model.load { F.moments }
        let first = Task { await model.refreshWraps() }
        try await Task.sleep(for: .milliseconds(20))
        // A write lands while the first fetch is out: the server now says 14.
        wraps.answer(week: thisWeek, .success(wrap(thisWeek, pebbles: 14)))
        wraps.weekDelay = nil
        await model.load { F.moments }
        await first.value
        await model.refreshWraps()
        #expect(model.weekWraps[F.weekStart]?.pebbles == 14)
        #expect(wraps.weekCalls.count == 2)
    }
}
