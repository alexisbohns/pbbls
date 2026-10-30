import SwiftUI

/// The locale the achievements page formats its dates in (#1306): the Spine's
/// `dateLocale` (the app's language with the device's region) unless a parent
/// pins one, as the references do so every Mac and CI render the same dates.
private struct AchievementsDateLocaleKey: EnvironmentKey {
    static let defaultValue: Locale? = nil
}

extension EnvironmentValues {
    var achievementsDateLocale: Locale? {
        get { self[AchievementsDateLocaleKey.self] }
        set { self[AchievementsDateLocaleKey.self] = newValue }
    }
}
