package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.AuthMode
import app.pbbls.android.core.model.PromiseLetter
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

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun OnboardingScreenLight() {
    PebblesTheme {
        OnboardingContent(
            state =
                OnboardingUiState(
                    letter =
                        PromiseLetter(
                            bodyEn = "There will never be a feed here.\n\nWhat you write stays yours.",
                            bodyFr = "Il n'y aura jamais de fil ici.\n\nCe que tu écris reste à toi.",
                            signedByEn = "The Pebbles team",
                            signedByFr = "L'équipe Pebbles",
                        ),
                ),
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
            onPersonRemove = {},
            onPersonGlyphSheet = {},
            onPersonGlyphPick = { _, _ -> },
            onDrawForPerson = {},
        )
    }
}
