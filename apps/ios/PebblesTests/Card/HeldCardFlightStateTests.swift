import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

/// #1146 §State model: the flight is the host's state. An open decides once
/// whether it flies and keeps the slot's rect; the close re-reads the rect
/// (the Spine may have scrolled) and flies by the committed face; the edge
/// swipe scrubs it; the end returns focus to the slot.
@Suite("HeldCardHostModel flight")
@MainActor
struct HeldCardFlightStateTests {
    private let slot = CGRect(x: 52, y: 300, width: 293, height: 330)
    private let rest = CGRect(x: 16, y: 100, width: 361, height: 700)

    private func photoRequest(face: CardFace = .recto) -> HeldCardRequest {
        let id = UUID()
        let summary = MomentSummary(
            id: id, name: "Fountain", happenedAt: Vectors.at("2026-10-08T19:00:00Z"),
            createdAt: Vectors.at("2026-10-08T19:00:00Z"), firstSnapPath: "a/b.jpg",
            pebble: MomentSummary.PebbleSummary(id: id, intensity: 2, positiveness: 1, visibility: .private,
                                                emotion: nil, glyph: nil, souls: []))
        return HeldCardRequest(momentId: id, face: face, seed: summary, origin: .path)
    }

    @Test("a seeded recto open from a measured slot flies from it")
    func opensFlying() {
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        #expect(host.flies && host.sourceRect == slot)
        #expect(host.phase == .opening && host.flight == 0 && host.chrome == 0 && host.pageOpacity == 1)
    }

    @Test("a stone press, a row without a seed and Reduce Motion fade in")
    func opensFading() {
        for (request, reduce) in [(photoRequest(face: .verso), false),
                                  (HeldCardRequest(momentId: UUID(), origin: .people), false),
                                  (photoRequest(), true)] {
            let host = HeldCardHostModel()
            host.open(request, sourceRect: slot, reduceMotion: reduce)
            #expect(!host.flies && host.sourceRect == nil)
            #expect(host.flight == 1 && host.pageOpacity == 0)
        }
    }

    @Test("the first rest of an open starts the flight; later ones only move the rest")
    func restReports() {
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        #expect(host.reportRest(rest))
        #expect(!host.reportRest(rest.offsetBy(dx: 0, dy: -40)))
        #expect(host.restRect == rest.offsetBy(dx: 0, dy: -40))
        host.opened()
        #expect(host.phase == .open)
    }

    @Test("the close flies back on the recto to the re-read rect, and fades otherwise")
    func closes() {
        let moved = slot.offsetBy(dx: 0, dy: 120)
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        _ = host.reportRest(rest)
        #expect(host.beginClose(sourceRect: moved, reduceMotion: false))
        #expect(host.sourceRect == moved)

        host.cancelClose()
        host.faceCommitted(.verso)
        #expect(!host.beginClose(sourceRect: moved, reduceMotion: false))

        host.cancelClose()
        host.faceCommitted(.recto)
        #expect(!host.beginClose(sourceRect: nil, reduceMotion: false))
        host.cancelClose()
        #expect(!host.beginClose(sourceRect: moved, reduceMotion: true))
        host.cancelClose()
        host.markLeaving()
        #expect(!host.beginClose(sourceRect: moved, reduceMotion: false))
    }

    @Test("a print in progress closes without flying")
    func printingFades() {
        let host = HeldCardHostModel()
        let request = photoRequest()
        host.open(request, sourceRect: slot, reduceMotion: false)
        _ = host.reportRest(rest)
        host.startPrinting(request.momentId)
        host.faceCommitted(.recto)
        #expect(!host.beginClose(sourceRect: slot, reduceMotion: false))
    }

    @Test("the edge swipe scrubs a flying close, or slides a fading one")
    func scrub() {
        let host = HeldCardHostModel()
        host.open(photoRequest(), sourceRect: slot, reduceMotion: false)
        _ = host.reportRest(rest)
        _ = host.beginClose(sourceRect: slot, reduceMotion: false)
        host.scrub(0.3, width: 393)
        #expect(host.phase == .closing(progress: 0.3) && abs(host.flight - 0.7) < 1e-9 && host.pageShift == 0)
        host.cancelClose()
        #expect(host.phase == .open && host.flight == 1 && host.chrome == 1)

        host.faceCommitted(.verso)
        _ = host.beginClose(sourceRect: slot, reduceMotion: false)
        host.scrub(0.5, width: 400)
        #expect(host.pageShift == 200 && host.pageOpacity == 0.5 && host.flight == 1)
    }

    @Test("the end of a close forgets the card and returns focus to its slot")
    func finish() {
        let host = HeldCardHostModel()
        let request = photoRequest()
        host.open(request, sourceRect: slot, reduceMotion: false)
        _ = host.beginClose(sourceRect: slot, reduceMotion: false)
        host.finishClose()
        #expect(host.request == nil && host.snapshot == nil && host.phase == .idle)
        #expect(host.returnFocus == request.momentId)
    }

    @Test("a card from another list returns focus to no slot")
    func finishElsewhere() {
        let host = HeldCardHostModel()
        host.open(HeldCardRequest(momentId: UUID(), origin: .people))
        _ = host.beginClose(sourceRect: nil, reduceMotion: false)
        host.finishClose()
        #expect(host.request == nil && host.returnFocus == nil)
    }

    @Test("a restored card opens at rest, without a flight")
    func restore() {
        let host = HeldCardHostModel()
        host.restore(HeldCardSnapshot(momentId: UUID(), face: .recto))
        #expect(!host.flies && host.phase == .open && host.pageOpacity == 1 && host.chrome == 1)
    }
}
