package app.pbbls.android.features.welcome

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.components.GoogleSignInButton
import app.pbbls.android.components.LegalDisclaimer
import app.pbbls.android.components.LegalDoc
import app.pbbls.android.components.PebblesPrimaryButton
import app.pbbls.android.components.openLegalDoc
import app.pbbls.android.rive.RiveLogo
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// One entry per revealed element, in fade-in order. iOS's 7th step (Continue
// with Apple) is dropped — no Apple sign-in on Android (settled non-goal).
private const val REVEAL_STEPS = 6
private val REVEAL_SCHEDULE_MILLIS = longArrayOf(0, 200, 450, 600, 750, 1100)

private val ErrorRed = Color(0xFFDC2626)

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
    onGoogleSignIn: suspend () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val inspection = LocalInspectionMode.current
    val scope = rememberCoroutineScope()

    var isSubmitting by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var revealStep by remember { mutableIntStateOf(if (inspection) REVEAL_STEPS else 0) }

    LaunchedEffectReveal(contentRevealed, reduceMotion, revealStep) { revealStep = it }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(system.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
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
                    isSubmitting = isSubmitting,
                    authError = authError,
                    onCreateAccount = onCreateAccount,
                    onLogin = onLogin,
                    onGoogleSignIn = {
                        if (!isSubmitting) {
                            scope.launch {
                                isSubmitting = true
                                authError = null
                                try {
                                    onGoogleSignIn()
                                } catch (e: Exception) {
                                    authError = e.message
                                }
                                isSubmitting = false
                            }
                        }
                    },
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
    authError: String?,
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
            if (authError != null) {
                Text(
                    text = authError,
                    style = PebblesTypography.subhead.copy(fontSize = 12.sp),
                    color = ErrorRed,
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

/** Outlined accent capsule for the secondary "Log in" action. */
@Composable
private fun WelcomeOutlineButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = PebblesTheme.colors.accent
    val shape = RoundedCornerShape(50)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(shape)
                .border(1.dp, accent.primary, shape)
                .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = PebblesTypography.buttonLabel, color = accent.primary)
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

/**
 * The Android analog of SwiftUI's `accessibilityReduceMotion`. Compose has no
 * first-class signal, so this reads the system animator duration scale — `0` when
 * the user disabled animations. In `@Preview`/screenshot rendering
 * ([LocalInspectionMode]) it returns `true` so the reveal renders fully in one
 * frame.
 */
@Composable
private fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
