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
private const val ONBOARDING_SKIP = "onboarding_skip"
private const val PATH_WEEK_PAGER = "path_week_pager"
private const val PATH_WEEK_LIST = "path_week_list"
private const val PATH_PEBBLE_ROW = "path_pebble_row"
private const val NEW_PEBBLE = "new_pebble"

private const val UI_TIMEOUT_MS = 15_000L

/** Sign-in is a network round trip, then Path's own load. */
private const val SIGN_IN_TIMEOUT_MS = 30_000L

/**
 * Pebble detail opens in a bottom sheet, which is its own window and so outside
 * the root that exposes test tags; there is no node to wait on. This is long
 * enough for the detail fetch and its first render on an emulator.
 */
private const val DETAIL_SETTLE_MS = 2_000L

/** Where a cold start lands: Welcome when signed out, Path when signed in. */
private val LANDING = Pattern.compile("$WELCOME_LOG_IN|$PATH_WEEK_PAGER")

private val AFTER_SIGN_IN = Pattern.compile("$ONBOARDING_SKIP|$PATH_WEEK_PAGER")

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
        "Neither Welcome nor Path appeared — is the app built with Supabase config?"
    }
}

/**
 * Leaves the app on Path, signing in through Welcome → Auth when the session is
 * gone (the first iteration, or after `pm clear`). Onboarding, shown once per
 * install, is skipped. Returns false without an [account]: Welcome and Auth are
 * still walked, so the signed-out funnel is profiled either way.
 */
internal fun MacrobenchmarkScope.ensureSignedIn(account: TestAccount?): Boolean {
    awaitLanding()
    if (device.hasObject(By.res(PATH_WEEK_PAGER))) return true

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
            "Sign-in never reached Path — check BENCHMARK_EMAIL / BENCHMARK_PASSWORD"
        }
    if (next.resourceName == ONBOARDING_SKIP) {
        next.click()
        checkNotNull(device.wait(Until.findObject(By.res(PATH_WEEK_PAGER)), UI_TIMEOUT_MS)) {
            "Skipping onboarding did not reach Path"
        }
    }
    return true
}

/**
 * The Path loop a returning user repeats: scroll the week, swipe to the week
 * before and back, open a pebble, open the record flow. Every step is optional
 * so an account with an empty week still runs; the record flow is left on its
 * first step, where closing it discards without asking and writes no draft.
 */
internal fun MacrobenchmarkScope.browsePath() {
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
