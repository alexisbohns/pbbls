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

    func playKarmaEarnedSound() {
        guard let url = Bundle.main.url(
            forResource: Self.karmaEarnedSound.name,
            withExtension: Self.karmaEarnedSound.ext
        ) else {
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
}
