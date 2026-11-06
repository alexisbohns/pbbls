package app.pbbls.android.baselineprofile

import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The journeys the profile is recorded from and the benchmark measures (#856),
 * shared so the two cannot drift.
 *
 * Nodes are found by the `testTag`s in `:app`'s `core/common/JourneyTags.kt`,
 * exposed as resource ids by `MainActivity`. This module is a separate APK and
 * cannot import that object, so the strings are repeated here — keep them in
 * step. A renamed tag does not fail anything: the journey just stops short, and
 * the profile quietly covers less.
 */
internal const val PACKAGE_NAME = "app.pbbls.android"

private const val WELCOME_LOG_IN = "welcome_log_in"
private const val AUTH_EMAIL = "auth_email"
private const val AUTH_PASSWORD = "auth_password"
private const val AUTH_SUBMIT = "auth_submit"
private const val ONBOARDING_NEXT = "onboarding_next"
private const val ONBOARDING_SKIP = "onboarding_skip"
private const val ONBOARDING_REASON = "onboarding_reason_keep_people"
private const val HOME = "home"

/** Back presses the hand-off's moment page may need: one for the keyboard, one for the page, one spare. */
private const val HANDOFF_BACKS = 3
private const val TAB_PATH = "tab_path"
private const val PATH_WEEK_PAGER = "path_week_pager"
private const val PATH_WEEK_LIST = "path_week_list"
private const val PATH_PEBBLE_ROW = "path_pebble_row"
private const val NEW_PEBBLE = "new_pebble"

private const val UI_TIMEOUT_MS = 15_000L

/** Sign-in is a network round trip, then Home's own load. */
private const val SIGN_IN_TIMEOUT_MS = 30_000L

/**
 * Pebble detail opens in a bottom sheet, which is its own window and so outside
 * the root that exposes test tags; there is no node to wait on. This is long
 * enough for the detail fetch and its first render on an emulator.
 */
private const val DETAIL_SETTLE_MS = 2_000L

/** Where a cold start lands: Welcome when signed out, Home when signed in (#1383 D1). */
private val LANDING = Pattern.compile("$WELCOME_LOG_IN|$HOME")

private val AFTER_SIGN_IN = Pattern.compile("$ONBOARDING_NEXT|$HOME")

internal data class TestAccount(
    val email: String,
    val password: String,
)

/**
 * The throwaway account from `BENCHMARK_EMAIL` / `BENCHMARK_PASSWORD`
 * (secrets.properties or env, passed as instrumentation arguments by
 * `build.gradle.kts`), or null when either is unset.
 */
internal fun testAccount(): TestAccount? {
    val args = InstrumentationRegistry.getArguments()
    val email = args.getString("benchmarkEmail").orEmpty()
    val password = args.getString("benchmarkPassword").orEmpty()
    return if (email.isBlank() || password.isBlank()) null else TestAccount(email, password)
}

/** Waits for the first real screen after the system splash lets go. */
internal fun MacrobenchmarkScope.awaitLanding() {
    checkNotNull(device.wait(Until.findObject(By.res(LANDING)), UI_TIMEOUT_MS)) {
        "Neither Welcome nor Home appeared — is the app built with Supabase config?"
    }
}

/**
 * Leaves the app on Home, signing in through Welcome → Auth when the session is
 * gone (the first iteration, or after `pm clear`). Onboarding, shown until the
 * profile has completed it (#1510), is walked with the least input: the promise,
 * the consent (already on record for the benchmark account, so locked on), one
 * reason, then Skip or Later on every remaining step. Returns false without an [account]: Welcome and Auth are
 * still walked, so the signed-out funnel is profiled either way.
 */
internal fun MacrobenchmarkScope.ensureSignedIn(account: TestAccount?): Boolean {
    awaitLanding()
    if (device.hasObject(By.res(HOME))) return true

    device.findObject(By.res(WELCOME_LOG_IN)).click()
    val email =
        checkNotNull(device.wait(Until.findObject(By.res(AUTH_EMAIL)), UI_TIMEOUT_MS)) {
            "Log in did not open Auth"
        }
    if (account == null) {
        device.pressBack()
        return false
    }
    email.text = account.email
    device.findObject(By.res(AUTH_PASSWORD)).text = account.password
    device.findObject(By.res(AUTH_SUBMIT)).click()

    val next =
        checkNotNull(device.wait(Until.findObject(By.res(AFTER_SIGN_IN)), SIGN_IN_TIMEOUT_MS)) {
            "Sign-in never reached Home — check BENCHMARK_EMAIL / BENCHMARK_PASSWORD"
        }
    if (next.resourceName == ONBOARDING_NEXT) {
        next.click() // 1. promise
        awaitAndClick(ONBOARDING_NEXT, "consent") // 2. consent, every act locked on
        awaitAndClick(ONBOARDING_REASON, "reasons")
        awaitAndClick(ONBOARDING_NEXT, "reasons") // 3. reasons
        repeat(3) { awaitAndClick(ONBOARDING_SKIP, "a skippable step") } // 4–6; the last completes
        checkNotNull(device.wait(Until.findObject(By.res(HOME)), SIGN_IN_TIMEOUT_MS)) {
            "Finishing onboarding did not reach Home"
        }
        // Home's resume then opens the moment page over it with the hand-off's prompt (record-flow D13, it
        // was a sheet before). Its name field takes focus, so with a soft keyboard the first back only hides
        // the IME: press back a bounded few times until Home's tag is the screen again. Closing it empty asks nothing.
        // First wait for the page to cover Home (it may never, if the note is absent: then this just times out).
        device.wait(Until.gone(By.res(HOME)), UI_TIMEOUT_MS)
        device.waitForIdle()
        repeat(HANDOFF_BACKS) {
            if (!device.hasObject(By.res(HOME))) {
                device.pressBack()
                device.waitForIdle()
            }
        }
        checkNotNull(device.wait(Until.findObject(By.res(HOME)), UI_TIMEOUT_MS)) {
            "Closing the onboarding's moment page did not reach Home"
        }
    }
    return true
}

private fun MacrobenchmarkScope.awaitAndClick(
    tag: String,
    step: String,
) {
    checkNotNull(device.wait(Until.findObject(By.res(tag)), UI_TIMEOUT_MS)) {
        "Onboarding never showed $tag on $step"
    }.click()
}

/**
 * The Path loop a returning user repeats, from the Path tab: scroll the week, swipe to the week
 * before and back, open a pebble, open Capture. Every step is optional so an
 * account with an empty week still runs; Capture is left on its photo step,
 * where back with nothing entered closes it without asking and writes nothing.
 */
internal fun MacrobenchmarkScope.browsePath() {
    // The app opens on Home (#1383 D1); Path is the second tab.
    if (clickFresh(TAB_PATH)) device.wait(Until.hasObject(By.res(PATH_WEEK_PAGER)), UI_TIMEOUT_MS)

    device.wait(Until.findObject(By.res(PATH_WEEK_LIST)), UI_TIMEOUT_MS)?.let { list ->
        // Keep the fling clear of the system gesture areas at the edges.
        list.setGestureMargin(device.displayHeight / 10)
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }

    device.findObject(By.res(PATH_WEEK_PAGER))?.let { pager ->
        pager.setGestureMargin(device.displayWidth / 10)
        pager.swipe(Direction.RIGHT, 0.8f)
        device.waitForIdle()
        pager.swipe(Direction.LEFT, 0.8f)
        device.waitForIdle()
    }

    if (clickFresh(PATH_PEBBLE_ROW)) {
        Thread.sleep(DETAIL_SETTLE_MS)
        device.pressBack()
        device.wait(Until.hasObject(By.res(PATH_WEEK_PAGER)), UI_TIMEOUT_MS)
    }

    if (clickFresh(NEW_PEBBLE)) {
        device.wait(Until.gone(By.res(NEW_PEBBLE)), UI_TIMEOUT_MS)
        device.waitForIdle()
        device.pressBack()
        device.wait(Until.hasObject(By.res(NEW_PEBBLE)), UI_TIMEOUT_MS)
    }
}

/**
 * Finds [res] and taps it, re-finding once if the node was recomposed between
 * the two (the pager settling after a swipe does that to every row). Returns
 * false when there is nothing to tap, e.g. an empty week.
 */
private fun MacrobenchmarkScope.clickFresh(res: String): Boolean {
    repeat(2) { attempt ->
        device.waitForIdle()
        val node = device.wait(Until.findObject(By.res(res)), UI_TIMEOUT_MS) ?: return false
        try {
            node.click()
            return true
        } catch (e: StaleObjectException) {
            Log.w(TAG, "$res went stale before the tap (attempt ${attempt + 1})", e)
        }
    }
    return false
}

private const val TAG = "PebblesJourney"
