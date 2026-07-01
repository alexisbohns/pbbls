import Foundation

/// Bundles the haptic + audio feedback for the swap slider so the view layer
/// has one small surface to call. Owns its own `HapticsService`/`AudioService`
/// instances (they are cheap and the drawer is transient). Not `@Observable` —
/// it has no view-observed state.
@MainActor
final class GlyphSwapFeedback {
    private let haptics: HapticsService
    private let audio: AudioService

    /// Which pebble-drop tick step last fired, so we retrigger the sound only a
    /// few times across the track instead of on every drag delta.
    private var lastTickStep = -1
    private let tickSteps = 5

    init(haptics: HapticsService = HapticsService(), audio: AudioService = AudioService()) {
        self.haptics = haptics
        self.audio = audio
    }

    /// Drag began: start the rising buzz.
    func begin() {
        lastTickStep = -1
        haptics.beginGlyphSlide()
    }

    /// Drag moved: ramp the buzz and tick the pebble-drop sound at thresholds.
    func update(progress: Double) {
        haptics.updateGlyphSlide(progress: Float(progress))
        let step = min(tickSteps - 1, Int(progress * Double(tickSteps)))
        if step != lastTickStep {
            lastTickStep = step
            audio.playGlyphSlideTick()
        }
    }

    /// Drag released below the threshold.
    func cancel() {
        haptics.endGlyphSlide()
    }

    /// Drag completed: sharp haptic + bamboo sound.
    func success() {
        haptics.endGlyphSlide()
        haptics.playGlyphSwapSuccess()
        audio.playGlyphSwapSuccessSound()
    }
}
