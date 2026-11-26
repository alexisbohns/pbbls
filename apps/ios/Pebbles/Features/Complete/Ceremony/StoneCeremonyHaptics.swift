import CoreHaptics
import Foundation
import os

/// What the ceremony's driver asks of the haptics; the tests stand in a spy.
@MainActor
protocol StoneCeremonyHapticPlaying: AnyObject {
    /// Starts the engine early, so the first event is not late.
    func prepare()
    /// Plays `events` as one pattern, starting at `begin`, the instant the frames start.
    func play(_ events: [IntroHapticEvent], startingAt begin: Date)
    func stop()
}

/// The stone ceremony's haptics (#1691): an act as one Core Haptics pattern of transients, so the
/// events keep their exact times. Its own engine, like `IntroHaptics`: no hardware, or an engine
/// that fails, plays nothing; the visuals still play. One act sounds at a time.
@MainActor
final class StoneCeremonyHaptics: StoneCeremonyHapticPlaying {
    static let shared = StoneCeremonyHaptics()

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "ceremony")
    private var engine: CHHapticEngine?
    private var player: CHHapticAdvancedPatternPlayer?

    init() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        do {
            let engine = try CHHapticEngine()
            engine.isAutoShutdownEnabled = true
            // The engine stops on backgrounding and route changes; restart it lazily, as `IntroHaptics` does.
            let logger = logger
            engine.resetHandler = { @Sendable [weak engine] in
                do {
                    try engine?.start()
                } catch {
                    logger.error("ceremony haptic restart failed: \(error.localizedDescription, privacy: .public)")
                }
            }
            self.engine = engine
        } catch {
            logger.error("ceremony haptic engine init failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func prepare() {
        guard let engine, HapticsPreference.isOn() else { return }
        do {
            try engine.start()
        } catch {
            logger.error("ceremony haptic engine start failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func play(_ events: [IntroHapticEvent], startingAt begin: Date) {
        guard let engine, HapticsPreference.isOn(), !events.isEmpty else { return }
        stop()
        do {
            try engine.start()
            let player = try engine.makeAdvancedPlayer(with: Self.pattern(events))
            self.player = player
            // Timed after the engine's start and the player's making, so a cold start can't put the
            // events behind the frames.
            try player.start(atTime: engine.currentTime + max(0, begin.timeIntervalSinceNow))
        } catch {
            logger.error("ceremony haptic play failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func stop() {
        guard let player else { return }
        self.player = nil
        do {
            try player.stop(atTime: CHHapticTimeImmediate)
        } catch {
            logger.warning("ceremony haptic stop failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    nonisolated static func pattern(_ events: [IntroHapticEvent]) throws -> CHHapticPattern {
        let hapticEvents = events.map { event in
            CHHapticEvent(eventType: .hapticTransient, parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: event.tick.intensity),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: event.tick.sharpness)
            ], relativeTime: event.at)
        }
        return try CHHapticPattern(events: hapticEvents, parameters: [])
    }
}
