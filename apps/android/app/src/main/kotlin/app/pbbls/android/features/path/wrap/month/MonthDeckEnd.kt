package app.pbbls.android.features.path.wrap.month

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.wrap.MonthWrap
import app.pbbls.android.features.path.wrap.WrapLookups
import app.pbbls.android.features.path.wrap.intro.IntroKey
import app.pbbls.android.features.path.wrap.intro.IntroReveal
import app.pbbls.android.features.path.wrap.intro.LocalIntroStage
import app.pbbls.android.features.path.wrap.intro.currentIntroReveal
import app.pbbls.android.features.path.wrap.intro.introReveal
import app.pbbls.android.features.path.wrap.week.rememberWrapCopy

/**
 * The deck's last card (#1242 D14; iOS `MonthDeckEnd`), the back of the deck: the cover's whole garland with
 * the month inside it, settled, and a button that replays the month from the cover; the month's name alone
 * when it has no cover. Centred in the card's room, [MonthDeckLayout.END_GAP] apart. In the intro the card
 * arrives on a tick and its button rises (`IntroScore.end`).
 */
@Composable
internal fun MonthDeckEnd(
    wrap: MonthWrap,
    lookups: WrapLookups,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PebblesTheme.spine.colors
    val type = PebblesTheme.spine.type
    val copy = rememberWrapCopy()
    val reveal = currentIntroReveal()
    Column(
        // The room's whole height, so the arrangement centres the two in it.
        modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(MonthDeckLayout.END_GAP, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (wrap.cover != null) {
            // Whole from the start, whatever the end card's turn: no clock reaches it.
            CompositionLocalProvider(LocalIntroStage provides null) {
                MonthCoverGarland(wrap, lookups)
            }
        } else {
            Text(
                copy.monthName(wrap.month),
                style = type.monthName,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
        }
        OutlinedButton(onClick = onReplay, modifier = Modifier.introReveal(reveal, IntroKey.Replay).tapsOnceShown(reveal)) {
            Icon(painterResource(R.drawable.ic_replay), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(copy.replayMonth())
        }
    }
}

/**
 * The replay takes a tap only once it shows (iOS: SwiftUI never hit-tests a view at opacity 0). Before its beat
 * the button is drawn at alpha 0 but would still hit-test, so a tap that skips the card's arrival would replay
 * too: its down is consumed first, in the Initial pass, and the button never presses. The card's `onAnyTap`
 * still sees the release, so the tap lands the card. Settled, it returns `this`.
 */
private fun Modifier.tapsOnceShown(reveal: IntroReveal): Modifier =
    if (reveal === IntroReveal.settled) {
        this
    } else {
        pointerInput(reveal) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (reveal(IntroKey.Replay) == 0f) down.consume()
            }
        }
    }
