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

    /** The onboarding shell's Back, and the Skip/Later of a step that has one (#1510). */
    const val ONBOARDING_BACK = "onboarding_back"
    const val ONBOARDING_SKIP = "onboarding_skip"

    /** A step's primary button: the promise's CTA, Continue on consent and reasons. */
    const val ONBOARDING_NEXT = "onboarding_next"

    /** The Home page, where a signed-in launch lands (#1383 D1). */
    const val HOME = "home"

    /** The Path item of the navigation bar or rail: Path is no longer where the app opens. */
    const val TAB_PATH = "tab_path"
    const val PATH_WEEK_PAGER = "path_week_pager"
    const val PATH_WEEK_LIST = "path_week_list"
    const val PATH_PEBBLE_ROW = "path_pebble_row"
    const val PATH_PEBBLE_STONE = "path_pebble_stone"
    const val NEW_PEBBLE = "new_pebble"
}
