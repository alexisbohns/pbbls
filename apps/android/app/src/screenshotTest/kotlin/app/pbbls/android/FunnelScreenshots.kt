package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.AuthMode
import app.pbbls.android.features.auth.AuthContent
import app.pbbls.android.features.auth.AuthUiState
import app.pbbls.android.features.onboarding.OnboardingContent
import app.pbbls.android.features.onboarding.OnboardingUiState
import app.pbbls.android.features.welcome.WelcomeContent
import app.pbbls.android.features.welcome.WelcomeUiState
import com.android.tools.screenshot.PreviewTest

/**
 * Screenshot-test previews for the entry funnel (sub-project C), rendered to PNGs
 * in CI so the maintainer can review Welcome/Auth/Onboarding without a device
 * (see `apps/android/CLAUDE.md`). The screens take plain action lambdas, so no
 * live `SupabaseService` is needed — previews pass no-ops. The timed reveal
 * collapses to its fully-revealed state under `LocalInspectionMode`.
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
            onHealthChange = {},
            onAgeChange = {},
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
            onHealthChange = {},
            onAgeChange = {},
            onDismissError = {},
            onSubmit = {},
            onGoogleSignIn = {},
        )
    }
}

/**
 * The onboarding (#1510), one preview per step over fixed states, in light, dark, French and at the
 * largest font scale. `OnboardingPromise` (light, default size, English) fills the view's slot in
 * `arkaik-shots.json`.
 */
@Composable
private fun OnboardingStepPreview(state: OnboardingUiState) {
    PebblesTheme {
        OnboardingContent(
            state = state,
            onBack = {},
            onSkip = {},
            onNext = {},
            onConsentToggle = { _, _ -> },
            onReasonToggle = {},
            onNameChange = {},
            onGlyphSelect = {},
            onDraw = {},
            onPersonAdd = {},
            onPersonNameChange = { _, _ -> },
            onPersonCommit = {},
            onPersonEdit = {},
            onPersonGlyphSheet = {},
            onPersonGlyphPick = { _, _ -> },
            onDrawForPerson = {},
            onPaceSelect = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "dark", showBackground = true, widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFontTall
@Composable
fun OnboardingPromise() = OnboardingStepPreview(OnboardingFixtures.promise)

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "dark", showBackground = true, widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFontTall
@Composable
fun OnboardingConsent() = OnboardingStepPreview(OnboardingFixtures.consent)

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "dark", showBackground = true, widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFontTall
@Composable
fun OnboardingReasons() = OnboardingStepPreview(OnboardingFixtures.reasons)

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "dark", showBackground = true, widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFontTall
@Composable
fun OnboardingYou() = OnboardingStepPreview(OnboardingFixtures.you)

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "dark", showBackground = true, widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFontTall
@Composable
fun OnboardingPeople() = OnboardingStepPreview(OnboardingFixtures.people)

@PreviewTest
@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Preview(name = "dark", showBackground = true, widthDp = 390, heightDp = 844, uiMode = UI_MODE_NIGHT_YES)
@PreviewFrench
@PreviewLargeFontTall
@Composable
fun OnboardingPace() = OnboardingStepPreview(OnboardingFixtures.pace)
