import CoreHaptics
import Foundation
import os

/// What the intro's clock asks of the haptics (#1241 D5); the tests stand in a spy.
@MainActor
protocol IntroHapticPlaying: AnyObject {
    /// Starts the engine early, so a sequence's first tick is not late.
    func prepare()
    /// Plays `beats`' ticks as one pattern, starting `lead` seconds from now.
    func play(_ sequence: IntroSequence, beats: [IntroBeat], after lead: TimeInterval)
    func stop(_ sequence: IntroSequence)
    func stopAll()
}

/// One tick of a sequence's pattern.
struct IntroHapticEvent: Hashable, Sendable {
    let at: TimeInterval
    let tick: IntroTick
}

/// The month intro's haptics (#1241 D5): each sequence as one Core Haptics
/// pattern of transients, so the ticks keep their exact times, which UIKit's
/// generators would merge in the garland's fast middle. Its own engine: not
/// `HapticsService` (karma, the glyph slider) nor `TapHaptics` (UI taps). No
/// hardware, or an engine that fails, plays nothing; the visuals still play.
@MainActor
final class IntroHaptics: IntroHapticPlaying {
    static let shared = IntroHaptics()

    /// Two ticks closer than this blur into one buzz: the later one is dropped (its visual beat stays).
    nonisolated static let floor: TimeInterval = 0.025

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "intro")
    private var engine: CHHapticEngine?
    private var players: [IntroSequence: CHHapticAdvancedPatternPlayer] = [:]

    init() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        do {
            let engine = try CHHapticEngine()
            engine.isAutoShutdownEnabled = true
            // The engine stops on backgrounding and route changes; restart it lazily, as `HapticsService` does.
            engine.resetHandler = { [weak engine] in try? engine?.start() }
            self.engine = engine
        } catch {
            logger.error("intro haptic engine init failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func prepare() {
        guard let engine else { return }
        do {
            try engine.start()
        } catch {
            logger.error("intro haptic engine start failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func play(_ sequence: IntroSequence, beats: [IntroBeat], after lead: TimeInterval) {
        guard let engine else { return }
        let events = Self.events(for: beats).map { event in
            CHHapticEvent(eventType: .hapticTransient, parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: event.tick.intensity),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: event.tick.sharpness)
            ], relativeTime: event.at)
        }
        guard !events.isEmpty else { return }
        stop(sequence)
        do {
            try engine.start()
            let player = try engine.makeAdvancedPlayer(with: CHHapticPattern(events: events, parameters: []))
            players[sequence] = player
            try player.start(atTime: engine.currentTime + lead)
        } catch {
            logger.error("intro haptic play failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func stop(_ sequence: IntroSequence) {
        guard let player = players.removeValue(forKey: sequence) else { return }
        do {
            try player.stop(atTime: CHHapticTimeImmediate)
        } catch {
            logger.warning("intro haptic stop failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func stopAll() {
        for sequence in Array(players.keys) {
            stop(sequence)
        }
    }

    /// The ticks to play: each ticking beat at its time, in time order,
    /// dropping one closer than `floor` to the last tick kept.
    nonisolated static func events(for beats: [IntroBeat]) -> [IntroHapticEvent] {
        var kept: [IntroHapticEvent] = []
        for beat in beats.sorted(by: { $0.at < $1.at }) {
            guard let tick = beat.tick else { continue }
            if let last = kept.last, beat.at - last.at < floor { continue }
            kept.append(IntroHapticEvent(at: beat.at, tick: tick))
        }
        return kept
    }
}
