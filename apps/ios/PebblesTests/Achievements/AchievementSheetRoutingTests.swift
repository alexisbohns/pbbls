import Testing
@testable import Pebbles

/// What each sheet marks seen when it opens (D10), its identity for
/// `.sheet(item:)`, and when it closes because its section left the payload.
@Suite("Achievement sheet routing")
@MainActor
struct AchievementSheetRoutingTests {
    @Test("each sheet marks its own card, a year sheet the year card")
    func cards() {
        let sheets: [AchievementSheet] = [.explorer, .emotions, .domains, .pebbles, .year(2026), .year(2025)]
        #expect(sheets.map(\.card) == [.explorer, .emotions, .domains, .pebbles, .year, .year])
        #expect(Set(sheets.map(\.id)).count == sheets.count)
    }

    @Test("a sheet is shown while its section is in the payload, and closes when it leaves")
    func shown() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        let sheets: [AchievementSheet] = [.explorer, .emotions, .domains, .pebbles, .year(2026)]
        #expect(sheets.allSatisfy { $0.isShown(by: page) })
        #expect(!AchievementSheet.year(2025).isShown(by: page))
        let bare = try AchievementsFixtures.json(#"{"years": []}"#).model
        #expect(sheets.allSatisfy { !$0.isShown(by: bare) })
        #expect(!AchievementSheet.explorer.isShown(by: nil))
    }

    @Test("a ready mark shows only while its set is offered, and waits during any pull")
    func ready() throws {
        let page = try AchievementsFixtures.json("""
        {"emotions": {"count": 38, "of": 38, "seats": [],
                      "completion": {"completed_at": "2026-10-01T10:00:00Z", "offered_from": "2026-10-02T04:00:00Z",
                                     "ready": true}}}
        """).model
        let mark = sheetReadyMark(page.emotions?.completion, pulling: nil, pull: { _ in })
        #expect(mark?.stack == .emotions && mark?.isPulling == false)
        #expect(sheetReadyMark(page.emotions?.completion, pulling: .domains, pull: { _ in })?.isPulling == true)
        let design = try AchievementsFixtures.payload("design-fixture").model
        #expect(sheetReadyMark(design.explorer?.completion, pulling: nil, pull: { _ in }) == nil, "pulled: no mark")
    }

    @Test("a sheet's rows read in the app's calendar and language")
    func context() throws {
        let ctx = AchievementsSheetRows.Context.current(try AchievementsLookups.fixture())
        #expect(ctx.calendar.timeZone == SpineCalendar.current.timeZone)
        #expect(ctx.locale == SpineStrings.appLocale)
    }
}
