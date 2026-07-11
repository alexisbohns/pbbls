package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.features.auth.AuthMode
import app.pbbls.android.features.auth.AuthScreen
import app.pbbls.android.features.onboarding.OnboardingScreen
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.welcome.WelcomeScreen
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot-test previews for the entry funnel (sub-project C), rendered to PNGs
 * in CI so the maintainer can review Welcome/Auth/Onboarding without a device
 * (see `apps/android/CLAUDE.md`). The screens take plain action lambdas, so no
 * live `SupabaseService` is needed — previews pass no-ops. `RiveLogo` and the
 * timed reveal both collapse to their fully-revealed placeholder state under
 * `LocalInspectionMode`.
 */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun WelcomeScreenLight() {
    PebblesTheme {
        WelcomeScreen(
            contentRevealed = true,
            onCreateAccount = {},
            onLogin = {},
            onGoogleSignIn = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun WelcomeScreenDark() {
    PebblesTheme {
        WelcomeScreen(
            contentRevealed = true,
            onCreateAccount = {},
            onLogin = {},
            onGoogleSignIn = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun AuthScreenLogin() {
    PebblesTheme {
        AuthScreen(
            initialMode = AuthMode.LOGIN,
            onSubmit = { _, _, _ -> },
            onGoogleSignIn = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AuthScreenSignup() {
    PebblesTheme {
        AuthScreen(
            initialMode = AuthMode.SIGNUP,
            onSubmit = { _, _, _ -> },
            onGoogleSignIn = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun OnboardingScreenLight() {
    PebblesTheme {
        OnboardingScreen(steps = OnboardingSteps.all, onFinish = {})
    }
}
