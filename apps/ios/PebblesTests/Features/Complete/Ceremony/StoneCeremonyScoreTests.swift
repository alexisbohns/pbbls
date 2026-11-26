import CoreGraphics
import Foundation
import Testing
@testable import Pebbles

@Suite("Stone ceremony haptic score")
struct StoneCeremonyScoreTests {
    private func timeline(_ valence: Valence, carves: Bool = true,
                          mode: StoneCeremonyMode = .full) -> StoneCeremonyTimeline {
        StoneCeremonyTimeline(valence: valence, carves: carves, mode: mode)
    }

    private func expectEvent(_ event: IntroHapticEvent, _ at: Double, _ intensity: Float, _ sharpness: Float) {
        #expect(abs(event.at - at) < 1e-9)
        #expect(abs(event.tick.intensity - intensity) < 1e-6)
        #expect(abs(event.tick.sharpness - sharpness) < 1e-6)
    }

    @Test func thudEchoClose() {
        let t = timeline(.neutralMedium)
        let events = StoneCeremonyScore.act1(t, carveLength: 300, hapticsOn: true)
        expectEvent(events[0], 0.32, 0.75, 0.45)
        expectEvent(events[1], 0.39, 0.2625, 0.35)
        expectEvent(events[events.count - 1], 1.40, 0.70, 0.60)
        let ticks = Array(events[2..<(events.count - 1)])
        for tick in ticks {
            #expect(tick.tick.sharpness == 0.85)
            #expect(tick.tick.intensity >= 0.18 - 1e-6 && tick.tick.intensity <= 0.30 + 1e-6)
        }
        #expect(zip(ticks, ticks.dropFirst()).allSatisfy { $0.tick.intensity <= $1.tick.intensity })
        // 25 ticks, minus the ones the floor drops.
        let unfloored = StoneCeremonyScore.carveTicks(start: t.carveStart, duration: t.carveDuration, length: 300)
        #expect(unfloored.count == 25)
        #expect(ticks.count < 25)
        #expect(ticks.count > 10)
    }

    @Test func byValence() {
        let lava = StoneCeremonyScore.act1(timeline(.lowlightSmall), carveLength: 300, hapticsOn: true)
        let gem = StoneCeremonyScore.act1(timeline(.highlightLarge), carveLength: 300, hapticsOn: true)
        expectEvent(lava[0], timeline(.lowlightSmall).contact, 0.55, 0.25)
        expectEvent(gem[0], timeline(.highlightLarge).contact, 0.95, 0.80)
        expectEvent(lava[lava.count - 1], timeline(.lowlightSmall).closure, 0.60, 0.35)
        expectEvent(gem[gem.count - 1], timeline(.highlightLarge).closure, 0.80, 0.80)
    }

    @Test func floor() {
        let t = timeline(.highlightMedium)
        let events = StoneCeremonyScore.act1(t, carveLength: 2000, hapticsOn: true)
        for (a, b) in zip(events, events.dropFirst()) {
            #expect(b.at - a.at >= IntroHaptics.floor - 1e-9)
        }
        expectEvent(events[0], t.contact, 0.75, 0.80)
        expectEvent(events[1], t.contact + 0.070, 0.2625, 0.70)
        expectEvent(events[events.count - 1], t.closure, 0.80, 0.80)
        let unfloored = StoneCeremonyScore.carveTicks(start: t.carveStart, duration: t.carveDuration, length: 2000)
        #expect(unfloored.count == 36)
        #expect(events.count < 36 + 3)
    }

    @Test func noGlyph() {
        let t = timeline(.neutralMedium, carves: false)
        let events = StoneCeremonyScore.act1(t, carveLength: 300, hapticsOn: true)
        #expect(events.count == 2)
        expectEvent(events[0], t.contact, 0.75, 0.45)
        expectEvent(events[1], t.contact + 0.070, 0.2625, 0.35)
    }

    @Test func neverBeforeContact() {
        for valence in Valence.allCases {
            for mode in [StoneCeremonyMode.full, .reducedMotion, .lowPower] {
                let t = timeline(valence, mode: mode)
                let act1 = StoneCeremonyScore.act1(t, carveLength: 300, hapticsOn: true)
                guard let first = act1.first else { continue }
                #expect(first.at >= t.contact)
            }
        }
    }

    @Test func act2() {
        let medium = StoneCeremonyScore.act2(timeline(.neutralMedium), hapticsOn: true)
        #expect(medium.count == 2)
        expectEvent(medium[0], 0, 0.30, 0.45)
        expectEvent(medium[1], 0.38, 0.55, 0.50)
        let small = StoneCeremonyScore.act2(timeline(.neutralSmall), hapticsOn: true)
        let large = StoneCeremonyScore.act2(timeline(.neutralLarge), hapticsOn: true)
        #expect(abs(small[1].tick.intensity - 0.45) < 1e-6)
        #expect(abs(large[1].tick.intensity - 0.65) < 1e-6)
    }

    @Test func silent() {
        let quiet = timeline(.lowlightLarge)
        #expect(StoneCeremonyScore.act1(quiet, carveLength: 300, hapticsOn: true).isEmpty)
        #expect(StoneCeremonyScore.act2(quiet, hapticsOn: true).isEmpty)
        let river = timeline(.neutralMedium)
        #expect(StoneCeremonyScore.act1(river, carveLength: 300, hapticsOn: false).isEmpty)
        #expect(StoneCeremonyScore.act2(river, hapticsOn: false).isEmpty)
    }

    @Test func calmModes() {
        let reduced = timeline(.neutralMedium, mode: .reducedMotion)
        let act1 = StoneCeremonyScore.act1(reduced, carveLength: 300, hapticsOn: true)
        #expect(act1.count == 1)
        expectEvent(act1[0], 0.25, 0.50, 0.40)
        let act2 = StoneCeremonyScore.act2(reduced, hapticsOn: true)
        #expect(act2.count == 1)
        expectEvent(act2[0], 0, 0.30, 0.45)
        let low = StoneCeremonyScore.act1(timeline(.neutralMedium, mode: .lowPower), carveLength: 300, hapticsOn: true)
        #expect(low.count == 1)
        expectEvent(low[0], 0.20, 0.50, 0.40)
    }

    @Test func pattern() throws {
        let events = StoneCeremonyScore.act1(timeline(.neutralMedium), carveLength: 300, hapticsOn: true)
        let pattern = try StoneCeremonyHaptics.pattern(events)
        #expect(abs(pattern.duration - events[events.count - 1].at) < 0.05)
    }
}
