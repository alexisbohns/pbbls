import Foundation
import Testing
@testable import Pebbles

/// #1226 W5: month pages load exactly as weeks do (one loader serves both): on
/// focus, with the device's copy first, stale after a write and fetched again
/// behind what is shown. Android: `PathViewModelTest`'s month case.
@Suite("PathModel months")
@MainActor
struct PathModelMonthTests {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek

    private let september = CalendarMonth(year: 2026, month: 9)

    private func model(_ wraps: FakePathWrapService) -> PathModel {
        let model = PathModel(calendar: F.calendar, now: { F.today }, locale: { Vectors.en })
        model.wraps = wraps
        return model
    }

    private var strip: Strip {
        Strip(items: [.month(StripMonth(month: september, pebbles: 24)),
                      .week(StripWeek(weekStart: CalendarDay(F.weekStart, in: F.calendar.timeZone), isoWeek: 41,
                                      isoYear: 2026, moments: 15, isFocusable: true, isLive: true, days: []))])
    }

    @Test("a focused month loads its page; the week beside it loads as its neighbour")
    func monthLoadsOnFocus() async throws {
        let october = try MonthPayloadFixture.october()
        let wraps = FakePathWrapService(strip: .success(strip))
        wraps.answer(month: september, .success(october))
        let model = model(wraps)
        await model.load { F.moments }
        model.focus(.month(september))
        await model.refreshWraps()
        #expect(model.monthWraps[september] == october)
        #expect(wraps.monthCalls == [september])
        #expect(wraps.weekCalls == [CalendarDay(F.weekStart, in: F.calendar.timeZone)])
    }

    @Test("a month not yet shown opens on its kept copy")
    func keptMonth() async throws {
        let october = try MonthPayloadFixture.october()
        let wraps = FakePathWrapService(strip: .success(strip))
        wraps.keep(month: september, october)
        let model = model(wraps)
        await model.load { F.moments }
        model.focus(.month(september))
        await model.refreshWraps()
        #expect(model.monthWraps[september] == october)
    }

    @Test("after a load the month goes stale, stays on screen, and is fetched again")
    func staleAfterWrite() async throws {
        let full = try MonthPayloadFixture.october()
        let thin = try WrapPayloads.month("month-thin")
        let wraps = FakePathWrapService(strip: .success(strip))
        wraps.answer(month: september, .success(full))
        let model = model(wraps)
        await model.load { F.moments }
        model.focus(.month(september))
        await model.refreshWraps()
        wraps.answer(month: september, .success(thin))
        await model.load { F.moments }
        #expect(model.monthWraps[september] == full)
        await model.refreshWraps()
        #expect(model.monthWraps[september] == thin)
        #expect(wraps.monthCalls.count == 2)
    }

    @Test("a month's kept copy shows while its fetch runs, then the fetched page replaces it")
    func keptCopyMidFlight() async throws {
        let full = try MonthPayloadFixture.october()
        let thin = try WrapPayloads.month("month-thin")
        let wraps = FakePathWrapService(strip: .success(strip))
        wraps.keep(month: september, thin)
        wraps.answer(month: september, .success(full))
        wraps.monthDelay = .milliseconds(300)
        let model = model(wraps)
        await model.load { F.moments }
        model.focus(.month(september))
        let refresh = Task { await model.refreshWraps() }
        try await Task.sleep(for: .milliseconds(100))
        #expect(model.monthWraps[september] == thin)
        await refresh.value
        #expect(model.monthWraps[september] == full)
    }

    @Test("a month fetch that started before a write lands stale and is asked for again")
    func overtakenByAWrite() async throws {
        let full = try MonthPayloadFixture.october()
        let thin = try WrapPayloads.month("month-thin")
        let wraps = FakePathWrapService(strip: .success(strip))
        wraps.answer(month: september, .success(full))
        wraps.monthDelay = .milliseconds(200)
        let model = model(wraps)
        await model.load { F.moments }
        model.focus(.month(september))
        let first = Task { await model.refreshWraps() }
        try await Task.sleep(for: .milliseconds(20))
        wraps.answer(month: september, .success(thin))
        wraps.monthDelay = nil
        await model.load { F.moments }
        await first.value
        await model.refreshWraps()
        #expect(model.monthWraps[september] == thin)
        #expect(wraps.monthCalls.count == 2)
    }

    @Test("the pebbles' soul names stand in for souls the reference cache lacks")
    func pebbleSoulNames() async throws {
        let model = model(FakePathWrapService(strip: .success(strip)))
        await model.load { F.moments }
        let soul = try #require(F.moments.compactMap(\.pebble).flatMap(\.souls).first)
        #expect(model.pebbleSoulNames[soul.id.uuidString.lowercased()] == soul.name)
    }

    @Test("the lab's embedded October is the harness's, verbatim")
    func embeddedOctober() throws {
        #expect(SpineFixtureMonth.october == (try MonthPayloadFixture.october()))
    }
}
