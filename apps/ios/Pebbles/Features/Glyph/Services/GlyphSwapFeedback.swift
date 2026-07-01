import Foundation

/// Bundles the haptic + audio feedback for the swap slider so the view layer
/// has one small surface to call. Owns its own `HapticsService`/`AudioService`
/// instances (they are cheap and the drawer is transient). Not `@Observable` —
/// it has no view-observed state.
@MainActor
final class GlyphSwapFeedback {
    private let haptics: HapticsService
    private let audio: AudioService

    init(haptics: HapticsService = HapticsService(), audio: AudioService = AudioService()) {
        self.haptics = haptics
        self.audio = audio
    }

    /// Drag began: start the rising buzz and arm the scrubbable pebble sound.
    func begin() {
        haptics.beginGlyphSlide()
        audio.beginGlyphSlideScrub()
    }

    /// Drag moved: ramp the buzz and scrub the pebble sound to match progress.
    func update(progress: Double) {
        haptics.updateGlyphSlide(progress: Float(progress))
        audio.scrubGlyphSlide(progress: progress)
    }

    /// Drag released below the threshold.
    func cancel() {
        haptics.endGlyphSlide()
        audio.endGlyphSlideScrub()
    }

    /// Drag completed — the irreversible moment: sharp haptic + bamboo clack.
    func success() {
        haptics.endGlyphSlide()
        audio.endGlyphSlideScrub()
        haptics.playGlyphSwapSuccess()
        audio.playGlyphSwapSuccessSound()
    }
}
