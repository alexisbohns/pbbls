package app.pbbls.android.services

import android.content.Context

/**
 * `hasSeenOnboarding` persistence — the `@AppStorage("hasSeenOnboarding")`
 * analog (D5). Backed by `SharedPreferences`; DataStore is deferred until real
 * settings exist. The onboarding overlay reads this once at the session gate and
 * writes `true` when the flow completes, so it shows once and never again across
 * restarts.
 */
object OnboardingPreferences {
    private const val PREFS_NAME = "pebbles_prefs"
    private const val KEY_HAS_SEEN_ONBOARDING = "hasSeenOnboarding"

    fun hasSeenOnboarding(context: Context): Boolean = prefs(context).getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    fun setHasSeenOnboarding(
        context: Context,
        value: Boolean,
    ) {
        prefs(context).edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, value).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
