package app.pbbls.android.features.welcome

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.GoogleSignInButton
import app.pbbls.android.core.designsystem.LegalDisclaimer
import app.pbbls.android.core.designsystem.LegalDoc
import app.pbbls.android.core.designsystem.PebblesActionHeight
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.openLegalDoc
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.rive.RiveLogo
import kotlinx.coroutines.delay

private const val TAG = "welcome"

// One entry per revealed element, in fade-in order. iOS's 7th step (Continue
// with Apple) is dropped — no Apple sign-in on Android (settled non-goal).
private const val REVEAL_STEPS = 6
private val REVEAL_SCHEDULE_MILLIS = longArrayOf(0, 200, 450, 600, 750, 1100)

/**
 * Pre-login landing AND splash — the `WelcomeView` analog. `RootScreen` keeps
 * this mounted for the whole splash hold so the Rive logo plays through without a
 * view-swap. While [contentRevealed] is false only the logo shows, centered; when
 * the parent flips it true, the carousel + buttons + disclaimer fade in one-by-one
 * on a timed schedule (all at once under reduced motion).
 *
 * Email buttons navigate to Auth via the parent NavHost; [onGoogleSignIn] is a
 * suspend action (hosted OAuth) supplied by the parent — the screen owns only the
 * in-flight/error view state, keeping business logic out of the view.
 */
@Composable
fun WelcomeScreen(
    contentRevealed: Boolean,
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    WelcomeContent(
        uiState = uiState,
        contentRevealed = contentRevealed,
        onCreateAccount = onCreateAccount,
        onLogin = onLogin,
        onGoogleSignIn = viewModel::signInWithGoogle,
        modifier = modifier,
    )
}

/**
 * Stateless Welcome hero — what the screenshots drive. Takes its state and its
 * callbacks rather than reading a ViewModel, so it renders without Hilt.
 */
@Composable
fun WelcomeContent(
    uiState: WelcomeUiState,
    contentRevealed: Boolean,
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val inspection = LocalInspectionMode.current

    // The reveal stays in the composable: it is presentation driven by
    // `contentRevealed` and the reduce-motion setting, with nothing to survive.
    var revealStep by remember { mutableIntStateOf(if (inspection) REVEAL_STEPS else 0) }

    LaunchedEffectReveal(contentRevealed, reduceMotion, revealStep) { revealStep = it }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .readableWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            RiveLogo(
                modifier =
                    Modifier
                        .fillMaxWidth(0.33f)
                        .aspectRatio(1f),
            )
            Spacer(modifier = Modifier.weight(1f))

            if (revealStep >= 1) {
                WelcomeRevealedContent(
                    revealStep = revealStep,
                    reduceMotion = reduceMotion,
                    isSubmitting = uiState.isSubmitting,
                    authErrorRes = uiState.authErrorRes,
                    onCreateAccount = onCreateAccount,
                    onLogin = onLogin,
                    onGoogleSignIn = onGoogleSignIn,
                    onTermsTap = { openLegalDoc(context, LegalDoc.TERMS) },
                    onPrivacyTap = { openLegalDoc(context, LegalDoc.PRIVACY) },
                )
            }
        }
    }
}

@Composable
private fun WelcomeRevealedContent(
    revealStep: Int,
    reduceMotion: Boolean,
    isSubmitting: Boolean,
    @StringRes authErrorRes: Int?,
    onCreateAccount: () -> Unit,
    onLogin: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onTermsTap: () -> Unit,
    onPrivacyTap: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WelcomeCarousel(
            reduceMotion = reduceMotion,
            modifier =
                Modifier
                    .revealAlpha(revealStep >= 2)
                    .padding(bottom = 24.dp),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PebblesPrimaryButton(
                text = stringResource(R.string.welcome_create_account),
                onClick = onCreateAccount,
                enabled = !isSubmitting,
                modifier = Modifier.revealAlpha(revealStep >= 3),
            )
            WelcomeOutlineButton(
                text = stringResource(R.string.welcome_log_in),
                onClick = onLogin,
                enabled = !isSubmitting,
                modifier = Modifier.revealAlpha(revealStep >= 4),
            )
            GoogleSignInButton(
                onClick = onGoogleSignIn,
                enabled = !isSubmitting,
                modifier = Modifier.revealAlpha(revealStep >= 5),
            )
            authErrorRes?.let { messageRes ->
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LegalDisclaimer(
                onTermsTap = onTermsTap,
                onPrivacyTap = onPrivacyTap,
                modifier =
                    Modifier
                        .revealAlpha(revealStep >= 6)
                        .padding(top = 8.dp)
                        .fillMaxWidth(),
            )
        }
    }
}

/** The secondary "Log in" action: a stock outlined button at the funnel's action height. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeOutlineButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(PebblesActionHeight),
        modifier = modifier.fillMaxWidth().heightIn(min = PebblesActionHeight),
        enabled = enabled,
        contentPadding = ButtonDefaults.contentPaddingFor(PebblesActionHeight),
    ) {
        Text(text = text, style = ButtonDefaults.textStyleFor(PebblesActionHeight))
    }
}

/** Fades content in as its reveal step is reached. */
@Composable
private fun Modifier.revealAlpha(visible: Boolean): Modifier {
    val target by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "welcomeReveal",
    )
    return this.alpha(target)
}

/**
 * Runs the timed reveal cadence once [contentRevealed] flips true. Extracted so
 * the `LaunchedEffect` keys stay explicit. Under reduced motion, jumps straight
 * to fully revealed.
 */
@Composable
private fun LaunchedEffectReveal(
    contentRevealed: Boolean,
    reduceMotion: Boolean,
    currentStep: Int,
    onStep: (Int) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(contentRevealed, reduceMotion) {
        if (!contentRevealed || currentStep >= REVEAL_STEPS) return@LaunchedEffect
        if (reduceMotion) {
            onStep(REVEAL_STEPS)
            return@LaunchedEffect
        }
        var previous = 0L
        REVEAL_SCHEDULE_MILLIS.forEachIndexed { index, at ->
            val wait = at - previous
            if (wait > 0) delay(wait)
            onStep(index + 1)
            previous = at
        }
    }
}
