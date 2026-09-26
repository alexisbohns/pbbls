package app.pbbls.android.testing

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.MainActivity
import app.pbbls.android.core.data.OnboardingPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * The base every whole-app Robolectric test extends (#857): the real
 * [MainActivity], the real `RootScreen` and `NavDisplay`, every real screen and
 * ViewModel — over [FakeServicesModule]'s fakes instead of a Supabase project.
 *
 * A subclass must carry `@HiltAndroidTest` itself: Hilt's processor reads it off
 * the concrete class to generate that class's test component. The runner and
 * the Robolectric config are inherited from here (`@RunWith` is `@Inherited`,
 * and Robolectric merges `@Config` down the class hierarchy).
 *
 * The screen is a Pixel 7 in portrait — the maintainer's device and the AVD the
 * app is smoke-tested on. Robolectric's own default (320×470 dp, mdpi) is
 * smaller than any phone this app supports, and a screen that short scrolls
 * things a real one shows. A large-screen test overrides `qualifiers` on its
 * own class.
 *
 * The activity is launched by the test, not by a rule, so a test arms its fakes
 * first — [launch] is the line after the "given". [supabase] starts
 * unresolved, exactly like the real service; [signIn] and [signOut] resolve it.
 *
 * `createEmptyComposeRule` rather than `createAndroidComposeRule<MainActivity>`
 * for the same reason: the latter launches in its own `before`, ahead of the
 * test body, and cannot take an intent — which the invite App Link tests need.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w412dp-h915dp-port-420dpi")
abstract class AppUiTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createEmptyComposeRule()

    @Inject lateinit var supabase: FakeSupabaseService

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    private var scenario: ActivityScenario<MainActivity>? = null

    /** The running activity, for `recreate()` and friends. Null until [launch]. */
    protected val activity: ActivityScenario<MainActivity> get() = checkNotNull(scenario) { "launch() first" }

    @Before
    fun injectFakes() {
        hilt.inject()
    }

    @After
    fun closeActivity() {
        scenario?.close()
    }

    /** Launches [MainActivity], optionally with the intent an App Link would deliver. */
    protected fun launch(intent: Intent? = null) {
        scenario =
            if (intent == null) {
                ActivityScenario.launch(MainActivity::class.java)
            } else {
                ActivityScenario.launch(intent.setClass(context, MainActivity::class.java))
            }
    }

    /** The intent Android delivers for an invite App Link to [token]. */
    protected fun inviteIntent(token: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pbbls.app/invite/$token"))

    /** Onboarding is first-run only; a test that is not about it marks it seen before [launch]. */
    protected fun skipOnboarding() {
        OnboardingPreferences.setHasSeenOnboarding(context, true)
    }

    /** The auth stream resolving to a signed-in session, as `SupabaseService.start()`'s collector would. */
    protected fun signIn(userId: String = "user-1") {
        compose.runOnIdle { supabase.emitResolved(testSession(userId)) }
        compose.waitForIdle()
    }

    /** The auth stream resolving to no session: a cold start while signed out, or a sign-out. */
    protected fun signOut() {
        compose.runOnIdle { supabase.emitResolved(session = null) }
        compose.waitForIdle()
    }

    protected fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = context.getString(id, *args)

    /** The node showing the string [id] — one merged node, so the text must be unique on screen. */
    protected fun onText(
        @StringRes id: Int,
        vararg args: Any,
    ): SemanticsNodeInteraction = compose.onNodeWithText(string(id, *args))
}
