import CoreGraphics
import Testing
@testable import Pebbles

private func close(_ a: Double, _ b: Double, _ tolerance: Double = 0.0005) -> Bool { abs(a - b) < tolerance }

@Suite("Stone ceremony frames")
struct StoneCeremonyFrameTests {
    private let t = StoneCeremonyTimeline(valence: .neutralMedium, carves: true, mode: .full)
    private let reach = [2.388, 2.245]
    private let marked = 2

    private func timeline(_ polarity: ValencePolarity, carves: Bool = true, quiet: Bool = false,
                          mode: StoneCeremonyMode = .full) -> StoneCeremonyTimeline {
        StoneCeremonyTimeline(size: .medium, polarity: polarity, carves: carves, quiet: quiet, mode: mode)
    }

    @Test func setDown() {
        let f0 = t.act1(at: 0, heroReach: reach)
        #expect(f0.stoneOpacity == 0); #expect(close(f0.stoneScale, 1.12)); #expect(f0.lift == 1)
        #expect(f0.lipTrim == 0); #expect(close(f0.inkOpacity, 0.6)); #expect(f0.buttonOpacity == 0)
        let fc = t.act1(at: 0.32, heroReach: reach)
        #expect(fc.stoneOpacity == 1); #expect(close(fc.stoneScale, 1)); #expect(fc.lift == 0)
        let lowest = stride(from: 0.32, through: 0.84, by: 0.005).map { t.act1(at: $0, heroReach: reach).stoneScale }.min()!
        #expect(abs(lowest - 0.94) < 0.005)
    }

    @Test func ripple() {
        // the first wave peaks (alpha 0.85) at 7 % of its eased run, which is early in its time
        let start = t.contact + 0.08
        let peak = stride(from: start, through: start + 0.7, by: 0.0002)
            .map { t.act1(at: $0, heroReach: reach).press.waves[0].alpha }.max()!
        #expect(close(peak, 0.85, 0.005))
        #expect(t.act1(at: start - 0.001, heroReach: reach).press.waves[0].alpha == 0)
        #expect(t.act1(at: start + 0.049, heroReach: reach).press.waves[0].alpha > 0.5)
        #expect(t.act1(at: 0.5, heroReach: reach).press.waves.count == 2)
        // the second wave is late by 0.11 s
        #expect(t.act1(at: start + 0.11 - 0.001, heroReach: reach).press.waves[1].alpha == 0)
        // the waves run out to the reach given
        let far = t.act1(at: start + 0.71, heroReach: reach).press.waves[0]
        #expect(close(far.scale, 2.388) && far.alpha == 0)
        let quiet = timeline(.neutral, quiet: true)
        #expect(quiet.act1(at: 0.5, heroReach: reach).press.waves.count == 1)
        let lowPower = timeline(.neutral, mode: .lowPower)
        #expect(lowPower.act1(at: 0.1, heroReach: reach).press.waves.isEmpty)
    }

    @Test func carve() {
        #expect(t.act1(at: 0.84, heroReach: reach).lipTrim == 0)
        #expect(t.act1(at: 1.40, heroReach: reach).lipTrim == 1)
        #expect(t.act1(at: 1.40, heroReach: reach).inkTrim < 1)
        #expect(t.act1(at: 1.47, heroReach: reach).inkTrim == 1)
        #expect(close(t.act1(at: 1.40, heroReach: reach).inkOpacity, 0.6))
        #expect(t.act1(at: 1.60, heroReach: reach).inkOpacity == 1)
        let bare = timeline(.neutral, carves: false).act1(at: 0, heroReach: reach)
        #expect(bare.lipTrim == 1 && bare.inkTrim == 1 && bare.inkOpacity == 1)
    }

    @Test func spark() {
        let gem = timeline(.highlight)
        #expect(gem.act1(at: gem.closure + 0.266, heroReach: reach).press.sparkAlpha > 0)
        #expect(gem.act1(at: gem.closure - 0.01, heroReach: reach).press.sparkAlpha == 0)
        let samples = stride(from: 0.0, through: 2.5, by: 0.02)
        for other in [timeline(.neutral), timeline(.highlight, quiet: true)] {
            #expect(samples.allSatisfy { other.act1(at: $0, heroReach: reach).press.sparkAlpha == 0 })
        }
    }

