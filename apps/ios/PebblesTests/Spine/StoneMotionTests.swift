import Foundation
import Testing
@testable import Pebbles

/// #1130 R12–R14: the web's stone-motion table at its stops, the WAAPI reading
/// (linear between keyframes in eased progress, clamped outside), the totals,
/// and the reduced-motion touch. Mirrors Android's StoneMotionTest.
@Suite("StoneMotion")
struct StoneMotionTests {
    typealias Motion = StoneMotion

    private func near(_ a: Double, _ b: Double, _ tolerance: Double = 1e-6) -> Bool { abs(a - b) <= tolerance }

    @Test("the endpoints are the first and last frames")
    func endpoints() {
        #expect(Motion.piecewise(0, Motion.Dip.frames) == 1)
        #expect(Motion.piecewise(1, Motion.Dip.frames) == 1)
        #expect(Motion.piecewise(0, Motion.Spark.rotationFrames) == 0)
        #expect(Motion.piecewise(1, Motion.Spark.rotationFrames) == 90)
    }

    @Test("between two frames the value is linear, and exact at a frame")
    func linear() {
        #expect(near(Motion.piecewise(0.13, Motion.Dip.frames), 0.95))
        #expect(near(Motion.piecewise(0.26, Motion.Dip.frames), 0.9))
        #expect(near(Motion.piecewise(0.6, Motion.Dip.frames), 1.03))
        #expect(near(Motion.piecewise(0.675, Motion.Spark.rotationFrames), 65))
    }

    @Test("outside the table the value clamps")
    func clamps() {
        #expect(Motion.piecewise(-1, Motion.Dip.frames) == 1)
        #expect(Motion.piecewise(2, Motion.Spark.rotationFrames) == 90)
        #expect(Motion.piecewise(2, Motion.Spark.alphaFrames) == 0)
    }

    @Test("a wave rises, then reaches its size's distance less half a step per wave")
    func wave() {
        let frames = Motion.Wave.scaleFrames(.small, 1)
        #expect(Motion.piecewise(0, frames) == 1.15)
        #expect(near(Motion.piecewise(0.07, frames), 1.45))
        #expect(near(Motion.piecewise(1, frames), 7.2 - 0.5))
        #expect(near(Motion.piecewise(0.07, Motion.Wave.alphaFrames), 0.85))
        #expect(Motion.Wave.reach[.medium] == 5.6 && Motion.Wave.reach[.large] == 4.4)
    }

    @Test("every element ends where the table says, and the press lasts 2.57 s")
    func totals() {
        #expect(near(Motion.Dip.element.end, 0.52))
        #expect(near(Motion.Rings.element.end, 1.26))
        #expect(near(Motion.Sweep.element.end, 1.04))
        #expect(near(Motion.Spark.element.end, 1.06))
        #expect(near(Motion.Wave.element(0).end, 1.77))
        #expect(near(Motion.Wave.element(2).end, 2.57))
        #expect(near(Motion.total(still: false), 2.57))
        #expect(Motion.total(still: true) == 0.9)
        #expect(Motion.openDelay == 0.36 && Motion.goPressDelay == 0.62)
    }

    @Test("one easing runs the whole element: 0 at its start, 1 at its end, rising between")
    func easing() {
        let rings = Motion.Rings.element
        #expect(rings.progress(at: 0) == 0)
        #expect(rings.progress(at: rings.delay) == 0)
        #expect(near(rings.progress(at: rings.end), 1))
        let samples = stride(from: rings.delay, through: rings.end, by: 0.05).map { rings.progress(at: $0) }
        #expect(samples == samples.sorted())
    }

    @Test("at the press the stone rests, the sweep is parked and the waves are mounted but clear")
    func start() {
        let frame = Motion.frame(at: 0, still: false, size: .medium)
        #expect(frame.dip == 1 && frame.rings == 1)
        #expect(frame.sweep == nil)
        #expect(frame.sparkAlpha == 0)
        #expect(frame.waves.count == 3 && frame.waves.allSatisfy { $0.alpha == 0 })
    }

    /// WAAPI: the eased progress passes the dip's 26 % stop within ~65 ms, so the
    /// stone is under 1 early and already springing back by the time the sweep runs.
    @Test("early in the press the stone dips; mid-press the sweep crosses")
    func middle() throws {
        #expect(Motion.frame(at: 0.06, still: false, size: .medium).dip < 1)
        let frame = Motion.frame(at: 0.2, still: false, size: .medium)
        let sweep = try #require(frame.sweep)
        #expect(sweep > 0 && sweep < 1)
    }

    @Test("after the last element everything is back at rest")
    func end() {
        let frame = Motion.frame(at: 2.6, still: false, size: .large)
        #expect(near(frame.dip, 1) && near(frame.rings, 1))
        #expect(frame.sweep == nil && frame.sparkAlpha == 0)
        #expect(frame.waves.allSatisfy { $0.alpha == 0 })
    }

    @Test("reduced motion holds a still sheen and dark rings for 900 ms, with no waves")
    func still() {
        let held = Motion.frame(at: 0.5, still: true, size: .small)
        #expect(held.stillSheen == 0.9 && held.stillRings)
        #expect(held.dip == 1 && held.waves.isEmpty && held.sweep == nil)
        let after = Motion.frame(at: 1, still: true, size: .small)
        #expect(after.stillSheen == 0 && !after.stillRings)
    }
}
