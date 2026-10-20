package app.pbbls.android.core.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * The scope a card flies in between its slot on the Spine and the held card (#1135, pebble
 * card spec §Behaviour, open and close): `RootScreen`'s `SharedTransitionLayout` around the
 * one `NavDisplay`, provided only while a detail covers its list (one pane). Beside the list
 * on two panes it is null, and the detail fades through as before.
 *
 * This is UI plumbing, not a service: nothing calls it, and leaf modifiers read it where
 * they lay out, as `LocalSheetOverlaySlot` is read. The android CLAUDE.md rule of exactly
 * three locals governs services a screen calls, which still come through `hiltViewModel()`.
 * Null by default, so previews, screenshot tests and the debug Spine Lab need no setup and
 * get no flight. (A decision-log entry records this in the card stack's last part.)
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The bounds key both ends of a card's flight share: one per moment. */
fun sharedCardKey(momentId: String): String = "card-$momentId"

/**
 * The card shell's end of the flight, for the moment [momentId]: the Spine's card and the
 * held card each put it on their shell, outside its shadow and clip, so the whole card
 * flies. A no-op without a [LocalSharedTransitionScope], or when not [enabled] (a stone's
 * press opens the verso with no flight; a card printing into its place stays put).
 *
 * Scaled, not remeasured: each card keeps the layout it has at rest and is scaled to the
 * flying bounds' width, top aligned, so the photo grows in place while the rest of the held
 * card is revealed under it. Remeasuring would lay out the held card's two faces (one of
 * them subcomposed twice) at a new width on every frame. Not clipped to the card's corners:
 * the held card's foot overhangs the flying bounds while it fades, but with an
 * `OverlayClip` the detail page drew nothing from the first frame of the close on a Pixel 8,
 * a black flash until the Path faded in; without one it fades out as it should.
 *
 * Reads `LocalNavAnimatedContentScope`, so it must sit inside a `NavDisplay` entry whenever
 * the scope is provided, which the root guarantees.
 */
@Composable
fun sharedCardBounds(
    momentId: String,
    enabled: Boolean = true,
): Modifier {
    val scope = LocalSharedTransitionScope.current
    if (scope == null || !enabled) return Modifier
    val visibility = LocalNavAnimatedContentScope.current
    return with(scope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(sharedCardKey(momentId)),
            animatedVisibilityScope = visibility,
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter),
        )
    }
}
