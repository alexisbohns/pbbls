import Foundation

/// Spec §2: the People tab's four states, decided by the server's totals and
/// the device's "Not now" (D11). A pure projection of `PeoplePrompts`.
enum PeopleOnboardingState: Equatable {
    /// Nothing recorded yet: point at the Path.
    case noPebbles
    /// Pebbles but nobody on them: explain souls and ask about up to three.
    case noSouls(prompts: [PeoplePrompts.Prompt], skipped: [String])
    /// One or two souls: keep asking, two pebbles at a time.
    case fewSouls(prompts: [PeoplePrompts.Prompt])
    /// Three souls, or the user said "Not now": the grid stands alone.
    case done

    /// The `UserDefaults` key of the device's "Not now".
    static let storageKey = "people.prompts.dismissed"

    static func make(_ prompts: PeoplePrompts, dismissed: Bool) -> PeopleOnboardingState {
        if dismissed || prompts.souls >= 3 { return .done }
        if prompts.pebbles == 0 { return .noPebbles }
        if prompts.souls == 0 {
            return .noSouls(prompts: Array(prompts.prompts.prefix(3)), skipped: Array(prompts.skipped.prefix(2)))
        }
        return .fewSouls(prompts: Array(prompts.prompts.prefix(2)))
    }
}
