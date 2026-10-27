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
}
