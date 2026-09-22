package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.AuthMode
import app.pbbls.android.features.auth.AuthContent
import app.pbbls.android.features.auth.AuthUiState
import app.pbbls.android.features.onboarding.OnboardingScreen
import app.pbbls.android.features.onboarding.OnboardingSteps
import app.pbbls.android.features.welcome.WelcomeContent
import app.pbbls.android.features.welcome.WelcomeUiState
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
@PreviewLargeFont
@PreviewFrench
@Composable
fun WelcomeScreenLight() {
    PebblesTheme {
        WelcomeContent(
            uiState = WelcomeUiState(),
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
        WelcomeContent(
            uiState = WelcomeUiState(),
            contentRevealed = true,
            onCreateAccount = {},
            onLogin = {},
            onGoogleSignIn = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun AuthScreenLogin() {
    PebblesTheme {
        AuthContent(
            uiState = AuthUiState(mode = AuthMode.LOGIN),
            onModeChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onTermsChange = {},
            onPrivacyChange = {},
            onDismissError = {},
            onSubmit = {},
            onGoogleSignIn = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun AuthScreenSignup() {
    PebblesTheme {
        AuthContent(
            uiState = AuthUiState(mode = AuthMode.SIGNUP),
            onModeChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onTermsChange = {},
            onPrivacyChange = {},
            onDismissError = {},
            onSubmit = {},
            onGoogleSignIn = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun OnboardingScreenLight() {
    PebblesTheme {
        OnboardingScreen(steps = OnboardingSteps.all, onFinish = {})
    }
}
