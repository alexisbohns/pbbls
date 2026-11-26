import Foundation

/// How much of the ceremony runs. Low Power wins over Reduce Motion.
enum StoneCeremonyMode: Equatable {
    case full, reducedMotion, lowPower

    static func resolve(reduceMotion: Bool, lowPower: Bool) -> StoneCeremonyMode {
        if lowPower { return .lowPower }
        return reduceMotion ? .reducedMotion : .full
    }
}

/// The stone reveal ceremony's beats (#1691), the prototype's `pT`, in seconds.
/// Act 1 runs from the first frame; Act 2 from Continue. A quiet valence (high
/// distress) runs every beat 1.25× slower, with fewer waves and no spark.
/// Pure arithmetic, so the choreography can be asserted without a view.
struct StoneCeremonyTimeline: Equatable {
    let size: ValenceSizeGroup
    let polarity: ValencePolarity
    let carves: Bool
    let quiet: Bool
    let mode: StoneCeremonyMode

    init(size: ValenceSizeGroup, polarity: ValencePolarity, carves: Bool, quiet: Bool, mode: StoneCeremonyMode) {
        self.size = size
        self.polarity = polarity
        self.carves = carves
        self.quiet = quiet
        self.mode = mode
    }

    init(valence: Valence, carves: Bool, mode: StoneCeremonyMode) {
        self.init(size: valence.sizeGroup, polarity: valence.polarity, carves: carves,
                  quiet: valence.quietSuccess, mode: mode)
    }

    var tempo: Double { quiet ? 1.25 : 1 }

    // MARK: Act 1

    /// The full contact, or the end of the fade in the other modes.
    var contact: Double {
        switch mode {
        case .full: return tempo * (polarity == .lowlight ? 0.42 : polarity == .neutral ? 0.32 : 0.28)
        case .reducedMotion: return tempo * 0.25
        case .lowPower: return tempo * 0.20
        }
    }

    var carveStart: Double { mode == .full ? contact + tempo * 0.52 : contact }

    /// 0 with no glyph to carve, and where the glyph simply appears.
    var carveDuration: Double {
        guard mode == .full, carves else { return 0 }
        return tempo * (polarity == .lowlight ? 1.05 : polarity == .neutral ? 0.56 : 0.50)
    }

    var closure: Double { carveStart + carveDuration }
    var nameIn: Double { mode == .full ? closure + tempo * 0.10 : closure }
    var karmaIn: Double { mode == .full ? closure + tempo * 0.19 : closure }

    var continueIn: Double {
        switch mode {
        case .full: return closure + tempo * 0.26
        case .reducedMotion: return tempo * 0.40
        case .lowPower: return tempo * 0.20
        }
    }

    var continueFade: Double {
        tempo * (mode == .lowPower ? 0.20 : 0.30)
    }

    /// Where Act 1 rests: the later of closure + 0.56 and the closure spark's end.
    var pause: Double {
        switch mode {
        case .full:
            let spark = sparksOnClosure ? StoneMotion.Spark.element.duration : 0
            return closure + tempo * max(0.56, spark)
        case .reducedMotion: return tempo * 0.70
        case .lowPower: return tempo * 0.40
        }
    }

    // MARK: Act 2

    var flight: Double {
        switch mode {
        case .full: return tempo * 0.38
        case .reducedMotion: return tempo * 0.25
        case .lowPower: return tempo * 0.20
        }
    }

    /// The stone lands the instant its flight ends.
    var landing: Double { flight }

    /// `offset` is the distance from the marked day, at least 1.
    func dayIn(_ offset: Int) -> Double {
        mode == .full ? landing + tempo * (0.05 + 0.06 * Double(offset - 1)) : 0
    }

    /// Index 0 is the "MAKE IT RICHER" header.
    func rowIn(_ index: Int) -> Double {
        mode == .full ? landing + tempo * (0.20 + 0.06 * Double(index)) : 0
    }

    var actionsIn: Double { mode == .full ? landing + tempo * 0.48 : 0 }

    /// The layout and Done are in: richer input is accepted from here.
    var settled: Double { mode == .full ? actionsIn + tempo * 0.30 : flight }

    /// When the last landing wave, sweep or spark has ended, so the clock can stop on a still frame.
    /// A quiet stone has none of the three, so it stops at `settled`.
    var still: Double {
        guard mode == .full else { return flight }
        var end = settled
        for k in 0..<landingWaveCount { end = max(end, landingWave(k).end) }
        if let sweep = landingSweep { end = max(end, sweep.end) }
        if let spark = landingSpark { end = max(end, spark.end) }
        return end
    }

    // MARK: The landing press, in seconds from Continue

    /// Landing wave `k`: the press table's wave at half speed, from the landing.
    func landingWave(_ k: Int) -> StoneMotion.Element {
        let wave = StoneMotion.Wave.element(k)
        return StoneMotion.Element(delay: landing + wave.delay * 0.5, duration: wave.duration * 0.5, curve: wave.curve)
    }

    var landingSweep: StoneMotion.Element? {
        guard mode == .full, !quiet else { return nil }
        let sweep = StoneMotion.Sweep.element
        return StoneMotion.Element(delay: landing + sweep.delay, duration: sweep.duration, curve: sweep.curve)
    }

    /// The gem's spark, which a quiet stone does not get.
    var landingSpark: StoneMotion.Element? {
        guard mode == .full, !quiet, polarity == .highlight else { return nil }
        let spark = StoneMotion.Spark.element
        return StoneMotion.Element(delay: landing + spark.delay, duration: spark.duration, curve: spark.curve)
    }

    // MARK: Tables

    var heroWaveCount: Int { mode == .full ? (quiet ? 1 : 2) : 0 }
    var landingWaveCount: Int { mode == .full && !quiet ? 2 : 0 }

    /// How far the stone dips under the touch, halved toward 1 when quiet.
    var dipDepth: Double {
        let depths: [ValenceSizeGroup: Double] = [.small: 0.95, .medium: 0.94, .large: 0.92]
        let depth = (depths[size] ?? 0.94) - (polarity == .lowlight ? 0.01 : 0)
        return quiet ? 1 - (1 - depth) / 2 : depth
    }

    /// The spec names the gem only; the prototype also sparked the river.
    var sparksOnClosure: Bool { polarity == .highlight && carves && !quiet && mode == .full }
}
