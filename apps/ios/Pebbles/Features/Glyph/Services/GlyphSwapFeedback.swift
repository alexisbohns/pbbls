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

    /// Thumb pressed: loop the pebble sound and start the (waveform-synced +
    /// background) slide haptics.
    func begin() {
        haptics.beginGlyphSlide()
        audio.beginGlyphSlideLoop()
    }

    /// Drag moved: ramp the haptic intensity with X. The pebble sound keeps
    /// looping untouched — only the vibration tracks position.
    func update(progress: Double) {
        haptics.updateGlyphSlide(progress: Float(progress))
    }

    /// Drag released below the threshold.
    func cancel() {
        haptics.endGlyphSlide()
        audio.endGlyphSlideLoop()
    }

    /// Drag completed — the irreversible moment: sharp haptic + bamboo clack.
    func success() {
        haptics.endGlyphSlide()
        audio.endGlyphSlideLoop()
        haptics.playGlyphSwapSuccess()
        audio.playGlyphSwapSuccessSound()
    }
}
