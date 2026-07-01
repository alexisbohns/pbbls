import AudioToolbox
import os

/// Plays short UI sound effects. Uses `AudioServicesPlaySystemSound`, which
/// respects the ring/silent switch automatically. Structured so swapping to a
/// bundled `.caf` + `AVAudioPlayer` (category `.ambient`) later is a
/// same-signature, one-file change.
struct AudioService {
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "audio")

    /// System sound played on a karma earn. ID is a placeholder — AUDITION ON
    /// DEVICE and pick by ear before shipping (candidates: 1103/1013/1025/1057).
    private let karmaEarnedSoundID: SystemSoundID = 1103

    func playKarmaEarnedSound() {
        AudioServicesPlaySystemSound(karmaEarnedSoundID)
    }
}
