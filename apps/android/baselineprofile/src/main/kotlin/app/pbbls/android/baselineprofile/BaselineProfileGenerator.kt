package app.pbbls.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the rules `:app` ships in `src/release/generated/baselineProfiles/`
 * (#856). Run through `./gradlew :app:generateBaselineProfile` with a device
 * attached, never directly — the Gradle task is what copies the output into
 * `:app`'s sources.
 *
 * Two tests on purpose: only [startup] is flagged `includeInStartupProfile`,
 * so the startup profile (which R8 uses to lay out the primary dex) holds just
 * what a cold start touches, and the Path loop stays in the baseline profile.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start to the first screen, signed in: the launch most people have. */
    @Test
    fun startup() =
        rule.collect(packageName = PACKAGE_NAME, includeInStartupProfile = true) {
            pressHome()
            startActivityAndWait()
            ensureSignedIn(testAccount())
        }

    /** What happens after the first frame: scrolling Path, detail, the record flow. */
    @Test
    fun path() =
        rule.collect(packageName = PACKAGE_NAME) {
            pressHome()
            startActivityAndWait()
            if (ensureSignedIn(testAccount())) browsePath()
        }
}
