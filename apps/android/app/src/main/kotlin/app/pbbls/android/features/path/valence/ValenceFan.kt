package app.pbbls.android.features.path.valence

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.zIndex
import app.pbbls.android.R
import app.pbbls.android.features.path.create.valencePolarityLabelRes
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.OutlineAssets
import app.pbbls.android.features.path.render.wobble.WobbleRenderer
import app.pbbls.android.theme.Spacing
import kotlin.math.min

/** Opacity of the eight stones that are not the chosen one. */
private const val DIMMED_OPACITY = 0.45f
private const val SELECTED_SCALE = 1.14f

/** Android's minimum comfortable target; the small stones are under it on both axes. */
private val MinimumHitTarget = 44.dp

/** The tallest state: the large hand word, the span, and the pyramid. */
private val LockupHeight = 144.dp

/** Long enough to read as a change of state, short enough to keep up with the roll's detents. */
private const val SELECTION_MS = 220

private const val TAG = "ValenceFan"

/**
 * The valence fan: nine real pebble stones arranged bottom-up, small and near
 * at the bottom, large and spread at the top, over a two-axis roll — ports iOS
 * `ValencePickerContent.swift`.
 *
 * Presentation only (D5). Shared by `ValencePickerSheet`, which stages and
 * commits on Done, and the record flow's valence step, which commits in place
 * and advances on Continue. Same fan, different commit semantics.
 *
 * The day/week/month wording that used to head each row is gone from the screen
 * — size carries it now — but `valence_group_*_name` still forms the TalkBack
 * label, so a screen reader hears "Day event, Highlight" as before.
 */
@Composable
fun ValenceFan(
    selected: Valence?,
    onSelect: (Valence) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // The reference canvas is authored at the content width of the
            // narrowest phone iOS supports, and iOS renders it at exactly that,
            // gaining side margin on wider screens. Compose can do better with
            // no risk: `BoxWithConstraints` reports a bounded width even inside
            // a vertical scroll, where SwiftUI's `GeometryReader` has no ideal
            // height to offer and the fan collapses. So the fan never overflows
            // a narrower screen — it shrinks to fit and is otherwise identical.
            val available = maxWidth
            val scale =
                if (available.isFinite) min(1f, available / ValenceFanLayout.REFERENCE_WIDTH.dp) else 1f

            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(
                            width = ValenceFanLayout.REFERENCE_WIDTH.dp * scale,
                            height = ValenceFanLayout.REFERENCE_HEIGHT.dp * scale,
                        ),
            ) {
                Valence.entries.forEach { valence ->
                    Stone(
                        valence = valence,
                        isSelected = valence == selected,
                        hasSelection = selected != null,
                        scale = scale,
                        reduceMotion = reduceMotion,
                        onSelect = onSelect,
                    )
                }
            }
        }

        // Reserves the tallest lockup's height, bottom-anchored, so rolling
        // between sizes never shoves the fan up and down the screen.
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = LockupHeight),
            contentAlignment = Alignment.BottomCenter,
        ) {
            // The step arrives already parked on a valence, so the roll always
            // has something under the finger. Null only happens in previews and
            // in the edit sheet before it stages a value.
            ValenceRoll(valence = selected ?: Valence.NEUTRAL_MEDIUM, onChange = onSelect)
        }
    }
}

@Composable
private fun Stone(
    valence: Valence,
    isSelected: Boolean,
    hasSelection: Boolean,
    scale: Float,
    reduceMotion: Boolean,
    onSelect: (Valence) -> Unit,
) {
    val size = valence.sizeGroup
    val stoneHeight = ValenceFanLayout.stoneHeight(size).dp * scale
    val stoneWidth = ValenceFanLayout.stoneWidth(size).dp * scale
    // The tap target never shrinks below the comfortable minimum, whatever the
    // stone does — the small stones are under it on both axes.
    val targetWidth = maxOf(stoneWidth, MinimumHitTarget)
    val targetHeight = maxOf(stoneHeight, MinimumHitTarget)

    val lift by animateFloatAsState(
        targetValue = if (isSelected && !reduceMotion) SELECTED_SCALE else 1f,
        animationSpec = tween(durationMillis = SELECTION_MS),
        label = "valenceStoneLift",
    )
    val dim by animateFloatAsState(
        targetValue = if (hasSelection && !isSelected) DIMMED_OPACITY else 1f,
        animationSpec = tween(durationMillis = SELECTION_MS),
        label = "valenceStoneDim",
    )

    val label =
        "${stringResource(valenceGroupNameRes(size))}, ${stringResource(valencePolarityLabelRes(valence.polarity))}"
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier =
            Modifier
                .zIndex(if (isSelected) 1f else 0f)
                .offset(
                    x = ValenceFanLayout.centreX(valence).dp * scale - targetWidth / 2,
                    y = ValenceFanLayout.centreY(valence).dp * scale - targetHeight / 2,
                ).size(width = targetWidth, height = targetHeight)
                // No ripple: a stone is the affordance, and a Material circle
                // washing over it reads as a bug (the iOS `.plain` button style).
                .clickable(interactionSource = interaction, indication = null) { onSelect(valence) }
                .semantics {
                    contentDescription = label
                    this.selected = isSelected
                },
        contentAlignment = Alignment.Center,
    ) {
        ValenceStone(
            valence = valence,
            height = stoneHeight,
            isSelected = isSelected,
            modifier =
                Modifier.graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                    alpha = dim
                },
        )
    }
}

private fun valenceGroupNameRes(size: ValenceSizeGroup): Int =
    when (size) {
        ValenceSizeGroup.SMALL -> R.string.valence_group_small_name
        ValenceSizeGroup.MEDIUM -> R.string.valence_group_medium_name
        ValenceSizeGroup.LARGE -> R.string.valence_group_large_name
    }

/**
 * Mirrors the private helper in `WelcomeScreen`, deliberately copied rather
 * than lifted: the original is inside a shipped screen and hoisting it is a
 * refactor of code this change has no other business in.
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

/**
 * Wobbles all nine silhouettes and artworks ahead of the fan needing them.
 *
 * Composing the step wobbles eighteen assets on the first frame, which is a
 * visible hitch on the main thread. Both caches are process-wide and safe to
 * warm twice, so the flow kicks this off on a background dispatcher when it
 * opens — two steps of runway is plenty.
 */
internal fun prewarmValenceStones(context: Context) {
    Valence.entries.forEach { valence ->
        val key = valenceAssetKey(valence)
        try {
            WobbleRenderer.backdropArt(key, readRawText(context, OutlineAssets.resId(valence.sizeGroup, valence.polarity)))
            ValenceArt.art(key, readRawText(context, ValenceArtAssets.resId(valence.sizeGroup, valence.polarity)))
        } catch (e: Exception) {
            // A missing or unreadable bundled asset is a setup bug; the stone's
            // own fallback still renders around it, so this must not take the
            // flow down with it.
            Log.e(TAG, "valence prewarm failed for $key", e)
        }
    }
}
