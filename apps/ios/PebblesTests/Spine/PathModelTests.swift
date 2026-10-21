import Foundation
import Observation
import Testing
@testable import Pebbles

/// #1130 §Data flow: `PathModel` takes over `PathView`'s load, its refocus rule
/// and its delete path, unchanged in behaviour, and builds each week's page.
@Suite("PathModel")
@MainActor
struct PathModelTests {
    // swiftlint:disable:next type_name
    typealias F = SpineFixtureWeek
    struct Failure: Error {}
    final class Calls: @unchecked Sendable { var count = 0 }
    final class Outcome: @unchecked Sendable { var value: PathModel.DeleteOutcome? }

    private func model() -> PathModel {
        PathModel(calendar: F.calendar, now: { F.today }, locale: { Vectors.en })
    }

    @Test("a first load lands on the current week and shows content")
    func firstLoad() async {
        let model = model()
        #expect(model.phase == .loading)
        await model.load { F.moments }
        #expect(model.phase == .content)
        #expect(model.focusedWeekStart == F.weekStart)
        #expect(model.entries.map(\.weekStart) == [F.weekStart])
    }

    @Test("a failed load logs and fails")
    func failedLoad() async {
        let model = model()
        await model.load { throw Failure() }
        #expect(model.phase == .failed)
    }

    @Test("a reload keeps the focused week while it is there, else takes the current one and drops the press")
    func refocus() async {
        let older = MomentSummary(id: UUID(), name: "Older", happenedAt: Vectors.at("2026-09-02T10:00:00Z"),
                                  createdAt: Vectors.at("2026-09-02T10:00:00Z"), firstSnapPath: nil, pebble: nil)
        let model = model()
        await model.load { F.moments + [older] }
        let olderWeek = WeekRollBuilder.weekStart(for: older.happenedAt, calendar: F.calendar)
        model.focus(olderWeek)
        await model.load { F.moments + [older] }
        #expect(model.focusedWeekStart == olderWeek)
        model.pressRequests.request(older.id)
        await model.load { F.moments }
        #expect(model.focusedWeekStart == F.weekStart)
        #expect(model.pressRequests.pending == nil)
    }

    @Test("focus ignores an equal week, and a page change cancels a pending press")
    func focus() async throws {
        let model = model()
        await model.load { F.moments }
        model.pressRequests.request(try #require(F.moments.first).id)
        model.focus(F.weekStart)
        #expect(model.pressRequests.pending != nil)
        model.focus(LocalDays.adding(-7, to: F.weekStart, calendar: F.calendar))
        #expect(model.pressRequests.pending == nil)
    }

    @Test("Capture's refocus finds the week that holds the moment")
    func focusContaining() async throws {
        let model = model()
        let older = MomentSummary(id: UUID(), name: nil, happenedAt: Vectors.at("2026-09-02T10:00:00Z"),
                                  createdAt: Vectors.at("2026-09-02T10:00:00Z"), firstSnapPath: nil, pebble: nil)
        await model.load { F.moments + [older] }
        model.focusWeek(containing: older.id)
        #expect(model.focusedWeekStart == WeekRollBuilder.weekStart(for: older.happenedAt, calendar: F.calendar))
        model.focusCurrentWeek()
        #expect(model.focusedWeekStart == F.weekStart)
    }

    @Test("a page carries its days, words, persons and contents in reading order")
    func page() async throws {
        let model = model()
        await model.load { F.moments }
        let page = try #require(model.page(for: F.weekStart))
        #expect(page.isCurrent)
        #expect(page.words.number == 41 && page.words.ordinal == "forty-first")
        #expect(page.week.pebbleCount == 13)
        #expect(page.contents.first?.name == "Flag march with Nadia")
        // Names come from the pebbles' own souls before the reference cache loads.
        #expect(page.persons.first?.name == "Mara")
        #expect(page.previous == nil && page.next == nil)
    }

    @Test("the reference cache's glyphs reach the pages")
    func souls() async throws {
        let model = model()
        await model.load { F.moments }
        _ = model.page(for: F.weekStart)
        model.updateSouls(names: F.soulNames, glyphs: F.soulGlyphs)
        let page = try #require(model.page(for: F.weekStart))
        #expect(page.soulGlyphs.count == 14)
    }

    @Test("a view that read a page before the souls arrived is told to read it again")
    func soulsInvalidatePages() async {
        let model = model()
        await model.load { F.moments }
        let changed = CardFrameRegistryTests.Flag()
        withObservationTracking { _ = model.page(for: F.weekStart) } onChange: { changed.raised = true }
        model.updateSouls(names: F.soulNames, glyphs: F.soulGlyphs)
        #expect(changed.raised)
    }

    @Test("the reading day is written only when it changes")
    func readingDay() async {
        let model = model()
        await model.load { F.moments }
        model.setReadingDay(2, for: F.weekStart)
        let changed = CardFrameRegistryTests.Flag()
        withObservationTracking { _ = model.readingDay } onChange: { changed.raised = true }
        model.setReadingDay(2, for: F.weekStart)
        #expect(!changed.raised)
        model.setReadingDay(3, for: F.weekStart)
        #expect(changed.raised)
        #expect(model.readingDay[F.weekStart] == 3)
    }

    @Test("a change made on another tab reloads; the Path's own does not")
    func revision() async {
        let model = model()
        let calls = Calls()
        let revision = MomentsRevision()
        revision.bump(from: .path)
        await model.reloadIfChangedOutside(revision) { calls.count += 1; return F.moments }
        #expect(calls.count == 0)
        revision.bump(from: .people)
        await model.reloadIfChangedOutside(revision) { calls.count += 1; return F.moments }
        #expect(calls.count == 1)
    }

    @Test("a delete reports its success, its failure, and a second confirm it ignored")
    func delete() async {
        let model = model()
        #expect(await model.delete { } == .done)
        #expect(await model.delete { throw Failure() } == .failed)
        let inner = Outcome()
        #expect(await model.delete { inner.value = await model.delete { } } == .done)
        #expect(inner.value == .ignored)
    }
}
