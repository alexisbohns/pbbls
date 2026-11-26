import CoreGraphics
import Foundation
import Observation

/// Runs the ceremony's state machine and its haptics together (#1691): the view hands it the
/// clock and the taps, and it plays each act's score at the instant that act's frames start.
/// A skip is silent; a ceremony that does not run, or a quiet one, plays nothing.
@Observable @MainActor
final class StoneCeremonyDriver {
    private(set) var ceremony: StoneCeremony

    @ObservationIgnored private let carveLength: CGFloat
    @ObservationIgnored private let hapticsOn: Bool
    @ObservationIgnored private let haptics: StoneCeremonyHapticPlaying
    /// A ceremony that starts at rest (the references' frozen frames) never plays, on any tap (D8).
    @ObservationIgnored private let frozen: Bool
    /// Set by `close()`: a late tap or `.task` after ✕ plays nothing.
    @ObservationIgnored private var closed = false

    init(ceremony: StoneCeremony, carveLength: CGFloat, hapticsOn: Bool,
         haptics: StoneCeremonyHapticPlaying? = nil) {
        self.ceremony = ceremony
        self.carveLength = carveLength
        self.hapticsOn = hapticsOn
        // Resolved here, in the main-actor init: a default argument is read from a nonisolated context.
        self.haptics = haptics ?? StoneCeremonyHaptics.shared
        self.frozen = !ceremony.isRunning
    }

    /// The hero's first appearance: Act 1 plays from this instant. A frozen ceremony plays nothing.
    func start(at now: Date) {
        guard !closed, !frozen, ceremony.isRunning else { return }
        let score = StoneCeremonyScore.act1(ceremony.timeline, carveLength: carveLength, hapticsOn: hapticsOn)
        guard !score.isEmpty else { return }
        haptics.prepare()
        haptics.play(score, startingAt: now)
    }

    func tick(at now: Date) {
        guard !closed else { return }
        let before = ceremony.phase
        ceremony.tick(at: now)
        warmIfPaused(since: before)
    }

    /// A tap that is not Continue skips to the next rest, and silences the act it skips.
    func tapOutside(at now: Date) {
        guard !closed else { return }
        let before = ceremony.phase
        let wasRunning = ceremony.isRunning
        ceremony.tapOutside(at: now)
        if wasRunning { haptics.stop() }
        warmIfPaused(since: before)
    }

    /// Continue ends Act 1's score and starts Act 2's; a tap that only skips is silent.
    func tapContinue(at now: Date) {
        guard !closed else { return }
        let before = ceremony.phase
        let wasRunning = ceremony.isRunning
        guard ceremony.tapContinue(at: now) else {
            if wasRunning { haptics.stop() }
            warmIfPaused(since: before)
            return
        }
        guard !frozen else { return }
        haptics.stop()
        let score = StoneCeremonyScore.act2(ceremony.timeline, hapticsOn: hapticsOn)
        guard !score.isEmpty else { return }
        haptics.play(score, startingAt: now)
    }

    func close() {
        closed = true
        haptics.stop()
    }

    /// Warms the engine as the ceremony arrives at the pause, so a quick Continue finds it running.
    /// The engine auto-shuts down when idle, so after a long pause this does nothing: `play` restarts
    /// the engine itself, and the Continue tick can then be a little late. Quiet and Haptics-off
    /// ceremonies play nothing, so they need no engine.
    private func warmIfPaused(since before: StoneCeremonyPhase) {
        guard !frozen, ceremony.phase == .paused, before != .paused else { return }
        guard !StoneCeremonyScore.act2(ceremony.timeline, hapticsOn: hapticsOn).isEmpty else { return }
        haptics.prepare()
    }
}
