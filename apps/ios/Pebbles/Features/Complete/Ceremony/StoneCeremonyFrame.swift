import SwiftUI

/// Every animated value of the stone reveal ceremony at one instant (#1691).
/// Pure data, sampled by `StoneCeremonyTimeline.act1` and `act2`, so the
/// choreography can be asserted without a view.
struct StoneCeremonyFrame: Equatable {
    // The stone, while it is the hero or in flight.
    var stoneOpacity = 0.0
    var stoneScale = 1.0
    /// 0…1: how far above the surface the stone is held (D11).
    var lift = 0.0
    var lipTrim = 1.0
    var inkTrim = 1.0
    var inkOpacity = 1.0
    /// Only `sweep`, the spark and `waves` are read.
    var press = StoneMotion.Frame()

    // The flight: 0 at the centre, 1 at the cell, eased.
    var flight = 0.0
    /// The week cell's own stone.
    var cellStoneOpacity = 0.0

    // The text: 1 puts it under the hero, 0 at its rest.
    var nameOpacity = 0.0
    /// The karma line, or the calm sentence.
    var karmaOpacity = 0.0
    var textDrop = 1.0

    // The bar.
    var buttonOpacity = 0.0
    /// 0 reads "Continue", 1 reads "Done".
    var buttonLabel = 0.0
    /// The quiet link at the pause.
    var supportUnderContinue = 0.0
    /// Record another, and the quiet link above Done.
    var actionsOpacity = 0.0

    // The cascade.
    var dayOpacity = [Double](repeating: 0, count: 7)
    var rowOpacity = [Double](repeating: 0, count: 4)
    var rowRise = [CGFloat](repeating: 0, count: 4)
}

// MARK: - Sampling

extension StoneCeremonyTimeline {
    /// Act 1 at `time` seconds from the first frame, held on the pause from there on.
    /// `heroReach[k]` is how far hero wave `k` travels, in stone widths.
    func act1(at time: Double, heroReach: [Double]) -> StoneCeremonyFrame {
        let t = min(max(time, 0), pause)
        var f = StoneCeremonyFrame()

        if mode == .full {
            f.stoneOpacity = progress(t, 0, 0.6 * contact)
            let fall = ease(Curves.fall, progress(t, 0, contact))
            let dip = StoneMotion.Dip.element
            let dipRun = StoneMotion.Element(delay: contact, duration: dip.duration, curve: dip.curve)
            let dipFrames: StoneMotion.Frames = [(0, 1), (0.26, dipDepth), (0.6, 1.03), (1, 1)]
            let dipValue = StoneMotion.piecewise(elapsed(dipRun, t), dipFrames)
            f.stoneScale = lerp(1.12, 1, fall) * dipValue
            f.lift = 1 - fall

            f.press.waves = (0..<heroWaveCount).map { k in
                let run = StoneMotion.Element(delay: contact + 0.08 + 0.11 * Double(k),
                                              duration: (0.70 + 0.07 * Double(k)) * tempo,
                                              curve: StoneMotion.Wave.element(k).curve)
                let reach = k < heroReach.count ? heroReach[k] : (heroReach.last ?? 2)
                return waveSample(run, t, scale: [(0, 1.15), (0.07, 1.45), (1, reach)])
            }

            if carves {
                f.lipTrim = ease(Curves.inOut, progress(t, carveStart, carveDuration))
                f.inkTrim = ease(Curves.inOut, progress(t - 0.07, carveStart, carveDuration))
                f.inkOpacity = lerp(0.6, 1, progress(t, closure, 0.2 * tempo))
            }

            if sparksOnClosure {
                let spark = StoneMotion.Spark.element
                applySpark(&f.press, StoneMotion.Element(delay: closure, duration: spark.duration, curve: spark.curve), t)
            }
        } else {
            f.stoneOpacity = ease(Curves.out, progress(t, 0, contact))
        }

        // Reduce Motion and Low Power show no text at the pause (D7); it arrives with Act 2.
        if mode == .full {
            f.nameOpacity = ease(Curves.out, progress(t, nameIn, 0.3 * tempo))
            f.karmaOpacity = ease(Curves.out, progress(t, karmaIn, 0.3 * tempo))
        }
        f.buttonOpacity = ease(Curves.out, progress(t, continueIn, continueFade))
        f.supportUnderContinue = quiet ? f.buttonOpacity : 0
        return f
    }

