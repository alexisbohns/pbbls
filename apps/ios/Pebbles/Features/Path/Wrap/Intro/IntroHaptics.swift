import CoreHaptics
import Foundation
import os

/// What the intro's clock asks of the haptics (#1241 D5); the tests stand in a
/// spy. A page's sequence is one voice, so a page stops only its own.
@MainActor
protocol IntroHapticPlaying: AnyObject {
    /// Starts the engine early, so a sequence's first tick is not late.
    func prepare()
    /// Plays `beats`' ticks as one pattern, starting at `begin`, the instant the sequence's frames start.
    func play(_ sequence: IntroSequence, of month: CalendarMonth, beats: [IntroBeat], startingAt begin: Date)
    func stop(_ sequence: IntroSequence, of month: CalendarMonth)
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

    /// A month page's sequence: one player each.
    private struct Voice: Hashable {
        let month: CalendarMonth
        let sequence: IntroSequence
    }

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "intro")
    private var engine: CHHapticEngine?
    private var players: [Voice: CHHapticAdvancedPatternPlayer] = [:]

    init() {
        guard CHHapticEngine.capabilitiesForHardware().supportsHaptics else { return }
        do {
            let engine = try CHHapticEngine()
            engine.isAutoShutdownEnabled = true
            // The engine stops on backgrounding and route changes; restart it lazily, as `HapticsService` does.
            let logger = logger
            engine.resetHandler = { @Sendable [weak engine] in
                do {
                    try engine?.start()
                } catch {
                    logger.error("intro haptic engine restart failed: \(error.localizedDescription, privacy: .public)")
                }
            }
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

    func play(_ sequence: IntroSequence, of month: CalendarMonth, beats: [IntroBeat], startingAt begin: Date) {
        guard let engine else { return }
        let events = Self.events(for: beats).map { event in
            CHHapticEvent(eventType: .hapticTransient, parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: event.tick.intensity),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: event.tick.sharpness)
            ], relativeTime: event.at)
        }
        guard !events.isEmpty else { return }
        stop(sequence, of: month)
        do {
            try engine.start()
            let player = try engine.makeAdvancedPlayer(with: CHHapticPattern(events: events, parameters: []))
            players[Voice(month: month, sequence: sequence)] = player
            // Timed after the engine's start and the player's making, so a cold start can't put the ticks behind
            // the frames.
            try player.start(atTime: engine.currentTime + max(0, begin.timeIntervalSinceNow))
        } catch {
            logger.error("intro haptic play failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    func stop(_ sequence: IntroSequence, of month: CalendarMonth) {
        guard let player = players.removeValue(forKey: Voice(month: month, sequence: sequence)) else { return }
        do {
            try player.stop(atTime: CHHapticTimeImmediate)
        } catch {
            logger.warning("intro haptic stop failed: \(error.localizedDescription, privacy: .public)")
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
