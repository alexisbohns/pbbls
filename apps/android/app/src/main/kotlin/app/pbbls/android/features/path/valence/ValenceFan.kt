package app.pbbls.android.features.path.valence

import android.content.Context
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.zIndex
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.rememberReduceMotion
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.ValencePolarity
import app.pbbls.android.core.model.ValenceSizeGroup
import app.pbbls.android.core.ui.render.OutlineAssets
import app.pbbls.android.core.ui.render.wobble.WobbleRenderer
import app.pbbls.android.features.path.card.GhostStone
import app.pbbls.android.features.path.create.valencePolarityLabelRes
import kotlin.math.min
import kotlin.math.roundToInt

/** Opacity of the eight stones that are not the chosen one. With nothing chosen all nine stay at full strength. */
private const val DIMMED_OPACITY = 0.45f

/** The empty slot's ghost stone, as wide as iOS draws it. */
private val GhostWidth = 56.dp

/** The most the fan's labels follow the system font scale. */
private const val MAX_LABEL_FONT_SCALE = 1.2f

/** Capitals carry 0.08 em in iOS's meta and card-heading ranks. */
private val CapsTracking = 0.08f.em

/** The Android 48 dp touch floor (iOS uses 44 pt); the small stones are under it on both axes. */
private val MinimumHitTarget = 48.dp

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
 * commits on Done, and Complete's valence step, which commits in place
 * and advances on Continue. Same fan, different commit semantics.
 *
 * Month, Week and Day up the margin and Lowlight, Neutral and Highlight under the fan are decorative
 * (D7): `valence_group_*_name` still forms each stone's TalkBack label, so a screen reader hears
 * "Day event, Highlight". With nothing chosen all nine stones draw at full strength and a ghost stone
 * holds the lockup's slot.
 */
@Composable
fun ValenceFan(
    selected: Valence?,
    onSelect: (Valence) -> Unit,
    modifier: Modifier = Modifier,
    /** False draws the fan alone: the help sheet's lit stone is its own explanation. */
    showsCaption: Boolean = true,
    /** The most of the reference canvas the fan may take: the help sheet draws it at 0.62. */
    maxScale: Float = 1f,
    /** False draws a picture: no taps, nothing for TalkBack to act on. */
    interactive: Boolean = true,
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
                if (available.isFinite) min(maxScale, available / ValenceFanLayout.REFERENCE_WIDTH.dp) else maxScale

            // The canvas with the column labels hung under it: the labels' own height decides the
            // column's, so a larger font never overlaps what follows.
            Column(modifier = Modifier.align(Alignment.Center)) {
                Box(
                    modifier =
                        Modifier.size(
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
                            interactive = interactive,
                            onSelect = onSelect,
                        )
                    }
                    RowLabels(scale)
                }
                ColumnLabels(scale)
            }
        }

        if (showsCaption) {
            // Reserves the tallest lockup's height, bottom-anchored, so rolling
            // between sizes never shoves the fan up and down the screen.
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = LockupHeight),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Until a pick (D7) a ghost stone and a prompt hold the slot. `null` also reaches
                // here from previews; the edit sheet always stages a value, so it never shows it.
                if (selected != null) {
                    ValenceRoll(valence = selected, onChange = onSelect)
                } else {
                    EmptyCaption()
                }
            }
        }
    }
}

/** The ghost stone and "Swipe or tap to choose": the fan's slot before the first touch. */
@Composable
private fun EmptyCaption() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Decorative: the prompt says what to do.
        GhostStone(width = GhostWidth, ink = MaterialTheme.colorScheme.outline, modifier = Modifier.clearAndSetSemantics {})
        Text(
            text = stringResource(R.string.valence_ghost_prompt).uppercase(LocalLocale.current.platformLocale),
            style = PebblesTheme.page.actionLabel.copy(letterSpacing = CapsTracking),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Month, Week and Day up the left margin, each in the box [ValenceFanLayout.rowLabelBox] keeps clear of
 * every stone, even a chosen, lifted one. They shrink with the fan. Decorative: each stone's own
 * description already says both axes.
 */
@Composable
private fun RowLabels(scale: Float) {
    ValenceSizeGroup.entries.forEach { size ->
        val box = ValenceFanLayout.rowLabelBox(size)
        Box(
            modifier =
                Modifier
                    .offset(x = box.left.dp * scale, y = box.top.dp * scale)
                    .shrunkBy(scale)
                    .size(width = box.width.dp, height = box.height.dp)
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.CenterStart,
        ) {
            AxisLabel(stringResource(valenceAxisSizeRes(size)))
        }
    }
}

/** Lowlight, Neutral and Highlight under the fan, spread across its width. Decorative, like [RowLabels]. */
@Composable
private fun ColumnLabels(scale: Float) {
    Row(
        modifier =
            Modifier
                .shrunkBy(scale)
                .requiredWidth(ValenceFanLayout.REFERENCE_WIDTH.dp)
                .padding(horizontal = Spacing.sm)
                .padding(top = Spacing.sm)
                .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ValencePolarity.entries.forEach { AxisLabel(stringResource(valencePolarityLabelRes(it))) }
    }
}

/** The meta rank iOS draws these in: Inclusive Sans 12, medium, capitals at 0.08 em, in the quiet text role. */
@Composable
private fun AxisLabel(text: String) {
    // The labels sit in fixed boxes between the rings and share a row: past 1.2x they would run into each
    // other and into the stones. They are decorative, so the one place the font scale is capped.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, min(density.fontScale, MAX_LABEL_FONT_SCALE))) {
        AxisLabelText(text)
    }
}

@Composable
private fun AxisLabelText(text: String) {
    Text(
        text = text.uppercase(LocalLocale.current.platformLocale),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = CapsTracking),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        modifier = Modifier.wrapContentSize(unbounded = true),
    )
}

/**
 * Lays [this] out at its natural size and reports [scale] of it, drawing it scaled about its top-left
 * corner: a label that shrinks with the fan keeps the fan's proportions without naming a font size.
 */
private fun Modifier.shrunkBy(scale: Float): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }

private fun valenceAxisSizeRes(size: ValenceSizeGroup): Int =
    when (size) {
        ValenceSizeGroup.LARGE -> R.string.valence_axis_month
        ValenceSizeGroup.MEDIUM -> R.string.valence_axis_week
        ValenceSizeGroup.SMALL -> R.string.valence_axis_day
    }

@Composable
private fun Stone(
    valence: Valence,
    isSelected: Boolean,
    hasSelection: Boolean,
    scale: Float,
    reduceMotion: Boolean,
    interactive: Boolean,
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
        targetValue = if (isSelected && !reduceMotion) ValenceFanLayout.SELECTED_SCALE else 1f,
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
                .then(
                    if (interactive) {
                        Modifier.clickable(interactionSource = interaction, indication = null) { onSelect(valence) }
                    } else {
                        Modifier
                    },
                ).semantics {
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
