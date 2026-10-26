import Foundation

/// How fast the month's intro plays (#1241 D9). The clock scales a
/// sequence's beats by its factor when it starts it: the cover by `cover`,
/// each section by `sections`. The Spine Lab tunes it live; the app plays
/// `.standard`.
///
/// Factors are 1 or more: the score decides which stacked seals tick at ×1,
/// against the haptics' 25 ms floor, so only a slower tempo keeps those
/// ticks clear of it. `factor(_:)` reads a smaller one as 1.
struct IntroTempo: Hashable, Sendable {
    /// ×1.45 takes the garland from 1.5 s to about 2.2 s.
    var cover: Double = 1.45
    var sections: Double = 2.5

    static let standard = IntroTempo()

    func factor(_ sequence: IntroSequence) -> Double {
        max(1, sequence == .cover ? cover : sections)
    }
}

extension IntroBeat {
    /// The beat `factor` times slower: its time and its duration.
    func scaled(by factor: Double) -> IntroBeat {
        IntroBeat(key: key, at: at * factor, duration: duration * factor, tick: tick)
    }
}
