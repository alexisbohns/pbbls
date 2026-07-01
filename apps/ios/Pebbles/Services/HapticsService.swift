import AVFoundation
import CoreHaptics
import os

/// Plays a haptic whose intensity envelope is derived from the karma sound's
/// waveform, so the vibration "fits" the audio: a sharp transient on the
/// ceramic attack, then a continuous buzz whose strength tracks the sound's
/// amplitude decay. Started in lockstep with `AudioService.playKarmaEarnedSound()`.
///
/// Core Haptics runs independently of the audio session, so — like normal iOS
/// UI haptics — the vibration fires regardless of the Ring/Silent switch, while
/// the sound (played via an `.ambient` session) only diffuses in ringtone mode.
///
/// A reference type: it owns the `CHHapticEngine` and caches the (relatively
/// expensive) waveform-derived pattern.
final class HapticsService {
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "haptics")
    private let supportsHaptics = CHHapticEngine.capabilitiesForHardware().supportsHaptics
    private var engine: CHHapticEngine?
    private var karmaPattern: CHHapticPattern?

    /// Long-lived continuous player for the swap slider; its intensity is ramped
    /// via a dynamic parameter while the user drags, then stopped on release.
    private var slidePlayer: CHHapticAdvancedPatternPlayer?

    init() {
        guard supportsHaptics else { return }
        do {
            let engine = try CHHapticEngine()
            engine.isAutoShutdownEnabled = true
            // The engine stops when the app backgrounds or on audio-route
            // changes; restart it lazily so the next earn still buzzes.
            engine.resetHandler = { [weak engine] in try? engine?.start() }
            self.engine = engine
        } catch {
            logger.error("haptic engine init failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Plays the karma-earned haptic, matched to the karma sound's waveform.
    func playKarmaEarned() {
        guard supportsHaptics, let engine else { return }
        do {
            let pattern = try karmaEarnedPattern()
            try engine.start()
            let player = try engine.makePlayer(with: pattern)
            try player.start(atTime: CHHapticTimeImmediate)
        } catch {
            logger.error("haptic play failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Starts a low continuous buzz for the swap slider. Intensity begins near
    /// zero and is raised with `updateGlyphSlide(progress:)` as the thumb moves.
    func beginGlyphSlide() {
        guard supportsHaptics, let engine else { return }
        do {
            try engine.start()
            let body = CHHapticEvent(
                eventType: .hapticContinuous,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 1.0),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.4)
                ],
                relativeTime: 0,
                duration: 30 // long-lived; stopped manually on release
            )
            let pattern = try CHHapticPattern(events: [body], parameters: [])
            let player = try engine.makeAdvancedPlayer(with: pattern)
            slidePlayer = player
            try player.start(atTime: CHHapticTimeImmediate)
            updateGlyphSlide(progress: 0)
        } catch {
            logger.error("glyph slide haptic start failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Ramps the slide buzz. `progress` is 0…1. Uses the intensity *control*
    /// parameter (a multiplier on the event's base intensity).
    func updateGlyphSlide(progress: Float) {
        guard let slidePlayer else { return }
        let clamped = max(0, min(1, progress))
        let value = 0.1 + clamped * 0.9 // never fully silent; peaks at release
        let param = CHHapticDynamicParameter(
            parameterID: .hapticIntensityControl,
            value: value,
            relativeTime: 0
        )
        try? slidePlayer.sendParameters([param], atTime: CHHapticTimeImmediate)
    }

    /// Stops the slide buzz (release or cancel).
    func endGlyphSlide() {
        try? slidePlayer?.stop(atTime: CHHapticTimeImmediate)
        slidePlayer = nil
    }

    /// A crisp, high-sharpness transient for a completed swap.
    func playGlyphSwapSuccess() {
        guard supportsHaptics, let engine else { return }
        do {
            try engine.start()
            let event = CHHapticEvent(
                eventType: .hapticTransient,
                parameters: [
                    CHHapticEventParameter(parameterID: .hapticIntensity, value: 1.0),
                    CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.9)
                ],
                relativeTime: 0
            )
            let pattern = try CHHapticPattern(events: [event], parameters: [])
            let player = try engine.makePlayer(with: pattern)
            try player.start(atTime: CHHapticTimeImmediate)
        } catch {
            logger.error("glyph swap success haptic failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    // MARK: - Pattern (cached)

    private func karmaEarnedPattern() throws -> CHHapticPattern {
        if let karmaPattern { return karmaPattern }
        guard let url = AudioService.karmaEarnedSoundURL else {
            throw HapticsError.missingAudio
        }
        let pattern = try Self.pattern(fromAudioAt: url)
        karmaPattern = pattern
        return pattern
    }

    /// Builds a haptic pattern from an audio file's amplitude envelope:
    /// a transient on the attack + a continuous event modulated by an
    /// intensity curve that follows the waveform's loudness over time.
    private static func pattern(fromAudioAt url: URL) throws -> CHHapticPattern {
        let envelope = try amplitudeEnvelope(ofAudioAt: url, points: 20)
        guard let peak = envelope.max(by: { $0.value < $1.value }), peak.value > 0 else {
            throw HapticsError.silentAudio
        }
        let duration = envelope.last?.time ?? 0.25

        // Attack: a crisp, bright transient sized to the loudest moment —
        // the "clink" of the ceramic.
        let attack = CHHapticEvent(
            eventType: .hapticTransient,
            parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: peak.value),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.9)
            ],
            relativeTime: peak.time
        )

        // Body: a continuous event spanning the sound, whose intensity is
        // driven by the envelope curve below (base intensity 1.0, scaled down
        // by the curve so it tracks the decay).
        let body = CHHapticEvent(
            eventType: .hapticContinuous,
            parameters: [
                CHHapticEventParameter(parameterID: .hapticIntensity, value: 1.0),
                CHHapticEventParameter(parameterID: .hapticSharpness, value: 0.7)
            ],
            relativeTime: 0,
            duration: duration
        )

        let intensityCurve = CHHapticParameterCurve(
            parameterID: .hapticIntensityControl,
            controlPoints: envelope.map {
                CHHapticParameterCurve.ControlPoint(relativeTime: $0.time, value: $0.value)
            },
            relativeTime: 0
        )

        return try CHHapticPattern(events: [body, attack], parameterCurves: [intensityCurve])
    }

    /// Reads `url` as PCM and returns a normalized peak-amplitude envelope
    /// downsampled to roughly `points` control points over the clip.
    private static func amplitudeEnvelope(
        ofAudioAt url: URL,
        points: Int
    ) throws -> [(time: TimeInterval, value: Float)] {
        let file = try AVAudioFile(forReading: url)
        let format = file.processingFormat
        let frameCount = AVAudioFrameCount(file.length)
        guard frameCount > 0,
              let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: frameCount) else {
            return []
        }
        try file.read(into: buffer)
        guard let channels = buffer.floatChannelData else { return [] }

        let channelCount = Int(format.channelCount)
        let frames = Int(buffer.frameLength)
        let sampleRate = format.sampleRate
        let window = max(1, frames / max(1, points))

        var result: [(TimeInterval, Float)] = []
        var maxPeak: Float = 0
        var frame = 0
        while frame < frames {
            let end = min(frame + window, frames)
            var peak: Float = 0
            for channelIndex in 0..<channelCount {
                let data = channels[channelIndex]
                for sampleIndex in frame..<end {
                    peak = max(peak, abs(data[sampleIndex]))
                }
            }
            result.append((TimeInterval(frame) / sampleRate, peak))
            maxPeak = max(maxPeak, peak)
            frame += window
        }

        guard maxPeak > 0 else { return result.map { ($0.0, 0) } }
        // Normalize to 0...1 and ensure the curve starts at t=0.
        var normalized = result.map { (time: $0.0, value: min(1, $0.1 / maxPeak)) }
        if let first = normalized.first, first.time > 0 {
            normalized.insert((time: 0, value: first.value), at: 0)
        }
        return normalized
    }

    private enum HapticsError: Error {
        case missingAudio
        case silentAudio
    }
}
