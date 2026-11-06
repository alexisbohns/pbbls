import Foundation
import Testing
@testable import Pebbles

/// Steps 4 and 5: the name, the glyph pick, the people and the carve (Android's `OnboardingViewModelTest`).
@Suite("OnboardingFlowModel you and people")
@MainActor
struct OnboardingFlowPeopleTests {
    // MARK: Helpers

    private func modelAtYou() -> OnboardingFlowModel {
        let model = OnboardingFlowHarness().model()
        model.seedStep(.you)
        return model
    }

    private func modelAtPeople() -> OnboardingFlowModel {
        let model = OnboardingFlowHarness().model()
        model.seedStep(.people)
        return model
    }

    private func english(_ resource: LocalizedStringResource) -> String {
        var resource = resource
        resource.locale = Locale(identifier: "en")
        return String(localized: resource)
    }

    private func addPerson(_ model: OnboardingFlowModel, _ name: String) {
        model.addPerson()
        let index = model.people.count - 1
        model.setPersonName(index, name)
        model.commitPerson(index)
    }

    @Test("a name is capped at the Settings limit, and so is a person's")
    func nameCap() {
        let model = modelAtYou()
        model.setName(String(repeating: "a", count: 60))
        #expect(model.name.count == OnboardingFlowModel.nameLimit)
        let people = modelAtPeople()
        people.addPerson()
        people.setPersonName(0, String(repeating: "b", count: 60))
        #expect(people.people[0].name.count == OnboardingFlowModel.nameLimit)
    }

    @Test("each new person wears a different glyph until a pick")
    func differentDefaultGlyphs() {
        let model = modelAtPeople()
        addPerson(model, "P0")
        addPerson(model, "P1")
        let expected = [OnboardingFlowHarness.glyphs[0].id, OnboardingFlowHarness.glyphs[1].id]
        #expect(model.answers.souls.map(\.glyphId) == expected)
    }

    @Test("a place past the last system glyph wears the first one")
    func defaultGlyphFallsBack() {
        let model = modelAtPeople()
        #expect(model.defaultGlyphId(1) == OnboardingFlowHarness.glyphs[1].id)
        #expect(model.defaultGlyphId(99) == OnboardingFlowHarness.glyphs[0].id)
        model.seed(step: .people, letter: nil, systemGlyphs: [], consent: OnboardingConsentState())
        #expect(model.defaultGlyphId(0) == nil)
    }

    @Test("a blank person is dropped on commit and at most three places exist")
    func blankDroppedAtMostThree() {
        let model = modelAtPeople()
        model.addPerson()
        model.commitPerson(0)
        #expect(model.people.isEmpty)
        for index in 0..<3 { addPerson(model, "P\(index)") }
        model.addPerson()
        #expect(model.people.count == 3)
        #expect(model.people.allSatisfy { !$0.isEditing })

        model.openGlyphSheet(1)
        #expect(model.glyphSheetFor == 1)
        let pick = OnboardingFlowHarness.glyphs[5].id
        model.pickPersonGlyph(1, pick)
        #expect(model.people[1].glyphId == pick)
        #expect(model.glyphSheetFor == nil)

        model.drawForPerson(2)
        #expect(model.carvingFor == .person(2))
        let mine = Glyph(id: UUID(), name: "Mine", strokes: [], viewBox: "0 0 200 200", userId: UUID())
        model.carved(mine)
        #expect(model.people[2].glyphId == mine.id)
        #expect(model.carved.map(\.id) == [mine.id])
        #expect(model.carvingFor == nil)

        model.editPerson(0)
        model.setPersonName(0, "")
        model.commitPerson(0)
        #expect(model.people.map(\.name) == ["P1", "P2"])
    }

    @Test("adding is refused while a place is being typed, and so is reopening another")
    func oneEditAtATime() {
        let model = modelAtPeople()
        addPerson(model, "A")
        addPerson(model, "B")
        model.addPerson()
        #expect(model.people.count == 3)
        model.addPerson()
        #expect(model.people.count == 3)
        model.editPerson(0) // the third is open: refused
        #expect(!model.people[0].isEditing)
        model.setPersonName(2, "C")
        model.commitPerson(2)
        model.editPerson(0)
        #expect(model.people[0].isEditing)
        model.editPerson(1)
        #expect(!model.people[1].isEditing)
    }

    @Test("a name is trimmed when its field closes")
    func trimmed() {
        let model = modelAtPeople()
        addPerson(model, "  Noé ")
        #expect(model.people[0].name == "Noé")
        #expect(!model.people[0].isEditing)
    }

    @Test("drawing for you keeps the carved glyph as the pick, and cancelling keeps the old one")
    func drawForYou() {
        let model = modelAtYou()
        let old = OnboardingFlowHarness.glyphs[2].id
        model.pickGlyph(old)
        model.drawForYou()
        #expect(model.carvingFor == .you)
        model.carveCancelled()
        #expect(model.carvingFor == nil)
        #expect(model.glyphId == old)
        model.drawForYou()
        let mine = Glyph(id: UUID(), name: "Mine", strokes: [], viewBox: "0 0 200 200", userId: UUID())
        model.carved(mine)
        #expect(model.glyphId == mine.id)
        #expect(model.carved.map(\.id) == [mine.id])
        #expect(model.carvingFor == nil)
    }

    @Test("a carve that nobody asked for changes nothing")
    func strayCarve() {
        let model = modelAtYou()
        model.carved(Glyph(id: UUID(), name: nil, strokes: [], viewBox: "0 0 200 200", userId: UUID()))
        #expect(model.carved.isEmpty)
        #expect(model.glyphId == nil)
    }

