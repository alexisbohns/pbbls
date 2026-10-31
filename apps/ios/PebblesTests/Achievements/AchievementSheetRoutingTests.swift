import Foundation
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

/// The page's sheet state (#1306): a row's moment closes the sheets and opens
/// once, whichever way the dismissal reports; a sheet whose section left closes.
@Suite("Achievement sheet state")
struct AchievementSheetStateTests {
    @Test("a row's moment closes the sheets and is taken once")
    func pendingOnce() throws {
        var state = AchievementSheetState(sheet: .explorer)
        let id = try #require(UUID(uuidString: "a0000000-0000-4000-8000-000000000001"))
        state.open(id)
        #expect(state.sheet == nil && state.pendingMoment == id)
        #expect(state.takePending() == id)
        #expect(state.takePending() == nil, "onDismiss and the fallback both ask: only the first opens it")
    }

    @Test("the open sheet closes when its section leaves the payload, and stays while it is there")
    func closesWhenGone() throws {
        let page = try AchievementsFixtures.payload("design-fixture").model
        var state = AchievementSheetState(sheet: .domains)
        state.close(ifGoneFrom: page)
        #expect(state.sheet == .domains)
        var gone = page
        gone.domains = nil
        state.close(ifGoneFrom: gone)
        #expect(state.sheet == nil)
        var year = AchievementSheetState(sheet: .year(2025))
        year.close(ifGoneFrom: page)
        #expect(year.sheet == nil)
    }
}

/// A pull that failed says so briefly, in Android's words; the other outcomes stay quiet.
@Suite("Achievements pull notice")
struct AchievementsPullNoticeTests {
    @Test("only a failure speaks, in English and French")
    func notice() {
        #expect(AchievementsCopy.pullNotice(.failed, locale: Locale(identifier: "en"))
            == "Couldn't open the ceremony. Please try again.")
        #expect(AchievementsCopy.pullNotice(.failed, locale: Locale(identifier: "fr"))
            == "Impossible d'ouvrir la cérémonie. Réessaie.")
        let quiet: [AchievementsModel.PullOutcome] = [.pulled(karma: 5), .pulledWithoutKarma, .alreadyPulled, .notReady, .busy]
        #expect(quiet.allSatisfy { AchievementsCopy.pullNotice($0, locale: Locale(identifier: "en")) == nil })
    }
}
