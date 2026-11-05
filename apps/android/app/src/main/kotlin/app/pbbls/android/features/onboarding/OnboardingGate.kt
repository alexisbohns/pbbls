package app.pbbls.android.features.onboarding

/**
 * Pure gating for the onboarding entry (#1510 D10). Present once a user id is known, this device
 * has not completed the flow (`hasSeenOnboarding`, kept so a device that just finished never
 * re-pushes while the profile re-reads), and the profile says `onboarding_completed = false`.
 * While the profile is unknown (null) nothing is presented: Home shows and the push follows the
 * read, as on web, where the auth callback reads the flag before routing.
 */
object OnboardingGate {
    fun shouldPresent(
        userId: String?,
        hasSeenOnboarding: Boolean,
        onboardingCompleted: Boolean?,
    ): Boolean = userId != null && !hasSeenOnboarding && onboardingCompleted == false
}