    @Test func pauseIsStill() {
        let p = t.act1(at: t.pause, heroReach: reach)
        #expect(p.lipTrim == 1 && p.inkTrim == 1 && p.inkOpacity == 1)
        #expect(p.nameOpacity == 1 && p.karmaOpacity == 1 && p.buttonOpacity == 1 && p.textDrop == 1)
        #expect(p.press.waves.allSatisfy { $0.alpha == 0 }); #expect(p.press.sweep == nil); #expect(p.press.sparkAlpha == 0)
        #expect(p.dayOpacity == Array(repeating: 0, count: 7)); #expect(p.rowOpacity == [0, 0, 0, 0]); #expect(p.actionsOpacity == 0)
        #expect(t.act1(at: 60, heroReach: reach) == p)   // a backgrounded app resumes on the pause
        let gem = timeline(.highlight)
        let g = gem.act1(at: gem.pause, heroReach: reach)
        #expect(g.press.sparkAlpha == 0 && g.press.waves.allSatisfy { $0.alpha == 0 })
        let quiet = timeline(.lowlight, quiet: true)
        let q = quiet.act1(at: quiet.pause, heroReach: reach)
        #expect(q.buttonOpacity == 1 && q.supportUnderContinue == 1 && q.press.waves.allSatisfy { $0.alpha == 0 })
        #expect(p.supportUnderContinue == 0)
    }

    @Test func toItsDay() {
        func at(_ u: Double) -> StoneCeremonyFrame { t.act2(at: u, markedDay: marked) }
        #expect(at(0).flight == 0); #expect(at(0.38).flight == 1)
        let early = stride(from: 0.0, to: 0.38, by: 0.005).map { at($0) }
        #expect(abs(early.map(\.stoneScale).max()! - 1.04) < 0.005)
        #expect(early.map(\.lift).max()! <= 0.7 + 1e-9)
        #expect(at(0).textDrop == 1); #expect(at(0.456).textDrop == 0)
        #expect(at(0).nameOpacity == 1 && at(0).karmaOpacity == 1)
        #expect(stride(from: 0.0, through: 1.16, by: 0.01).allSatisfy { at($0).buttonOpacity == 1 })
        #expect(at(0.86).buttonLabel == 0); #expect(at(1.16).buttonLabel == 1)
        #expect(at(0.86).actionsOpacity == 0); #expect(at(1.16).actionsOpacity == 1)
        #expect(at(0).supportUnderContinue == 0)   // a stone that is not quiet has no link to fade
        #expect(at(0.38).dayOpacity[2] == 1); #expect(at(0.37).dayOpacity[2] == 0)
        #expect(at(0.43).dayOpacity[1] == 0); #expect(at(0.73).dayOpacity[1] == 1)
        #expect(at(0.60).dayOpacity[6] == 0); #expect(at(0.61 + 0.001).dayOpacity[6] > 0)
        #expect(at(0.58).rowOpacity[0] == 0); #expect(at(0.58).rowRise[0] == 6)
        #expect(at(0.88).rowOpacity[0] == 1); #expect(at(0.88).rowRise[0] == 0)
        #expect(at(0.60).rowRise[0] < 6 && at(0.60).rowRise[0] > 0)
        // the landing press
        #expect(at(0.43).press.waves.count == 2); #expect(at(0.439).press.waves[0].alpha == 0)
        #expect(at(0.45).press.waves[0].alpha > 0)
        #expect(at(0.50).press.sweep == nil); #expect(at(0.53).press.sweep != nil)
        #expect(at(0.43).press.sparkAlpha == 0)
        #expect(at(0.52).stoneOpacity == 1 && at(0.52).cellStoneOpacity == 0)
        let quiet = StoneCeremonyTimeline(size: .medium, polarity: .neutral, carves: true, quiet: true, mode: .full)
        let q = quiet.act2(at: quiet.landing + 0.3, markedDay: marked)
        #expect(q.press.waves.isEmpty); #expect(q.press.sweep == nil)
        #expect(quiet.act2(at: 0, markedDay: marked).supportUnderContinue == 1)
        #expect(quiet.act2(at: 0.25, markedDay: marked).supportUnderContinue == 0)
        #expect(t.act2(at: t.still, markedDay: marked) == t.settledFrame(markedDay: marked))
        let gem = timeline(.highlight)
        #expect(gem.act2(at: gem.landing + 0.30 + 0.266, markedDay: marked).press.sparkAlpha > 0)
        #expect(gem.act2(at: gem.still, markedDay: marked) == gem.settledFrame(markedDay: marked))
        #expect(quiet.act2(at: quiet.still, markedDay: marked) == quiet.settledFrame(markedDay: marked))
    }

