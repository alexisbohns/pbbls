import Testing
@testable import Pebbles

/// #1146 §State model, §Turn: one turn at a time; `front` swaps at the half;
/// `landing`, what is saved, is the face a running turn lands on, the face in
/// front while a finger holds the card, else the face. A landing on 1 commits
/// the other face at progress 0 in one change. The view animates `progress`.
@Suite("CardTurnModel")
@MainActor
struct CardTurnModelTests {
    @Test("the card opens on the face asked for, at rest")
    func opens() {
        let turn = CardTurnModel(face: .verso)
        #expect(turn.face == .verso && turn.progress == 0 && turn.front == .verso && !turn.isBusy)
    }

    @Test("the button turns, and the landing commits the other face at 0")
    func button() {
        let turn = CardTurnModel(face: .recto)
        #expect(turn.beginTurn(reduceMotion: false))
        #expect(turn.landing == .verso && turn.isBusy)
        turn.progress = 1
        turn.settled()
        #expect(turn.face == .verso && turn.progress == 0 && turn.landing == .verso && !turn.isBusy)
    }

    @Test("a turn during a turn is ignored")
    func oneAtATime() {
        let turn = CardTurnModel(face: .recto)
        #expect(turn.beginTurn(reduceMotion: false))
        #expect(!turn.beginTurn(reduceMotion: false))
    }

    @Test("a drag that starts mid-turn is not taken")
    func dragMidTurn() {
        let turn = CardTurnModel(face: .recto)
        _ = turn.beginTurn(reduceMotion: false)
        #expect(!turn.startDrag())
    }

    @Test("the button is ignored while a drag holds the card")
    func buttonDuringDrag() {
        let turn = CardTurnModel(face: .recto)
        #expect(turn.startDrag())
        #expect(!turn.beginTurn(reduceMotion: false))
    }

    @Test("while held, the landing is the face in front, swapping at the half")
    func held() {
        let turn = CardTurnModel(face: .recto)
        #expect(turn.startDrag())
        turn.drag(to: 0.6, reduceMotion: false)
        #expect(turn.front == .verso && turn.landing == .verso)
        turn.drag(to: 0.3, reduceMotion: false)
        #expect(turn.front == .recto && turn.landing == .recto)
    }

    @Test("a release past the half settles on the other face and lands there")
    func releasePast() throws {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.7, reduceMotion: false)
        let settle = try #require(turn.release(at: 0.7, velocity: 0, reduceMotion: false))
        #expect(settle.target == 1 && settle.springVelocity == 0)
        #expect(turn.landing == .verso)
        turn.progress = settle.target
        turn.settled()
        #expect(turn.face == .verso && turn.progress == 0)
    }

    @Test("a release short of the half settles back")
    func releaseShort() throws {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.3, reduceMotion: false)
        let settle = try #require(turn.release(at: 0.3, velocity: 0, reduceMotion: false))
        #expect(settle.target == 0 && turn.landing == .recto)
        turn.progress = settle.target
        turn.settled()
        #expect(turn.face == .recto)
    }

    @Test("a flick carries, with the finger's speed over the distance left")
    func flick() throws {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.2, reduceMotion: false)
        let settle = try #require(turn.release(at: 0.2, velocity: 2.4, reduceMotion: false))
        #expect(settle.target == 1)
        #expect(abs(settle.springVelocity - 2.4 / 0.8) < 1e-9)
    }

    @Test("Reduce Motion: a drag moves nothing, a release past the half commits at once")
    func reduceMotion() {
        let turn = CardTurnModel(face: .recto)
        _ = turn.startDrag()
        turn.drag(to: 0.6, reduceMotion: true)
        #expect(turn.progress == 0)
        #expect(turn.release(at: 0.6, velocity: 0, reduceMotion: true) == nil)
        #expect(turn.face == .verso && !turn.isBusy)
        #expect(turn.beginTurn(reduceMotion: true))
        #expect(turn.face == .recto && !turn.isBusy)
    }

    @Test("a drag taken away settles back to the face that was up")
    func cancel() {
        let turn = CardTurnModel(face: .verso)
        _ = turn.startDrag()
        turn.drag(to: 0.4, reduceMotion: false)
        #expect(turn.cancelDrag())
        #expect(turn.landing == .verso)
        turn.progress = 0
        turn.settled()
        #expect(turn.face == .verso && !turn.isBusy)
        #expect(!turn.cancelDrag())
    }
}
