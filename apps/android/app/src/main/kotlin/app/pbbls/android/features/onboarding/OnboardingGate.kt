package app.pbbls.android.features.onboarding

/**
 * Pure gating logic for the onboarding overlay, extracted from `RootScreen` so
 * it can be unit-tested (the `RootView.onChange(of: session?.user.id)` analog).
 * Onboarding presents the first time a user id appears while the
 * `hasSeenOnboarding` flag (SharedPreferences, D5) is still false.
 */
object OnboardingGate {
    fun shouldPresent(
        userId: String?,
        hasSeenOnboarding: Boolean,
    ): Boolean = userId != null && !hasSeenOnboarding
}
