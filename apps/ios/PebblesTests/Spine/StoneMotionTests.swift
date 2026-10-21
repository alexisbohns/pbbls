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

    @Test("each element's delay and duration are the web's, one by one")
    func timings() {
        let elements: [(element: Motion.Element, timing: [Double])] = [
            (Motion.Dip.element, [0, 0.52]),
            (Motion.Rings.element, [0.11, 1.15]),
            (Motion.Sweep.element, [0.14, 0.9]),
            (Motion.Spark.element, [0.3, 0.76]),
            (Motion.Wave.element(0), [0.12, 1.65]),
            (Motion.Wave.element(1), [0.35, 1.82]),
            (Motion.Wave.element(2), [0.58, 1.99])
        ]
        for (element, timing) in elements {
            #expect(near(element.delay, timing[0]) && near(element.duration, timing[1]), "\(timing)")
        }
    }

    @Test("every keyframe table is the web's, stop by stop")
    func tables() {
        func stops(_ frames: Motion.Frames) -> [[Double]] { frames.map { [$0.at, $0.value] } }
        #expect(stops(Motion.Dip.frames) == [[0, 1], [0.26, 0.9], [0.6, 1.03], [1, 1]])
        #expect(stops(Motion.Rings.frames) == [[0, 1], [0.35, 1.08], [1, 1]])
        #expect(stops(Motion.Spark.scaleFrames) == [[0, 0], [0.35, 1], [1, 0]])
        #expect(stops(Motion.Spark.rotationFrames) == [[0, 0], [0.35, 40], [1, 90]])
        #expect(stops(Motion.Spark.alphaFrames) == [[0, 0], [0.35, 1], [1, 0]])
        #expect(stops(Motion.Wave.alphaFrames) == [[0, 0], [0.07, 0.85], [1, 0]])
        #expect(stops(Motion.Wave.scaleFrames(.medium, 2)) == [[0, 1.15], [0.07, 1.45], [1, 4.6]])
        #expect(Motion.Wave.reach[.small] == 7.2)
        #expect(Motion.Sweep.travel == 1.6)
    }

    /// Halfway through each element, against the web's `cubic-bezier` solved
    /// for x = 0.5 (bisection on the CSS definition).
    @Test("each easing is the web's cubic-bezier")
    func curves() {
        let tolerance = 1e-3
        #expect(near(Motion.Dip.element.progress(at: 0.26), 0.92217, tolerance))
        #expect(near(Motion.Rings.element.progress(at: 0.685), 0.92958, tolerance))
        #expect(near(Motion.Sweep.element.progress(at: 0.59), 0.5, tolerance))
        #expect(near(Motion.Spark.element.progress(at: 0.68), 0.87668, tolerance))
        #expect(near(Motion.Wave.element(0).progress(at: 0.945), 0.88907, tolerance))
    }

    @Test("a zero-length element jumps at its delay")
    func instant() {
        let element = Motion.Element(delay: 0.2, duration: 0, curve: .linear)
        #expect(element.progress(at: 0.1) == 0 && element.progress(at: 0.2) == 1)
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
        // 70 ms in: eased progress 0.351, past the 26 % stop, so 0.935 on the way back up.
        let dip = Motion.frame(at: 0.07, still: false, size: .medium).dip
        #expect((0.93...0.94).contains(dip))
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