    @Test("leaving people commits a typed name and drops a blank place")
    func leavingPeople() {
        let model = modelAtPeople()
        model.addPerson()
        model.setPersonName(0, " Noé ")
        model.next() // Continue with the field still open
        #expect(model.step.id == .pace)
        #expect(model.people.map(\.name) == ["Noé"])
        #expect(model.people.allSatisfy { !$0.isEditing })
        model.back()
        model.addPerson()
        model.skip() // Later with a blank place open
        #expect(model.step.id == .pace)
        #expect(model.people.isEmpty)
    }

    @Test("Later clears the people even when some are named")
    func laterClears() {
        let model = modelAtPeople()
        addPerson(model, "A")
        model.skip()
        #expect(model.people.isEmpty)
        #expect(model.answers.souls.isEmpty)
    }

    @Test("the people are listed only once closed and named, and a person with no glyph at all is known")
    func soulsNeedAGlyph() {
        let model = modelAtPeople()
        model.addPerson()
        model.setPersonName(0, "Open")
        #expect(model.answers.souls.isEmpty)
        model.seed(
            step: .people, letter: nil, systemGlyphs: [], consent: OnboardingConsentState(),
            people: [OnboardingPerson(name: "Noé", glyphId: nil, isEditing: false)]
        )
        #expect(model.answers.souls.isEmpty)
        #expect(model.hasPersonWithoutGlyph)
    }

    @Test("Continue on you needs a name or a glyph")
    func canContinueYou() {
        let model = modelAtYou()
        #expect(!model.canContinueYou)
        model.setName("   ")
        #expect(!model.canContinueYou)
        model.setName("Lena")
        #expect(model.canContinueYou)
        model.setName("")
        model.pickGlyph(OnboardingFlowHarness.glyphs[0].id)
        #expect(model.canContinueYou)
    }

    @Test("Continue on people needs a named person, and three name the button")
    func peopleContinueAndCTA() {
        let model = modelAtPeople()
        #expect(!model.canContinuePeople)
        #expect(english(model.peopleCTA) == "These are my people")
        addPerson(model, "Maya")
        #expect(model.canContinuePeople)
        addPerson(model, "Théo")
        addPerson(model, "Inès")
        #expect(english(model.peopleCTA) == "Keep Maya, Théo and Inès")
        model.editPerson(2)
        model.setPersonName(2, "")
        model.commitPerson(2)
        #expect(english(model.peopleCTA) == "These are my people")
    }

    @Test("the stone on you offers the system glyphs and then the carved ones")
    func glyphChoices() {
        let model = modelAtYou()
        model.drawForYou()
        let mine = Glyph(id: UUID(), name: "Mine", strokes: [], viewBox: "0 0 200 200", userId: UUID())
        model.carved(mine)
        #expect(model.glyphChoices.map(\.id) == OnboardingFlowHarness.glyphs.map(\.id) + [mine.id])
    }
}

extension OnboardingFlowPeopleTests {
    @Test("the glyph sheet does not open while another person's field is open")
    func sheetRefusedWhileEditing() {
        let model = modelAtPeople()
        addPerson(model, "A")
        addPerson(model, "B")
        addPerson(model, "C")
        model.editPerson(1)
        model.openGlyphSheet(2)
        #expect(model.glyphSheetFor == nil)
        model.commitPerson(1)
        model.openGlyphSheet(2)
        #expect(model.glyphSheetFor == 2)
    }

    @Test("glyph actions on an index that is gone do nothing")
    func staleIndexes() {
        let model = modelAtPeople()
        addPerson(model, "A")
        model.pickPersonGlyph(5, OnboardingFlowHarness.glyphs[1].id)
        model.drawForPerson(5)
        #expect(model.people[0].glyphId == nil)
        #expect(model.carvingFor == nil)
        model.seed(step: .people, letter: nil, systemGlyphs: OnboardingFlowHarness.glyphs,
                   consent: OnboardingConsentState(), people: [], carvingFor: .person(0))
        let mine = Glyph(id: UUID(), name: "Mine", strokes: [], viewBox: "0 0 200 200", userId: UUID())
        model.carved(mine)
        #expect(model.people.isEmpty)
    }

    @Test("a name equal to another's, trimmed and ignoring case, is refused like a blank")
    func duplicateRefused() {
        let harness = OnboardingFlowHarness()
        let model = harness.model()
        model.seedStep(.people)
        addPerson(model, "Ana")
        harness.forgetTaps()
        addPerson(model, " ana ")
        #expect(model.people.map(\.name) == ["Ana"])
        #expect(harness.taps.contains(.warning))
    }

    @Test("accents are not folded: Lea and Léa both stay")
    func accentsStay() {
        let model = modelAtPeople()
        addPerson(model, "Léa")
        addPerson(model, "Lea")
        #expect(model.people.map(\.name) == ["Léa", "Lea"])
    }

    @Test("editing a person to match another removes the place")
    func editedToDuplicate() {
        let model = modelAtPeople()
        addPerson(model, "Ana")
        addPerson(model, "Bo")
        model.editPerson(1)
        model.setPersonName(1, "ANA")
        model.commitPerson(1)
        #expect(model.people.map(\.name) == ["Ana"])
    }

    @Test("leaving the step drops an open duplicate, so the answers never collide")
    func leavingDropsDuplicate() {
        let model = modelAtPeople()
        addPerson(model, "Ana")
        model.addPerson()
        model.setPersonName(1, "ana")
        model.next()
        #expect(model.people.map(\.name) == ["Ana"])
        let names = model.answers.souls.map { $0.name.lowercased() }
        #expect(Set(names).count == names.count)
    }
}
