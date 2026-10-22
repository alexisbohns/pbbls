import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

// Ported from apps/android/app/src/test/kotlin/app/pbbls/android/features/path/card/logic/CardFlightTest.kt
// at d53be9cf (its placeholder rule is `opensFlying` here), plus Reduce Motion and the
// missing rect (#1146 §Open and close), and FlightGeometry's FLIP.
@Suite("CardFlight (Android CardFlightTest.kt)")
@MainActor
struct CardFlightTests {
    private let slot = CGRect(x: 52, y: 300, width: 293, height: 330)
    private let rest = CGRect(x: 16, y: 100, width: 361, height: 700)

    private func request(face: CardFace = .recto, photo: Bool = true, pebble: Bool = true,
                         seeded: Bool = true) -> HeldCardRequest {
        let id = UUID()
        let summary = MomentSummary(
            id: id, name: "Fountain", happenedAt: Vectors.at("2026-10-08T19:00:00Z"),
            createdAt: Vectors.at("2026-10-08T19:00:00Z"), firstSnapPath: photo ? "a/b.jpg" : nil,
            pebble: pebble ? MomentSummary.PebbleSummary(id: id, intensity: 2, positiveness: 1, visibility: .private,
                                                         emotion: nil, glyph: nil, souls: []) : nil)
        return HeldCardRequest(momentId: id, face: face, seed: seeded ? summary : nil, origin: .path)
    }

    @Test("a spine card flies only when its moment has a pebble")
    func spineCard() {
        #expect(CardFlight.spineCardFlies(hasPhotoCard: true, hasPebble: true))
        // A bare moment with a photo: a card on the Spine, the bare page in the hand.
        #expect(!CardFlight.spineCardFlies(hasPhotoCard: true, hasPebble: false))
        #expect(!CardFlight.spineCardFlies(hasPhotoCard: false, hasPebble: true))
    }

    @Test("the held card flies while its recto shows, and fades otherwise")
    func heldCard() {
        #expect(CardFlight.heldCardFlies(showing: .recto, printing: false, leaving: false, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .verso, printing: false, leaving: false, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .recto, printing: true, leaving: false, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .recto, printing: false, leaving: true, reduceMotion: false))
        #expect(!CardFlight.heldCardFlies(showing: .recto, printing: false, leaving: false, reduceMotion: true))
    }

    @Test("an open flies only for a seeded recto press on a photo card with a measured slot")
    func opens() {
        #expect(CardFlight.opensFlying(request(), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(face: .verso), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(photo: false), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(pebble: false), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(seeded: false), sourceRect: slot, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(), sourceRect: nil, reduceMotion: false))
        #expect(!CardFlight.opensFlying(request(), sourceRect: slot, reduceMotion: true))
    }

    @Test("the FLIP starts on the slot, fill-width and top-leading, and ends at rest")
    func flip() {
        let start = FlightGeometry.transform(from: slot, to: rest, progress: 0)
        #expect(abs(start.scale - 293.0 / 361.0) < 1e-9)
        #expect(start.offset == CGSize(width: 36, height: 200))
        let end = FlightGeometry.transform(from: slot, to: rest, progress: 1)
        #expect(end.scale == 1 && end.offset == .zero)
        let half = FlightGeometry.transform(from: slot, to: rest, progress: 0.5)
        #expect(abs(half.scale - (293.0 / 361.0 + 1) / 2) < 1e-9)
        #expect(half.offset == CGSize(width: 18, height: 100))
    }

    @Test("a rest with no width does not transform")
    func noRest() {
        let none = FlightGeometry.transform(from: slot, to: .zero, progress: 0)
        #expect(none.scale == 1 && none.offset == .zero)
    }
}