    @Test func handover() {
        let before = t.act2(at: t.still - 0.01, markedDay: marked)
        #expect(before.stoneOpacity == 1 && before.cellStoneOpacity == 0)
        let after = t.act2(at: t.still, markedDay: marked)
        #expect(after.stoneOpacity == 0 && after.cellStoneOpacity == 1)
    }

    @Test func settled() {
        let s = t.settledFrame(markedDay: marked)
        #expect(s.stoneOpacity == 0); #expect(s.cellStoneOpacity == 1); #expect(s.flight == 1); #expect(s.textDrop == 0)
        #expect(s.nameOpacity == 1 && s.karmaOpacity == 1 && s.buttonOpacity == 1 && s.buttonLabel == 1)
        #expect(s.actionsOpacity == 1 && s.supportUnderContinue == 0)
        #expect(s.dayOpacity == Array(repeating: 1, count: 7)); #expect(s.rowOpacity == Array(repeating: 1, count: 4))
        #expect(s.rowRise == Array(repeating: 0, count: 4))
        #expect(s.press.waves.allSatisfy { $0.alpha == 0 }); #expect(s.press.sweep == nil); #expect(s.press.sparkAlpha == 0)
        #expect(close(s.stoneScale, 1)); #expect(s.lift == 0)
    }

    @Test func reducedMotionAndLowPower() {
        let rm = timeline(.neutral, mode: .reducedMotion)
        let a = rm.act1(at: 0.25, heroReach: reach)
        #expect(a.stoneOpacity == 1); #expect(a.lipTrim == 1 && a.inkTrim == 1 && a.inkOpacity == 1)
        #expect(a.stoneScale == 1); #expect(a.lift == 0); #expect(a.press.waves.isEmpty); #expect(a.nameOpacity == 0)
        #expect(rm.act1(at: 0.70, heroReach: reach).buttonOpacity == 1)
        for u in stride(from: 0.0, to: 0.25, by: 0.01) {
            let f = rm.act2(at: u, markedDay: marked)
            #expect(f.flight == 0 && f.stoneScale == 1 && f.textDrop == 0)
            #expect(f.press.waves.isEmpty && f.press.sweep == nil)
        }
        #expect(rm.act2(at: 0.25, markedDay: marked) == rm.settledFrame(markedDay: marked))
        let mid = rm.act2(at: 0.125, markedDay: marked)
        #expect(close(mid.stoneOpacity + mid.cellStoneOpacity, 1))
        #expect(mid.dayOpacity.allSatisfy { $0 == mid.cellStoneOpacity } && mid.rowOpacity.allSatisfy { $0 == mid.cellStoneOpacity })
        #expect(mid.nameOpacity == mid.cellStoneOpacity && mid.actionsOpacity == mid.cellStoneOpacity)

        let lp = timeline(.highlight, mode: .lowPower)
        #expect(lp.act1(at: 0.1, heroReach: reach).stoneOpacity > 0)
        for u in stride(from: 0.0, to: 0.20, by: 0.01) {
            let f = lp.act2(at: u, markedDay: marked)
            #expect(f.flight == 0 && f.stoneScale == 1 && f.press.sweep == nil && f.press.sparkAlpha == 0)
        }
        #expect(lp.act2(at: 0.20, markedDay: marked) == lp.settledFrame(markedDay: marked))
    }
}
