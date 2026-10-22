import Testing
@testable import Pebbles

/// #1146 E1: the card opens on the face asked for, and the footer's button
/// turns it at once. `landing`, what is saved, is the face.
@Suite("CardTurnModel")
@MainActor
struct CardTurnModelTests {
    @Test("the card opens on the face asked for")
    func opens() {
        #expect(CardTurnModel(face: .verso).face == .verso)
        #expect(CardTurnModel(face: .recto).front == .recto)
    }

    @Test("the button turns at once, and landing follows")
    func turns() {
        let turn = CardTurnModel(face: .recto)
        turn.turnInstantly()
        #expect(turn.face == .verso && turn.front == .verso && turn.landing == .verso)
        turn.turnInstantly()
        #expect(turn.face == .recto)
    }
}
