import Foundation
import Testing
@testable import Pebbles

/// #1226 W3: `PathModel` loads the server's strip beside the feed and pages over
/// its focusable items. Android: `PathViewModelTest`'s strip cases.
@Suite("PathModel strip")
@MainActor
struct PathModelStripTests {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek

    private var fixtureWeek: CalendarDay { CalendarDay(F.weekStart, in: F.calendar.timeZone) }
    private let september = CalendarMonth(year: 2026, month: 9)

    private var strip: Strip {
        let lastSeptemberWeek = CalendarDay(year: 2026, month: 9, day: 28)
        return Strip(items: [
            .week(StripWeek(weekStart: lastSeptemberWeek, isoWeek: 40, isoYear: 2026, moments: 2, isFocusable: true,
                            isLive: false, days: [DayWeight(moments: 2, topIntensity: 3)])),
            .month(StripMonth(month: september, pebbles: 2)),
            .week(StripWeek(weekStart: fixtureWeek, isoWeek: 41, isoYear: 2026, moments: F.moments.count,
                            isFocusable: true, isLive: true, days: []))
        ])
    }

    private func model(_ wraps: FakePathWrapService) -> PathModel {
        let model = PathModel(calendar: F.calendar, now: { F.today }, locale: { Vectors.en })
        model.wraps = wraps
        return model
    }

    @Test("the strip loads in the feed's zone, and its months become pages")
    func loadsStrip() async {
        let wraps = FakePathWrapService(strip: .success(strip))
        let model = model(wraps)
        await model.load { F.moments }
        #expect(wraps.stripZones == [F.calendar.timeZone])
        #expect(model.strip == strip)
        #expect(model.stripItems == strip.items)
        let september28 = CalendarDay(year: 2026, month: 9, day: 28).date(in: F.calendar.timeZone)
        #expect(model.pages == [.week(september28 ?? .distantPast), .month(september), .week(F.weekStart)])
        #expect(model.focusedPage == .week(F.weekStart))
        // A week the strip focuses and the feed does not hold yet still draws.
        #expect(model.page(for: september28 ?? .distantPast)?.contents.isEmpty == true)
    }

    @Test("a strip failure never fails the timeline, and a failed refresh keeps the last strip")
    func failureKeepsStrip() async {
        let wraps = FakePathWrapService()
        let model = model(wraps)
        await model.load { F.moments }
        #expect(model.phase == .content)
        #expect(model.strip == nil)
        #expect(model.pages == [.week(F.weekStart)])

        wraps.answer(strip: .success(strip))
        await model.load { F.moments }
        #expect(model.strip == strip)

        wraps.answer(strip: .failure(FakePathWrapService.Failure()))
        await model.load { F.moments }
        #expect(model.strip == strip)
        #expect(model.pages.contains(.month(september)))
    }

    @Test("a failed feed leaves the strip as it was")
    func failedFeedKeepsStrip() async {
        let wraps = FakePathWrapService(strip: .success(strip))
        let model = model(wraps)
        await model.load { throw FakePathWrapService.Failure() }
        #expect(model.phase == .failed)
        #expect(model.strip == nil)
    }

    @Test("a load a newer one overtook does not write its strip")
    func staleLoadKeepsNewerStrip() async throws {
        let older = Strip(items: [.month(StripMonth(month: september, pebbles: 1))])
        let wraps = FakePathWrapService(strip: .success(older))
        let model = model(wraps)
        let slow = Task { await model.load { try await Task.sleep(for: .milliseconds(400)); return F.moments } }
        try await Task.sleep(for: .milliseconds(100))
        wraps.answer(strip: .success(strip))
        await model.load { F.moments }
        await slow.value
        #expect(model.strip == strip)
    }

    @Test("a focused month stays focused across a reload, and a month focus drops a pending press")
    func monthFocus() async throws {
        let model = model(FakePathWrapService(strip: .success(strip)))
        await model.load { F.moments }
        model.pressRequests.request(try #require(F.moments.first).id)
        model.focus(.month(september))
        #expect(model.pressRequests.pending == nil)
        #expect(model.focusedWeekStart == nil)
        await model.load { F.moments }
        #expect(model.focusedPage == .month(september))
        model.focusCurrentWeek()
        #expect(model.focusedPage == .week(F.weekStart))
    }
}
