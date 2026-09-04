import Testing
@testable import Pebbles

@Suite("TapHaptics", .serialized)
@MainActor
struct TapHapticsTests {

    @Test("play records the flavor it was asked for")
    func recordsFlavor() {
        TapHaptics.resetForTesting()
        TapHaptics.play(.selection)
        #expect(TapHaptics.playCount == 1)
        #expect(TapHaptics.lastPlayed == .selection)
    }

    @Test("every flavor is playable and tallied")
    func everyFlavorPlays() {
        TapHaptics.resetForTesting()
        for flavor in [TapHaptic.selection, .advance, .success, .warning] {
            TapHaptics.play(flavor)
        }
        #expect(TapHaptics.playCount == 4)
        #expect(TapHaptics.lastPlayed == .warning)
    }
}
