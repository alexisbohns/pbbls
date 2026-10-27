package app.pbbls.android.features.path.wrap.intro

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.designsystem.rememberTouchExploration

/**
 * Whether the intro plays at all (#1242 D20; iOS `IntroPolicy`): not under Reduce Motion, not while
 * TalkBack explores by touch, where hidden and moving parts would fight its focus, and not at a large
 * text size, where a card scaled to fit would read too small. The month is then the plain page, settled.
 */
object IntroPolicy {
    /** The Pixel's 1.5, 1.8 and 2.0 steps: past iOS's xxxLarge (1.35×), its accessibility sizes. */
    const val LARGE_TEXT = 1.5f

    fun isLargeText(fontScale: Float): Boolean = fontScale >= LARGE_TEXT

    fun plays(
        reduceMotion: Boolean,
        touchExploration: Boolean,
        largeText: Boolean,
    ): Boolean = !reduceMotion && !touchExploration && !largeText
}

/** [IntroPolicy] over this composition's settings. False in previews (`rememberReduceMotion` is true there). */
@Composable
fun rememberIntroAllowed(): Boolean =
    IntroPolicy.plays(
        reduceMotion = rememberReduceMotion(),
        touchExploration = rememberTouchExploration(),
        largeText = IntroPolicy.isLargeText(LocalDensity.current.fontScale),
    )
