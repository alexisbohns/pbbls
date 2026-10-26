import Foundation

/// How fast the month's intro plays (#1241 D9). The clock scales a
/// sequence's beats by its factor when it starts it: the cover by `cover`,
/// each section by `sections`. The Spine Lab tunes it live; the app plays
/// `.standard`.
struct IntroTempo: Hashable, Sendable {
    /// ×1.45 takes the garland from 1.5 s to about 2.2 s.
    var cover: Double = 1.45
    var sections: Double = 2.5

    static let standard = IntroTempo()

    func factor(_ sequence: IntroSequence) -> Double {
        sequence == .cover ? cover : sections
    }
}

extension IntroBeat {
    /// The beat `factor` times slower: its time and its duration.
    func scaled(by factor: Double) -> IntroBeat {
        IntroBeat(key: key, at: at * factor, duration: duration * factor, tick: tick)
    }
}
