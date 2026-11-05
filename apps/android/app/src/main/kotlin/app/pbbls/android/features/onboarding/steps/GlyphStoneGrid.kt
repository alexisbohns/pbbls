package app.pbbls.android.features.onboarding.steps

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.stone.StoneGridLayout
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase

/** The starter grid's seed, the same on every surface (D7). */
const val GLYPH_STONE_SEED = "onboarding-glyphs"

/** The draw piece's tag, for the tests. */
const val ONBOARDING_GLYPH_DRAW_TAG = "onboarding_glyph_draw"

/** The piece of [glyphId], for the tests. */
fun onboardingGlyphTag(glyphId: String?): String = "onboarding_glyph_${glyphId ?: "empty"}"

private const val GRID_COLUMNS = 3
private const val GRID_ROWS = 3
private const val FOLDED_COLUMNS = 4
private const val GRID_GLYPHS = GRID_ROWS * GRID_COLUMNS - 1
private const val FOLDED_GLYPHS = FOLDED_COLUMNS - 1

/**
 * The 3×3 split stone of glyphs (#1510 D7): eight glyphs (the system ones first, then any carved
 * in the flow; the last eight) and a "Draw" piece marked by a dashed inset. The chosen piece is
 * `primaryContainer`, the rest `surfaceContainer`, no outlines. [folded] is the keyboard-up state
 * of step 4: one row of four, the chosen glyph, two others and Draw.
 */
@Composable
fun GlyphStoneGrid(
    glyphs: List<Glyph>,
    selectedId: String?,
    onSelect: (Glyph) -> Unit,
    onDraw: () -> Unit,
    modifier: Modifier = Modifier,
    folded: Boolean = false,
    @StringRes drawLabelRes: Int = R.string.onboarding_you_draw,
) {
    val colors = MaterialTheme.colorScheme
    // The chosen glyph first, so it is never cut off the stone once carved ones push past eight.
    val ordered = listOfNotNull(glyphs.firstOrNull { it.id == selectedId }) + glyphs.filter { it.id != selectedId }
    val shown: List<Glyph?> = ordered.padTo(if (folded) FOLDED_GLYPHS else GRID_GLYPHS) + null
    val rows = if (folded) 1 else GRID_ROWS
    val columns = if (folded) FOLDED_COLUMNS else GRID_COLUMNS
    val fills =
        shown.map { glyph ->
            if (glyph != null && glyph.id == selectedId) colors.primaryContainer else colors.surfaceContainer
        }
    val dashColor = colors.outline
    val dashCorner = MaterialTheme.shapes.large.topStart
    StoneGridLayout(
        rows = rows,
        columns = columns,
        seed = if (folded) "$GLYPH_STONE_SEED-folded" else GLYPH_STONE_SEED,
        fills = fills,
        modifier = modifier.selectableGroup(),
        minCellHeight = if (folded) FOLDED_CELL_HEIGHT else CELL_HEIGHT,
    ) { index, column ->
        val glyph = shown[index]
        val isDraw = index == shown.lastIndex
        val selected = glyph != null && glyph.id == selectedId
        val drawLabel = stringResource(drawLabelRes)
        val label =
            when {
                isDraw -> drawLabel
                glyph != null -> glyphSpokenName(glyph) ?: stringResource(R.string.onboarding_glyph_nth, index + 1)
                else -> null
            }
        Box(
            Modifier
                .fillMaxSize()
                .pieceClip(column)
                .then(
                    if (isDraw) {
                        Modifier.clickable(role = Role.Button, onClick = onDraw)
                    } else {
                        Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            enabled = glyph != null,
                            onClick = { glyph?.let(onSelect) },
                        )
                    },
                ).semantics { if (label != null) contentDescription = label }
                .testTag(if (isDraw) ONBOARDING_GLYPH_DRAW_TAG else onboardingGlyphTag(glyph?.id)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isDraw ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(Spacing.md)
                            .drawBehind {
                                val stroke = DASH_WIDTH.toPx()
                                val inset = stroke / 2f
                                drawRoundRect(
                                    color = dashColor,
                                    topLeft = Offset(inset, inset),
                                    size = Size(size.width - stroke, size.height - stroke),
                                    cornerRadius = CornerRadius(dashCorner.toPx(size, this) - inset),
                                    style =
                                        Stroke(
                                            width = stroke,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx())),
                                        ),
                                )
                            }.padding(Spacing.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
                    ) {
                        PbblsIconImage(PbblsIcon.PLUS_CIRCLE, PbblsIconSize.TILE, contentDescription = null, tint = colors.onSurfaceVariant)
                        if (!folded) {
                            Text(
                                drawLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                glyph != null ->
                    GlyphView(
                        case = if (selected) GlyphViewCase.SELECTED else GlyphViewCase.DEFAULT,
                        strokes = glyph.strokes,
                        viewBox = glyph.viewBox,
                        side = if (folded) FOLDED_GLYPH_SIDE else GLYPH_SIDE,
                    )
            }
        }
    }
}

/** The name TalkBack may read for [glyph]: a system glyph's name is an internal key ("domain:family"), never copy. */
internal fun glyphSpokenName(glyph: Glyph): String? = glyph.name?.takeUnless { glyph.isSystem }

/** [this] with trailing empty places up to [size], so the stone keeps its cut while glyphs load. */
private fun List<Glyph>.padTo(size: Int): List<Glyph?> = take(size) + List((size - this.size).coerceAtLeast(0)) { null }

private val CELL_HEIGHT = 96.dp
private val FOLDED_CELL_HEIGHT = 72.dp
private val GLYPH_SIDE = 56.dp
private val FOLDED_GLYPH_SIDE = 44.dp
private val DASH_WIDTH = 1.5.dp
private val DASH = 6.dp
