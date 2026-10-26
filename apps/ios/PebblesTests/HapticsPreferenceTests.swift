import Foundation
import Testing
@testable import Pebbles

@Suite("Haptics preference")
struct HapticsPreferenceTests {
    @Test("haptics are on until turned off, and off once turned off")
    func onUntilTurnedOff() throws {
        let name = "HapticsPreferenceTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        #expect(HapticsPreference.isOn(defaults))
        defaults.set(false, forKey: HapticsPreference.storageKey)
        #expect(!HapticsPreference.isOn(defaults))
        defaults.set(true, forKey: HapticsPreference.storageKey)
        #expect(HapticsPreference.isOn(defaults))
    }
}
