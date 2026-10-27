import Foundation
import Testing
@testable import Pebbles

@Suite("Home model")
@MainActor
struct HomeModelTests {
    @Test func loadsTheName() async {
        let model = HomeModel()
        model.service = FakeHomeService(answer: { "Alexis" })
        await model.load()
        #expect(model.name == "Alexis")
        #expect(model.hasLoadedOnce)
    }

    @Test func keepsAnEmptyNameWhenTheLoadFails() async {
        let model = HomeModel()
        model.service = FakeHomeService(answer: { throw FakeHomeService.Failure() })
        await model.load()
        #expect(model.name == "")
        #expect(model.hasLoadedOnce)
    }

    @Test func wrapsLoadAndKeepTheLastValueOnFailure() async throws {
        let fake = FakeHomeService(answer: { "A" })
        let wraps = try HomePayloads.wraps()
        fake.answer(wraps: .success(wraps))
        let model = HomeModel(); model.service = fake
        await model.load()
        #expect(model.wraps == wraps && model.wrapsError == nil)
        fake.answer(wraps: .failure(FakeHomeService.Failure()))
        await model.loadWraps()
        #expect(model.wraps == wraps && model.wrapsError != nil)
    }

    @Test func aRevealFlipsTheMonthInPlace() async throws {
        let fake = FakeHomeService(answer: { "A" })
        let wraps = try HomePayloads.wraps()
        fake.answer(wraps: .success(wraps))
        let sept = CalendarMonth(year: 2026, month: 9)
        let at = ISO8601Flexible.parse("2026-10-26T22:24:21Z")
        fake.answer(reveal: sept, .success(HomeReveal(month: sept, revealedAt: at)))
        let model = HomeModel(); model.service = fake
        await model.load()
        let reveal = try await model.reveal(sept, zone: TimeZone(identifier: "Europe/Paris")!)
        #expect(reveal.revealedAt == at)
        #expect(model.wraps?.months.first { $0.month == sept }?.isFolded == false)
    }
}
