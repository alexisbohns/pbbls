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
    /// Plays one tick now, outside any sequence (the deck landing on a card that plays no intro, D11).
    func tick(_ tick: IntroTick)
}

/// One tick of a sequence's pattern.
struct IntroHapticEvent: Hashable, Sendable {
    let at: TimeInterval
    let tick: IntroTick
}

/// One swell of a sequence's pattern (D15): a continuous vibration from `at` for `duration`.
struct IntroHapticSwell: Hashable, Sendable {
    let at: TimeInterval
    let duration: TimeInterval
    let swell: IntroSwell
}

/// The month intro's haptics (#1241 D5): each sequence as one Core Haptics
/// pattern of transients, and of swells (D15), so the ticks keep their exact times, which UIKit's
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
        var events = Self.events(for: beats).map { event in
            CHHapticEvent(eventType: .hapticTransient, parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: event.tick.intensity),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: event.tick.sharpness)
            ], relativeTime: event.at)
        }
        var curves: [CHHapticParameterCurve] = []
        for swell in Self.swells(for: beats) {
            events.append(CHHapticEvent(eventType: .hapticContinuous, parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: 1),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: swell.swell.sharpness)
            ], relativeTime: swell.at, duration: swell.duration))
            // The control scales every event while it holds: back to full once the swell is over.
            curves.append(CHHapticParameterCurve(parameterID: .hapticIntensityControl, controlPoints: [
                CHHapticParameterCurve.ControlPoint(relativeTime: 0, value: swell.swell.from),
                CHHapticParameterCurve.ControlPoint(relativeTime: swell.duration, value: swell.swell.to),
                CHHapticParameterCurve.ControlPoint(relativeTime: swell.duration + Self.floor, value: 1)
            ], relativeTime: swell.at))
        }
        guard !events.isEmpty else { return }
        stop(sequence, of: month)
        do {
            try engine.start()
            let pattern = try CHHapticPattern(events: events, parameterCurves: curves)
            let player = try engine.makeAdvancedPlayer(with: pattern)
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

    func tick(_ tick: IntroTick) {
        guard let engine else { return }
        let event = CHHapticEvent(eventType: .hapticTransient, parameters: [
            CHHapticEventParameter(parameterID: .hapticIntensity, value: tick.intensity),
            CHHapticEventParameter(parameterID: .hapticSharpness, value: tick.sharpness)
        ], relativeTime: 0)
        do {
            try engine.start()
            let player = try engine.makePlayer(with: CHHapticPattern(events: [event], parameters: []))
            try player.start(atTime: CHHapticTimeImmediate)
        } catch {
            logger.error("intro haptic tick failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// The swells to play (D15): each swelling beat over its duration, in time order.
    nonisolated static func swells(for beats: [IntroBeat]) -> [IntroHapticSwell] {
        beats.compactMap { beat in
            guard let swell = beat.swell, beat.duration > 0 else { return nil }
            return IntroHapticSwell(at: beat.at, duration: beat.duration, swell: swell)
        }
        .sorted { $0.at < $1.at }
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
