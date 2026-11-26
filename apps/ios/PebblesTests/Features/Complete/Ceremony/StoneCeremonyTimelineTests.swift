import CoreGraphics
import Testing
@testable import Pebbles

private func close(_ a: Double, _ b: Double) -> Bool { abs(a - b) < 0.0005 }

@Suite("Stone ceremony timeline and layout")
struct StoneCeremonyTimelineTests {
    @Test func riverMedium() {
        let t = StoneCeremonyTimeline(valence: .neutralMedium, carves: true, mode: .full)
        #expect(close(t.contact, 0.32)); #expect(close(t.carveStart, 0.84)); #expect(close(t.closure, 1.40))
        #expect(close(t.nameIn, 1.50)); #expect(close(t.karmaIn, 1.59)); #expect(close(t.continueIn, 1.66))
        #expect(close(t.pause, 1.96))                                  // spec: Act 1 about 1.9–2.2 s
        #expect(close(t.landing, 0.38)); #expect(close(t.dayIn(1), 0.43)); #expect(close(t.dayIn(3), 0.55))
        #expect(close(t.rowIn(0), 0.58)); #expect(close(t.rowIn(3), 0.76))
        #expect(close(t.actionsIn, 0.86)); #expect(close(t.settled, 1.16)) // spec: Act 2 about 1.2 s
        #expect(close(t.still, 1.465)); #expect(t.tempo == 1)
    }

    @Test func lavaAndGem() {
        // lava carves 1.0–1.1 s (spec)
        let lava = StoneCeremonyTimeline(valence: .lowlightMedium, carves: true, mode: .full)
        #expect(close(lava.contact, 0.42)); #expect(close(lava.carveDuration, 1.05))
        #expect(close(lava.closure, 1.99)); #expect(close(lava.pause, 2.55))
        // the gem's spark ends at closure + 0.76, after closure + 0.56
        let gem = StoneCeremonyTimeline(valence: .highlightMedium, carves: true, mode: .full)
        #expect(gem.sparksOnClosure)
        #expect(close(gem.contact, 0.28)); #expect(close(gem.carveDuration, 0.50))
        #expect(close(gem.closure, 1.30)); #expect(close(gem.pause, 2.06))
        // the landing spark ends at landing + 0.30 + 0.76 = 1.44, before the second wave's 1.465
        #expect(close(gem.still, 1.465)); #expect(gem.still >= gem.settled)
    }

    @Test func noGlyph() {
        let t = StoneCeremonyTimeline(valence: .neutralMedium, carves: false, mode: .full)
        #expect(t.carveDuration == 0)                                   // spec: 1.4 s
        #expect(close(t.closure, 0.84)); #expect(close(t.pause, 1.40))
        #expect(!t.sparksOnClosure)
        let gem = StoneCeremonyTimeline(valence: .highlightMedium, carves: false, mode: .full)
        #expect(!gem.sparksOnClosure)
    }

    @Test func quietIsSlower() {
        let q = StoneCeremonyTimeline(valence: .lowlightLarge, carves: true, mode: .full)
        let n = StoneCeremonyTimeline(size: .large, polarity: .lowlight, carves: true, quiet: false, mode: .full)
        #expect(q.tempo == 1.25); #expect(n.tempo == 1)
        #expect(close(q.contact, n.contact * 1.25)); #expect(close(q.carveStart, n.carveStart * 1.25))
        #expect(close(q.closure, n.closure * 1.25)); #expect(close(q.nameIn, n.nameIn * 1.25))
        #expect(close(q.karmaIn, n.karmaIn * 1.25)); #expect(close(q.continueIn, n.continueIn * 1.25))
        #expect(close(q.pause, n.pause * 1.25)); #expect(close(q.landing, n.landing * 1.25))
        #expect(close(q.actionsIn, n.actionsIn * 1.25)); #expect(close(q.settled, n.settled * 1.25))
        #expect(q.heroWaveCount == 1); #expect(q.landingWaveCount == 0); #expect(!q.sparksOnClosure)
        #expect(close(q.dipDepth, 1 - (1 - 0.91) / 2))
    }

    @Test func modes() {
        #expect(StoneCeremonyMode.resolve(reduceMotion: true, lowPower: true) == .lowPower)
        #expect(StoneCeremonyMode.resolve(reduceMotion: true, lowPower: false) == .reducedMotion)
        #expect(StoneCeremonyMode.resolve(reduceMotion: false, lowPower: true) == .lowPower)
        #expect(StoneCeremonyMode.resolve(reduceMotion: false, lowPower: false) == .full)

        let rm = StoneCeremonyTimeline(valence: .neutralMedium, carves: true, mode: .reducedMotion)
        #expect(close(rm.contact, 0.25)); #expect(close(rm.continueIn, 0.40)); #expect(close(rm.continueFade, 0.30))
        #expect(close(rm.pause, 0.70)); #expect(close(rm.settled, 0.25)); #expect(close(rm.still, 0.25))
        #expect(close(rm.landing, 0.25)); #expect(close(rm.dayIn(2), 0)); #expect(close(rm.rowIn(1), 0))
        #expect(close(rm.actionsIn, 0)); #expect(rm.heroWaveCount == 0); #expect(rm.landingWaveCount == 0)
        #expect(!rm.sparksOnClosure)

        let lp = StoneCeremonyTimeline(valence: .highlightMedium, carves: true, mode: .lowPower)
        #expect(close(lp.contact, 0.20)); #expect(close(lp.continueIn, 0.20)); #expect(close(lp.continueFade, 0.20))
        #expect(close(lp.pause, 0.40)); #expect(close(lp.settled, 0.20)); #expect(close(lp.still, 0.20))
        #expect(lp.heroWaveCount == 0); #expect(lp.landingWaveCount == 0); #expect(!lp.sparksOnClosure)
    }

    @Test func sizes() {
        #expect(StoneCeremonyLayout.heroSize(.small).width == 122)
        #expect(StoneCeremonyLayout.heroSize(.medium) == CGSize(width: 158, height: 158))
        #expect(StoneCeremonyLayout.heroSize(.large).width == 168)
        #expect(abs(StoneCeremonyLayout.heroSize(.large).height - 200) < 1)
        #expect(ValenceSizeGroup.allCases.map { StoneCeremonyLayout.landingSize($0).width } == [24, 28, 34])
        #expect(abs(StoneCeremonyLayout.slotHeight(.small) - 19.23) < 0.01)
        #expect(StoneCeremonyLayout.slotHeight(.medium) == 28)
        #expect(abs(StoneCeremonyLayout.slotHeight(.large) - 40.6) < 0.05)
        #expect(StoneCeremonyLayout.slotHeight(nil) == 10)
        #expect(abs(StoneCeremonyLayout.heroWaveReach(screenWidth: 393, heroWidth: 158, wave: 0) - 2.388) < 0.001)
        #expect(abs(StoneCeremonyLayout.heroWaveReach(screenWidth: 393, heroWidth: 158, wave: 1) - 2.245) < 0.001)
    }
}
