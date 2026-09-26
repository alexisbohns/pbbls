package app.pbbls.android.testing

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.MainActivity
import app.pbbls.android.core.data.OnboardingPreferences
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration
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
 * The empty rule finds whichever Compose root is attached, so it follows the
 * activity across [restartAfterProcessDeath].
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

    private var controller: ActivityController<MainActivity>? = null

    @Before
    fun injectFakes() {
        hilt.inject()
    }

    @After
    fun closeActivity() {
        controller
            ?.takeUnless { it.get().isDestroyed }
            ?.pause()
            ?.stop()
            ?.destroy()
    }

    /**
     * Launches [MainActivity], optionally with the intent an App Link would
     * deliver, or restoring [savedState] the way the system does after process
     * death.
     *
     * An `ActivityController` rather than `ActivityScenario`, because only the
     * controller hands back the saved-state bundle — which is what
     * [restartAfterProcessDeath] needs, and what `ActivityScenario.recreate()`
     * never exercises: that is a configuration change, so every ViewModel
     * survives it and a `SavedStateHandle` is never read back.
     */
    protected fun launch(
        intent: Intent? = null,
        savedState: Bundle? = null,
    ) {
        val start = (intent ?: Intent(Intent.ACTION_MAIN)).setClass(context, MainActivity::class.java)
        val built = Robolectric.buildActivity(MainActivity::class.java, start)
        // setup(null) is not setup(): it still calls onRestoreInstanceState, which NPEs.
        controller = if (savedState == null) built.setup() else built.setup(savedState)
        compose.waitForIdle()
    }

    /**
     * Kills the activity the way the system does to a backgrounded process, and
     * brings it back.
     *
     * The saved state is written on the way down, round-tripped through a
     * [Parcel] — so a key that cannot be parcelled fails here, as it would on a
     * device — and handed to a brand-new activity. The old activity is
     * destroyed as a *finishing* one, not a changing one, so its
     * `ViewModelStore` is cleared: every ViewModel, `RootViewModel` included, is
     * rebuilt from its `SavedStateHandle` alone.
     *
     * The Hilt singletons are the one thing that survives, which a real process
     * death would not. Here they stand in for the server and the disk — the
     * fakes hold what Supabase and `ComposerSnapshotStore` would still hold — so
     * that is the faithful half. The auth stream is reset to unresolved, as it
     * is in a fresh process; the test resolves it again with [signIn].
     */
    protected fun restartAfterProcessDeath() {
        val dying = checkNotNull(controller) { "launch() first" }
        val outState = Bundle()
        dying
            .pause()
            .saveInstanceState(outState)
            .stop()
            .destroy()
        val restored = outState.parcelRoundTrip()
        compose.runOnIdle { supabase.isInitializing = true }
        launch(intent = dying.intent, savedState = restored)
    }

    private fun Bundle.parcelRoundTrip(): Bundle {
        val parcel = Parcel.obtain()
        try {
            writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return checkNotNull(parcel.readBundle(MainActivity::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    /** The intent Android delivers for an invite App Link to [token]. */
    protected fun inviteIntent(token: String) = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pbbls.app/invite/$token"))

    /** Onboarding is first-run only; a test that is not about it marks it seen before [launch]. */
    protected fun skipOnboarding() {
        OnboardingPreferences.setHasSeenOnboarding(context, true)
    }

    /**
     * The user leaving the app and coming back to it: the activity stops and
     * starts again without being destroyed, so every screen sees a fresh
     * `ON_RESUME` — which is what drives the resume refreshes.
     */
    protected fun backgroundAndReturn() {
        checkNotNull(controller) { "launch() first" }
            .pause()
            .stop()
            .start()
            .resume()
        compose.waitForIdle()
    }

    /**
     * The system back gesture, delivered the way the platform delivers it:
     * through the activity's dispatcher, so whichever handler is registered
     * highest wins — `NavDisplay`'s, or a screen's own.
     */
    protected fun pressBack() {
        val activity = checkNotNull(controller) { "launch() first" }.get()
        compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    /**
     * Lets [millis] of main-thread time pass. A `delay` inside `viewModelScope`
     * (an autosave debounce) is a delayed message on the main looper, which
     * Robolectric's paused looper only delivers when its clock moves — and
     * Compose's idling waits on the frame clock, not on that one.
     */
    protected fun idleMainLooperFor(millis: Long) {
        compose.runOnUiThread { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)) }
        compose.waitForIdle()
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
