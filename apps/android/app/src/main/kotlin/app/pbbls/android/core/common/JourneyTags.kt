package app.pbbls.android.core.common

/**
 * Test tags the baseline-profile journey finds the UI by (#856).
 *
 * `MainActivity` sets `testTagsAsResourceId`, which exposes each tag to
 * UiAutomator as a resource id, so the journey in `:baselineprofile` reaches
 * these nodes by id rather than by (localized) text. That module is a separate
 * APK and cannot import this object: it repeats the strings, and renaming one
 * here silently shortens the profiled journey. Grep `:baselineprofile` first.
 */
object JourneyTags {
    const val WELCOME_LOG_IN = "welcome_log_in"
    const val AUTH_EMAIL = "auth_email"
    const val AUTH_PASSWORD = "auth_password"
    const val AUTH_SUBMIT = "auth_submit"
    const val ONBOARDING_SKIP = "onboarding_skip"
    const val PATH_WEEK_PAGER = "path_week_pager"
    const val PATH_WEEK_LIST = "path_week_list"
    const val PATH_PEBBLE_ROW = "path_pebble_row"
    const val NEW_PEBBLE = "new_pebble"
}
