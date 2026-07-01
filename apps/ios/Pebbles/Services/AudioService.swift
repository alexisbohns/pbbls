import AVFoundation
import os

/// Plays short UI sound effects from a bundled asset via `AVAudioPlayer`.
///
/// The player runs over an `.ambient` audio session so playback:
///   - is **silenced by the Ring/Silent switch** (only diffuses in ringtone
///     mode), matching iOS' expectation for incidental UI sounds, and
///   - **mixes with other audio** (`.mixWithOthers`) so it never interrupts,
///     pauses, or ducks the user's music/podcast (e.g. Spotify keeps playing).
///
/// A reference type because it must retain the `AVAudioPlayer` for the lifetime
/// of playback; a released player stops mid-sound.
final class AudioService {
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "audio")
    private var player: AVAudioPlayer?

    /// Bundled karma-earned sound (see `apps/ios/Pebbles/Resources/`).
    private static let karmaEarnedSound = (name: "pbbls-sfx-ceramic", ext: "m4a")

    /// URL of the bundled karma sound. Shared with `HapticsService` so the
    /// vibration envelope is derived from the exact same waveform.
    static var karmaEarnedSoundURL: URL? {
        Bundle.main.url(forResource: karmaEarnedSound.name, withExtension: karmaEarnedSound.ext)
    }

    func playKarmaEarnedSound() {
        guard let url = Self.karmaEarnedSoundURL else {
            logger.error("karma sound asset missing from bundle")
            return
        }

        do {
            // `.ambient` obeys the Ring/Silent switch; `.mixWithOthers` keeps
            // other apps' audio playing untouched. We activate but never
            // deactivate: leaving an ambient/mixing session active is inert for
            // other audio, and deactivating could notify/resume other sessions.
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.ambient, options: [.mixWithOthers])
            try session.setActive(true)

            let player = try AVAudioPlayer(contentsOf: url)
            self.player = player
            player.play()
        } catch {
            logger.error("karma sound playback failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Plays a short bundled sound over the shared `.ambient` / `.mixWithOthers`
    /// session (same policy as the karma sound: obeys the Ring/Silent switch,
    /// never ducks other apps' audio). Retains the player for its lifetime.
    private func playBundled(_ name: String, ext: String = "m4a") {
        guard let url = Bundle.main.url(forResource: name, withExtension: ext) else {
            logger.error("sound asset \(name, privacy: .public) missing from bundle")
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.ambient, options: [.mixWithOthers])
            try session.setActive(true)
            let player = try AVAudioPlayer(contentsOf: url)
            self.player = player
            player.play()
        } catch {
            logger.error(
                "sound \(name, privacy: .public) playback failed: \(error.localizedDescription, privacy: .public)"
            )
        }
    }

    /// Dedicated player for the swap-slide scrub, kept separate from `player` so a
    /// success/other sound can't clobber it mid-drag.
    private var slidePlayer: AVAudioPlayer?

    /// Prepares the pebble-drop sound as a scrubbable track for the swap slider.
    /// Playback position is driven by `scrubGlyphSlide(progress:)` so the audio
    /// tracks the thumb instead of restarting on every step.
    func beginGlyphSlideScrub() {
        guard let url = Bundle.main.url(forResource: "pbbls-sfx-pebbles_drop", withExtension: "m4a") else {
            logger.error("slide scrub sound missing from bundle")
            return
        }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.ambient, options: [.mixWithOthers])
            try session.setActive(true)
            let scrubber = try AVAudioPlayer(contentsOf: url)
            scrubber.prepareToPlay()
            scrubber.currentTime = 0
            slidePlayer = scrubber
        } catch {
            logger.error("slide scrub prepare failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// Maps slide progress (0…1) onto the sound's timeline — the slider behaves like
    /// the sound's own progress bar. Starts playback lazily on first advance.
    func scrubGlyphSlide(progress: Double) {
        guard let slidePlayer else { return }
        let clamped = max(0, min(1, progress))
        slidePlayer.currentTime = clamped * slidePlayer.duration
        if !slidePlayer.isPlaying { slidePlayer.play() }
    }

    /// Stops the scrub track on release/cancel.
    func endGlyphSlideScrub() {
        slidePlayer?.stop()
        slidePlayer = nil
    }

    /// The bamboo "clack" on a completed swap.
    func playGlyphSwapSuccessSound() { playBundled("pbbls-sfx-bamboo") }
}