    /// Act 2 at `u` seconds from Continue. The clock stops at `still`, where this
    /// equals `settledFrame`.
    func act2(at u: Double, markedDay: Int) -> StoneCeremonyFrame {
        let u = max(u, 0)
        var f = StoneCeremonyFrame()
        // The quiet link fades as Continue becomes Done; a stone that is not quiet never showed one.
        f.supportUnderContinue = quiet ? 1 - progress(u, 0, 0.2) : 0
        f.buttonOpacity = 1

        guard mode == .full else {
            let a = ease(Curves.out, progress(u, 0, flight))
            f.stoneOpacity = 1 - a
            f.cellStoneOpacity = a
            f.nameOpacity = a; f.karmaOpacity = a
            f.buttonLabel = a; f.actionsOpacity = a
            f.dayOpacity = f.dayOpacity.map { _ in a }
            f.rowOpacity = f.rowOpacity.map { _ in a }
            f.textDrop = 0
            return f
        }

        f.flight = ease(Curves.out, progress(u, 0, flight))
        let bump = progress(u, 0, 0.14) * (1 - progress(u, landing - 0.1, 0.1))
        let dip = StoneMotion.Dip.element
        let dipRun = StoneMotion.Element(delay: landing, duration: dip.duration, curve: dip.curve)
        let dipValue = StoneMotion.piecewise(elapsed(dipRun, u), [(0, 1), (0.26, 0.94), (0.6, 1.03), (1, 1)])
        f.stoneScale = (1 + 0.04 * bump) * dipValue
        f.lift = 0.7 * bump

        f.press.waves = (0..<landingWaveCount).map { k in
            waveSample(landingWave(k), u, scale: StoneMotion.Wave.scaleFrames(size, k))
        }
        if let sweep = landingSweep, sweep.isRunning(at: u) { f.press.sweep = sweep.progress(at: u) }
        if let spark = landingSpark { applySpark(&f.press, spark, u) }

        f.textDrop = 1 - ease(Curves.out, progress(u, 0, 1.2 * flight))
        f.nameOpacity = 1; f.karmaOpacity = 1

        // The flying stone owns the landing press until it is still, then the cell's stone takes over.
        let handedOver = u >= still
        f.stoneOpacity = handedOver ? 0 : 1
        f.cellStoneOpacity = handedOver ? 1 : 0

        f.dayOpacity = (0..<7).map { i in
            if i == markedDay { return u >= landing ? 1 : 0 }
            return ease(Curves.out, progress(u, dayIn(abs(i - markedDay)), 0.3 * tempo))
        }
        let rows = (0..<4).map { ease(Curves.out, progress(u, rowIn($0), 0.3 * tempo)) }
        f.rowOpacity = rows
        f.rowRise = rows.map { CGFloat(6 * (1 - $0)) }

        f.buttonLabel = ease(Curves.out, progress(u, actionsIn, 0.3 * tempo))
        f.actionsOpacity = f.buttonLabel
        return f
    }

    /// Act 2 once everything has come to rest.
    func settledFrame(markedDay: Int) -> StoneCeremonyFrame {
        act2(at: still, markedDay: markedDay)
    }
}

// MARK: - Helpers

private enum Curves {
    static let fall = bezier(0.5, 0, 0.9, 0.6)
    static let out = bezier(0.2, 0.7, 0.2, 1)
    static let inOut = bezier(0.37, 0, 0.63, 1)

    private static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> UnitCurve {
        .bezier(startControlPoint: UnitPoint(x: x1, y: y1), endControlPoint: UnitPoint(x: x2, y: y2))
    }
}

/// How far `x` is through `start ..< start + duration`, 0…1. A zero duration jumps at `start`.
/// A hair under 1 counts as 1, so a sum like 0.43 + 0.3 lands on its end.
private func progress(_ x: Double, _ start: Double, _ duration: Double) -> Double {
    guard duration > 0 else { return x >= start ? 1 : 0 }
    let p = (x - start) / duration
    if p <= 0 { return 0 }
    return p >= 1 - 1e-9 ? 1 : p
}

private func lerp(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }

/// `curve` at `p`, exactly 0 and 1 at the ends.
private func ease(_ curve: UnitCurve, _ p: Double) -> Double {
    if p <= 0 { return 0 }
    return p >= 1 ? 1 : curve.value(at: p)
}

/// An element's eased progress: exactly 0 before it starts and 1 from its end.
private func elapsed(_ element: StoneMotion.Element, _ x: Double) -> Double {
    if x <= element.delay { return 0 }
    return x >= element.end ? 1 : element.progress(at: x)
}

private func waveSample(_ element: StoneMotion.Element, _ x: Double,
                        scale: StoneMotion.Frames) -> StoneMotion.WaveSample {
    let p = elapsed(element, x)
    return StoneMotion.WaveSample(scale: StoneMotion.piecewise(p, scale),
                                  alpha: StoneMotion.piecewise(p, StoneMotion.Wave.alphaFrames))
}

private func applySpark(_ frame: inout StoneMotion.Frame, _ element: StoneMotion.Element, _ x: Double) {
    let p = elapsed(element, x)
    frame.sparkScale = StoneMotion.piecewise(p, StoneMotion.Spark.scaleFrames)
    frame.sparkRotation = StoneMotion.piecewise(p, StoneMotion.Spark.rotationFrames)
    frame.sparkAlpha = StoneMotion.piecewise(p, StoneMotion.Spark.alphaFrames)
}
