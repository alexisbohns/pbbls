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
            logger.error("sound \(name, privacy: .public) playback failed: \(error.localizedDescription, privacy: .public)")
        }
    }

    /// A single pebble-drop tick, retriggered as the swap slider advances.
    func playGlyphSlideTick() { playBundled("pbbls-sfx-pebbles_drop") }

    /// The bamboo "clack" on a completed swap.
    func playGlyphSwapSuccessSound() { playBundled("pbbls-sfx-bamboo") }
}
