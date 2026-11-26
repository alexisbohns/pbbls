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

    init(ceremony: StoneCeremony, carveLength: CGFloat, hapticsOn: Bool,
         haptics: StoneCeremonyHapticPlaying = StoneCeremonyHaptics.shared) {
        self.ceremony = ceremony
        self.carveLength = carveLength
        self.hapticsOn = hapticsOn
        self.haptics = haptics
    }

    /// The hero's first appearance: Act 1 plays from this instant. A frozen ceremony plays nothing.
    func start(at now: Date) {
        guard ceremony.isRunning else { return }
        let score = StoneCeremonyScore.act1(ceremony.timeline, carveLength: carveLength, hapticsOn: hapticsOn)
        guard !score.isEmpty else { return }
        haptics.prepare()
        haptics.play(score, startingAt: now)
    }

    func tick(at now: Date) {
        ceremony.tick(at: now)
    }

    /// A tap that is not Continue skips to the next rest, and silences the act it skips.
    func tapOutside(at now: Date) {
        let wasRunning = ceremony.isRunning
        ceremony.tapOutside(at: now)
        if wasRunning { haptics.stop() }
    }

    /// Continue ends Act 1's score and starts Act 2's; a tap that only skips is silent.
    func tapContinue(at now: Date) {
        let wasRunning = ceremony.isRunning
        guard ceremony.tapContinue(at: now) else {
            if wasRunning { haptics.stop() }
            return
        }
        haptics.stop()
        let score = StoneCeremonyScore.act2(ceremony.timeline, hapticsOn: hapticsOn)
        guard !score.isEmpty else { return }
        haptics.play(score, startingAt: now)
    }

    func close() {
        haptics.stop()
    }
}
