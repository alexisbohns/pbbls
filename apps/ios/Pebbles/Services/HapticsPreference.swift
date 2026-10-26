import Foundation

/// Pebbles' own haptics switch (#1241), kept on this device. Core Haptics,
/// which the month's intro, karma and the glyph slider play through, ignores
/// Settings → Sounds & Haptics → System Haptics, so every haptic the app plays
/// asks this first; the UIKit taps follow both. On until turned off.
enum HapticsPreference {
    static let storageKey = "haptics.enabled"

    static func isOn(_ defaults: UserDefaults = .standard) -> Bool {
        defaults.object(forKey: storageKey) as? Bool ?? true
    }
}
